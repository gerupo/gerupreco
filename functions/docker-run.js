"use strict";

/**
 * Sobe o container do rastreamento.
 *
 * Existe porque a linha do `docker run` tinha uma armadilha: o caminho da
 * credencial vinha de uma variavel de ambiente expandida pelo shell, e com ela
 * vazia o docker respondia "invalid spec: :/run/secrets/...: empty section
 * between colons" - uma mensagem que fala do formato do -v e nao da variavel que
 * ninguem definiu.
 *
 * A segunda armadilha e pior porque nao da erro: apontando o -v para um arquivo
 * que nao existe, o docker cria um DIRETORIO vazio no lugar. O container sobe,
 * o firebase-admin tenta ler a credencial, encontra um diretorio, e a falha
 * aparece bem depois, na primeira consulta, dizendo outra coisa.
 *
 * Entao aqui o caminho e resolvido, conferido e so entao passado adiante.
 */

const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const IMAGE = "geruprecotracking";
const CONTAINER = "geruprecotracking";
const PORT = 3456;
const MOUNT = "/run/secrets/service-account.json";

/** O mesmo lugar que o README sugere, para o caso comum nao precisar de nada. */
const DEFAULT_CREDENTIALS = path.join(
  os.homedir(),
  ".config",
  "gerupreco",
  "service-account.json"
);

function fail(message) {
  console.error(`\n${message}\n`);
  process.exit(1);
}

function credentialsPath() {
  const chosen = process.env.GERUPRECO_CREDENTIALS || DEFAULT_CREDENTIALS;
  const resolved = path.resolve(chosen);

  if (!fs.existsSync(resolved)) {
    fail(
      `Credencial nao encontrada em:\n  ${resolved}\n\n` +
        "Gere a chave em Firebase > Configuracoes do projeto > Contas de servico\n" +
        "> Gerar nova chave privada, e entao:\n\n" +
        `  mkdir -p ${path.dirname(DEFAULT_CREDENTIALS)}\n` +
        `  mv ~/Downloads/gerupreco-*.json ${DEFAULT_CREDENTIALS}\n` +
        `  chmod 600 ${DEFAULT_CREDENTIALS}\n\n` +
        "Ou aponte para outro lugar com GERUPRECO_CREDENTIALS=/caminho/da/chave.json"
    );
  }

  if (!fs.statSync(resolved).isFile()) {
    fail(
      `Isto nao e um arquivo:\n  ${resolved}\n\n` +
        "Quase sempre e um diretorio vazio que o proprio docker criou numa\n" +
        "tentativa anterior, quando o caminho apontava para algo inexistente.\n" +
        "Apague o diretorio e ponha a chave no lugar."
    );
  }

  return resolved;
}

function main() {
  const credentials = credentialsPath();

  const args = [
    "run",
    "-d",
    "--name", CONTAINER,
    "--restart=unless-stopped",
    "-p", `${PORT}:${PORT}`,
    "-v", `${credentials}:${MOUNT}:ro`,
  ];

  // Repassado so quando definido: sem ele o gatilho manual fica desligado, que
  // e o padrao desejado.
  if (process.env.TRACKING_TOKEN) {
    args.push("-e", `TRACKING_TOKEN=${process.env.TRACKING_TOKEN}`);
  }

  args.push(IMAGE);

  console.log(`Credencial: ${credentials}`);
  console.log(`docker ${args.join(" ")}\n`);

  const result = spawnSync("docker", args, { stdio: "inherit" });

  if (result.error) {
    fail(`Nao consegui executar o docker: ${result.error.message}`);
  }

  if (result.status !== 0) {
    fail(
      "O docker recusou. Se a queixa for de nome ja em uso, o container de uma\n" +
        `tentativa anterior ainda existe:\n\n  docker rm -f ${CONTAINER}\n`
    );
  }

  console.log(
    `\nNo ar. Estado:  curl -s localhost:${PORT}/health\n` +
      `Log:            docker logs -f ${CONTAINER}`
  );
}

main();
