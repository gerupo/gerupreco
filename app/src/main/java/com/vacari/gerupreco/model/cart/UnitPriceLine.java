package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * Um produto do carrinho no ranking de custo-beneficio: o menor preco achado
 * para ele e quanto isso da por quilo ou por litro.
 *
 * Guarda o estabelecimento do menor preco so como referencia - aqui a
 * comparacao e entre produtos, e nao entre mercados.
 */
@Getter
@Setter
public class UnitPriceLine {

    private CartItem cartItem;
    private BigDecimal bestPrice;
    private BigDecimal pricePerBase;
    private String baseLabel;
    private String marketName;
    private Date date;
}
