package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.cart.CartComparison;
import com.vacari.gerupreco.model.cart.MarketQuote;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartCompareTest {

    @Test
    public void ranking_ordenaCompletosPeloMenorTotal() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", 1), item("222", "Feijao", 1));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "10.00"),
                product("MERC_B", "Mercado B", "8.00")));
        prices.put("222", Arrays.asList(product("MERC_A", "Mercado A", "5.00"),
                product("MERC_B", "Mercado B", "9.00")));

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        assertEquals(2, result.getQuotes().size());
        assertEquals("Mercado A", result.getQuotes().get(0).getName());
        assertEquals(new BigDecimal("15.00"), result.getQuotes().get(0).getTotal());
        assertEquals("Mercado B", result.getQuotes().get(1).getName());
        assertEquals(new BigDecimal("17.00"), result.getQuotes().get(1).getTotal());
    }

    @Test
    public void ranking_multiplicaPelaQuantidade() {
        List<CartItem> cart = Arrays.asList(item("111", "Cerveja", 6));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "4.50")));

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        assertEquals(new BigDecimal("27.00"), result.getQuotes().get(0).getTotal());
    }

    /**
     * Regra pedida: quem tem tudo vem antes de quem falta algo, mesmo que o
     * total parcial do incompleto seja menor.
     */
    @Test
    public void ranking_completoVemAntesDeIncompletoMaisBarato() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", 1), item("222", "Feijao", 1));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("COMPLETO", "Completo", "50.00"),
                product("FALTANDO", "Faltando", "1.00")));
        prices.put("222", Arrays.asList(product("COMPLETO", "Completo", "50.00")));

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        MarketQuote first = result.getQuotes().get(0);
        MarketQuote second = result.getQuotes().get(1);

        assertEquals("Completo", first.getName());
        assertTrue(first.isComplete());

        assertEquals("Faltando", second.getName());
        assertFalse(second.isComplete());
        assertEquals(1, second.getMissing().size());
        assertEquals("Feijao", second.getMissing().get(0).getDescription());
    }

    @Test
    public void ranking_incompletosOrdenamPorMenosFaltantes() {
        List<CartItem> cart = Arrays.asList(
                item("111", "A", 1), item("222", "B", 1), item("333", "C", 1));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("FALTA_UM", "Falta um", "90.00"),
                product("FALTA_DOIS", "Falta dois", "1.00")));
        prices.put("222", Arrays.asList(product("FALTA_UM", "Falta um", "90.00")));
        prices.put("333", new ArrayList<>());

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        assertEquals("Falta um", result.getQuotes().get(0).getName());
        assertEquals("Falta dois", result.getQuotes().get(1).getName());
    }

    /**
     * Produto sem preco em lugar nenhum sai do calculo de faltantes: se ficasse,
     * jogaria todos os mercados para o grupo dos incompletos sem diferenciar.
     */
    @Test
    public void produtoSemPrecoEmLugarNenhumNaoContaComoFaltante() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", 1), item("999", "Fantasma", 1));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "10.00")));
        prices.put("999", new ArrayList<>());

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        assertEquals(1, result.getUnavailable().size());
        assertEquals("Fantasma", result.getUnavailable().get(0).getDescription());
        assertTrue(result.getQuotes().get(0).isComplete());
        assertEquals(1, result.getComparedItems());
    }

    @Test
    public void janelaDeData_descartaPrecoAntigo() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", 1));

        Product recente = product("NOVO", "Novo", "10.00");
        recente.setDatahora(daysAgo(1));

        Product antigo = product("VELHO", "Velho", "1.00");
        antigo.setDatahora(daysAgo(20));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(recente, antigo));

        CartComparison seteDias = CartCompare.compare(cart, prices, 7);
        assertEquals(1, seteDias.getQuotes().size());
        assertEquals("Novo", seteDias.getQuotes().get(0).getName());

        CartComparison qualquerData = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);
        assertEquals(2, qualquerData.getQuotes().size());
    }

    /**
     * A mesma loja aparece duas vezes quando o GTIN esta cadastrado com e sem
     * zero a esquerda. Vale o menor preco, e nao duas linhas somadas.
     */
    @Test
    public void mesmoEstabelecimentoDuplicado_ficaComOMenorPreco() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", 1));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "10.00"),
                product("MERC_A", "Mercado A", "7.00")));

        CartComparison result = CartCompare.compare(cart, prices, CartCompare.ANY_AGE);

        assertEquals(1, result.getQuotes().size());
        assertEquals(1, result.getQuotes().get(0).getLines().size());
        assertEquals(new BigDecimal("7.00"), result.getQuotes().get(0).getTotal());
    }

    private CartItem item(String barCode, String description, int quantity) {
        CartItem cartItem = new CartItem();
        cartItem.setBarCode(barCode);
        cartItem.setDescription(description);
        cartItem.setQuantity(quantity);
        return cartItem;
    }

    private Product product(String code, String name, String value) {
        Company company = new Company();
        company.setCodigo(code);
        company.setNm_fan(name);

        Product product = new Product();
        product.setEstabelecimento(company);
        product.setValor(value);
        product.setDatahora(new Date());
        return product;
    }

    private Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L));
    }
}
