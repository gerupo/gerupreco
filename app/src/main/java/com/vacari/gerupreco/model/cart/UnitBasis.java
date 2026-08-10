package com.vacari.gerupreco.model.cart;

import java.math.BigDecimal;

import lombok.Getter;

/**
 * Quanto o produto rende na unidade base - quilo ou litro. E o divisor do preco
 * no ranking de custo-beneficio.
 */
@Getter
public class UnitBasis {

    private final BigDecimal quantity;
    private final String label;

    public UnitBasis(BigDecimal quantity, String label) {
        this.quantity = quantity;
        this.label = label;
    }
}
