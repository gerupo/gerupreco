package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Os codigos abaixo sao copias literais de respostas da Nota Parana - um da
 * consulta legitima da cerveja Lagunitas, outro da resposta forjada que a API
 * devolveu para a mesma consulta quando classificou o cliente como raspagem.
 */
public class DecoyFilterTest {

    private static final String REAL_CODE = "G3x7H0WKhGwqBUh2lKDikFAPVibrFC4PvP7JEkKrb8M";

    private static final String DECOY_CODE =
            "mbpf5iyob5r4x6w7l14vrsyqmb699qtpasddne980gfhvrnvacfnivoknaksn3fqoacrwut102depecdu50knao6";

    @Test
    public void mantemORegistroLegitimo() {
        assertFalse(DecoyFilter.isDecoy(product(REAL_CODE, "SUPER MUFFATO", "10.99")));
    }

    @Test
    public void descartaORegistroForjado() {
        assertTrue(DecoyFilter.isDecoy(product(DECOY_CODE, "TEEA DOS ALSIMENTOOS", "0.54")));
    }

    /**
     * O caso que originou o filtro: o preco forjado era o menor da lista, entao
     * era ele que ganhava o ranking de preco por litro.
     */
    @Test
    public void tiraOMenorPrecoQuandoEleEForjado() {
        List<Product> products = Arrays.asList(
                product(DECOY_CODE, "TEEA DOS ALSIMENTOOS", "0.54"),
                product(REAL_CODE, "SUPER MUFFATO", "10.99"));

        List<Product> clean = DecoyFilter.clean(products);

        assertEquals(1, clean.size());
        assertEquals("10.99", clean.get(0).getValor());
    }

    /**
     * A resposta envenenada vem inteira forjada. Sobrar lista vazia e o
     * resultado desejado: o produto aparece como sem preco em vez de exibir um
     * valor inventado.
     */
    @Test
    public void respostaInteiraForjadaSobraVazia() {
        List<Product> products = Arrays.asList(
                product(DECOY_CODE, "TEEA DOS ALSIMENTOOS", "0.54"),
                product(DECOY_CODE, "COPANXIA DO TENPEQ", "1.32"));

        assertTrue(DecoyFilter.clean(products).isEmpty());
    }

    @Test
    public void listaNulaNaoEstoura() {
        assertTrue(DecoyFilter.clean(null).isEmpty());
    }

    /**
     * Sem estabelecimento nao ha o que checar, e o registro segue: a regra
     * descreve a falsificacao, e o que ela nao reconhece passa.
     */
    @Test
    public void registroSemEstabelecimentoPassa() {
        Product product = new Product();
        product.setValor("10.99");

        assertFalse(DecoyFilter.isDecoy(product));
        assertEquals(1, DecoyFilter.clean(Arrays.asList(product)).size());
    }

    private Product product(String companyCode, String companyName, String value) {
        Company company = new Company();
        company.setCodigo(companyCode);
        company.setNm_emp(companyName);

        Product product = new Product();
        product.setEstabelecimento(company);
        product.setValor(value);
        return product;
    }
}
