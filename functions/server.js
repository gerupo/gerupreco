"use strict";

/**
 * A rotina de alertas como servico de longa duracao, para rodar num container
 * no servidor de casa.
 *
 * E o terceiro ponto de entrada sobre o mesmo src/run.js. Os outros dois:
 * index.js (Cloud Functions, desativado - ver README) e run-once.js (uma rodada
 * e sai, para quem prefere agendar por cron do sistema).
 *
 * Este existe porque container e cron nao combinam bem: um container que executa
 * e termina, com --restart=unless-stopped, reinicia em laco fechado - e cada
 * volta e uma consulta a mais na Nota Parana. Entao o processo fica de pe e se
 * agenda por dentro.
 *
 * A porta HTTP nao e enfeite: um servico que so escreve em log e invisivel para
 * quem esta fora do container, e "nenhum alerta chegou" continuaria sem resposta
 * - o /health diz se a rodada aconteceu, quando, e o que ela viu.
 */

const http = require("node:http");

const admin = require("firebase-admin");

const { run } = require("./src/run");
const schedule = require("./src/schedule");

const PORT = Number(process.env.PORT || 3456);

/**
 * Gatilho manual desligado por padrao. Ligar exige definir um token, porque
 * disparar uma rodada envia push para os aparelhos - e a porta pode acabar
 * encaminhada para fora sem ninguem lembrar.
 */
const TOKEN = process.env.TRACKING_TOKEN || "";

const RUN_ON_START = process.env.RUN_ON_START === "1";

function stamp() {
  return new Date().toISOString();
}

const logger = {
  info: (line) => console.log(`${stamp()} INFO  ${line}`),
  warn: (line) => console.warn(`${stamp()} WARN  ${line}`),
  error: (line) => console.error(`${stamp()} ERROR ${line}`),
};

/** O que o /health responde. Vazio ate a primeira rodada terminar. */
const state = {
  startedAt: new Date().toISOString(),
  lastRunAt: null,
  lastSummary: null,
  lastError: null,
  nextRunAt: null,
  running: false,
};

let db;
let messaging;

async function runOnce(reason) {
  if (state.running) {
    logger.warn(`Rodada ja em andamento; ${reason} ignorado.`);
    return null;
  }

  state.running = true;

  try {
    logger.info(`Rodada iniciada (${reason}).`);
    const summary = await run({ db, messaging, logger });

    state.lastRunAt = new Date().toISOString();
    state.lastSummary = summary;
    state.lastError = null;

    // Resposta inteira forjada e indistinguivel de "nenhum produto atingiu o
    // alvo" para quem so ve o numero de alertas. Aqui o aviso e explicito,
    // porque num container ninguem le o log a toa.
    if (summary.discarded > 0 && summary.notified === 0) {
      logger.error(
        `Todas as consultas voltaram forjadas (${summary.discarded} registros descartados). ` +
          "Este IP esta sendo tratado como raspagem pela Nota Parana."
      );
    }

    return summary;
  } catch (error) {
    state.lastRunAt = new Date().toISOString();
    state.lastError = error.message;
    logger.error(`Rodada abortada: ${error.stack || error.message}`);
    return null;
  } finally {
    state.running = false;
  }
}

/**
 * Reagenda a cada rodada em vez de usar um intervalo fixo: o processo pode ficar
 * horas suspenso (a maquina dorme, o container e pausado) e um setInterval
 * acordaria fora de hora, ou dispararia varias vezes seguidas para "recuperar".
 */
function scheduleNext() {
  const at = schedule.nextRunAt();
  state.nextRunAt = new Date(at).toISOString();

  const delay = at - Date.now();
  logger.info(`Proxima rodada: ${schedule.format(at)}.`);

  setTimeout(async () => {
    await runOnce("agendada");
    scheduleNext();
  }, delay).unref?.();
}

function json(response, status, body) {
  const payload = JSON.stringify(body, null, 2);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(payload),
  });
  response.end(payload);
}

function authorized(request) {
  if (!TOKEN) {
    return false;
  }
  return request.headers.authorization === `Bearer ${TOKEN}`;
}

const server = http.createServer((request, response) => {
  const url = new URL(request.url, `http://localhost:${PORT}`);

  if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/health")) {
    json(response, 200, { ...state, horarios: schedule.HOURS, agora: new Date().toISOString() });
    return;
  }

  if (request.method === "POST" && url.pathname === "/run") {
    if (!TOKEN) {
      json(response, 403, {
        erro: "Gatilho manual desligado. Defina TRACKING_TOKEN para habilitar.",
      });
      return;
    }

    if (!authorized(request)) {
      json(response, 401, { erro: "Envie o cabecalho Authorization: Bearer <TRACKING_TOKEN>." });
      return;
    }

    runOnce("manual").then((summary) =>
      json(response, summary ? 200 : 500, summary || { erro: state.lastError })
    );
    return;
  }

  json(response, 404, { erro: "Use GET /health ou POST /run." });
});

function main() {
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS && !process.env.FIREBASE_CONFIG) {
    logger.error(
      "GOOGLE_APPLICATION_CREDENTIALS nao esta definida. Monte o JSON da conta " +
        "de servico no container e aponte a variavel para ele."
    );
    process.exit(2);
  }

  admin.initializeApp({ credential: admin.credential.applicationDefault() });
  db = admin.firestore();
  messaging = admin.messaging();

  server.listen(PORT, () => {
    logger.info(`Ouvindo em :${PORT}. GET /health para o estado.`);

    if (!TOKEN) {
      logger.info("Gatilho manual desligado (TRACKING_TOKEN nao definida).");
    }

    if (RUN_ON_START) {
      runOnce("na subida").then(scheduleNext);
    } else {
      scheduleNext();
    }
  });

  // Sem isto o docker stop espera os 10s do timeout antes de matar a forca.
  for (const signal of ["SIGTERM", "SIGINT"]) {
    process.on(signal, () => {
      logger.info(`${signal} recebido; encerrando.`);
      server.close(() => process.exit(0));
    });
  }
}

main();
