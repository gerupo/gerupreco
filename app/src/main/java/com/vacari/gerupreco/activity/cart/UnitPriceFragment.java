package com.vacari.gerupreco.activity.cart;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.cart.UnitPriceAdapter;
import com.vacari.gerupreco.model.cart.UnitPriceReport;
import com.vacari.gerupreco.util.CartUnitPrice;

/**
 * Aba "Produtos": qual produto rende mais por quilo ou por litro.
 *
 * Ranqueia os produtos entre si, nao os mercados - o estabelecimento entra so
 * como referencia na linha. A quantidade do carrinho nao entra na conta: seis
 * latas nao mudam o preco do litro.
 */
public class UnitPriceFragment extends CartTabFragment {

    private UnitPriceAdapter mAdapter;

    public UnitPriceFragment() {
        super(R.layout.fragment_cart_unit_price);
    }

    @Override
    protected void initGUI(@NonNull View view) {
        RecyclerView mRecyclerView = view.findViewById(R.id.unit_price_recycler);
        mRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false));
        mAdapter = new UnitPriceAdapter(requireContext());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    @Override
    protected void render(@NonNull View view) {
        UnitPriceReport report = CartUnitPrice.rank(host().getCartItems(),
                host().getPrices(), host().getWindowDays());

        mAdapter.refresh(report.getLines());

        // O aviso de vazio so vale depois da consulta; antes dela o resultado
        // esta vazio por falta de dados, nao por falta de oferta.
        view.findViewById(R.id.unit_price_empty).setVisibility(
                host().isLoaded() && report.isEmpty() ? View.VISIBLE : View.GONE);

        renderWarning(view, R.id.unit_price_unmeasured_card, R.id.unit_price_unmeasured_text,
                report.getUnmeasured(), R.plurals.cart_unmeasured);
        renderWarning(view, R.id.unit_price_unpriced_card, R.id.unit_price_unpriced_text,
                report.getUnpriced(), R.plurals.cart_unavailable);
    }
}
