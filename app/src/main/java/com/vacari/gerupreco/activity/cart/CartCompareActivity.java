package com.vacari.gerupreco.activity.cart;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.dialog.cart.MarketFilterDialog;
import com.vacari.gerupreco.model.cart.MarketOption;
import com.vacari.gerupreco.model.cart.MarketSelection;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.retrofit.CartPriceLoader;
import com.vacari.gerupreco.util.MarketFilter;
import com.vacari.gerupreco.util.PriceWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparador do carrinho, em duas leituras do mesmo dado.
 *
 * "Mercados" ranqueia estabelecimentos pela soma do carrinho; "Produtos"
 * ranqueia os itens entre si pelo preco por quilo e por litro. As duas eram
 * telas separadas e se confundiam - o nome de cada aba diz o que ela ranqueia,
 * que e exatamente onde diferem.
 *
 * O host guarda os precos porque as duas abas consomem os mesmos: uma consulta
 * so, e a segunda aba abre instantanea. Os precos vem sem filtro de data e o
 * recorte e local (ver CartCompare), entao trocar o chip so refaz o calculo em
 * memoria - a reordenacao e imediata e nao gera trafego novo.
 *
 * O filtro de mercado segue a mesma ideia e vive no mesmo lugar: e do host,
 * vale para as duas abas e recorta em memoria. A diferenca esta em onde ele
 * entra - o recorte acontece antes das abas lerem, dentro de getPrices(), de
 * modo que nem CartCompare nem CartUnitPrice sabem que ele existe.
 */
public class CartCompareActivity extends AppCompatActivity {

    private static final int TAB_MARKETS = 0;
    private static final int TAB_PRODUCTS = 1;
    private static final int TAB_COUNT = 2;

    /** Abas anexadas agora. O ViewPager2 destroi as que saem de vista. */
    private final List<CartTabFragment> tabs = new ArrayList<>();

    private ProgressDialog progressDialog;
    private TextView caption;

    private TextView filterLabel;

    private List<CartItem> cartItems = new ArrayList<>();

    /** Resposta inteira da consulta; e dela que sai a lista de mercados. */
    private Map<String, List<Product>> allPrices = new HashMap<>();

    /** O que as abas leem: allPrices recortado pelo mercado escolhido. */
    private Map<String, List<Product>> prices = new HashMap<>();

    private MarketSelection marketSelection;
    private boolean loaded;
    private int windowDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_compare);
        setTitle(R.string.cart_compare_title);

        windowDays = PriceWindow.load(this);

        initGUI();
        PriceWindow.buildChips(this, findViewById(R.id.compare_window_group), windowDays,
                days -> {
                    windowDays = days;
                    renderTabs();
                });
        load();
    }

    private void initGUI() {
        caption = findViewById(R.id.compare_caption);
        filterLabel = findViewById(R.id.compare_filter_label);

        ViewPager2 pager = findViewById(R.id.compare_pager);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == TAB_MARKETS
                        ? new MarketQuoteFragment()
                        : new UnitPriceFragment();
            }

            @Override
            public int getItemCount() {
                return TAB_COUNT;
            }
        });

        TabLayout tabLayout = findViewById(R.id.compare_tabs);
        new TabLayoutMediator(tabLayout, pager, (tab, position) -> tab.setText(
                position == TAB_MARKETS
                        ? R.string.cart_tab_markets
                        : R.string.cart_tab_products)).attach();

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateCaption(position);
            }
        });
        updateCaption(TAB_MARKETS);
    }

    private void updateCaption(int position) {
        caption.setText(position == TAB_MARKETS
                ? R.string.cart_tab_markets_caption
                : R.string.cart_tab_products_caption);
    }

    private void load() {
        cartItems = CartRepository.findAll(this);
        List<String> barCodes = CartRepository.barCodes(cartItems);

        showProgress(barCodes.size());

        CartPriceLoader.load(barCodes,
                done -> runOnUiThread(() -> updateProgress(done, barCodes.size())),
                result -> runOnUiThread(() -> {
                    allPrices = result;
                    prices = result;
                    loaded = true;
                    closeProgress();
                    // A acao de filtrar so aparece agora: a lista de mercados
                    // sai das ofertas que voltaram.
                    invalidateOptionsMenu();
                    renderTabs();
                }));
    }

    /** Copia a lista: uma aba pode se desanexar durante o proprio render. */
    private void renderTabs() {
        for (CartTabFragment tab : new ArrayList<>(tabs)) {
            tab.render();
        }
    }

    void registerTab(CartTabFragment tab) {
        tabs.add(tab);
    }

    void unregisterTab(CartTabFragment tab) {
        tabs.remove(tab);
    }

    List<CartItem> getCartItems() {
        return cartItems;
    }

    Map<String, List<Product>> getPrices() {
        return prices;
    }

    /**
     * Com filtro ativo, vazio quer dizer que aquele mercado nao tem, e nao que
     * nenhum tem - mandar afrouxar a janela de datas seria mandar procurar no
     * lugar errado.
     */
    boolean hasMarketFilter() {
        return marketSelection != null;
    }

    int getWindowDays() {
        return windowDays;
    }

    /** Falso ate a consulta voltar - as abas seguram o aviso de vazio ate la. */
    boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_cart_compare, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /** Sem precos na mao nao ha mercado para escolher. */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem filter = menu.findItem(R.id.menu_compare_filter);
        if (filter != null) {
            filter.setVisible(loaded && !MarketFilter.options(allPrices).isEmpty());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_compare_filter) {
            openMarketFilter();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openMarketFilter() {
        List<MarketOption> options = MarketFilter.options(allPrices);
        new MarketFilterDialog(this, options, marketSelection, this::applyMarketFilter).show();
    }

    /** Selecao nula e o "Limpar filtro": volta a valer a resposta inteira. */
    private void applyMarketFilter(MarketSelection selection) {
        marketSelection = selection;
        prices = selection == null
                ? allPrices
                : MarketFilter.apply(allPrices, selection.getCodes());

        renderFilterLabel();
        renderTabs();
    }

    private void renderFilterLabel() {
        if (marketSelection == null) {
            filterLabel.setVisibility(View.GONE);
            return;
        }

        filterLabel.setText(marketSelection.getAddress() == null
                ? getString(R.string.cart_filter_active_all, marketSelection.getName())
                : getString(R.string.cart_filter_active, marketSelection.getName(),
                        marketSelection.getAddress()));
        filterLabel.setVisibility(View.VISIBLE);
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
