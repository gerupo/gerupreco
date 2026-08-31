"use strict";

/**
 * As mesmas regras do PriceUtil do app, portadas.
 *
 * A Nota Parana devolve o valor como texto com ponto decimal ("4.50"). Tratar
 * isso como formato pt-BR apagaria o ponto e transformaria 3.11 em 311 - o bug
 * que fez os totais do carrinho sairem cem vezes maiores. A virgula so e
 * considerada separador decimal quando de fato aparece na string, e ai o ponto
 * e que vira separador de milhar.
 */

/**
 * A formatacao e feita a mao, e nao por Intl, de proposito: o texto do alerta e
 * a unica coisa que o usuario ve desta rotina, e Intl depende do ICU embutido
 * no runtime. Num runtime com ICU reduzido "pt-BR" cai em ingles sem avisar, e
 * o alerta chegaria como "BRL 64.79" - defeito que os testes locais, com ICU
 * completo, nunca pegariam.
 */
function formatCurrency(value) {
  const negative = value < 0;
  const totalCents = Math.abs(Math.round(value * 100));
  const reais = String(Math.floor(totalCents / 100));
  const centavos = String(totalCents % 100).padStart(2, "0");
  const withThousands = reais.replace(/\B(?=(\d{3})+(?!\d))/g, ".");

  return `${negative ? "-" : ""}R$ ${withThousands},${centavos}`;
}

/**
 * Valor invalido volta null em vez de estourar: a oferta e tratada como preco
 * indisponivel e sai da conta, do mesmo jeito que na tela.
 */
function parse(value) {
  if (value === null || value === undefined) {
    return null;
  }

  let normalized = String(value).trim();

  if (normalized === "") {
    return null;
  }

  if (normalized.includes(",")) {
    normalized = normalized.replace(/\./g, "").replace(",", ".");
  }

  const parsed = Number(normalized);

  if (!Number.isFinite(parsed)) {
    return null;
  }

  return round(parsed);
}

/**
 * Preco em centavos, arredondado. Toda comparacao entre alvo e oferta passa por
 * aqui: o alvo vem do Firestore como Double, o preco vem da API como texto, e
 * comparar os dois como ponto flutuante deixaria 3.10 <= 3.10 depender de
 * representacao binaria.
 */
function cents(value) {
  return value === null || value === undefined ? null : Math.round(value * 100);
}

function round(value) {
  return Math.round(value * 100) / 100;
}

function format(value) {
  return value === null || value === undefined ? "--" : formatCurrency(value);
}

module.exports = { parse, cents, round, format };
