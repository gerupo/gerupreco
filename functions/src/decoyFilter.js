"use strict";

/**
 * Porte do DecoyFilter do app: descarta os registros forjados que a Nota Parana
 * devolve quando classifica o cliente como raspagem.
 *
 * A API nao responde erro nesse caso - troca a resposta inteira por dados
 * gerados, no mesmo formato do JSON legitimo, com descricoes embaralhadas
 * ("BJLACJA REKEADG TRAKIRAS DORANGJ") e precos aleatorios.
 *
 * A separacao foi medida sobre 1880 registros (1486 legitimos, 394 forjados):
 * o codigo do estabelecimento legitimo tem 43 caracteres e traz maiusculas, o
 * forjado passa de 80 e sai de um alfabeto de minusculas e digitos. A regra
 * descreve o registro FORJADO, e nao o legitimo, de proposito: se o formato do
 * identificador mudar, o filtro para de reconhecer a falsificacao em vez de
 * descartar o catalogo inteiro como suspeito.
 *
 * Aqui o descarte precisa aparecer no log, e essa e a unica diferenca para a
 * versao do app. A funcao roda sozinha, sem tela: se o IP da funcao for
 * marcado, toda resposta volta forjada, a limpeza esvazia tudo e o sintoma e
 * nenhum alerta disparar - silencio identico ao de "nenhum produto atingiu o
 * alvo". Sem o log nao ha como distinguir os dois.
 */

/**
 * O codigo legitimo tem 43 caracteres; o forjado passa de 80. O corte no meio
 * evita depender do tamanho exato de qualquer um dos dois.
 */
const DECOY_CODE_LENGTH = 60;

function isLowerCaseAlphanumeric(value) {
  return /^[a-z0-9]+$/.test(value);
}

function isDecoy(product) {
  const code = product && product.estabelecimento && product.estabelecimento.codigo;

  if (!code) {
    return false;
  }

  return code.length >= DECOY_CODE_LENGTH && isLowerCaseAlphanumeric(code);
}

/**
 * Devolve os registros confiaveis e quantos foram descartados, para quem chama
 * poder registrar o descarte. Uma resposta envenenada vem inteira forjada -
 * nunca misturada -, entao discarded > 0 com trusted vazio e o retrato de um IP
 * marcado, e nao de um produto sem oferta.
 */
function clean(products) {
  const all = Array.isArray(products) ? products : [];
  const trusted = all.filter((product) => !isDecoy(product));

  return { trusted, discarded: all.length - trusted.length };
}

module.exports = { clean, isDecoy };
