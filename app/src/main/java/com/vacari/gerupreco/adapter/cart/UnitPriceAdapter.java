package com.vacari.gerupreco.adapter.cart;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.cart.UnitPriceLine;
import com.vacari.gerupreco.model.notaparana.Company;
import com.vacari.gerupreco.model.notaparana.Product;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.util.DateUtil;
import com.vacari.gerupreco.util.PriceUtil;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnitPriceAdapter extends RecyclerView.Adapter {

    private static final String SEPARATOR = "  ·  ";

    private final List<UnitPriceLine> lines = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Context context;

    public UnitPriceAdapter(Context context) {
        this.context = context;
    }

    public void refresh(List<UnitPriceLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
        // Trocar a janela de datas reordena a lista inteira: manter os cards
        // abertos deixaria abertos os que calharem de cair naquelas posicoes.
        expanded.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.unit_price_listview, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ViewHolder holder = (ViewHolder) viewHolder;
        UnitPriceLine line = lines.get(i);

        holder.rank.setText(String.valueOf(i + 1));
        holder.description.setText(line.getCartItem().getDescription());
        holder.unitPrice.setText(context.getString(R.string.cart_unit_price_value,
                PriceUtil.format(line.getPricePerBase()), line.getBaseLabel()));
        holder.detail.setText(detail(line));

        // Card fechado traz so o nome; endereco e o resto ficam no detalhamento.
        holder.market.setText(StringUtil.or(line.getMarketName(), ""));
        holder.market.setVisibility(
                StringUtil.isEmpty(line.getMarketName()) ? View.GONE : View.VISIBLE);

        bindBreakdown(holder, line);
        configureActions(holder, line);
    }

    /**
     * Tamanho e preco da embalagem: e ele que o usuario ve na gondola, mas nao
     * e por ele que a lista esta ordenada, entao fica na linha de apoio e nao
     * no destaque. A data saiu daqui para o detalhamento.
     */
    private String detail(UnitPriceLine line) {
        List<String> parts = new ArrayList<>();

        String size = formatSize(line.getCartItem());
        if (StringUtil.isNotEmpty(size)) {
            parts.add(size);
        }

        parts.add(PriceUtil.format(line.getPrice()));

        return TextUtils.join(SEPARATOR, parts);
    }

    /**
     * O registro da oferta, so com o que a linha fechada nao cabe mostrar.
     *
     * E deliberadamente curto: a versao anterior listava tambem codigo de
     * barras, preco de tabela, desconto, cidade e a conta da divisao, e o card
     * aberto virou um paredao. Campo vazio nao vira linha em branco - a API
     * deixa de devolver distancia e ate o nome fantasia conforme o registro.
     */
    private void bindBreakdown(ViewHolder holder, UnitPriceLine line) {
        holder.breakdown.removeAllViews();

        Product source = line.getSource();
        Company company = source == null ? null : source.getEstabelecimento();

        if (source != null) {
            addRow(holder, R.string.cart_offer_product, source.getDesc());
        }

        addRow(holder, R.string.cart_offer_establishment, line.getMarketName());

        if (company != null) {
            addRow(holder, R.string.cart_offer_address, company.getFullAddress());
        }

        if (source != null) {
            addRow(holder, R.string.cart_offer_distance, distance(source));
        }

        addRow(holder, R.string.cart_offer_registered, registered(line));

        boolean isExpanded = expanded.contains(line.getKey());
        holder.breakdown.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.toggle.setText(isExpanded
                ? R.string.cart_hide_details
                : R.string.cart_show_details);
    }

    private String distance(Product source) {
        if (StringUtil.isEmpty(source.getDistkm())) {
            return null;
        }
        return context.getString(R.string.cart_offer_distance_value, source.getDistkm());
    }

    private String registered(UnitPriceLine line) {
        if (line.getDate() == null) {
            return null;
        }

        String moment = DateUtil.format.format(line.getDate());
        Product source = line.getSource();

        if (source == null || StringUtil.isEmpty(source.getTempo())) {
            return moment;
        }
        return context.getString(R.string.cart_offer_registered_value, moment, source.getTempo());
    }

    private void addRow(ViewHolder holder, int label, String value) {
        if (StringUtil.isEmpty(value)) {
            return;
        }

        View row = LayoutInflater.from(context)
                .inflate(R.layout.unit_price_detail_row, holder.breakdown, false);

        ((TextView) row.findViewById(R.id.detail_label)).setText(label);
        ((TextView) row.findViewById(R.id.detail_value)).setText(value);

        holder.breakdown.addView(row);
    }

    /**
     * A oferta e lembrada pela chave, nao pela posicao: trocar a janela de
     * datas reordena a lista inteira.
     */
    private void configureActions(ViewHolder holder, UnitPriceLine line) {
        holder.card.setOnClickListener(view -> {
            if (!expanded.remove(line.getKey())) {
                expanded.add(line.getKey());
            }
            notifyItemChanged(holder.getAdapterPosition());
        });
    }

    private String formatSize(CartItem cartItem) {
        if (StringUtil.isEmpty(cartItem.getSize())) {
            return "";
        }
        return cartItem.getSize() + " " + StringUtil.or(cartItem.getUnitMeasure(), "");
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final TextView rank;
        final TextView description;
        final TextView detail;
        final TextView market;
        final TextView unitPrice;
        final LinearLayout breakdown;
        final TextView toggle;

        public ViewHolder(View view) {
            super(view);
            card = (MaterialCardView) view;
            rank = view.findViewById(R.id.unit_price_rank);
            description = view.findViewById(R.id.unit_price_description);
            detail = view.findViewById(R.id.unit_price_detail);
            market = view.findViewById(R.id.unit_price_market);
            unitPrice = view.findViewById(R.id.unit_price_value);
            breakdown = view.findViewById(R.id.unit_price_breakdown);
            toggle = view.findViewById(R.id.unit_price_toggle);
        }
    }
}
