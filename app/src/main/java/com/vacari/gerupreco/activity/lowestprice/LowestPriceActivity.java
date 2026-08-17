package com.vacari.gerupreco.activity.lowestprice;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.lowestprice.LowestPriceAdapter;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.retrofit.RetrofitRequest;
import com.vacari.gerupreco.util.CartCompare;
import com.vacari.gerupreco.util.PriceOffers;
import com.vacari.gerupreco.util.PriceWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Precos de um unico produto, do mais barato ao mais caro.
 *
 * A consulta traz tudo (data=-1) e a janela de datas e recortada em memoria, do
 * mesmo jeito que nas abas do carrinho: trocar o chip reordena na hora, sem
 * trafego novo. A escolha e a mesma guardada pelo comparador do carrinho.
 */
public class LowestPriceActivity extends AppCompatActivity {

    private LowestPriceAdapter mAdapter;
    private TextView emptyView;
    private static ProgressDialog progressDialog;

    /** Resposta inteira da API; a lista da tela e um recorte dela. */
    private final List<Product> offers = new ArrayList<>();
    private boolean loaded;
    private int windowDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lowest_price);

        String barCode = getIntent().getExtras().getString("BARCODE");
        windowDays = PriceWindow.load(this);

        initGUI();
        PriceWindow.buildChips(this, findViewById(R.id.price_window_group), windowDays,
                days -> {
                    windowDays = days;
                    render();
                });
        search(barCode);
    }

    private void initGUI() {
        emptyView = findViewById(R.id.lowest_price_empty);

        RecyclerView mRecyclerView = findViewById(R.id.recycler_price_id);
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new LowestPriceAdapter(new ArrayList<>(), this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void search(String barCode) {
        showProgress();
        RetrofitRequest.searchLowestPrice(barCode, this, data -> {
            LowestPriceActivity.this.runOnUiThread(() -> {
                offers.clear();
                offers.addAll(data);
                loaded = true;
                render();
                closeProgress();
            });
        });
    }

    private void render() {
        List<Product> visible = PriceOffers.arrange(offers, windowDays);
        mAdapter.refresh(visible);

        // Com a janela aberta nao ha o que afrouxar: mandar mexer no filtro
        // seria mandar procurar no lugar errado.
        emptyView.setText(windowDays == CartCompare.ANY_AGE
                ? R.string.lowest_price_none
                : R.string.lowest_price_empty);

        // Antes da consulta voltar a lista esta vazia por falta de dados, e nao
        // por falta de oferta - sem a guarda a tela pisca o aviso.
        emptyView.setVisibility(loaded && visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showProgress() {
        progressDialog = ProgressDialog.show(this, getString(R.string.app_name),
                getString(R.string.search), true);
    }

    public void closeProgress() {
        if(progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    public void openMaps() {
//        Uri gmmIntentUri = Uri.parse("geo:0,0?q=1600 Amphitheatre Parkway, Mountain+View, California");
//        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
//        mapIntent.setPackage("com.google.android.apps.maps");
//        startActivity(mapIntent);
    }
}
