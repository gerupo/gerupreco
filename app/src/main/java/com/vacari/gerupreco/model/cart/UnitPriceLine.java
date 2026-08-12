package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Uma oferta no ranking de custo-beneficio: um produto do carrinho num
 * estabelecimento, por quanto sai a embalagem e quanto isso da por quilo ou por
 * litro.
 *
 * O mesmo produto rende uma linha por mercado que o vende, e todas concorrem
 * entre si na mesma lista ordenada.
 */
@Getter
@Setter
public class UnitPriceLine {

    private CartItem cartItem;
    private BigDecimal price;
    private BigDecimal pricePerBase;
    private String baseLabel;
    private String marketName;
    /**
     * Bairro. Nao aparece na tela - o endereco completo esta no detalhamento -,
     * mas desempata filiais de mesmo nome e mesmo preco na ordenacao, para a
     * ordem nao depender de como a API enfileirou a resposta.
     */
    private String marketArea;
    private Date date;

    /**
     * Identifica a oferta - produto mais estabelecimento. E por ele que o
     * adapter lembra quais cards estao abertos: a posicao na lista nao serve,
     * porque trocar a janela de datas reordena tudo.
     */
    private String key;

    /**
     * O registro da Nota Parana que originou a linha, para o detalhamento.
     *
     * Os campos acima sao os que a linha fechada mostra, e existem separados
     * para o ranking continuar sendo logica pura e testavel sem montar um
     * Product inteiro. O detalhamento e outra coisa: quer o registro cru, com
     * descricao da nota, distancia, endereco e hora - dados que nao entram em
     * nenhum calculo.
     */
    private Product source;
}
