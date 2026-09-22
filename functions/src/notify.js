"use strict";

/**
 * Envio dos alertas pelo FCM.
 *
 * A mensagem carrega titulo e texto em notification E em data, de proposito: com
 * o app em segundo plano quem desenha a notificacao e o proprio sistema, a
 * partir do bloco notification, e o GeruMessagingService nem chega a ser
 * chamado; com o app aberto a entrega cai no servico e o desenho e por conta
 * dele, a partir do data. Mandar so um dos dois deixa metade dos casos mudo.
 */

/**
 * O mesmo id de canal declarado em strings.xml e no manifesto como canal padrao
 * do FCM. E o caminho usado quando o sistema desenha a notificacao sozinho, com
 * o app fechado - sem ele o alerta cai num canal generico, fora do controle do
 * usuario.
 */
const CHANNEL_ID = "price_alerts";

/**
 * Topico dos alertas de alcance geral. O nome e contrato com o app
 * (TrackingRegistration.TOPIC_ALL): mudar de um lado so deixa o alerta sem
 * ninguem escutando.
 */
const TOPIC_ALL = "geral";

/**
 * O nome do campo barCode no data e contrato com o app
 * (TrackingNotifier.EXTRA_BAR_CODE): com o app fechado o sistema repassa esse
 * bloco como extras do intent, e e por ele que o toque no alerta abre a tela de
 * precos do produto. Renomear aqui faz o toque cair na home.
 */
function buildMessage(target, { title, body, barCode }) {
  return {
    ...target,
    notification: { title, body },
    data: { title, body, barCode: barCode || "" },
    android: {
      priority: "high",
      notification: { channelId: CHANNEL_ID },
    },
  };
}

module.exports = { buildMessage, CHANNEL_ID, TOPIC_ALL };
