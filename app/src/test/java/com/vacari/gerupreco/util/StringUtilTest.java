package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringUtilTest {

    @Test
    public void normalize_removeAcentosCaixaEEspacos() {
        assertEquals("agua", StringUtil.normalize("Água"));
        assertEquals("agua", StringUtil.normalize("  AGUA "));
        assertEquals("alcool", StringUtil.normalize("Álcool"));
        assertEquals("", StringUtil.normalize(null));
    }

    @Test
    public void ordenacao_mantemAguaEAguaAcentuadaJuntas() {
        List<String> descriptions = new ArrayList<>(Arrays.asList(
                "Água Sferrie", "Banana", "Agua Crystal", "Abacaxi"));

        descriptions.sort(StringUtil.textComparator());

        assertEquals(Arrays.asList("Abacaxi", "Agua Crystal", "Água Sferrie", "Banana"),
                descriptions);
    }

    @Test
    public void ordenacao_ignoraCaixa() {
        List<String> descriptions = new ArrayList<>(Arrays.asList("banana", "Abacaxi", "AMSTEL"));

        descriptions.sort(StringUtil.textComparator());

        assertEquals(Arrays.asList("Abacaxi", "AMSTEL", "banana"), descriptions);
    }

    @Test
    public void ordenacao_aceitaNulos() {
        List<String> descriptions = new ArrayList<>(Arrays.asList("Banana", null, "Abacaxi"));

        descriptions.sort(StringUtil.textComparator());

        assertTrue(descriptions.get(0) == null);
    }
}
