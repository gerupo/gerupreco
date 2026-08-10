package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * O carrinho fechado num estabelecimento: o que ele tem, por quanto, e o que
 * falta. Identificado pelo codigo do estabelecimento, nao pelo nome, porque a
 * mesma rede tem varias lojas com o mesmo nome fantasia.
 */
@Getter
@Setter
public class MarketQuote {

    private String code;
    private String name;
    private String address;
    private List<QuoteLine> lines = new ArrayList<>();
    private List<CartItem> missing = new ArrayList<>();

    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteLine line : lines) {
            total = total.add(line.getSubtotal());
        }
        return total;
    }

    public boolean isComplete() {
        return missing.isEmpty();
    }

    public int getFoundCount() {
        return lines.size();
    }

    /**
     * Preco mais antigo do orcamento: e ele que define o quanto o total inteiro
     * pode estar desatualizado.
     */
    public Date getOldestDate() {
        Date oldest = null;
        for (QuoteLine line : lines) {
            if (line.getDate() == null) {
                continue;
            }
            if (oldest == null || line.getDate().before(oldest)) {
                oldest = line.getDate();
            }
        }
        return oldest;
    }
}
