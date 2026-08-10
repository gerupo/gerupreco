package com.vacari.gerupreco.adapter.cart;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.cart.MarketQuote;
import com.vacari.gerupreco.model.cart.QuoteLine;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.util.DateUtil;
import com.vacari.gerupreco.util.PriceUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarketQuoteAdapter extends RecyclerView.Adapter {

    private static final int MISSING_PREVIEW = 3;

    private final List<MarketQuote> quotes = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Context context;

    public MarketQuoteAdapter(Context context) {
        this.context = context;
    }

    public void refresh(List<MarketQuote> newQuotes) {
        quotes.clear();
        quotes.addAll(newQuotes);
        expanded.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(context).inflate(R.layout.market_quote_listview, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ViewHolder holder = (ViewHolder) viewHolder;
        MarketQuote quote = quotes.get(i);

        holder.rank.setText(String.valueOf(i + 1));
        holder.company.setText(quote.getName());
        holder.address.setText(quote.getAddress());
        holder.total.setText(PriceUtil.format(quote.getTotal()));

        bindCoverage(holder, quote);
        bindBreakdown(holder, quote);
        configureActions(holder, quote);
    }

    private void bindCoverage(ViewHolder holder, MarketQuote quote) {
        if (quote.isComplete()) {
            holder.coverageIcon.setImageResource(R.drawable.ic_check_circle_24);
            holder.coverageIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary_container));
            holder.coverage.setText(context.getResources().getQuantityString(
                    R.plurals.cart_complete, quote.getFoundCount(), quote.getFoundCount()));
            holder.coverage.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
            return;
        }

        holder.coverageIcon.setImageResource(R.drawable.ic_error_outline_24);
        holder.coverageIcon.setColorFilter(ContextCompat.getColor(context, R.color.error));
        holder.coverage.setTextColor(ContextCompat.getColor(context, R.color.error));

        holder.coverage.setText(context.getResources().getQuantityString(
                R.plurals.cart_missing, quote.getMissing().size(),
                quote.getMissing().size(), summarizeMissing(quote.getMissing())));
    }

    /**
     * Um carrinho grande pode faltar 20 produtos num mercado, e listar todos
     * transforma o card num paredao. No resumo entram alguns nomes; a lista
     * inteira fica no detalhamento.
     */
    private String summarizeMissing(List<CartItem> missing) {
        StringBuilder names = new StringBuilder();
        int shown = Math.min(missing.size(), MISSING_PREVIEW);

        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(missing.get(i).getDescription().trim());
        }

        if (missing.size() > shown) {
            return context.getString(R.string.cart_missing_more,
                    names.toString(), missing.size() - shown);
        }
        return names.toString();
    }

    private void bindBreakdown(ViewHolder holder, MarketQuote quote) {
        holder.breakdown.removeAllViews();

        for (QuoteLine line : quote.getLines()) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.quote_line_row, holder.breakdown, false);

            TextView quantity = row.findViewById(R.id.line_quantity);
            TextView description = row.findViewById(R.id.line_description);
            TextView subtotal = row.findViewById(R.id.line_subtotal);

            quantity.setText(context.getString(R.string.cart_quantity_short,
                    line.getCartItem().getQuantity()));
            description.setText(line.getCartItem().getDescription());
            subtotal.setText(PriceUtil.format(line.getSubtotal()));

            holder.breakdown.addView(row);
        }

        bindMissingRows(holder, quote);

        if (quote.getOldestDate() != null) {
            TextView freshness = new TextView(context);
            freshness.setTextAppearance(R.style.TextAppearance_GeruPreco_LabelCaps);
            freshness.setText(context.getString(R.string.cart_oldest_price,
                    DateUtil.dayFormat.format(quote.getOldestDate())));
            holder.breakdown.addView(freshness);
        }

        boolean isExpanded = expanded.contains(quote.getCode());
        holder.breakdown.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.toggle.setText(isExpanded
                ? R.string.cart_hide_details
                : R.string.cart_show_details);
    }

    /**
     * A lista completa de faltantes vive aqui, e nao no resumo do card.
     */
    private void bindMissingRows(ViewHolder holder, MarketQuote quote) {
        if (quote.getMissing().isEmpty()) {
            return;
        }

        for (CartItem cartItem : quote.getMissing()) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.quote_line_row, holder.breakdown, false);

            TextView quantity = row.findViewById(R.id.line_quantity);
            TextView description = row.findViewById(R.id.line_description);
            TextView subtotal = row.findViewById(R.id.line_subtotal);

            quantity.setText(context.getString(R.string.cart_quantity_short,
                    cartItem.getQuantity()));
            description.setText(cartItem.getDescription());
            description.setTextColor(ContextCompat.getColor(context, R.color.error));
            subtotal.setText(R.string.cart_missing_price);
            subtotal.setTextColor(ContextCompat.getColor(context, R.color.error));

            holder.breakdown.addView(row);
        }
    }

    private void configureActions(ViewHolder holder, MarketQuote quote) {
        holder.card.setOnClickListener(view -> {
            if (!expanded.remove(quote.getCode())) {
                expanded.add(quote.getCode());
            }
            notifyItemChanged(holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final TextView rank;
        final TextView company;
        final TextView address;
        final TextView total;
        final TextView coverage;
        final ImageView coverageIcon;
        final LinearLayout breakdown;
        final TextView toggle;

        public ViewHolder(View view) {
            super(view);
            card = (MaterialCardView) view;
            rank = view.findViewById(R.id.quote_rank);
            company = view.findViewById(R.id.quote_company);
            address = view.findViewById(R.id.quote_address);
            total = view.findViewById(R.id.quote_total);
            coverage = view.findViewById(R.id.quote_coverage);
            coverageIcon = view.findViewById(R.id.quote_coverage_icon);
            breakdown = view.findViewById(R.id.quote_breakdown);
            toggle = view.findViewById(R.id.quote_toggle);
        }
    }
}
