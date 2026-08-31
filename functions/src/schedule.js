"use strict";

/**
 * Quando sai a proxima rodada.
 *
 * Logica pura e testada, separada do daemon porque e conta de data - a classe de
 * codigo que erra em silencio e so aparece meses depois, num horario de verao ou
 * numa virada de ano.
 *
 * Dentro do container nao ha cron: o proprio processo se agenda. Por isso isto
 * existe aqui e nao num crontab.
 */

/** Os mesmos quatro horarios de sempre, em hora de Brasilia. */
const HOURS = [9, 12, 15, 18];

/**
 * Horario de Brasilia e deslocamento fixo desde 2019, quando o horario de verao
 * acabou no pais. A conta e uma subtracao, sem depender do banco de fusos do
 * sistema - o container alpine pode nem ter tzdata instalado, e ai um
 * toLocaleString("America/Sao_Paulo") devolveria UTC caladamente.
 */
const OFFSET_MS = 3 * 60 * 60 * 1000;

/**
 * O instante da proxima rodada, estritamente depois de `now`.
 *
 * "Estritamente" importa: sem isso, acordar exatamente na hora cheia devolveria
 * o mesmo horario de novo e o processo entraria num laco de timeouts de zero.
 */
function nextRunAt(now = Date.now()) {
  const local = new Date(now - OFFSET_MS);

  const year = local.getUTCFullYear();
  const month = local.getUTCMonth();
  const day = local.getUTCDate();

  for (const hour of HOURS) {
    const at = Date.UTC(year, month, day, hour) + OFFSET_MS;

    if (at > now) {
      return at;
    }
  }

  // Passou de todas as de hoje: a primeira de amanha. O Date.UTC vira o mes e o
  // ano sozinho quando o dia estoura.
  return Date.UTC(year, month, day + 1, HOURS[0]) + OFFSET_MS;
}

/**
 * Rotulo em hora de Brasilia, para o log e para o /health. Montado a mao pelo
 * mesmo motivo do offset: nao depender de tzdata no container.
 */
function format(at) {
  const local = new Date(at - OFFSET_MS);

  const pad = (n) => String(n).padStart(2, "0");

  return (
    `${pad(local.getUTCDate())}/${pad(local.getUTCMonth() + 1)}/${local.getUTCFullYear()} ` +
    `${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())} (Brasilia)`
  );
}

module.exports = { nextRunAt, format, HOURS, OFFSET_MS };
