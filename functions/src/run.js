"use strict";

const notaParana = require("./notaParana");
const rules = require("./trackingRules");
const message = require("./message");
const notify = require("./notify");

/**
 * Uma rodada da rotina de alertas.
 *
 * Recebe db, messaging e logger de fora para os testes poderem rodar a rodada
 * inteira sem Firestore nem FCM. A hora tambem entra por parametro, senao a
 * janela de 24 horas so seria testavel com dados fabricados no instante do
 * teste.
 */
async function run({ db, messaging, logger, now = Date.now(), search = notaParana.searchLowestPrice }) {
  const [trackings, groupsById, tokensByDevice] = await Promise.all([
    loadTrackings(db),
    loadGroups(db),
    loadDeviceTokens(db),
  ]);

  const plans = [];

  for (const tracking of trackings) {
    const plan = rules.resolvePlan(tracking, groupsById);

    // Pausado ou sem alvo nao e consultado: gastaria uma chamada a Nota Parana
    // - e a paciencia do IP - para uma comparacao que nao existe.
    if (plan.active && plan.targetPrice !== null && plan.targetPrice !== undefined && tracking.barCode) {
      plans.push({ tracking, plan });
    }
  }

  if (plans.length === 0) {
    logger.info("Nenhum produto rastreado ativo com alvo definido; rodada encerrada.");
    return { checked: 0, notified: 0, failed: 0, discarded: 0 };
  }

  const barCodes = [...new Set(plans.map(({ tracking }) => tracking.barCode))];
  const offersByBarCode = new Map();
  const failures = new Map();
  let discarded = 0;

  // Uma consulta por codigo de barras, uma de cada vez. O espacador ja segura o
  // intervalo, mas o laco sequencial deixa isso explicito: disparar tudo em
  // paralelo e o que atrai a marcacao de raspagem.
  for (const barCode of barCodes) {
    try {
      const result = await search(barCode);
      offersByBarCode.set(barCode, result.offers);
      discarded += result.discarded;

      if (result.discarded > 0) {
        // Sem este aviso, um IP marcado se parece com "nenhum produto atingiu o
        // alvo": a resposta envenenada vem inteira forjada, a limpeza esvazia
        // tudo e a rodada termina em silencio.
        logger.warn(
          `Nota Parana devolveu ${result.discarded} registro(s) forjado(s) para o gtin ${barCode}; ` +
            `sobraram ${result.offers.length}. IP possivelmente marcado como raspagem.`
        );
      }
    } catch (error) {
      // Falha de rede num produto nao derruba a rodada, e o produto fica sem
      // ser avaliado: tratar erro como "sem oferta" rearmaria o alerta de quem
      // ja tinha avisado, e o mesmo aviso voltaria na proxima rodada.
      failures.set(barCode, error);
      logger.error(`Falha ao consultar o gtin ${barCode}: ${error.message}`);
    }
  }

  const updates = [];
  let notified = 0;

  for (const { tracking, plan } of plans) {
    if (failures.has(tracking.barCode)) {
      continue;
    }

    const lowest = rules.lowestRecentOffer(offersByBarCode.get(tracking.barCode), now);
    const decision = rules.decide(plan, tracking.lastNotifiedPrice, lowest);
    const update = { lastCheckedAt: now };

    if (decision.notify) {
      const sent = await send({ db, messaging, logger, tracking, plan, lowest, tokensByDevice });

      if (sent) {
        notified++;
        update.lastNotifiedPrice = decision.lastNotifiedPrice;
        update.lastNotifiedAt = now;
      }
      // Envio falho nao grava lastNotifiedPrice: o alerta continua armado e a
      // proxima rodada tenta de novo. Gravar aqui perderia o aviso em silencio.
    } else if (decision.changed) {
      update.lastNotifiedPrice = decision.lastNotifiedPrice;
      update.lastNotifiedAt = null;
    }

    updates.push({ id: tracking.id, values: update });
  }

  await commit(db, updates, logger);

  const summary = {
    checked: updates.length,
    notified,
    failed: failures.size,
    discarded,
  };

  logger.info(
    `Rodada concluida: ${summary.checked} produto(s) avaliado(s), ${summary.notified} alerta(s), ` +
      `${summary.failed} consulta(s) com falha, ${summary.discarded} registro(s) forjado(s) descartado(s).`
  );

  return summary;
}

/**
 * Manda o alerta para o alcance que o plano pede.
 *
 * Escopo particular sem token nao vira alerta geral: um aviso destinado a um
 * aparelho chegando em todos e pior que aviso nenhum. Fica sem enviar e sem
 * gravar, entao a proxima rodada tenta de novo - o token reaparece assim que o
 * app for aberto naquele aparelho.
 */
async function send({ db, messaging, logger, tracking, plan, lowest, tokensByDevice }) {
  const alert = message.buildAlert({
    description: tracking.description,
    groupName: plan.groupName,
    price: lowest.price,
    targetPrice: plan.targetPrice,
    offer: lowest.offer,
  });

  let target;

  if (rules.isForAllDevices(plan)) {
    target = { topic: notify.TOPIC_ALL };
  } else {
    const token = tokensByDevice.get(plan.deviceId);

    if (!token) {
      logger.warn(
        `Alerta particular de "${tracking.description}" nao enviado: o aparelho ${plan.deviceId} ` +
          `nao tem token no Firestore. Continua armado para a proxima rodada.`
      );
      return false;
    }

    target = { token };
  }

  try {
    await messaging.send(notify.buildMessage(target, { ...alert, barCode: tracking.barCode }));
    logger.info(`Alerta enviado: ${alert.title} - ${alert.body.replace(/\n/g, " ")}`);
    return true;
  } catch (error) {
    logger.error(`Falha ao enviar o alerta de "${tracking.description}": ${error.message}`);
    return false;
  }
}

async function loadTrackings(db) {
  const snapshot = await db.collection("tracking").get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
}

async function loadGroups(db) {
  const snapshot = await db.collection("trackingGroup").get();
  return new Map(snapshot.docs.map((doc) => [doc.id, { id: doc.id, ...doc.data() }]));
}

/**
 * O id do documento e o ANDROID_ID, que e o mesmo deviceId gravado no
 * rastreamento - e a unica identidade que o app tem, ja que nao ha login.
 */
async function loadDeviceTokens(db) {
  const snapshot = await db.collection("device").get();
  const tokens = new Map();

  for (const doc of snapshot.docs) {
    const token = doc.data().fcmToken;

    if (token) {
      tokens.set(doc.id, token);
    }
  }

  return tokens;
}

/**
 * Grava campo a campo, nunca o documento inteiro: a tela pode ter mudado alvo,
 * escopo ou descricao enquanto a rodada consultava a API, e um set completo com
 * os dados lidos no comeco desfaria a edicao do usuario.
 */
async function commit(db, updates, logger) {
  if (updates.length === 0) {
    return;
  }

  const batch = db.batch();

  for (const { id, values } of updates) {
    batch.update(db.collection("tracking").doc(id), values);
  }

  try {
    await batch.commit();
    return;
  } catch (error) {
    // O lote e tudo ou nada, e basta o usuario apagar um rastreamento enquanto
    // a rodada consultava a API para o update daquele documento derrubar os
    // outros. Como os alertas ja foram enviados a esta altura, perder as
    // gravacoes faria o mesmo aviso sair de novo na proxima rodada.
    logger.warn(`Lote de gravacao falhou (${error.message}); gravando um a um.`);
  }

  for (const { id, values } of updates) {
    try {
      await db.collection("tracking").doc(id).update(values);
    } catch (error) {
      logger.error(`Falha ao gravar o rastreamento ${id}: ${error.message}`);
    }
  }
}

module.exports = { run };
