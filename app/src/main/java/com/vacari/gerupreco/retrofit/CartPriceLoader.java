package com.vacari.gerupreco.retrofit;

import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Busca os precos de varios codigos de barras.
 *
 * A API aceita um gtin por chamada - testei lista separada por virgula, o
 * parametro repetido e a forma gtin[], e nenhuma funciona - entao o carrinho
 * dispara uma consulta por produto. O OkHttp segura a concorrencia por host,
 * e as respostas sao juntadas conforme chegam.
 */
public class CartPriceLoader {

    private CartPriceLoader() {
    }

    public static void load(List<String> barCodes, Callback<Integer> onProgress,
                            Callback<Map<String, List<Product>>> onComplete) {

        Map<String, List<Product>> result = new ConcurrentHashMap<>();

        if (barCodes.isEmpty()) {
            onComplete.callback(result);
            return;
        }

        RetrofitRequestService apiService = new RetrofitConfig().build().create(RetrofitRequestService.class);
        AtomicInteger pending = new AtomicInteger(barCodes.size());
        AtomicInteger done = new AtomicInteger(0);

        for (String barCode : barCodes) {
            RetrofitRequest.searchLowestPrice(apiService, barCode,
                    products -> {
                        result.put(barCode, products);
                        finish(pending, done, onProgress, onComplete, result);
                    },
                    // Falha de rede num produto nao derruba a comparacao: ele
                    // entra como sem preco e o resto do carrinho segue.
                    error -> {
                        result.put(barCode, new ArrayList<>());
                        finish(pending, done, onProgress, onComplete, result);
                    });
        }
    }

    private static void finish(AtomicInteger pending, AtomicInteger done,
                               Callback<Integer> onProgress,
                               Callback<Map<String, List<Product>>> onComplete,
                               Map<String, List<Product>> result) {
        onProgress.callback(done.incrementAndGet());

        if (pending.decrementAndGet() == 0) {
            onComplete.callback(result);
        }
    }
}
