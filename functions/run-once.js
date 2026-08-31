"use strict";

/**
 * Uma rodada da rotina de alertas, para rodar FORA do Google Cloud - tipicamente
 * num servidor de casa, chamado por cron.
 *
 * Existe porque a Nota Parana devolve dados forjados para IP de datacenter. Nao
 * e limite de volume: medido em 30/08/2026, a primeira requisicao da Cloud
 * Function ja voltou 100% forjada, enquanto o mesmo GTIN consultado de um IP
 * residencial no mesmo minuto voltou integro. Rodar da rede de casa e usar a API
 * como qualquer usuario do app ja faz.
 *
 * A logica e exatamente a mesma do server.js - os dois compartilham
 * src/run.js. O que muda e so quem agenda.
 *
 * Roda uma vez e sai. Nao ha laco interno de proposito: quem repete e o cron do
 * sistema, que sobrevive a reinicio da maquina e a processo morto, coisas que um
 * setInterval dentro do Node nao sobrevive.
 */

const fs = require("node:fs");

const admin = require("firebase-admin");

const { run } = require("./src/run");

const DRY_RUN = process.argv.includes("--dry-run");

/**
 * O log do container mostra so a saida do processo 1. Uma rodada forcada por
 * "docker exec" e outro processo, entao ela sumia do "docker logs" - e quem
 * fosse conferir depois via so as rodadas agendadas, com a forcada faltando
 * bem no meio, como se nao tivesse acontecido.
 *
 * Escrever tambem em /proc/1/fd/1 devolve a rodada ao log do container.
 *
 * O teste do /.dockerenv nao e zelo excessivo: fora de container o processo 1 e
 * o init do sistema, e despejar log ali seria escrever no console da maquina do
 * usuario. Sem o arquivo, ou sem permissao no descritor, a funcao desiste em
 * silencio e sobra a saida normal, que ja e o que o cron registra.
 */
function containerLog() {
  if (process.pid === 1 || !fs.existsSync("/.dockerenv")) {
    return null;
  }

  try {
    return fs.createWriteStream("/proc/1/fd/1", { flags: "a" });
  } catch (error) {
    return null;
  }
}

const mirror = containerLog();

function stamp() {
  return new Date().toISOString();
}

function write(level, line) {
  const text = `${stamp()} ${level} ${line}`;

  if (level === "ERROR") {
    console.error(text);
  } else if (level === "WARN") {
    console.warn(text);
  } else {
    console.log(text);
  }

  if (mirror) {
    // Marcado para a rodada forcada nao se confundir com as agendadas na
    // leitura do "docker logs".
    mirror.write(`${text}   [forcada]
`);
  }
}

const logger = {
  info: (line) => write("INFO ", line),
  warn: (line) => write("WARN ", line),
  error: (line) => write("ERROR", line),
};

/**
 * Fora do Google Cloud nao existe credencial implicita: e preciso apontar
 * GOOGLE_APPLICATION_CREDENTIALS para o JSON da conta de servico. Sem isso o
 * firebase-admin falha bem depois, no meio da primeira consulta, com um erro que
 * nao diz o que falta.
 */
function credentialsMissing() {
  return !process.env.GOOGLE_APPLICATION_CREDENTIALS && !process.env.FIREBASE_CONFIG;
}

/**
 * Em simulacao o Firestore e lido de verdade, mas nada e gravado e nenhum push
 * sai - so o registro do que teria acontecido. E como conferir a primeira rodada
 * no servidor novo sem acordar os aparelhos de madrugada.
 */
function readOnly(db) {
  return {
    collection(name) {
      const real = db.collection(name);
      return {
        get: () => real.get(),
        doc: (id) => ({
          id,
          update: async (values) =>
            logger.info(`[simulacao] gravaria ${name}/${id}: ${JSON.stringify(values)}`),
        }),
      };
    },
    batch() {
      const pending = [];
      return {
        update: (ref, values) => pending.push({ id: ref.id, values }),
        commit: async () => {
          for (const { id, values } of pending) {
            logger.info(`[simulacao] gravaria tracking/${id}: ${JSON.stringify(values)}`);
          }
        },
      };
    },
  };
}

function silentMessaging() {
  return {
    send: async (payload) => {
      const alvo = payload.topic ? `topico ${payload.topic}` : "token de um aparelho";
      logger.info(`[simulacao] enviaria para ${alvo}: ${payload.notification.title}`);
      return "simulacao";
    },
  };
}

async function main() {
  if (credentialsMissing()) {
    logger.error(
      "GOOGLE_APPLICATION_CREDENTIALS nao esta definida. Aponte para o JSON da " +
        "conta de servico do projeto (Firebase > Configuracoes > Contas de servico)."
    );
    process.exit(2);
  }

  admin.initializeApp({ credential: admin.credential.applicationDefault() });

  const db = admin.firestore();

  if (DRY_RUN) {
    logger.info("Simulacao: le o Firestore, nao grava nada e nao envia push.");
  }

  const summary = await run({
    db: DRY_RUN ? readOnly(db) : db,
    messaging: DRY_RUN ? silentMessaging() : admin.messaging(),
    logger,
  });

  // Resposta inteira forjada e o retrato de um IP marcado como raspagem, e
  // daqui isso e indistinguivel de "nenhum produto atingiu o alvo". O codigo de
  // saida separado deixa o cron ou o monitor do servidor perceberem sozinhos.
  if (summary.discarded > 0 && summary.notified === 0) {
    logger.error(
      `Todas as consultas voltaram forjadas (${summary.discarded} registros descartados). ` +
        "Este IP esta sendo tratado como raspagem pela Nota Parana."
    );
    process.exit(3);
  }

  process.exit(0);
}

main().catch((error) => {
  logger.error(`Rodada abortada: ${error.stack || error.message}`);
  process.exit(1);
});
