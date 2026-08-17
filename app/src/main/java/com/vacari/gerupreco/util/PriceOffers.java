package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.notaparana.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Recorta e ordena as ofertas de um unico produto, para a tela de precos.
 *
 * O filtro de data e local, pelo mesmo motivo descrito em CartCompare: a busca
 * pede tudo com data=-1 e a janela e recortada em memoria, entao trocar o chip
 * reordena na hora, sem consulta nova.
 *
 * A ordem e preco crescente e, no empate, data decrescente. O empate e comum
 * aqui: como a lista e o historico de notas de um mesmo GTIN, a mesma loja
 * costuma aparecer varias vezes pelo mesmo valor, e sem o desempate a nota de
 * duas semanas atras podia ficar na frente da de ontem.
 */
public class PriceOffers {

    private PriceOffers() {
    }

    public static List<Product> arrange(List<Product> products, int maxAgeDays) {
        List<Product> offers = new ArrayList<>();

        if (products == null) {
            return offers;
        }

        Date cutoff = CartCompare.cutoffDate(maxAgeDays);
        for (Product product : products) {
            if (CartCompare.accepts(product, cutoff)) {
                offers.add(product);
            }
        }

        Collections.sort(offers, ranking());
        return offers;
    }

    private static Comparator<Product> ranking() {
        return (a, b) -> {
            int byPrice = comparePrice(PriceUtil.parse(a.getValor()), PriceUtil.parse(b.getValor()));
            if (byPrice != 0) {
                return byPrice;
            }
            return compareDate(a.getDatahora(), b.getDatahora());
        };
    }

    /** Preco ilegivel nao some da lista, mas vai para o fim: nao da para ranquear. */
    private static int comparePrice(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b ? 0 : (a == null ? 1 : -1);
        }
        return a.compareTo(b);
    }

    /** Mais recente primeiro; registro sem data fica atras de qualquer datado. */
    private static int compareDate(Date a, Date b) {
        if (a == null || b == null) {
            return a == b ? 0 : (a == null ? 1 : -1);
        }
        return b.compareTo(a);
    }
}
