package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.cart.CartComparison;
import com.vacari.gerupreco.model.cart.MarketQuote;
import com.vacari.gerupreco.model.cart.QuoteLine;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o ranking de estabelecimentos para um carrinho.
 *
 * O filtro de data e aplicado aqui, e nao no parametro "data" da API: aquele
 * parametro devolve resultados inconsistentes (7 dias traz mais registros que 3),
 * entao a busca sempre pede tudo e a janela e recortada localmente. Assim trocar
 * a janela na tela reordena na hora, sem nova consulta.
 */
public class CartCompare {

    public static final int ANY_AGE = -1;

    private CartCompare() {
    }

    public static CartComparison compare(List<CartItem> cartItems,
                                         Map<String, List<Product>> pricesByBarCode,
                                         int maxAgeDays) {
        CartComparison comparison = new CartComparison();
        Date cutoff = cutoffDate(maxAgeDays);

        // Melhor preco de cada produto em cada estabelecimento.
        Map<String, Map<String, QuoteLine>> byMarket = new LinkedHashMap<>();
        Map<String, Company> companies = new LinkedHashMap<>();
        List<CartItem> available = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            List<Product> products = pricesByBarCode.get(cartItem.getBarCode());
            boolean found = false;

            if (products != null) {
                for (Product product : products) {
                    if (!accepts(product, cutoff)) {
                        continue;
                    }

                    Company company = product.getEstabelecimento();
                    BigDecimal price = PriceUtil.parse(product.getValor());
                    if (company == null || StringUtil.isEmpty(company.getCodigo()) || price == null) {
                        continue;
                    }

                    found = true;
                    companies.putIfAbsent(company.getCodigo(), company);

                    Map<String, QuoteLine> marketLines = byMarket.computeIfAbsent(
                            company.getCodigo(), key -> new LinkedHashMap<>());

                    QuoteLine current = marketLines.get(cartItem.getBarCode());
                    // A mesma loja pode aparecer duas vezes quando o produto esta
                    // cadastrado com e sem o zero a esquerda no GTIN.
                    if (current == null || price.compareTo(current.getUnitPrice()) < 0) {
                        QuoteLine line = new QuoteLine();
                        line.setCartItem(cartItem);
                        line.setUnitPrice(price);
                        line.setDate(product.getDatahora());
                        marketLines.put(cartItem.getBarCode(), line);
                    }
                }
            }

            if (found) {
                available.add(cartItem);
            } else {
                comparison.getUnavailable().add(cartItem);
            }
        }

        for (Map.Entry<String, Map<String, QuoteLine>> entry : byMarket.entrySet()) {
            Company company = companies.get(entry.getKey());

            MarketQuote quote = new MarketQuote();
            quote.setCode(entry.getKey());
            quote.setName(StringUtil.or(company.getNm_fan(), company.getNm_emp()));
            quote.setAddress(company.getFullAddress());

            for (CartItem cartItem : available) {
                QuoteLine line = entry.getValue().get(cartItem.getBarCode());
                if (line != null) {
                    quote.getLines().add(line);
                } else {
                    quote.getMissing().add(cartItem);
                }
            }

            comparison.getQuotes().add(quote);
        }

        comparison.setComparedItems(available.size());
        Collections.sort(comparison.getQuotes(), ranking());
        return comparison;
    }

    /**
     * Quem tem o carrinho inteiro vem primeiro, do mais barato ao mais caro.
     * Depois os incompletos, primeiro os que menos deixam faltar - um total
     * baixo nao vale nada se veio de um mercado que so tem metade da lista.
     */
    private static Comparator<MarketQuote> ranking() {
        return (a, b) -> {
            if (a.isComplete() != b.isComplete()) {
                return a.isComplete() ? -1 : 1;
            }
            if (!a.isComplete()) {
                int byMissing = Integer.compare(a.getMissing().size(), b.getMissing().size());
                if (byMissing != 0) {
                    return byMissing;
                }
            }
            return a.getTotal().compareTo(b.getTotal());
        };
    }

    // Visivel no pacote: o ranking de custo-beneficio recorta a mesma janela.
    static boolean accepts(Product product, Date cutoff) {
        if (cutoff == null) {
            return true;
        }
        // Sem data nao da para garantir que esta dentro da janela.
        return product.getDatahora() != null && !product.getDatahora().before(cutoff);
    }

    static Date cutoffDate(int maxAgeDays) {
        if (maxAgeDays == ANY_AGE) {
            return null;
        }
        long millis = System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L);
        return new Date(millis);
    }
}
