package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.sqlite.CartItem;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Resultado da comparacao: o ranking de estabelecimentos e os produtos que nao
 * tem preco em lugar nenhum.
 */
@Getter
@Setter
public class CartComparison {

    private List<MarketQuote> quotes = new ArrayList<>();

    /**
     * Produtos sem nenhum preco dentro da janela escolhida. Ficam fora da lista
     * de faltantes de cada mercado: como faltariam em todos, so empurrariam
     * todo mundo para o grupo dos incompletos sem diferenciar ninguem.
     */
    private List<CartItem> unavailable = new ArrayList<>();

    private int comparedItems;

    public boolean isEmpty() {
        return quotes.isEmpty();
    }
}
