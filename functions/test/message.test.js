"use strict";

const test = require("node:test");
const assert = require("node:assert");

const message = require("../src/message");

test("a data do alerta sai em horario de Brasilia", () => {
  // 02:20 UTC ainda e o dia anterior no Brasil, e a nota precisa aparecer com o
  // dia em que foi emitida por quem vai procurar o preco na loja.
  const alert = message.buildAlert({
    description: "Omo",
    price: 64.79,
    targetPrice: 70,
    offer: { datahora: "2026-08-30T02:20:00.000Z", estabelecimento: { nm_fan: "MUFFATAO" } },
  });

  assert.match(alert.body, /nota de 29\/08/);
});

test("loja sem nome de fantasia cai na razao social", () => {
  const alert = message.buildAlert({
    description: "Omo",
    price: 1,
    targetPrice: 2,
    offer: { datahora: "2026-08-30T12:00:00.000Z", estabelecimento: { nm_fan: "", nm_emp: "IRMAOS MUFFATO & CIA LTDA" } },
  });

  assert.match(alert.body, /IRMAOS MUFFATO/);
});
