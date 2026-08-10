package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.vacari.gerupreco.model.cart.UnitBasis;

import org.junit.Test;

import java.math.BigDecimal;

public class UnitMeasureUtilTest {

    @Test
    public void gramaVirumaFracaoDoQuilo() {
        UnitBasis basis = UnitMeasureUtil.basisFor("500", "G");

        assertEquals(0, basis.getQuantity().compareTo(new BigDecimal("0.5")));
        assertEquals(UnitMeasureUtil.BASE_WEIGHT, basis.getLabel());
    }

    @Test
    public void mililitroViraFracaoDoLitro() {
        UnitBasis basis = UnitMeasureUtil.basisFor("350", "ML");

        assertEquals(0, basis.getQuantity().compareTo(new BigDecimal("0.35")));
        assertEquals(UnitMeasureUtil.BASE_VOLUME, basis.getLabel());
    }

    @Test
    public void quiloELitroFicamComoEstao() {
        assertEquals(0, UnitMeasureUtil.basisFor("5", "KG")
                .getQuantity().compareTo(new BigDecimal("5")));
        assertEquals(0, UnitMeasureUtil.basisFor("2", "L")
                .getQuantity().compareTo(new BigDecimal("2")));
    }

    @Test
    public void unidadeAceitaCaixaEAcentoDoCadastro() {
        assertEquals(UnitMeasureUtil.BASE_VOLUME,
                UnitMeasureUtil.basisFor("1", "l").getLabel());
        assertEquals(UnitMeasureUtil.BASE_WEIGHT,
                UnitMeasureUtil.basisFor("1", " Kg ").getLabel());
    }

    /**
     * O tamanho e campo de texto livre, entao chega com a unidade junto.
     */
    @Test
    public void tamanhoIgnoraLetrasDigitadasJunto() {
        assertEquals(0, UnitMeasureUtil.basisFor("500g", "G")
                .getQuantity().compareTo(new BigDecimal("0.5")));
    }

    /**
     * Mesma armadilha do PriceUtil: o ponto e decimal, nao separador de milhar.
     */
    @Test
    public void pontoEDecimalMasVirgulaTemPrecedencia() {
        assertEquals(0, UnitMeasureUtil.basisFor("1.5", "L")
                .getQuantity().compareTo(new BigDecimal("1.5")));
        assertEquals(0, UnitMeasureUtil.basisFor("1,5", "L")
                .getQuantity().compareTo(new BigDecimal("1.5")));
    }

    @Test
    public void cadastroIncompletoOuInvalidoVoltaNulo() {
        assertNull(UnitMeasureUtil.basisFor(null, "KG"));
        assertNull(UnitMeasureUtil.basisFor("", "KG"));
        assertNull(UnitMeasureUtil.basisFor("grande", "KG"));
        assertNull(UnitMeasureUtil.basisFor("0", "KG"));
        assertNull(UnitMeasureUtil.basisFor("500", null));
        assertNull(UnitMeasureUtil.basisFor("500", "duzia"));
    }
}
