package com.vacari.gerupreco.activity.cart;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.cart.MarketQuoteAdapter;
import com.vacari.gerupreco.model.cart.CartComparison;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.retrofit.CartPriceLoader;
import com.vacari.gerupreco.util.CartCompare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking de estabelecimentos para o carrinho.
 *
 * Os precos sao buscados uma unica vez, sem filtro de data, e guardados em
 * memoria. Trocar a janela de validade so refaz o calculo local, entao a
 * reordenacao e instantanea e nao gera trafego novo.
 */
public class CartCompareActivity extends AppCompatActivity {

    private static final String PREFS = "cart_compare";
    private static final String PREF_WINDOW = "window_days";
    private static final int DEFAULT_WINDOW = 7;

    private static final int[] WINDOW_DAYS = {1, 2, 3, 7, 15, 30, CartCompare.ANY_AGE};

    private MarketQuoteAdapter mAdapter;
    private ProgressDialog progressDialog;

    private List<CartItem> cartItems = new ArrayList<>();
    private Map<String, List<Product>> prices = new HashMap<>();
    private int windowDays = DEFAULT_WINDOW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_compare);
        setTitle(R.string.cart_compare_title);

        windowDays = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_WINDOW, DEFAULT_WINDOW);

        initGUI();
        buildWindowChips();
        load();
    }

    private void initGUI() {
        RecyclerView mRecyclerView = findViewById(R.id.compare_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mAdapter = new MarketQuoteAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void buildWindowChips() {
        ChipGroup group = findViewById(R.id.compare_window_group);

        for (int days : WINDOW_DAYS) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(windowLabel(days));
            chip.setTag(days);
            chip.setCheckable(true);
            chip.setChecked(days == windowDays);
            chip.setTextAppearanceResource(R.style.TextAppearance_GeruPreco_LabelCaps);
            // Cor por estado, senao o chip marcado fica igual aos outros.
            chip.setChipBackgroundColor(
                    ContextCompat.getColorStateList(this, R.color.chip_window_background));
            chip.setTextColor(
                    ContextCompat.getColorStateList(this, R.color.chip_window_text));
            chip.setChipStrokeWidth(0f);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);

            chip.setOnClickListener(view -> {
                windowDays = (int) view.getTag();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_WINDOW, windowDays).apply();
                render();
            });

            group.addView(chip);
        }
    }

    private String windowLabel(int days) {
        if (days == CartCompare.ANY_AGE) {
            return getString(R.string.cart_window_any);
        }
        return getResources().getQuantityString(R.plurals.cart_window_days, days, days);
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
        CartComparison comparison = CartCompare.compare(cartItems, prices, windowDays);

        mAdapter.refresh(comparison.getQuotes());

        findViewById(R.id.compare_empty)
                .setVisibility(comparison.isEmpty() ? View.VISIBLE : View.GONE);

        renderUnavailable(comparison);
    }

    private void renderUnavailable(CartComparison comparison) {
        MaterialCardView card = findViewById(R.id.compare_unavailable_card);

        if (comparison.getUnavailable().isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (CartItem cartItem : comparison.getUnavailable()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(cartItem.getDescription());
        }

        TextView text = findViewById(R.id.compare_unavailable_text);
        text.setText(getResources().getQuantityString(R.plurals.cart_unavailable,
                comparison.getUnavailable().size(),
                comparison.getUnavailable().size(), names.toString()));
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
