package com.vacari.gerupreco.adapter.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.cart.CartActivity;
import com.vacari.gerupreco.model.sqlite.CartItem;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter {

    private final List<CartItem> cartItems = new ArrayList<>();
    private final CartActivity mActivity;

    public CartAdapter(CartActivity mActivity) {
        this.mActivity = mActivity;
    }

    public void refresh(List<CartItem> items) {
        cartItems.clear();
        cartItems.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mActivity).inflate(R.layout.cart_listview, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ViewHolder holder = (ViewHolder) viewHolder;
        CartItem cartItem = cartItems.get(i);

        holder.description.setText(cartItem.getDescription());
        holder.quantity.setText(String.valueOf(cartItem.getQuantity()));
        holder.size.setText(formatSize(cartItem));

        holder.increase.setOnClickListener(view ->
                mActivity.changeQuantity(cartItem, cartItem.getQuantity() + 1));
        holder.decrease.setOnClickListener(view ->
                mActivity.changeQuantity(cartItem, cartItem.getQuantity() - 1));
        holder.remove.setOnClickListener(view -> mActivity.removeItem(cartItem));
    }

    private String formatSize(CartItem cartItem) {
        if (StringUtil.isEmpty(cartItem.getSize())) {
            return "";
        }
        return cartItem.getSize() + " " + StringUtil.or(cartItem.getUnitMeasure(), "");
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView description;
        final TextView size;
        final TextView quantity;
        final ImageButton increase;
        final ImageButton decrease;
        final ImageButton remove;

        public ViewHolder(View view) {
            super(view);
            description = view.findViewById(R.id.cart_description);
            size = view.findViewById(R.id.cart_size);
            quantity = view.findViewById(R.id.cart_quantity);
            increase = view.findViewById(R.id.cart_increase);
            decrease = view.findViewById(R.id.cart_decrease);
            remove = view.findViewById(R.id.cart_remove);
        }
    }
}
