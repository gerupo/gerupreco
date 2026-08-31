"use strict";

const priceUtil = require("./priceUtil");

/**
 * Monta o texto do alerta.
 *
 * O nome de fantasia da loja vem vazio em quase metade dos registros
 * legitimos - a razao social e o unico campo sempre preenchido, entao ela e o
 * reserva. Sem isso o alerta diria "no undefined".
 */
function marketName(offer) {
  const company = (offer && offer.estabelecimento) || {};
  const name = (company.nm_fan || "").trim() || (company.nm_emp || "").trim();

  return name || "estabelecimento nao identificado";
}

/**
 * O alerta precisa se explicar sozinho na bandeja, porque e ali que ele e lido:
 * o que baixou, para quanto, onde, e qual era o alvo. Sem o alvo escrito, um
 * preco que parece alto vira duvida sobre a rotina ter errado a conta.
 *
 * A data entra porque os dados da Nota Parana atrasam um dia: a janela de 24
 * horas quase nunca alcanca uma nota do proprio dia, e o alerta fala do preco
 * de ontem. Sem dizer isso, quem chega na loja hoje e nao acha o preco acha que
 * o alerta mentiu.
 */
function buildAlert({ description, groupName, price, targetPrice, offer }) {
  const parts = [
    `${priceUtil.format(price)} no ${marketName(offer)}`,
    `alvo ${priceUtil.format(targetPrice)}`,
    `nota de ${formatDate(offer && offer.datahora)}`,
  ];

  return {
    title: `Baixou: ${description || "produto rastreado"}`,
    body: groupName ? `${parts.join(" · ")}\nGrupo ${groupName}` : parts.join(" · "),
  };
}

/**
 * Horario de Brasilia e um deslocamento fixo desde 2019, quando o horario de
 * verao acabou no pais - entao a conversao e uma subtracao, sem depender do
 * banco de fusos do runtime, pelo mesmo motivo que a moeda e formatada a mao no
 * priceUtil.
 */
const BRASILIA_OFFSET_MS = 3 * 60 * 60 * 1000;

function formatDate(value) {
  const at = Date.parse(value);

  if (!Number.isFinite(at)) {
    return "data desconhecida";
  }

  const local = new Date(at - BRASILIA_OFFSET_MS);
  const day = String(local.getUTCDate()).padStart(2, "0");
  const month = String(local.getUTCMonth() + 1).padStart(2, "0");

  return `${day}/${month}`;
}

module.exports = { buildAlert, marketName };
