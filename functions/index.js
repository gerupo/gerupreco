"use strict";

const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");
const { onSchedule } = require("firebase-functions/v2/scheduler");

const { run } = require("./src/run");

admin.initializeApp();

/**
 * A rotina de alertas de preco do modulo Rastreamento.
 *
 * O app so faz o cadastro: quem consulta a Nota Parana, compara com o alvo e
 * dispara a notificacao e esta funcao. Por isso a TrackingActivity nao consulta
 * preco nenhum - o que a tela mostra e exatamente o que esta rodada vai usar.
 *
 * Quatro rodadas por dia, no horario de Brasilia. Mais que isso nao traz dado
 * novo: os registros da Nota Parana atrasam um dia, e a janela de 24 horas
 * quase nunca alcanca uma nota do proprio dia.
 */
exports.checkTrackedPrices = onSchedule(
  {
    // ATENCAO: este caminho esta comprovadamente quebrado. Medido em
    // 30/08/2026, a Nota Parana devolveu 100% de registros forjados em TODAS as
    // rodadas saidas daqui - inclusive na primeira, antes de qualquer volume -,
    // enquanto os mesmos GTINs consultados de um IP residencial no mesmo minuto
    // voltaram integros. Nao e limite de requisicoes: e o tratamento dado a IP
    // de datacenter.
    //
    // Quem roda a rotina de verdade e o run-once.js, num servidor de casa. Esta
    // funcao fica como esqueleto, para o dia em que a origem deixar de importar.
    // Antes de reativar, confira uma rodada de log: "sobraram 0" em todos os
    // GTINs significa que nada mudou.
    schedule: "0 9,12,15,18 * * *",
    timeZone: "America/Sao_Paulo",
    region: "southamerica-east1",

    // Uma consulta a cada 400ms, uma por produto rastreado: 60 produtos gastam
    // 24s so de espera, e a API ainda responde em cima disso.
    timeoutSeconds: 540,
    memory: "256MiB",

    // Duas rodadas simultaneas dobrariam a taxa contra a Nota Parana - o
    // espacador e estado de processo, nao um limite global -, e e volume por IP
    // que faz a API passar a devolver dados forjados.
    maxInstances: 1,

    // Sem repeticao automatica: uma rodada que falhou por erro da API voltaria
    // batendo no mesmo IP ja sobrecarregado. A proxima rodada agendada resolve,
    // e nenhum alerta se perde - lastNotifiedPrice so e gravado depois do envio.
    retryCount: 0,
  },
  async () => {
    await run({
      db: admin.firestore(),
      messaging: admin.messaging(),
      logger,
    });
  }
);
