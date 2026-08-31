"use strict";

const test = require("node:test");
const assert = require("node:assert");

const { awaitSlot, MIN_INTERVAL_MS } = require("../src/requestSpacer");

/**
 * O caso da rodada: varias consultas pedindo a vez ao mesmo tempo, uma por
 * produto rastreado. Sem o espacador elas sairiam todas juntas, e e a rajada
 * que faz a Nota Parana devolver registros forjados.
 */
test("consultas simultaneas saem espacadas", async () => {
  const requests = 4;
  const start = Date.now();

  await Promise.all(Array.from({ length: requests }, () => awaitSlot()));

  const elapsed = Date.now() - start;
  const expected = (requests - 1) * MIN_INTERVAL_MS;

  assert.ok(
    elapsed >= expected - 50,
    `as consultas sairam em rajada: ${elapsed}ms para ${requests}`
  );
});

test("consultas seguidas respeitam o intervalo", async () => {
  await awaitSlot();

  const start = Date.now();
  await awaitSlot();
  const elapsed = Date.now() - start;

  assert.ok(elapsed >= MIN_INTERVAL_MS - 20, `saiu cedo demais: ${elapsed}ms`);
});
