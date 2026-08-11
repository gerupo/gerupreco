package com.vacari.gerupreco.activity.cart;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.cart.MarketQuoteAdapter;
import com.vacari.gerupreco.model.cart.CartComparison;
import com.vacari.gerupreco.util.CartCompare;

/**
 * Aba "Mercados": onde o carrinho inteiro sai mais barato.
 *
 * Ranqueia estabelecimentos - a soma do carrinho e a unidade de comparacao.
 * A aba irma ranqueia os produtos entre si.
 */
public class MarketQuoteFragment extends CartTabFragment {

    private MarketQuoteAdapter mAdapter;

    public MarketQuoteFragment() {
        super(R.layout.fragment_cart_markets);
    }

    @Override
    protected void initGUI(@NonNull View view) {
        RecyclerView mRecyclerView = view.findViewById(R.id.compare_recycler);
        mRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));
        mAdapter = new MarketQuoteAdapter(requireContext());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    @Override
    protected void render(@NonNull View view) {
        CartComparison comparison = CartCompare.compare(host().getCartItems(),
                host().getPrices(), host().getWindowDays());

        mAdapter.refresh(comparison.getQuotes());

        // O aviso de vazio so vale depois da consulta; antes dela o resultado
        // esta vazio por falta de dados, nao por falta de oferta.
        view.findViewById(R.id.compare_empty).setVisibility(
                host().isLoaded() && comparison.isEmpty() ? View.VISIBLE : View.GONE);

        renderWarning(view, R.id.compare_unavailable_card, R.id.compare_unavailable_text,
                comparison.getUnavailable(), R.plurals.cart_unavailable);
    }
}
