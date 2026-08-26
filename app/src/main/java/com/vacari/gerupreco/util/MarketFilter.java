package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.cart.MarketOption;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recorta as ofertas do carrinho por estabelecimento.
 *
 * O filtro entra antes de qualquer ranking: as abas continuam recebendo o mesmo
 * mapa de precos por codigo de barras, so que com menos ofertas dentro. Por
 * isso nem CartCompare nem CartUnitPrice sabem que ele existe.
 *
 * A escolha e feita em dois passos - primeiro o nome, depois o endereco -
 * porque nome sozinho nao identifica loja: ha tres "MUFFATAO" distintos, e
 * agrupar por nome fundiria filiais. O que o filtro aplica e sempre um conjunto
 * de {@code estabelecimento.codigo}; o nome so serve para a primeira escolha.
 */
public class MarketFilter {

    private MarketFilter() {
    }

    /**
     * Estabelecimentos presentes na consulta, um por loja, ordenados por nome e
     * endereco.
     *
     * Sai da resposta inteira, sem recortar pela janela de datas: a lista do
     * dialogo mudaria a cada troca de chip, e um mercado escolhido podia sumir
     * dela continuando aplicado.
     */
    public static List<MarketOption> options(Map<String, List<Product>> pricesByBarCode) {
        Map<String, MarketOption> byCode = new LinkedHashMap<>();

        if (pricesByBarCode != null) {
            for (List<Product> products : pricesByBarCode.values()) {
                if (products == null) {
                    continue;
                }
                for (Product product : products) {
                    collect(byCode, product.getEstabelecimento());
                }
            }
        }

        List<MarketOption> options = new ArrayList<>(byCode.values());
        Collections.sort(options, byNameThenAddress());
        return options;
    }

    private static void collect(Map<String, MarketOption> byCode, Company company) {
        if (company == null || StringUtil.isEmpty(company.getCodigo())
                || byCode.containsKey(company.getCodigo())) {
            return;
        }

        MarketOption option = new MarketOption();
        option.setCode(company.getCodigo());
        option.setName(StringUtil.or(company.getNm_fan(), company.getNm_emp()));
        option.setAddress(company.getFullAddress());
        byCode.put(option.getCode(), option);
    }

    /** Nomes distintos, na ordem em que as opcoes ja vieram. */
    public static List<String> names(List<MarketOption> options) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (MarketOption option : options) {
            if (seen.add(StringUtil.normalize(option.getName()))) {
                names.add(option.getName());
            }
        }
        return names;
    }

    /** Enderecos das lojas que atendem por esse nome. */
    public static List<String> addresses(List<MarketOption> options, String name) {
        List<String> addresses = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (MarketOption option : withName(options, name)) {
            if (seen.add(StringUtil.normalize(option.getAddress()))) {
                addresses.add(option.getAddress());
            }
        }
        return addresses;
    }

    /**
     * Codigos que o filtro deixa passar. Endereco vazio quer dizer "qualquer
     * loja com esse nome", que e o unico caso em que o nome vale sozinho.
     */
    public static Set<String> codesFor(List<MarketOption> options, String name, String address) {
        Set<String> codes = new LinkedHashSet<>();
        boolean anyAddress = StringUtil.isEmpty(address);
        String wanted = StringUtil.normalize(address);

        for (MarketOption option : withName(options, name)) {
            if (anyAddress || StringUtil.normalize(option.getAddress()).equals(wanted)) {
                codes.add(option.getCode());
            }
        }
        return codes;
    }

    /**
     * Mesmo mapa, so com as ofertas dos estabelecimentos escolhidos.
     *
     * As chaves sao preservadas mesmo quando sobram sem oferta nenhuma: e assim
     * que o produto que o mercado nao tem continua aparecendo como faltante em
     * vez de desaparecer da comparacao.
     */
    public static Map<String, List<Product>> apply(Map<String, List<Product>> pricesByBarCode,
                                                   Set<String> codes) {
        if (pricesByBarCode == null || codes == null || codes.isEmpty()) {
            return pricesByBarCode;
        }

        Map<String, List<Product>> filtered = new LinkedHashMap<>();

        for (Map.Entry<String, List<Product>> entry : pricesByBarCode.entrySet()) {
            List<Product> kept = new ArrayList<>();

            if (entry.getValue() != null) {
                for (Product product : entry.getValue()) {
                    Company company = product.getEstabelecimento();
                    if (company != null && codes.contains(company.getCodigo())) {
                        kept.add(product);
                    }
                }
            }

            filtered.put(entry.getKey(), kept);
        }

        return filtered;
    }

    private static List<MarketOption> withName(List<MarketOption> options, String name) {
        List<MarketOption> matches = new ArrayList<>();
        String wanted = StringUtil.normalize(name);

        for (MarketOption option : options) {
            if (StringUtil.normalize(option.getName()).equals(wanted)) {
                matches.add(option);
            }
        }
        return matches;
    }

    /** Collator pt-BR nos dois campos: sem ele "Agua" e "Agua" acentuado se separam. */
    private static Comparator<MarketOption> byNameThenAddress() {
        Comparator<String> text = StringUtil.textComparator();
        return (a, b) -> {
            int byName = text.compare(a.getName(), b.getName());
            return byName != 0 ? byName : text.compare(a.getAddress(), b.getAddress());
        };
    }
}
