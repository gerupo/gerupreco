package com.vacari.gerupreco.activity.cart;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.PluralsRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.cart.UnitPriceAdapter;
import com.vacari.gerupreco.model.cart.UnitPriceReport;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.retrofit.CartPriceLoader;
import com.vacari.gerupreco.util.CartUnitPrice;
import com.vacari.gerupreco.util.PriceWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking de custo-beneficio dos produtos do carrinho.
 *
 * Segue o mesmo desenho da CartCompareActivity: os precos sao buscados uma
 * unica vez, sem filtro de data, e guardados em memoria. Trocar a janela so
 * refaz o calculo local, entao a reordenacao e instantanea e nao gera trafego.
 */
public class CartUnitPriceActivity extends AppCompatActivity {

    private UnitPriceAdapter mAdapter;
    private ProgressDialog progressDialog;

    private List<CartItem> cartItems = new ArrayList<>();
    private Map<String, List<Product>> prices = new HashMap<>();
    private int windowDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_unit_price);
        setTitle(R.string.cart_unit_price_title);

        windowDays = PriceWindow.load(this);

        initGUI();
        PriceWindow.buildChips(this, findViewById(R.id.unit_price_window_group), windowDays,
                days -> {
                    windowDays = days;
                    render();
                });
        load();
    }

    private void initGUI() {
        RecyclerView mRecyclerView = findViewById(R.id.unit_price_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mAdapter = new UnitPriceAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void load() {
        cartItems = CartRepository.findAll(this);
        List<String> barCodes = CartRepository.barCodes(cartItems);

        showProgress(barCodes.size());

        CartPriceLoader.load(barCodes,
                done -> runOnUiThread(() -> updateProgress(done, barCodes.size())),
                result -> runOnUiThread(() -> {
                    prices = result;
                    closeProgress();
                    render();
                }));
    }

    private void render() {
        UnitPriceReport report = CartUnitPrice.rank(cartItems, prices, windowDays);

        mAdapter.refresh(report.getLines());

        findViewById(R.id.unit_price_empty)
                .setVisibility(report.isEmpty() ? View.VISIBLE : View.GONE);

        renderWarning(R.id.unit_price_unmeasured_card, R.id.unit_price_unmeasured_text,
                report.getUnmeasured(), R.plurals.cart_unmeasured);
        renderWarning(R.id.unit_price_unpriced_card, R.id.unit_price_unpriced_text,
                report.getUnpriced(), R.plurals.cart_unavailable);
    }

    private void renderWarning(@IdRes int cardId, @IdRes int textId,
                               List<CartItem> items, @PluralsRes int plural) {
        MaterialCardView card = findViewById(cardId);

        if (items.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (CartItem cartItem : items) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(cartItem.getDescription());
        }

        TextView text = findViewById(textId);
        text.setText(getResources().getQuantityString(plural, items.size(),
                items.size(), names.toString()));
        card.setVisibility(View.VISIBLE);
    }

    private void showProgress(int total) {
        progressDialog = ProgressDialog.show(this, getString(R.string.app_name),
                getString(R.string.cart_searching, 0, total), true);
    }

    private void updateProgress(int done, int total) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(getString(R.string.cart_searching, done, total));
        }
    }

    private void closeProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        closeProgress();
        super.onDestroy();
    }
}
