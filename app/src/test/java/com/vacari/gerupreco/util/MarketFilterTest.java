package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.cart.MarketOption;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarketFilterTest {

    @Test
    public void options_umaEntradaPorLoja_ordenadaPorNomeEEndereco() {
        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(
                product("COD_C", "MUFFATAO", "BRASIL", "7210"),
                product("COD_A", "ATACADAO", "TANCREDO NEVES", "3401"),
                product("COD_B", "MUFFATAO", "AVENIDA", "100")));
        // A mesma loja repetida em outro produto nao vira opcao nova.
        prices.put("222", Arrays.asList(product("COD_A", "ATACADAO", "TANCREDO NEVES", "3401")));

        List<MarketOption> options = MarketFilter.options(prices);

        assertEquals(3, options.size());
        assertEquals("ATACADAO", options.get(0).getName());
        assertEquals("MUFFATAO", options.get(1).getName());
        assertEquals("AVENIDA, 100 - CENTRO", options.get(1).getAddress());
        assertEquals("BRASIL, 7210 - CENTRO", options.get(2).getAddress());
    }

    /**
     * Filiais com o mesmo nome sao lojas diferentes: o nome so agrupa a
     * primeira escolha, e o endereco e quem separa.
     */
    @Test
    public void names_agrupaFiliais_addressesSeparaCadaUma() {
        List<MarketOption> options = MarketFilter.options(pricesComTresLojas());

        assertEquals(Arrays.asList("ATACADAO", "MUFFATAO"), MarketFilter.names(options));
        assertEquals(Arrays.asList("AVENIDA, 100 - CENTRO", "BRASIL, 7210 - CENTRO"),
                MarketFilter.addresses(options, "MUFFATAO"));
    }

    @Test
    public void codesFor_semEndereco_pegaTodasAsFiliais() {
        List<MarketOption> options = MarketFilter.options(pricesComTresLojas());

        Set<String> codes = MarketFilter.codesFor(options, "MUFFATAO", null);

        assertEquals(2, codes.size());
        assertTrue(codes.contains("COD_B"));
        assertTrue(codes.contains("COD_C"));
    }

    @Test
    public void codesFor_comEndereco_pegaSoAquelaLoja() {
        List<MarketOption> options = MarketFilter.options(pricesComTresLojas());

        Set<String> codes = MarketFilter.codesFor(options, "MUFFATAO", "BRASIL, 7210 - CENTRO");

        assertEquals(1, codes.size());
        assertTrue(codes.contains("COD_C"));
    }

    @Test
    public void apply_mantemSoAsOfertasDosCodigosEscolhidos() {
        Map<String, List<Product>> filtered =
                MarketFilter.apply(pricesComTresLojas(), codes("COD_C"));

        assertEquals(1, filtered.get("111").size());
        assertEquals("COD_C", filtered.get("111").get(0).getEstabelecimento().getCodigo());
    }

    /**
     * O produto que a loja filtrada nao vende continua como chave sem oferta -
     * e assim que ele aparece como faltante em vez de sumir da comparacao.
     */
    @Test
    public void apply_preservaProdutoSemOfertaNaLojaEscolhida() {
        Map<String, List<Product>> filtered =
                MarketFilter.apply(pricesComTresLojas(), codes("COD_C"));

        assertTrue(filtered.containsKey("222"));
        assertTrue(filtered.get("222").isEmpty());
    }

    @Test
    public void apply_semCodigos_devolveTudo() {
        Map<String, List<Product>> prices = pricesComTresLojas();

        assertEquals(prices, MarketFilter.apply(prices, null));
        assertEquals(prices, MarketFilter.apply(prices, new LinkedHashSet<>()));
    }

    private Map<String, List<Product>> pricesComTresLojas() {
        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(
                product("COD_C", "MUFFATAO", "BRASIL", "7210"),
                product("COD_A", "ATACADAO", "TANCREDO NEVES", "3401"),
                product("COD_B", "MUFFATAO", "AVENIDA", "100")));
        prices.put("222", Arrays.asList(product("COD_A", "ATACADAO", "TANCREDO NEVES", "3401")));
        return prices;
    }

    private Set<String> codes(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private Product product(String code, String name, String street, String number) {
        Company company = new Company();
        company.setCodigo(code);
        company.setNm_fan(name);
        company.setNm_logr(street);
        company.setNr_logr(number);
        company.setBairro("CENTRO");

        Product product = new Product();
        product.setEstabelecimento(company);
        product.setValor("10.00");
        return product;
    }
}
