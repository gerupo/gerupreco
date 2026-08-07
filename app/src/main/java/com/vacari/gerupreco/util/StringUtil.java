package com.vacari.gerupreco.util;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

public class StringUtil {

    public static String or(String st1, String st2) {
        if(isNotEmpty(st1)) {
            return st1;
        }

        return st2;
    }

    public static boolean isEmpty(String st) {
        return st == null || st.trim().isEmpty();
    }

    public static boolean isNotEmpty(String st) {
        return !isEmpty(st);
    }

    /**
     * Remove acentos, caixa e espacos das pontas. Usado para busca e para
     * comparar tags, de forma que "Agua", "agua" e "Água" sejam equivalentes.
     */
    public static String normalize(String st) {
        if (st == null) {
            return "";
        }

        String withoutAccents = Normalizer.normalize(st, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Ordenacao de texto em portugues: "Agua" e "Água" ficam lado a lado em vez
     * de "Água" cair no fim da lista pela ordem de code point do Unicode.
     */
    public static Comparator<String> textComparator() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        collator.setStrength(Collator.SECONDARY);
        return Comparator.nullsFirst(collator::compare);
    }
}
