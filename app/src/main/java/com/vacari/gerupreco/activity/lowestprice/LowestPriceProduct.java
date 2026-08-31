package com.vacari.gerupreco.activity.lowestprice;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.MainActivity;
import com.vacari.gerupreco.activity.cart.CartActivity;
import com.vacari.gerupreco.adapter.lowestprice.ItemAdapter;
import com.vacari.gerupreco.dialog.GenericDialog;
import com.vacari.gerupreco.dialog.lowestprice.RegisterProductDialog;
import com.vacari.gerupreco.dialog.tracking.TrackProductDialog;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.repository.ItemRepository;
import com.vacari.gerupreco.repository.TrackingGroupRepository;
import com.vacari.gerupreco.repository.TrackingRepository;
import com.vacari.gerupreco.update.UpdateJob;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.SwipeToCart;
import com.vacari.gerupreco.util.TrackingScopes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LowestPriceProduct extends AppCompatActivity {

    private ItemAdapter mAdapter;

    private SearchView searchView;

    private View cartActionView;

    private ActivityResultLauncher<ScanOptions> barcodeLauncher;

    /**
     * Grupos e produtos ja rastreados, buscados uma vez para o dialogo do long
     * press abrir sem esperar rede. Vazio significa "ainda nao voltou", e o
     * dialogo apenas nao oferece grupo - preferivel a travar o gesto mais curto
     * da tela.
     */
    private List<TrackingGroup> trackingGroups = new ArrayList<>();

    private List<Tracking> trackings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        DatabaseManager.initDatabase(this);
        setContentView(R.layout.activity_lowest_price_product);

        initGUI();
        configureActions();
        registerResults();
        searchItems();
    }

    /**
     * Recarrega o cadastro do rastreamento e remarca a lista.
     *
     * Roda no onResume, e nao so no onCreate, porque a marca de rastreado pode
     * ficar velha por fora desta tela: o rastreamento some quando o usuario o
     * apaga na TrackingActivity, e a lista continuaria marcando um produto que
     * ninguem mais vigia. E a mesma razao pela qual o carrinho e remarcado ali.
     */
    private void loadTracking() {
        // Encadeadas, e nao em paralelo: o recorte do que este aparelho pode ver
        // depende do grupo, que e quem dita o alcance de quem esta dentro dele.
        TrackingGroupRepository.searchAll(loadedGroups ->
                TrackingRepository.searchAll(loadedTrackings -> {
                    trackingGroups = TrackingScopes.visibleGroups(this, loadedGroups);
                    trackings = TrackingScopes.visibleTrackings(this, loadedTrackings, loadedGroups);
                    refreshTrackingMarks();
                }));
    }

    private void refreshTrackingMarks() {
        Set<String> barCodes = new HashSet<>();
        for (Tracking tracking : trackings) {
            if (StringUtil.isNotEmpty(tracking.getBarCode())) {
                barCodes.add(tracking.getBarCode());
            }
        }
        mAdapter.setTrackedBarCodes(barCodes);
    }

    private void initGUI() {
        RecyclerView mRecyclerView = findViewById(R.id.lowest_price_product_recycler);
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        mAdapter = new ItemAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());

        configureSwipeToCart(mRecyclerView);
    }

    /**
     * Atalhos do long press: arrastar a linha para a direita adiciona ao
     * carrinho, para a esquerda remove. O produto continua na lista, entao o
     * notify e o que traz o card de volta para o lugar depois do gesto.
     */
    private void configureSwipeToCart(RecyclerView recyclerView) {
        ItemTouchHelper helper = new ItemTouchHelper(new SwipeToCart(this, new SwipeToCart.Host() {

            @Override
            public boolean isInCart(int position) {
                return mAdapter.isInCart(position);
            }

            @Override
            public void addToCart(int position) {
                LowestPriceProduct.this.addToCart(position);
                mAdapter.notifyItemChanged(position);
            }

            @Override
            public void removeFromCart(int position) {
                LowestPriceProduct.this.removeFromCart(position);
                mAdapter.notifyItemChanged(position);
            }
        }));
        helper.attachToRecyclerView(recyclerView);
    }

    private void configureActions() {
        SwipeRefreshLayout swipe = findViewById(R.id.main_swipe);
        swipe.setOnRefreshListener(() -> searchItems());
    }

    private void registerResults() {
        barcodeLauncher = registerForActivityResult(new ScanContract(),
                result -> {
                    if(result.getContents() != null) {
                        new RegisterProductDialog(LowestPriceProduct.this, result.getContents(), null).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
        refreshCartMarks();
        loadTracking();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();

        searchView.setQueryHint("Buscar...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.filter(newText);
                return false;
            }
        });

        // O item do carrinho usa actionLayout, entao nao passa por
        // onOptionsItemSelected e precisa do proprio listener.
        cartActionView = menu.findItem(R.id.menu_cart).getActionView();
        cartActionView.setOnClickListener(view -> openCart());
        updateCartBadge();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_scan_barcode) {
            openScanBarCode();
            return true;
        }

        return false;
    }

    private void updateCartBadge() {
        if (cartActionView == null) {
            return;
        }

        TextView badge = cartActionView.findViewById(R.id.cart_badge);
        int units = CartRepository.countUnits(this);

        badge.setText(units > 99 ? "99+" : String.valueOf(units));
        badge.setVisibility(units > 0 ? View.VISIBLE : View.GONE);
    }

    public void openCart() {
        clearSearchFocus();
        startActivity(new Intent(this, CartActivity.class));
    }

    public void addToCart(int position) {
        clearSearchFocus();
        Item item = mAdapter.getItemByPosition(position);
        CartRepository.add(this, item);
        onCartChanged(getString(R.string.cart_added_one, item.getDescription()));
    }

    /**
     * Tira do carrinho a linha inteira do produto, com a quantidade que tiver:
     * a lista marca presenca, e nao quantidade - quem ajusta unidade e a tela
     * do carrinho.
     */
    public void removeFromCart(int position) {
        clearSearchFocus();
        Item item = mAdapter.getItemByPosition(position);

        if (!CartRepository.removeByBarCode(this, item.getBarCode())) {
            // A marca estava velha (o carrinho mudou em outra tela): so recolhe.
            refreshCartMarks();
            return;
        }

        onCartChanged(getString(R.string.cart_removed_one, item.getDescription()));
    }

    /**
     * Ponto unico de atualizacao apos mexer no carrinho: avisa, refaz o contador
     * e remarca quais produtos da lista estao la dentro.
     */
    public void onCartChanged(String message) {
        updateCartBadge();
        refreshCartMarks();
        loadTracking();
        toast(message);
    }

    private void refreshCartMarks() {
        mAdapter.setCartBarCodes(CartRepository.barCodesInCart(this));
    }

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void searchItems() {
        ItemRepository.searchItem(data -> {
            mAdapter.refresh(data);
            SwipeRefreshLayout swipe = findViewById(R.id.main_swipe);
            swipe.setRefreshing(false);
        });
    }

    /**
     * Tira o foco da busca antes de abrir dialogo ou outra tela. Sem isto o
     * SearchView reassume o foco quando a sobreposicao fecha e reexibe o teclado
     * sozinho. O texto digitado e o filtro aplicado sao preservados.
     */
    private void clearSearchFocus() {
        if (searchView != null) {
            searchView.clearFocus();
        }
    }

    public void openScanBarCode() {
        clearSearchFocus();
        ScanOptions options = new ScanOptions();
        // TODO realizar consulta na nota parana quando ler qrcode
//        options.setDesiredBarcodeFormats(ScanOptions.EAN_13, ScanOptions.EAN_8);
        options.setDesiredBarcodeFormats(ScanOptions.EAN_13);
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    public void openLowestPrice(String barCode) {
        clearSearchFocus();
        Intent intent = new Intent(this, LowestPriceActivity.class);
        intent.putExtra("BARCODE", barCode);
        startActivity(intent);
    }

    /**
     * Poe o produto no rastreamento de precos. A partir daqui quem consulta e
     * compara com o alvo e a rotina agendada no servidor - a lista so cadastra.
     *
     * Um produto ja rastreado abre o cadastro que existe, em vez de criar outro:
     * duas linhas do mesmo codigo de barras renderiam duas notificacoes na mesma
     * queda, e nada na lista denunciaria a duplicata.
     */
    public void trackProduct(int position) {
        clearSearchFocus();
        Item item = mAdapter.getItemByPosition(position);

        Tracking tracking = trackedBy(item.getBarCode());
        boolean isNew = tracking == null;
        if (isNew) {
            tracking = TrackProductDialog.newFor(item);
        }

        new TrackProductDialog(this, tracking, trackingGroups,
                saved -> runOnUiThread(() -> {
                    if (saved != null && isNew) {
                        toast(getString(R.string.tracking_saved, item.getDescription()));
                    }
                    loadTracking();
                })).show();
    }

    private Tracking trackedBy(String barCode) {
        for (Tracking tracking : trackings) {
            if (barCode != null && barCode.equals(tracking.getBarCode())) {
                return tracking;
            }
        }
        return null;
    }

    /**
     * Tags ja usadas em qualquer produto, para sugerir no cadastro.
     */
    public java.util.List<String> getKnownTags() {
        return mAdapter.getAllTags();
    }

    public boolean existProduct(String barCode) {
        if(mAdapter.existProduct(barCode)) {
            GenericDialog.showDialogError(this, getString(R.string.product_duplicate));
            return true;
        }
        return false;
    }

    public void deleteItem(int position) {
        clearSearchFocus();
        Item item = mAdapter.getItemByPosition(position);
        GenericDialog.showConfirmDeleteDialog(this, data -> ItemRepository.delete(item.getId(), dat -> searchItems()));
    }

    public void editItem(int position) {
        clearSearchFocus();
        Item item = mAdapter.getItemByPosition(position);
        new RegisterProductDialog(LowestPriceProduct.this, null, item).show();
    }
}