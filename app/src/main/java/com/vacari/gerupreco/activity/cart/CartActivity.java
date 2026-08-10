package com.vacari.gerupreco.activity.cart;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.cart.CartAdapter;
import com.vacari.gerupreco.dialog.cart.AddByTagDialog;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.repository.ItemRepository;

import java.util.List;

public class CartActivity extends AppCompatActivity {

    private CartAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);
        setTitle(R.string.cart);

        initGUI();
        configureActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void initGUI() {
        RecyclerView mRecyclerView = findViewById(R.id.cart_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mAdapter = new CartAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    private void configureActions() {
        MaterialButton compare = findViewById(R.id.cart_compare);
        compare.setOnClickListener(view -> openCompare());
    }

    private void refresh() {
        List<CartItem> cartItems = CartRepository.findAll(this);
        mAdapter.refresh(cartItems);

        boolean empty = cartItems.isEmpty();
        findViewById(R.id.cart_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.cart_recycler).setVisibility(empty ? View.GONE : View.VISIBLE);
        findViewById(R.id.cart_bottom_bar).setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty) {
            invalidateOptionsMenu();
            return;
        }

        int units = 0;
        for (CartItem cartItem : cartItems) {
            units += cartItem.getQuantity();
        }

        TextView count = findViewById(R.id.cart_summary_count);
        TextView unitsLabel = findViewById(R.id.cart_summary_units);
        count.setText(getResources().getQuantityString(
                R.plurals.cart_products, cartItems.size(), cartItems.size()));
        unitsLabel.setText(getResources().getQuantityString(
                R.plurals.cart_units, units, units));

        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_cart, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem clear = menu.findItem(R.id.menu_cart_clear);
        if (clear != null) {
            clear.setVisible(mAdapter.getItemCount() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_cart_clear) {
            confirmClear();
            return true;
        }

        if (item.getItemId() == R.id.menu_cart_add_by_tag) {
            openAddByTag();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * O catalogo vive no Firestore, entao a lista de tags so existe apos a
     * consulta - por isso o dialogo abre no retorno dela, e nao no toque.
     */
    private void openAddByTag() {
        ProgressDialog loading = ProgressDialog.show(this, getString(R.string.app_name),
                getString(R.string.search), true);

        ItemRepository.searchItem(items -> runOnUiThread(() -> {
            loading.dismiss();

            new AddByTagDialog(this, items, added -> {
                if (added == 0) {
                    Toast.makeText(this, R.string.cart_no_tags, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, getResources().getQuantityString(
                        R.plurals.cart_added, added, added), Toast.LENGTH_SHORT).show();
                refresh();
            }).show();
        }));
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cart_clear)
                .setMessage(R.string.cart_clear_confirm)
                .setPositiveButton(R.string.cart_clear, (dialog, which) -> {
                    CartRepository.clear(this);
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void changeQuantity(CartItem cartItem, int quantity) {
        CartRepository.updateQuantity(this, cartItem, quantity);
        refresh();
    }

    public void removeItem(CartItem cartItem) {
        CartRepository.delete(this, cartItem);
        refresh();
    }

    private void openCompare() {
        if (mAdapter.getItemCount() == 0) {
            Toast.makeText(this, R.string.cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, CartCompareActivity.class));
    }
}
