package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.cart.UnitBasis;
import com.vacari.gerupreco.model.cart.UnitPriceLine;
import com.vacari.gerupreco.model.cart.UnitPriceReport;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Ranking de custo-beneficio dos itens do carrinho: quanto custa o quilo ou o
 * litro de cada produto, do mais barato ao mais caro.
 *
 * Difere do CartCompare, que fecha o carrinho inteiro num estabelecimento. Aqui
 * a comparacao e entre os proprios produtos, entao vale o menor preco de cada
 * um, independente de onde foi achado - o estabelecimento entra so como
 * referencia na linha.
 *
 * Normalizar por embalagem nao muda nada dentro de um mesmo produto: a busca e
 * por GTIN, e todas as ofertas de um GTIN tem o mesmo tamanho. O que a divisao
 * revela e a comparacao entre produtos diferentes - a lata de 350ml contra a
 * garrafa de 1L.
 *
 * Peso e volume entram na mesma lista ordenada. Sao grandezas diferentes e
 * comparar R$/kg de arroz com R$/L de sabao nao diz nada sozinho; a lista unica
 * foi pedida assim de proposito, e cada linha carrega o rotulo da unidade.
 *
 * O filtro de data e local, pelo mesmo motivo descrito em CartCompare.
 */
public class CartUnitPrice {

    private static final int SCALE = 2;

    private CartUnitPrice() {
    }

    public static UnitPriceReport rank(List<CartItem> cartItems,
                                       Map<String, List<Product>> pricesByBarCode,
                                       int maxAgeDays) {
        UnitPriceReport report = new UnitPriceReport();
        Date cutoff = CartCompare.cutoffDate(maxAgeDays);

        for (CartItem cartItem : cartItems) {
            UnitBasis basis = UnitMeasureUtil.basisFor(cartItem.getSize(), cartItem.getUnitMeasure());

            // Tamanho invalido e problema de cadastro, nao falta de preco. Somar
            // esse produto aos "sem preco" mandaria procurar no lugar errado.
            if (basis == null) {
                report.getUnmeasured().add(cartItem);
                continue;
            }

            UnitPriceLine line = cheapest(cartItem, pricesByBarCode.get(cartItem.getBarCode()), cutoff);

            if (line == null) {
                report.getUnpriced().add(cartItem);
                continue;
            }

            line.setBaseLabel(basis.getLabel());
            line.setPricePerBase(line.getBestPrice()
                    .divide(basis.getQuantity(), SCALE, RoundingMode.HALF_UP));

            report.getLines().add(line);
        }

        Collections.sort(report.getLines(), ranking());
        return report;
    }

    /**
     * Menor preco do produto na janela. A mesma loja pode aparecer duas vezes
     * quando o GTIN esta cadastrado com e sem o zero a esquerda, e nao faz
     * diferenca aqui: o que interessa e o menor valor da lista inteira.
     */
    private static UnitPriceLine cheapest(CartItem cartItem, List<Product> products, Date cutoff) {
        if (products == null) {
            return null;
        }

        UnitPriceLine best = null;

        for (Product product : products) {
            if (!CartCompare.accepts(product, cutoff)) {
                continue;
            }

            BigDecimal price = PriceUtil.parse(product.getValor());
            if (price == null) {
                continue;
            }

            if (best != null && price.compareTo(best.getBestPrice()) >= 0) {
                continue;
            }

            Company company = product.getEstabelecimento();

            best = new UnitPriceLine();
            best.setCartItem(cartItem);
            best.setBestPrice(price);
            best.setDate(product.getDatahora());
            best.setMarketName(company == null
                    ? null
                    : StringUtil.or(company.getNm_fan(), company.getNm_emp()));
        }

        return best;
    }

    /**
     * Empate no preco por unidade e comum entre produtos da mesma linha; o
     * desempate por descricao mantem a lista estavel entre duas consultas.
     */
    private static Comparator<UnitPriceLine> ranking() {
        Comparator<String> byText = StringUtil.textComparator();

        return (a, b) -> {
            int byPrice = a.getPricePerBase().compareTo(b.getPricePerBase());
            if (byPrice != 0) {
                return byPrice;
            }
            return byText.compare(a.getCartItem().getDescription(),
                    b.getCartItem().getDescription());
        };
    }
}
