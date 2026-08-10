package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Um produto do carrinho dentro do orcamento de um estabelecimento.
 */
@Getter
@Setter
public class QuoteLine {

    private CartItem cartItem;
    private BigDecimal unitPrice;
    private Date date;

    public BigDecimal getSubtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
    }
}
