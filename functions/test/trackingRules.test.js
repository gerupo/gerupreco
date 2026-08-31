"use strict";

const test = require("node:test");
const assert = require("node:assert");

const rules = require("../src/trackingRules");

const NOW = Date.parse("2026-08-30T12:00:00.000Z");
const HOUR = 60 * 60 * 1000;

function offer(price, hoursAgo) {
  return {
    valor: price,
    datahora: new Date(NOW - hoursAgo * HOUR).toISOString(),
    estabelecimento: { codigo: "RtInPjHg_rKUd9EhwlYL74zWzYX1qZREiE3gidg1rqw", nm_fan: "MUFFATAO" },
  };
}

function groups(entries) {
  return new Map(entries.map((group) => [group.id, group]));
}

test("grupo manda em alvo e alcance", () => {
  const plan = rules.resolvePlan(
    { groupId: "g1", targetPrice: 99, scope: "PARTICULAR", deviceId: "abc" },
    groups([{ id: "g1", name: "Cervejas", targetPrice: 4.5, scope: "GERAL" }])
  );

  assert.strictEqual(plan.targetPrice, 4.5);
  assert.strictEqual(plan.scope, "GERAL");
  assert.strictEqual(plan.groupName, "Cervejas");
});

test("grupo pausado pausa os produtos dele", () => {
  const plan = rules.resolvePlan(
    { groupId: "g1", active: true },
    groups([{ id: "g1", targetPrice: 4.5, active: false }])
  );

  assert.strictEqual(plan.active, false);
});

test("produto orfao de grupo cai no proprio alvo", () => {
  // releaseFromGroup solta os produtos antes de apagar o grupo, mas se a
  // segunda escrita daquela sequencia falhar sobra um apontamento morto.
  const plan = rules.resolvePlan({ groupId: "sumiu", targetPrice: 7 }, groups([]));

  assert.strictEqual(plan.targetPrice, 7);
  assert.strictEqual(plan.groupName, null);
});

test("escopo ausente ou desconhecido conta como geral", () => {
  assert.strictEqual(rules.isForAllDevices({ scope: undefined }), true);
  assert.strictEqual(rules.isForAllDevices({ scope: "QUALQUER" }), true);
  assert.strictEqual(rules.isForAllDevices({ scope: "PARTICULAR" }), false);
});

test("a janela e de 24 horas", () => {
  const lowest = rules.lowestRecentOffer([offer("3.00", 30), offer("9.90", 5)], NOW);

  assert.strictEqual(lowest.price, 9.9);
});

test("registro sem data e preco ilegivel ficam de fora", () => {
  const semData = { valor: "1.00", estabelecimento: {} };
  const semPreco = { valor: "", datahora: new Date(NOW - HOUR).toISOString() };

  const lowest = rules.lowestRecentOffer([semData, semPreco, offer("9.90", 2)], NOW);

  assert.strictEqual(lowest.price, 9.9);
});

test("nenhuma oferta na janela volta nulo", () => {
  assert.strictEqual(rules.lowestRecentOffer([offer("3.00", 48)], NOW), null);
  assert.strictEqual(rules.lowestRecentOffer([], NOW), null);
});

test("armado: primeira queda ate o alvo notifica", () => {
  const plan = { active: true, targetPrice: 10 };
  const decision = rules.decide(plan, null, { price: 9.5 });

  assert.strictEqual(decision.notify, true);
  assert.strictEqual(decision.lastNotifiedPrice, 9.5);
});

test("preco exatamente no alvo notifica", () => {
  const decision = rules.decide({ active: true, targetPrice: 10 }, null, { price: 10 });

  assert.strictEqual(decision.notify, true);
});

test("mesma promocao nao repete o aviso a cada rodada", () => {
  const decision = rules.decide({ active: true, targetPrice: 10 }, 9.5, { price: 9.5 });

  assert.strictEqual(decision.notify, false);
  assert.strictEqual(decision.changed, false);
});

test("queda alem da ja avisada notifica de novo", () => {
  const decision = rules.decide({ active: true, targetPrice: 10 }, 9.5, { price: 8.9 });

  assert.strictEqual(decision.notify, true);
  assert.strictEqual(decision.lastNotifiedPrice, 8.9);
});

test("preco acima do alvo rearma para a proxima queda", () => {
  const decision = rules.decide({ active: true, targetPrice: 10 }, 9.5, { price: 12 });

  assert.strictEqual(decision.notify, false);
  assert.strictEqual(decision.changed, true);
  assert.strictEqual(decision.lastNotifiedPrice, null);
});

test("caro ha dias nao reescreve o documento a cada rodada", () => {
  const decision = rules.decide({ active: true, targetPrice: 10 }, null, { price: 12 });

  assert.strictEqual(decision.changed, false);
});

test("rodada sem oferta nao mexe no estado", () => {
  // Silencio nao e alta de preco: rearmar aqui faria o mesmo aviso voltar
  // assim que a proxima nota aparecesse.
  const decision = rules.decide({ active: true, targetPrice: 10 }, 9.5, null);

  assert.strictEqual(decision.notify, false);
  assert.strictEqual(decision.changed, false);
});

test("sem alvo nao ha o que comparar", () => {
  const decision = rules.decide({ active: true, targetPrice: null }, null, { price: 1 });

  assert.strictEqual(decision.notify, false);
});
