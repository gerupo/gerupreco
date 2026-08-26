package com.vacari.gerupreco.model.cart;

import lombok.Getter;
import lombok.Setter;

/**
 * Um estabelecimento que aparece nas ofertas do carrinho, do jeito que o filtro
 * precisa dele: identificador para filtrar, nome e endereco para escolher.
 *
 * O codigo e a identidade - ha tres lojas distintas chamadas "MUFFATAO", e e o
 * endereco que as separa na tela.
 */
@Getter
@Setter
public class MarketOption {

    private String code;

    private String name;

    private String address;

}
