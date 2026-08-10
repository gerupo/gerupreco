package com.vacari.gerupreco.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class PriceUtil {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private PriceUtil() {
    }

    /**
     * A Nota Parana devolve o valor como texto com ponto decimal ("4.50").
     * Tratar isso como formato pt-BR apagaria o ponto e transformaria 3.11 em
     * 311, entao a virgula so e considerada separador decimal quando aparece -
     * e ai o ponto e que vira separador de milhar.
     *
     * Valor invalido volta nulo em vez de estourar, e a linha e tratada como
     * preco indisponivel.
     */
    public static BigDecimal parse(String value) {
        if (StringUtil.isEmpty(value)) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        }

        try {
            return new BigDecimal(normalized).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String format(BigDecimal value) {
        if (value == null) {
            return "--";
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance(PT_BR);
        return currency.format(value);
    }
}
