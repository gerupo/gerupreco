"use strict";

const test = require("node:test");
const assert = require("node:assert");

const decoyFilter = require("../src/decoyFilter");

/** Codigo real de 43 caracteres, com maiusculas, como a API devolve. */
const LEGIT_CODE = "RtInPjHg_rKUd9EhwlYL74zWzYX1qZREiE3gidg1rqw";

/** Codigo forjado: passa de 80 caracteres e so tem minusculas e digitos. */
const DECOY_CODE = "a3f9c1b7d2e8f04a6c5b9d3e7f1a8c2b6d4e9f0a3c7b1d5e8f2a6c9b3d7e1f4a8c2b6d9e3f7a1c5b8d";

function product(code) {
  return { valor: "9.90", estabelecimento: { codigo: code } };
}

test("descarta o registro forjado e conta o descarte", () => {
  const { trusted, discarded } = decoyFilter.clean([product(LEGIT_CODE), product(DECOY_CODE)]);

  assert.strictEqual(trusted.length, 1);
  assert.strictEqual(trusted[0].estabelecimento.codigo, LEGIT_CODE);
  assert.strictEqual(discarded, 1);
});

test("a regra descreve o forjado: codigo em formato novo passa", () => {
  // Se a Nota Parana mudar o identificador legitimo, o filtro deixa passar em
  // vez de descartar o catalogo inteiro como suspeito.
  const { trusted, discarded } = decoyFilter.clean([product("codigo-curto-novo")]);

  assert.strictEqual(trusted.length, 1);
  assert.strictEqual(discarded, 0);
});

test("resposta envenenada sobra vazia", () => {
  const { trusted, discarded } = decoyFilter.clean([product(DECOY_CODE), product(DECOY_CODE)]);

  assert.deepStrictEqual(trusted, []);
  assert.strictEqual(discarded, 2);
});

test("corpo vazio nao estoura", () => {
  assert.deepStrictEqual(decoyFilter.clean(undefined), { trusted: [], discarded: 0 });
  assert.deepStrictEqual(decoyFilter.clean(null), { trusted: [], discarded: 0 });
});

test("registro sem estabelecimento nao e forjado", () => {
  assert.strictEqual(decoyFilter.isDecoy({ valor: "1.00" }), false);
});
