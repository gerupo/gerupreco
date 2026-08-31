"use strict";

const test = require("node:test");
const assert = require("node:assert");

const priceUtil = require("../src/priceUtil");

test("ponto e decimal, porque a Nota Parana devolve 4.50", () => {
  assert.strictEqual(priceUtil.parse("4.50"), 4.5);
  assert.strictEqual(priceUtil.parse("3.11"), 3.11);
});

test("virgula na string faz o ponto virar separador de milhar", () => {
  assert.strictEqual(priceUtil.parse("1.234,56"), 1234.56);
});

test("valor ilegivel volta nulo em vez de estourar", () => {
  assert.strictEqual(priceUtil.parse(""), null);
  assert.strictEqual(priceUtil.parse("  "), null);
  assert.strictEqual(priceUtil.parse("sem preco"), null);
  assert.strictEqual(priceUtil.parse(null), null);
});

test("comparacao e em centavos inteiros", () => {
  // 3 * 1.15 da 3.4499999999999997 em ponto flutuante; o alvo gravado como
  // 3.45 no Firestore precisa empatar com ele, e nao ficar um centesimo acima.
  assert.strictEqual(priceUtil.cents(priceUtil.round(3 * 1.15)), priceUtil.cents(3.45));
});

test("moeda formatada a mao, sem depender do ICU do runtime", () => {
  assert.strictEqual(priceUtil.format(64.79), "R$ 64,79");
  assert.strictEqual(priceUtil.format(3.1), "R$ 3,10");
  assert.strictEqual(priceUtil.format(0.54), "R$ 0,54");
  assert.strictEqual(priceUtil.format(1234.5), "R$ 1.234,50");
  assert.strictEqual(priceUtil.format(1234567.89), "R$ 1.234.567,89");
  assert.strictEqual(priceUtil.format(null), "--");
});
