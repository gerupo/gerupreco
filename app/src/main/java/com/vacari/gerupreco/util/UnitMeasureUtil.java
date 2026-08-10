package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.cart.UnitBasis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converte o tamanho cadastrado do produto ("500" + "G") para a unidade base do
 * custo-beneficio: quilo para peso e litro para volume.
 *
 * A Nota Parana nao devolve o tamanho da embalagem - so descricao, preco, data
 * e estabelecimento - entao essa informacao so pode sair do cadastro do proprio
 * catalogo, onde "size" e campo de texto livre. Por isso a leitura e tolerante:
 * aceita "500", "500g", "1,5" e "1.5". Sem numero valido volta nulo, e a tela
 * pede correcao do cadastro em vez de chutar um valor.
 */
public class UnitMeasureUtil {

    public static final String BASE_WEIGHT = "kg";
    public static final String BASE_VOLUME = "L";

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final int SCALE = 6;

    private UnitMeasureUtil() {
    }

    public static UnitBasis basisFor(String size, String unitMeasure) {
        BigDecimal amount = parseSize(size);
        if (amount == null || amount.signum() <= 0) {
            return null;
        }

        switch (StringUtil.normalize(unitMeasure)) {
            case "g":
                return new UnitBasis(toBase(amount, THOUSAND), BASE_WEIGHT);
            case "kg":
                return new UnitBasis(toBase(amount, BigDecimal.ONE), BASE_WEIGHT);
            case "ml":
                return new UnitBasis(toBase(amount, THOUSAND), BASE_VOLUME);
            case "l":
                return new UnitBasis(toBase(amount, BigDecimal.ONE), BASE_VOLUME);
            default:
                return null;
        }
    }

    private static BigDecimal toBase(BigDecimal amount, BigDecimal divisor) {
        return amount.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Mesma convencao do PriceUtil: o ponto so vale como separador de milhar
     * quando a virgula tambem aparece, senao "1.5" litro viraria 15.
     */
    static BigDecimal parseSize(String size) {
        if (StringUtil.isEmpty(size)) {
            return null;
        }

        String number = size.replaceAll("[^0-9.,]", "");

        if (number.contains(",")) {
            number = number.replace(".", "").replace(",", ".");
        }

        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
