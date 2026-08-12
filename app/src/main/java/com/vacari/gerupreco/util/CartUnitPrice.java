package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.cart.UnitBasis;
import com.vacari.gerupreco.model.cart.UnitPriceLine;
import com.vacari.gerupreco.model.cart.UnitPriceReport;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking de custo-beneficio do carrinho: quanto custa o quilo ou o litro de
 * cada oferta, da mais barata a mais cara.
 *
 * Difere do CartCompare, que fecha o carrinho inteiro num estabelecimento. Aqui
 * a comparacao e entre as ofertas, misturando produtos e mercados na mesma
 * lista ordenada.
 *
 * A unidade da lista e a oferta - um produto num estabelecimento -, e nao o
 * produto: o mesmo item aparece uma vez por mercado que o vende, e todos
 * concorrem entre si pela posicao. Guardar so o menor preco de cada produto
 * respondia "qual produto rende mais", mas escondia por quanto ele sai nos
 * outros mercados, que e o que decide onde comprar.
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

            List<UnitPriceLine> offers = offers(cartItem, basis,
                    pricesByBarCode.get(cartItem.getBarCode()), cutoff);

            if (offers.isEmpty()) {
                report.getUnpriced().add(cartItem);
                continue;
            }

            report.getLines().addAll(offers);
        }

        Collections.sort(report.getLines(), ranking());
        return report;
    }

    /**
     * Uma linha por estabelecimento que vende o produto dentro da janela.
     *
     * O mesmo mercado costuma aparecer varias vezes na resposta - uma por nota
     * registrada, e mais uma quando o GTIN esta cadastrado com e sem o zero a
     * esquerda -, entao vale o menor preco de cada um. Sem isso a lista viraria
     * o historico de notas do produto, com a mesma loja repetida em datas
     * diferentes.
     */
    private static List<UnitPriceLine> offers(CartItem cartItem, UnitBasis basis,
                                              List<Product> products, Date cutoff) {
        Map<String, UnitPriceLine> byMarket = new LinkedHashMap<>();

        if (products == null) {
            return Collections.emptyList();
        }

        for (Product product : products) {
            if (!CartCompare.accepts(product, cutoff)) {
                continue;
            }

            BigDecimal price = PriceUtil.parse(product.getValor());
            if (price == null) {
                continue;
            }

            Company company = product.getEstabelecimento();
            String key = groupKey(company, byMarket.size());

            UnitPriceLine current = byMarket.get(key);
            if (current != null && price.compareTo(current.getPrice()) >= 0) {
                continue;
            }

            UnitPriceLine line = new UnitPriceLine();
            line.setCartItem(cartItem);
            line.setPrice(price);
            line.setPricePerBase(price.divide(basis.getQuantity(), SCALE, RoundingMode.HALF_UP));
            line.setBaseLabel(basis.getLabel());
            line.setDate(product.getDatahora());
            line.setMarketName(company == null
                    ? null
                    : StringUtil.or(company.getNm_fan(), company.getNm_emp()));
            line.setMarketArea(company == null ? null : company.getBairro());
            line.setKey(cartItem.getBarCode() + "|" + key);
            line.setSource(product);

            byMarket.put(key, line);
        }

        return new ArrayList<>(byMarket.values());
    }

    /**
     * Agrupa por codigo, nunca por nome: ha tres lojas distintas chamadas
     * "MUFFATAO", e agrupar por nome fundiria filiais. Registro sem codigo nao
     * tem como ser agrupado com ninguem, entao vira linha propria.
     */
    private static String groupKey(Company company, int position) {
        if (company == null || StringUtil.isEmpty(company.getCodigo())) {
            return "#" + position;
        }
        return company.getCodigo();
    }

    /**
     * Empate no preco por unidade e comum entre ofertas do mesmo produto; o
     * desempate por descricao e por mercado mantem a lista estavel entre duas
     * consultas.
     */
    private static Comparator<UnitPriceLine> ranking() {
        Comparator<String> byText = StringUtil.textComparator();

        return (a, b) -> {
            int byPrice = a.getPricePerBase().compareTo(b.getPricePerBase());
            if (byPrice != 0) {
                return byPrice;
            }

            int byDescription = byText.compare(a.getCartItem().getDescription(),
                    b.getCartItem().getDescription());
            if (byDescription != 0) {
                return byDescription;
            }

            int byMarket = byText.compare(StringUtil.or(a.getMarketName(), ""),
                    StringUtil.or(b.getMarketName(), ""));
            if (byMarket != 0) {
                return byMarket;
            }

            // Filiais de mesmo nome empatadas no preco: o bairro e o que sobra
            // para a ordem nao depender de como a API enfileirou a resposta.
            return byText.compare(StringUtil.or(a.getMarketArea(), ""),
                    StringUtil.or(b.getMarketArea(), ""));
        };
    }
}
