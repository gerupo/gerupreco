package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * Descarta os registros forjados que a Nota Parana devolve quando classifica o
 * cliente como raspagem.
 *
 * A API nao responde erro nesse caso: ela troca a resposta inteira por dados
 * gerados, no mesmo formato do JSON legitimo. As descricoes e os nomes de loja
 * vem embaralhados ("BJLACJA REKEADG TRAKIRAS DORANGJ", "TEEA DOS ALSIMENTOOS")
 * e os precos sao aleatorios - o que fazia a aba Produtos ranquear em primeiro
 * uma cerveja a R$ 0,54, preco que nenhum estabelecimento real praticava.
 *
 * A separacao foi medida sobre 1880 registros baixados da API (1486 legitimos,
 * 394 forjados) e e limpa em quatro campos independentes:
 *
 * <pre>
 * campo                     legitimo                 forjado
 * estabelecimento.codigo    43 chars, com maiusculas 81-90 chars, so minusculas
 * local                     geohash da loja (11)     ecoa o da consulta (9)
 * nm_fan                    preenchido em 55%        sempre vazio
 * uf                        sempre PR                PR, PE e ES misturados
 * </pre>
 *
 * O teste usa so o codigo do estabelecimento: e o campo mais estavel dos quatro
 * (identificador opaco do servidor) e sozinho ja separa os 1880 registros sem
 * um unico engano. A regra descreve o registro <b>forjado</b>, e nao o
 * legitimo, de proposito: se a Nota Parana mudar o formato do identificador, o
 * filtro para de reconhecer a falsificacao em vez de descartar o catalogo
 * inteiro como suspeito.
 *
 * Uma resposta envenenada vem inteira forjada - nunca misturada -, entao o que
 * sobra da limpeza e uma lista vazia, e o produto aparece como sem preco. E o
 * comportamento desejado: preco nenhum e melhor que preco inventado.
 */
public class DecoyFilter {

    /**
     * O codigo legitimo tem 43 caracteres; o forjado passa de 80. O corte no
     * meio evita depender do tamanho exato de qualquer um dos dois.
     */
    private static final int DECOY_CODE_LENGTH = 60;

    private DecoyFilter() {
    }

    public static List<Product> clean(List<Product> products) {
        List<Product> trusted = new ArrayList<>();

        if (products == null) {
            return trusted;
        }

        for (Product product : products) {
            if (!isDecoy(product)) {
                trusted.add(product);
            }
        }

        return trusted;
    }

    static boolean isDecoy(Product product) {
        Company company = product == null ? null : product.getEstabelecimento();

        if (company == null) {
            return false;
        }

        String code = company.getCodigo();
        return code != null && code.length() >= DECOY_CODE_LENGTH && isLowerCaseAlphanumeric(code);
    }

    /**
     * O codigo legitimo e base64url e sempre traz maiusculas; o forjado sai de
     * um alfabeto de minusculas e digitos.
     */
    private static boolean isLowerCaseAlphanumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
