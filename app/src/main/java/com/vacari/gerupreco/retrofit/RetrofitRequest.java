package com.vacari.gerupreco.retrofit;

import android.widget.Toast;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.lowestprice.LowestPriceActivity;
import com.vacari.gerupreco.model.notaparana.LowestPrice;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.DecoyFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class RetrofitRequest {

    public static void searchLowestPrice(String barCode, LowestPriceActivity context, Callback<List<Product>> callbackRepo) {
        RetrofitRequestService apiService = new RetrofitConfig().build().create(RetrofitRequestService.class);

        searchLowestPrice(apiService, barCode, callbackRepo, error -> {
            Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_LONG).show();
            context.closeProgress();
        });
    }

    /**
     * Consulta desacoplada de Activity, para o carrinho poder disparar varias em
     * paralelo reaproveitando o mesmo cliente HTTP.
     *
     * A limpeza do DecoyFilter mora aqui, e nao em quem consome, porque e o
     * unico funil por onde as duas telas passam: nenhuma delas tem como saber
     * que a resposta veio forjada.
     */
    public static void searchLowestPrice(RetrofitRequestService apiService, String barCode,
                                         Callback<List<Product>> onSuccess, Callback<Throwable> onError) {
        Call<LowestPrice> call = apiService.callProdutos("6g9fp8frx", 0, 20, -1, 0, barCode);
        call.enqueue(new retrofit2.Callback<LowestPrice>() {
            @Override
            public void onResponse(Call<LowestPrice> call, Response<LowestPrice> response) {
                // A API responde 503 quando esta sobrecarregada, e ai o corpo nao
                // e JSON. Sem esta guarda a recusa do servidor chegava na tela
                // como "produto sem preco em nenhum estabelecimento".
                if (!response.isSuccessful()) {
                    onError.callback(new IOException("HTTP " + response.code()));
                    return;
                }

                LowestPrice body = response.body();

                if (body == null || body.getProdutos() == null) {
                    // Produto sem nenhum registro devolve corpo vazio, e nao erro.
                    onSuccess.callback(new ArrayList<>());
                    return;
                }

                onSuccess.callback(DecoyFilter.clean(body.getProdutos()));
            }

            @Override
            public void onFailure(Call<LowestPrice> call, Throwable t) {
                onError.callback(t);
            }
        });
    }
}
