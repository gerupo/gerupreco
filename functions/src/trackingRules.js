"use strict";

const priceUtil = require("./priceUtil");

/**
 * Toda a decisao da rodada, sem rede e sem Firestore: dado um rastreamento, o
 * grupo dele e as ofertas que voltaram da API, diz se ha alerta a disparar e
 * qual estado gravar de volta. E o que os testes cobrem.
 */

const SCOPE_ALL = "GERAL";
const SCOPE_DEVICE = "PARTICULAR";

/**
 * A rotina so considera ofertas das ultimas 24 horas.
 *
 * Medido na API em 28/08/2026, sobre dois produtos do catalogo: nenhuma nota do
 * proprio dia aparece - o lote de ontem e o mais novo que existe. Entao o
 * alerta fala do preco de ontem, e produto de giro lento passa dias sem oferta
 * nenhuma na janela, o que e silencio, nao erro. Sem esse recorte, uma nota
 * barata de meses atras dispararia alerta para sempre.
 */
const WINDOW_MS = 24 * 60 * 60 * 1000;

/**
 * Alvo, alcance e estado ligado/desligado de um rastreamento, ja resolvidos
 * contra o grupo.
 *
 * Grupo manda: um tracking com groupId ignora o proprio targetPrice e scope. E
 * a mesma regra que a tela aplica ao desligar esses campos no dialogo, e ela
 * precisa valer nos dois lugares - o app so desliga a edicao, quem de fato
 * decide o alvo e esta rotina.
 */
function resolvePlan(tracking, groupsById) {
  const group = tracking.groupId ? groupsById.get(tracking.groupId) : null;

  // Grupo apagado com produto ainda apontando para ele nao deveria acontecer -
  // TrackingRepository.releaseFromGroup solta os produtos antes de apagar -,
  // mas se a segunda escrita daquela sequencia falhar, sobra um orfao. Sem alvo
  // proprio ele fica de fora da rodada em vez de herdar alvo nenhum.
  const source = group || tracking;

  return {
    active: tracking.active !== false && (!group || group.active !== false),
    targetPrice: source.targetPrice === undefined ? null : source.targetPrice,
    scope: source.scope,
    deviceId: source.deviceId,
    groupName: group ? group.name : null,
  };
}

/**
 * Escopo desconhecido ou ausente conta como geral, como no app: alerta que
 * chega a todo mundo incomoda, alerta que nao chega a ninguem passa
 * despercebido.
 */
function isForAllDevices(plan) {
  return plan.scope !== SCOPE_DEVICE;
}

/**
 * A oferta mais barata dentro da janela de 24 horas.
 *
 * Registro sem data nao entra: sem saber quando a nota foi emitida nao da para
 * dizer que ele esta na janela, e um preco velho barato dispararia alerta a
 * cada rodada. Preco ilegivel tambem sai, pelo mesmo motivo que a tela o joga
 * para o fim da lista.
 */
function lowestRecentOffer(offers, now) {
  let best = null;

  for (const offer of offers || []) {
    const price = priceUtil.parse(offer.valor);

    if (price === null) {
      continue;
    }

    const at = Date.parse(offer.datahora);

    if (!Number.isFinite(at) || now - at > WINDOW_MS) {
      continue;
    }

    if (best === null || price < best.price) {
      best = { price, offer, at };
    }
  }

  return best;
}

/**
 * A regra inteira de "uma vez por queda".
 *
 * lastNotifiedPrice nulo significa armado. Notifica quando o menor preco chega
 * ao alvo e ou o campo esta nulo, ou o preco caiu ainda mais que o ja avisado;
 * e volta o campo a nulo assim que o preco sobe acima do alvo, rearmando para a
 * proxima queda. Sem isso, quatro rodadas por dia repetiriam o mesmo aviso
 * enquanto a promocao durasse, e o usuario desligaria as notificacoes do app.
 *
 * Rodada sem oferta na janela nao mexe no estado: silencio nao e alta de preco,
 * e rearmar ali faria o mesmo aviso voltar assim que a proxima nota aparecesse.
 */
function decide(plan, lastNotifiedPrice, lowest) {
  const target = priceUtil.cents(plan.targetPrice);

  if (target === null || !plan.active) {
    return { notify: false, changed: false };
  }

  if (lowest === null) {
    return { notify: false, changed: false };
  }

  const price = priceUtil.cents(lowest.price);
  const notified = priceUtil.cents(lastNotifiedPrice);

  if (price > target) {
    // Rearma. So grava se havia algo armado, para nao reescrever documento sem
    // motivo nas rodadas em que o produto esta caro ha dias.
    return { notify: false, changed: notified !== null, lastNotifiedPrice: null };
  }

  if (notified === null || price < notified) {
    return { notify: true, changed: true, lastNotifiedPrice: lowest.price };
  }

  return { notify: false, changed: false };
}

module.exports = {
  SCOPE_ALL,
  SCOPE_DEVICE,
  WINDOW_MS,
  resolvePlan,
  isForAllDevices,
  lowestRecentOffer,
  decide,
};
