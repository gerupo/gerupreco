package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.cart.UnitPriceReport;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartUnitPriceTest {

    /**
     * O caso que justifica a tela: a lata custa menos na gondola, mas o litro
     * dela sai mais caro que o da garrafa.
     */
    @Test
    public void ordenaPeloPrecoPorLitro_naoPeloPrecoDaEmbalagem() {
        List<CartItem> cart = Arrays.asList(
                item("111", "Cerveja lata", "350", "ML"),
                item("222", "Cerveja garrafa", "1", "L"));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "3.50")));
        prices.put("222", Arrays.asList(product("MERC_A", "Mercado A", "8.00")));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, CartCompare.ANY_AGE);

        assertEquals(2, report.getLines().size());
        assertEquals("Cerveja garrafa", report.getLines().get(0).getCartItem().getDescription());
        assertEquals(new BigDecimal("8.00"), report.getLines().get(0).getPricePerBase());
        assertEquals("Cerveja lata", report.getLines().get(1).getCartItem().getDescription());
        assertEquals(new BigDecimal("10.00"), report.getLines().get(1).getPricePerBase());
    }

    @Test
    public void gramaEQuiloCaemNaMesmaBase() {
        List<CartItem> cart = Arrays.asList(
                item("111", "Cafe", "500", "G"),
                item("222", "Arroz", "5", "KG"));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "20.00")));
        prices.put("222", Arrays.asList(product("MERC_A", "Mercado A", "25.00")));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, CartCompare.ANY_AGE);

        assertEquals("Arroz", report.getLines().get(0).getCartItem().getDescription());
        assertEquals(new BigDecimal("5.00"), report.getLines().get(0).getPricePerBase());
        assertEquals(new BigDecimal("40.00"), report.getLines().get(1).getPricePerBase());
        assertEquals(UnitMeasureUtil.BASE_WEIGHT, report.getLines().get(0).getBaseLabel());
    }

    /**
     * Aqui a comparacao e entre produtos, entao nao importa em qual mercado o
     * menor preco foi achado - so que seja o menor.
     */
    @Test
    public void usaOMenorPrecoEntreOsMercados() {
        List<CartItem> cart = Arrays.asList(item("111", "Leite", "1", "L"));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "6.00"),
                product("MERC_B", "Mercado B", "4.00")));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, CartCompare.ANY_AGE);

        assertEquals(new BigDecimal("4.00"), report.getLines().get(0).getBestPrice());
        assertEquals("Mercado B", report.getLines().get(0).getMarketName());
    }

    /**
     * A quantidade no carrinho nao entra: seis latas nao mudam o preco do litro.
     */
    @Test
    public void quantidadeDoCarrinhoNaoAfetaOPrecoPorUnidade() {
        List<CartItem> cart = Arrays.asList(quantity(item("111", "Cerveja", "500", "ML"), 6));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "5.00")));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, CartCompare.ANY_AGE);

        assertEquals(new BigDecimal("10.00"), report.getLines().get(0).getPricePerBase());
    }

    @Test
    public void tamanhoInvalidoSaiComoCadastroAConsertar_naoComoSemPreco() {
        List<CartItem> cart = Arrays.asList(
                item("111", "Sabao", null, null),
                item("222", "Detergente", "500", "ML"));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "9.00")));
        prices.put("222", Arrays.asList(product("MERC_A", "Mercado A", "2.00")));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, CartCompare.ANY_AGE);

        assertEquals(1, report.getLines().size());
        assertEquals(1, report.getUnmeasured().size());
        assertEquals("Sabao", report.getUnmeasured().get(0).getDescription());
        assertTrue(report.getUnpriced().isEmpty());
    }

    @Test
    public void produtoSemPrecoNaJanelaSaiDaLista() {
        List<CartItem> cart = Arrays.asList(
                item("111", "Arroz", "5", "KG"),
                item("222", "Feijao", "1", "KG"));

        Product antigo = product("MERC_A", "Mercado A", "8.00");
        antigo.setDatahora(daysAgo(30));

        Map<String, List<Product>> prices = new HashMap<>();
        prices.put("111", Arrays.asList(product("MERC_A", "Mercado A", "25.00")));
        prices.put("222", Arrays.asList(antigo));

        UnitPriceReport report = CartUnitPrice.rank(cart, prices, 7);

        assertEquals(1, report.getLines().size());
        assertEquals(1, report.getUnpriced().size());
        assertEquals("Feijao", report.getUnpriced().get(0).getDescription());
    }

    @Test
    public void carrinhoSemNenhumPrecoVoltaVazio() {
        List<CartItem> cart = Arrays.asList(item("111", "Arroz", "5", "KG"));

        UnitPriceReport report = CartUnitPrice.rank(cart, new HashMap<>(), CartCompare.ANY_AGE);

        assertTrue(report.isEmpty());
        assertEquals(1, report.getUnpriced().size());
    }

    private CartItem item(String barCode, String description, String size, String unitMeasure) {
        CartItem cartItem = new CartItem();
        cartItem.setBarCode(barCode);
        cartItem.setDescription(description);
        cartItem.setSize(size);
        cartItem.setUnitMeasure(unitMeasure);
        cartItem.setQuantity(1);
        return cartItem;
    }

    private CartItem quantity(CartItem cartItem, int quantity) {
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
