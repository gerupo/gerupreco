"use strict";

const test = require("node:test");
const assert = require("node:assert");

const { nextRunAt, format } = require("../src/schedule");

/** Brasilia e UTC-3, entao 09/12/15/18 locais sao 12/15/18/21 em UTC. */
function utc(iso) {
  return Date.parse(iso);
}

test("dentro do dia, pega o proximo horario", () => {
  // 10:30 em Brasilia -> proxima e 12:00 local = 15:00 UTC
  assert.strictEqual(
    nextRunAt(utc("2026-08-31T13:30:00Z")),
    utc("2026-08-31T15:00:00Z")
  );
});

test("antes da primeira, pega a primeira do dia", () => {
  assert.strictEqual(
    nextRunAt(utc("2026-08-31T04:00:00Z")),
    utc("2026-08-31T12:00:00Z")
  );
});

test("depois da ultima, vira para o dia seguinte", () => {
  // 20:00 em Brasilia -> primeira de amanha, 09:00 local = 12:00 UTC
  assert.strictEqual(
    nextRunAt(utc("2026-08-31T23:00:00Z")),
    utc("2026-09-01T12:00:00Z")
  );
});

test("na hora cheia, devolve a seguinte e nao ela mesma", () => {
  // Sem o "estritamente depois", o daemon agendaria um timeout de zero e
  // entraria em laco na virada da hora.
  assert.strictEqual(
    nextRunAt(utc("2026-08-31T15:00:00Z")),
    utc("2026-08-31T18:00:00Z")
  );
});

test("vira o mes e o ano sem ajuda", () => {
  assert.strictEqual(
    nextRunAt(utc("2026-12-31T23:00:00Z")),
    utc("2027-01-01T12:00:00Z")
  );
});

test("o rotulo sai em hora de Brasilia", () => {
  assert.strictEqual(format(utc("2026-08-31T15:00:00Z")), "31/08/2026 12:00 (Brasilia)");
  // Virada de dia: 01:00 UTC ainda e o dia anterior no Brasil.
  assert.strictEqual(format(utc("2026-09-01T01:00:00Z")), "31/08/2026 22:00 (Brasilia)");
});
