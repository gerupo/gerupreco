package com.vacari.gerupreco.model.cart;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

/**
 * Filtro de mercado escolhido no dialogo.
 *
 * Nome e endereco existem so para escrever na tela o que esta filtrado; quem
 * decide o que passa e {@link #getCodes()}, resolvido no momento da escolha a
 * partir dos estabelecimentos presentes na consulta. Endereco nulo significa
 * "todos os enderecos deste mercado" - ai o conjunto traz uma filial por loja.
 */
@Getter
@Setter
public class MarketSelection {

    private String name;

    private String address;

    private Set<String> codes;

}
