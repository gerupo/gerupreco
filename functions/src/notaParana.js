"use strict";

const { awaitSlot } = require("./requestSpacer");
const decoyFilter = require("./decoyFilter");

/**
 * Consulta de precos por codigo de barras, com os mesmos parametros que o app
 * usa no RetrofitRequest.
 *
 * A API aceita um gtin por chamada - lista separada por virgula, parametro
 * repetido e gtin[] foram testados e nenhum funciona -, entao a rodada dispara
 * uma consulta por produto, uma de cada vez, pelo espacador.
 *
 * O parametro data=-1 pede tudo de proposito: aquele filtro e inconsistente na
 * origem (data=7 devolve mais registros que data=3), e a janela de 24 horas e
 * recortada aqui, em memoria.
 */

const BASE_URL = "https://menorpreco.notaparana.pr.gov.br/api/v1/produtos";

/** O mesmo geohash que o app manda: a regiao de interesse do usuario. */
const LOCAL = "6g9fp8frx";

const RADIUS_KM = 20;

/** Os mesmos 6s de conexao e leitura do RetrofitConfig. */
const TIMEOUT_MS = 6000;

const USER_AGENT =
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
  "Chrome/114.0.0.0 Safari/537.36";

function buildUrl(gtin) {
  const params = new URLSearchParams({
    local: LOCAL,
    offset: "0",
    raio: String(RADIUS_KM),
    data: "-1",
    ordem: "0",
    gtin,
  });

  return `${BASE_URL}?${params}`;
}

/**
 * Devolve as ofertas confiaveis de um gtin, junto com quantos registros foram
 * descartados por forjados - quem chama registra isso no log.
 *
 * Erro de rede ou HTTP sobe como excecao: falha num produto nao pode virar
 * "preco subiu" e rearmar o alerta de outro. Quem chama trata como rodada sem
 * dado para aquele produto.
 */
async function searchLowestPrice(gtin) {
  await awaitSlot();

  const response = await fetch(buildUrl(gtin), {
    headers: { "User-Agent": USER_AGENT },
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });

  // A API responde 503 quando esta sobrecarregada, e ai o corpo nem e JSON.
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ao consultar o gtin ${gtin}`);
  }

  const body = await response.json();

  // Produto sem nenhum registro devolve corpo vazio, e nao erro.
  const { trusted, discarded } = decoyFilter.clean(body && body.produtos);

  return { offers: trusted, discarded };
}

module.exports = { searchLowestPrice, buildUrl, LOCAL, RADIUS_KM };
