package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.math.BigDecimal;

public class PriceUtilTest {

    /**
     * O formato que a Nota Parana realmente devolve. Tratar isso como pt-BR
     * apagava o ponto e virava 311 - o preco saia cem vezes maior.
     */
    @Test
    public void parse_pontoEhSeparadorDecimal() {
        assertEquals(new BigDecimal("3.11"), PriceUtil.parse("3.11"));
        assertEquals(new BigDecimal("4.50"), PriceUtil.parse("4.50"));
        assertEquals(new BigDecimal("12.50"), PriceUtil.parse("12.5"));
        assertEquals(new BigDecimal("99.99"), PriceUtil.parse("99.99"));
    }

    @Test
    public void parse_aceitaVirgulaComoDecimal() {
        assertEquals(new BigDecimal("3.11"), PriceUtil.parse("3,11"));
        assertEquals(new BigDecimal("1234.56"), PriceUtil.parse("1.234,56"));
    }

    @Test
    public void parse_valorInvalidoVoltaNulo() {
        assertNull(PriceUtil.parse(null));
        assertNull(PriceUtil.parse(""));
        assertNull(PriceUtil.parse("  "));
        assertNull(PriceUtil.parse("abc"));
    }

    @Test
    public void parse_mantemOrdemEntreValoresComCasasDiferentes() {
        // 12.5 tem de ficar acima de 4.50, e nao virar 125 contra 450.
        assertEquals(1, PriceUtil.parse("12.5").compareTo(PriceUtil.parse("4.50")));
    }
}
