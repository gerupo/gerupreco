"use strict";

const test = require("node:test");
const assert = require("node:assert");

const { run } = require("../src/run");

const NOW = Date.parse("2026-08-30T12:00:00.000Z");

function offer(price) {
  return {
    valor: price,
    datahora: new Date(NOW - 3600 * 1000).toISOString(),
    estabelecimento: { codigo: "RtInPjHg_rKUd9EhwlYL74zWzYX1qZREiE3gidg1rqw", nm_fan: "MUFFATAO" },
  };
}

/**
 * Firestore de mentira: so o que a rodada usa - ler tres colecoes e gravar um
 * lote de updates. Os updates ficam expostos para o teste conferir campo a
 * campo o que teria sido gravado.
 */
function fakeDb(collections, { batchFails = false, rejectDoc = null } = {}) {
  const updates = [];

  return {
    updates,
    collection(name) {
      return {
        async get() {
          const docs = Object.entries(collections[name] || {}).map(([id, data]) => ({
            id,
            data: () => data,
          }));
          return { docs };
        },
        doc: (id) => ({
          collection: name,
          id,
          async update(values) {
            if (id === rejectDoc) {
              throw new Error("NOT_FOUND: documento apagado");
            }
            updates.push({ id, values });
          },
        }),
      };
    },
    batch() {
      const pending = [];

      return {
        update(ref, values) {
          pending.push({ id: ref.id, values });
        },
        async commit() {
          if (batchFails) {
            throw new Error("NOT_FOUND: um dos documentos nao existe mais");
          }
          updates.push(...pending);
        },
      };
    },
  };
}

function fakeMessaging(behavior = () => {}) {
  const sent = [];

  return {
    sent,
    async send(payload) {
      await behavior(payload);
      sent.push(payload);
      return "ok";
    },
  };
}

function fakeLogger() {
  const lines = { info: [], warn: [], error: [] };

  return {
    lines,
    info: (line) => lines.info.push(line),
    warn: (line) => lines.warn.push(line),
    error: (line) => lines.error.push(line),
  };
}

function trackedProduct(extra = {}) {
  return {
    t1: {
      barCode: "7891150067646",
      description: "Omo liquido",
      targetPrice: 70,
      active: true,
      lastNotifiedPrice: null,
      ...extra,
    },
  };
}

test("queda ate o alvo vira alerta no topico geral", async () => {
  const db = fakeDb({ tracking: trackedProduct() });
  const messaging = fakeMessaging();
  const logger = fakeLogger();

  const summary = await run({
    db,
    messaging,
    logger,
    now: NOW,
    search: async () => ({ offers: [offer("64.79")], discarded: 0 }),
  });

  assert.strictEqual(summary.notified, 1);
  assert.strictEqual(messaging.sent.length, 1);
  assert.strictEqual(messaging.sent[0].topic, "geral");
  assert.match(messaging.sent[0].notification.title, /Omo liquido/);
  // Titulo e texto vao nos dois lugares: o sistema desenha a partir do
  // notification com o app fechado, o servico desenha a partir do data.
  assert.strictEqual(messaging.sent[0].data.body, messaging.sent[0].notification.body);
  assert.strictEqual(messaging.sent[0].data.barCode, "7891150067646");
  assert.strictEqual(messaging.sent[0].android.notification.channelId, "price_alerts");

  assert.deepStrictEqual(db.updates, [
    { id: "t1", values: { lastCheckedAt: NOW, lastNotifiedPrice: 64.79, lastNotifiedAt: NOW } },
  ]);
});

test("escopo particular vai para o token do aparelho", async () => {
  const db = fakeDb({
    tracking: trackedProduct({ scope: "PARTICULAR", deviceId: "android-id-1" }),
    device: { "android-id-1": { fcmToken: "token-do-aparelho" } },
  });
  const messaging = fakeMessaging();

  await run({
    db,
    messaging,
    logger: fakeLogger(),
    now: NOW,
    search: async () => ({ offers: [offer("64.79")], discarded: 0 }),
  });

  assert.strictEqual(messaging.sent[0].token, "token-do-aparelho");
  assert.strictEqual(messaging.sent[0].topic, undefined);
});

test("alerta particular sem token nao vira alerta geral", async () => {
  const db = fakeDb({ tracking: trackedProduct({ scope: "PARTICULAR", deviceId: "sumiu" }) });
  const messaging = fakeMessaging();
  const logger = fakeLogger();

  await run({
    db,
    messaging,
    logger,
    now: NOW,
    search: async () => ({ offers: [offer("64.79")], discarded: 0 }),
  });

  assert.strictEqual(messaging.sent.length, 0);
  assert.strictEqual(logger.lines.warn.length, 1);
  // Continua armado: a proxima rodada tenta de novo quando o token voltar.
  assert.deepStrictEqual(db.updates, [{ id: "t1", values: { lastCheckedAt: NOW } }]);
});

test("envio falho deixa o alerta armado", async () => {
  const db = fakeDb({ tracking: trackedProduct() });
  const messaging = fakeMessaging(() => {
    throw new Error("FCM fora do ar");
  });
  const logger = fakeLogger();

  const summary = await run({
    db,
    messaging,
    logger,
    now: NOW,
    search: async () => ({ offers: [offer("64.79")], discarded: 0 }),
  });

  assert.strictEqual(summary.notified, 0);
  assert.deepStrictEqual(db.updates, [{ id: "t1", values: { lastCheckedAt: NOW } }]);
  assert.strictEqual(logger.lines.error.length, 1);
});

test("consulta que falha nao avalia o produto", async () => {
  const db = fakeDb({ tracking: trackedProduct({ lastNotifiedPrice: 64.79 }) });
  const logger = fakeLogger();

  const summary = await run({
    db,
    messaging: fakeMessaging(),
    logger,
    now: NOW,
    search: async () => {
      throw new Error("HTTP 503");
    },
  });

  assert.strictEqual(summary.failed, 1);
  assert.strictEqual(summary.checked, 0);
  // Sem update nenhum: tratar erro como "sem oferta" rearmaria o alerta.
  assert.deepStrictEqual(db.updates, []);
  assert.strictEqual(logger.lines.error.length, 1);
});

test("descarte de registros forjados aparece no log", async () => {
  const db = fakeDb({ tracking: trackedProduct() });
  const logger = fakeLogger();

  const summary = await run({
    db,
    messaging: fakeMessaging(),
    logger,
    now: NOW,
    search: async () => ({ offers: [], discarded: 12 }),
  });

  assert.strictEqual(summary.discarded, 12);
  assert.match(logger.lines.warn[0], /forjado/);
  // Sem esse aviso, IP marcado e "nenhum produto atingiu o alvo" sao o mesmo
  // silencio.
  assert.strictEqual(summary.notified, 0);
});

test("mesmo codigo de barras em dois rastreamentos consulta uma vez so", async () => {
  const db = fakeDb({
    tracking: {
      t1: { barCode: "789", description: "Omo", targetPrice: 70, active: true },
      t2: { barCode: "789", description: "Omo do outro aparelho", targetPrice: 60, active: true },
    },
  });

  let calls = 0;

  const summary = await run({
    db,
    messaging: fakeMessaging(),
    logger: fakeLogger(),
    now: NOW,
    search: async () => {
      calls++;
      return { offers: [offer("64.79")], discarded: 0 };
    },
  });

  assert.strictEqual(calls, 1);
  assert.strictEqual(summary.checked, 2);
  assert.strictEqual(summary.notified, 1);
});

test("pausado e sem alvo nao gastam consulta", async () => {
  const db = fakeDb({
    tracking: {
      t1: { barCode: "789", targetPrice: 70, active: false },
      t2: { barCode: "790", active: true },
    },
  });

  let calls = 0;

  const summary = await run({
    db,
    messaging: fakeMessaging(),
    logger: fakeLogger(),
    now: NOW,
    search: async () => {
      calls++;
      return { offers: [], discarded: 0 };
    },
  });

  assert.strictEqual(calls, 0);
  assert.strictEqual(summary.checked, 0);
});

test("lote de gravacao que falha nao leva os outros produtos junto", async () => {
  // Basta o usuario apagar um rastreamento enquanto a rodada consultava a API
  // para o update daquele documento derrubar o lote inteiro - e os alertas ja
  // teriam sido enviados, entao o mesmo aviso sairia de novo na proxima rodada.
  const db = fakeDb(
    {
      tracking: {
        t1: { barCode: "789", description: "Omo", targetPrice: 70, active: true },
        apagado: { barCode: "790", description: "Sumiu", targetPrice: 70, active: true },
      },
    },
    { batchFails: true, rejectDoc: "apagado" }
  );
  const logger = fakeLogger();

  await run({
    db,
    messaging: fakeMessaging(),
    logger,
    now: NOW,
    search: async () => ({ offers: [offer("64.79")], discarded: 0 }),
  });

  assert.deepStrictEqual(db.updates.map((update) => update.id), ["t1"]);
  assert.strictEqual(logger.lines.warn.length, 1);
  assert.strictEqual(logger.lines.error.length, 1);
});

test("produto de grupo usa o alvo do grupo e cita o grupo no alerta", async () => {
  const db = fakeDb({
    tracking: {
      t1: { barCode: "789", description: "Skol lata", targetPrice: 1, groupId: "g1", active: true },
    },
    trackingGroup: { g1: { name: "Cervejas", targetPrice: 4.5, active: true } },
  });
  const messaging = fakeMessaging();

  await run({
    db,
    messaging,
    logger: fakeLogger(),
    now: NOW,
    search: async () => ({ offers: [offer("3.99")], discarded: 0 }),
  });

  assert.strictEqual(messaging.sent.length, 1);
  assert.match(messaging.sent[0].notification.body, /Grupo Cervejas/);
});
