package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.notaparana.Product;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class PriceOffersTest {

    @Test
    public void ordenaPeloMenorPreco() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("8.00", daysAgo(1)),
                product("3.50", daysAgo(1)),
                product("5.00", daysAgo(1))), CartCompare.ANY_AGE);

        assertEquals("3.50", offers.get(0).getValor());
        assertEquals("5.00", offers.get(1).getValor());
        assertEquals("8.00", offers.get(2).getValor());
    }

    /** O caso comum: o mesmo produto pelo mesmo valor em notas de datas diferentes. */
    @Test
    public void empateNoPrecoDesempataPelaDataMaisRecente() {
        Date hoje = daysAgo(0);
        Date semanaPassada = daysAgo(7);
        Date ontem = daysAgo(1);

        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("4.00", semanaPassada),
                product("4.00", hoje),
                product("4.00", ontem)), CartCompare.ANY_AGE);

        assertEquals(hoje, offers.get(0).getDatahora());
        assertEquals(ontem, offers.get(1).getDatahora());
        assertEquals(semanaPassada, offers.get(2).getDatahora());
    }

    /** Preco menor vem antes mesmo sendo mais antigo: a data so desempata. */
    @Test
    public void precoPesaMaisQueData() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("9.00", daysAgo(0)),
                product("4.00", daysAgo(10))), CartCompare.ANY_AGE);

        assertEquals("4.00", offers.get(0).getValor());
    }

    @Test
    public void descartaOfertasForaDaJanela() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("2.00", daysAgo(10)),
                product("6.00", daysAgo(1))), 3);

        assertEquals(1, offers.size());
        assertEquals("6.00", offers.get(0).getValor());
    }

    @Test
    public void janelaAbertaMantemTudo() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("2.00", daysAgo(90)),
                product("6.00", daysAgo(1))), CartCompare.ANY_AGE);

        assertEquals(2, offers.size());
    }

    /**
     * O ponto e decimal na resposta da API: tratado como milhar, 3.11 viraria
     * 311 e a oferta mais barata iria para o fim da lista.
     */
    @Test
    public void pontoDecimalNaoViraMilhar() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product("12.00", daysAgo(1)),
                product("3.11", daysAgo(1))), CartCompare.ANY_AGE);

        assertEquals("3.11", offers.get(0).getValor());
    }

    /** Registro ilegivel nao some da tela, mas nao pode ocupar o topo. */
    @Test
    public void precoIlegivelVaiParaOFim() {
        List<Product> offers = PriceOffers.arrange(Arrays.asList(
                product(null, daysAgo(0)),
                product("7.00", daysAgo(5))), CartCompare.ANY_AGE);

        assertEquals(2, offers.size());
        assertEquals("7.00", offers.get(0).getValor());
    }

    @Test
    public void listaNulaNaoQuebra() {
        assertTrue(PriceOffers.arrange(null, CartCompare.ANY_AGE).isEmpty());
    }

    private static Product product(String valor, Date datahora) {
        Product product = new Product();
        product.setValor(valor);
        product.setDatahora(datahora);
        return product;
    }

    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L));
    }
}
