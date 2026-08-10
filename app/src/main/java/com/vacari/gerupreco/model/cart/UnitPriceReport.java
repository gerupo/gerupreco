package com.vacari.gerupreco.model.cart;

import com.vacari.gerupreco.model.sqlite.CartItem;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Resultado do ranking de custo-beneficio do carrinho.
 *
 * Quem fica de fora sai separado por motivo, porque a acao e diferente:
 * "unmeasured" se resolve corrigindo o cadastro do produto, "unpriced" so
 * abrindo a janela de datas ou esperando alguem registrar o preco.
 */
@Getter
@Setter
public class UnitPriceReport {

    private List<UnitPriceLine> lines = new ArrayList<>();
    private List<CartItem> unpriced = new ArrayList<>();
    private List<CartItem> unmeasured = new ArrayList<>();

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
