"use strict";

/**
 * Porte do RequestSpacer: garante um intervalo minimo entre duas consultas a
 * Nota Parana.
 *
 * A API pune volume por IP. Medido do app, com o mesmo conjunto de codigos de
 * barras: 9 consultas em paralelo devolveram 5 respostas HTTP 503 e uma das 4
 * restantes veio inteira forjada; 10 consultas a cada 400ms devolveram 168
 * registros, nenhum forjado, nenhum erro.
 *
 * Aqui o risco e maior que no aparelho: a funcao sai de um IP de datacenter do
 * Google, compartilhado e sem historico de uso residencial, e a rodada consulta
 * a lista inteira de produtos rastreados de uma vez.
 *
 * Espacar nao substitui o DecoyFilter, porque nao desfaz a marcacao: uma vez
 * punido, o IP recebe resposta forjada mesmo consultando devagar.
 *
 * A versao do app dorme segurando o monitor. Aqui nao ha monitor: a fila e uma
 * promessa encadeada, e cada consulta so parte depois que a anterior marcou a
 * sua partida. Encadear em vez de medir o relogio na hora e o que impede duas
 * chamadas simultaneas de lerem o mesmo "ja passou o intervalo" e sairem
 * juntas.
 */

const MIN_INTERVAL_MS = 400;

let queue = Promise.resolve();
let lastRequestAt = 0;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Espera a vez na fila. Resolve quando a consulta pode partir.
 */
function awaitSlot() {
  const slot = queue.then(async () => {
    const remaining = lastRequestAt + MIN_INTERVAL_MS - Date.now();

    if (remaining > 0) {
      await sleep(remaining);
    }

    lastRequestAt = Date.now();
  });

  // A fila nao pode quebrar: um erro em quem esperava atras deixaria toda
  // consulta seguinte rejeitada de saida.
  queue = slot.catch(() => {});

  return slot;
}

module.exports = { awaitSlot, MIN_INTERVAL_MS };
