package com.vacari.gerupreco.adapter.cart;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.cart.UnitPriceLine;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.util.DateUtil;
import com.vacari.gerupreco.util.PriceUtil;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class UnitPriceAdapter extends RecyclerView.Adapter {

    private static final String SEPARATOR = "  ·  ";

    private final List<UnitPriceLine> lines = new ArrayList<>();
    private final Context context;

    public UnitPriceAdapter(Context context) {
        this.context = context;
    }

    public void refresh(List<UnitPriceLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
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
    }

    /**
     * O preco da embalagem fica na linha de apoio, e nao no destaque: e ele que
     * o usuario ve na gondola, mas nao e por ele que a lista esta ordenada.
     */
    private String detail(UnitPriceLine line) {
        List<String> parts = new ArrayList<>();

        String size = formatSize(line.getCartItem());
        if (StringUtil.isNotEmpty(size)) {
            parts.add(size);
        }

        String price = PriceUtil.format(line.getBestPrice());
        parts.add(StringUtil.isEmpty(line.getMarketName())
                ? price
                : context.getString(R.string.cart_unit_price_at, price, line.getMarketName()));

        if (line.getDate() != null) {
            parts.add(DateUtil.dayFormat.format(line.getDate()));
        }

        return TextUtils.join(SEPARATOR, parts);
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

        final TextView rank;
        final TextView description;
        final TextView detail;
        final TextView unitPrice;

        public ViewHolder(View view) {
            super(view);
            rank = view.findViewById(R.id.unit_price_rank);
            description = view.findViewById(R.id.unit_price_description);
            detail = view.findViewById(R.id.unit_price_detail);
            unitPrice = view.findViewById(R.id.unit_price_value);
        }
    }
}
