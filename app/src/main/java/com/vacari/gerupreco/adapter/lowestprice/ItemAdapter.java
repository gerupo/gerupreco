package com.vacari.gerupreco.adapter.lowestprice;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.lowestprice.LowestPriceProduct;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TagUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter {

    private List<Item> itemList;
    private List<Item> allItemList;
    private LowestPriceProduct mActivity;

    public ItemAdapter(LowestPriceProduct mActivity) {
        this.itemList = new ArrayList<>();
        this.allItemList = new ArrayList<>();
        this.mActivity = mActivity;
    }

    public void refresh(List<Item> itemList) {
        this.itemList.clear();;
        this.itemList.addAll(itemList);
        this.allItemList.clear();;
        this.allItemList.addAll(itemList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mActivity)
                .inflate(R.layout.item_listview, viewGroup, false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        ViewHolder holder = (ViewHolder) viewHolder;

        Item item = itemList.get(i);

        configureActions(holder, item);

        holder.description.setText(item.getDescription());
        holder.size.setText(item.getSize());
        holder.unitMeasure.setText(item.getUnitMeasure());

        bindTags(holder, item);
    }

    private void bindTags(ViewHolder holder, Item item) {
        holder.tags.removeAllViews();

        List<String> tags = TagUtil.distinctTags(item.getTags());
        holder.tags.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);

        for (String tag : tags) {
            holder.tags.addView(TagUtil.createChip(mActivity, tag, false));
        }
    }

    /**
     * A busca ignora acentos e caixa, e considera tanto a descricao quanto as tags.
     */
    public void filter(String query) {
        String normalizedQuery = StringUtil.normalize(query);

        List<Item> filteredList = new ArrayList<>();
        if (normalizedQuery.isEmpty()) {
            filteredList.addAll(allItemList); // Se a consulta estiver vazia, mostra a lista completa
        } else {
            for (Item item : allItemList) {
                if (matches(item, normalizedQuery)) {
                    filteredList.add(item);
                }
            }
        }

        itemList.clear();
        itemList.addAll(filteredList);
        notifyDataSetChanged();
    }

    private boolean matches(Item item, String normalizedQuery) {
        if (StringUtil.normalize(item.getDescription()).contains(normalizedQuery)) {
            return true;
        }

        for (String tag : item.getTags()) {
            if (StringUtil.normalize(tag).contains(normalizedQuery)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Todas as tags ja usadas, para sugerir no cadastro.
     */
    public List<String> getAllTags() {
        List<String> tags = new ArrayList<>();
        for (Item item : allItemList) {
            tags.addAll(item.getTags());
        }
        return TagUtil.distinctTags(tags);
    }

    private void configureActions(ViewHolder holder, Item item) {
        holder.card.setOnClickListener(view -> {
            mActivity.openLowestPrice(item.getBarCode());
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public boolean existProduct(String barCode) {
        for(Item item : allItemList) {
            if(item.getBarCode().equals(barCode)) {
                return true;
            }
        }
        return false;
    }

    public Item getItemByPosition(int position) {
        return itemList.get(position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {

        final MaterialCardView card;
        final TextView description;
        final TextView size;
        final TextView unitMeasure;
        final ChipGroup tags;

        public ViewHolder(View view) {
            super(view);
            view.setOnCreateContextMenuListener(this);

            card = view.findViewById(R.id.item_card);
            description = view.findViewById(R.id.item_description);
            size = view.findViewById(R.id.item_size);
            unitMeasure = view.findViewById(R.id.item_unitMeasure);
            tags = view.findViewById(R.id.item_tags);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            MenuInflater inflater = new MenuInflater(v.getContext());
            inflater.inflate(R.menu.context_menu, menu);
            configureMenuActions(menu);
        }

        private void configureMenuActions(ContextMenu menu) {
            int position = getAdapterPosition();

            MenuItem delete = (MenuItem) menu.findItem(R.id.action_delete);
            delete.setOnMenuItemClickListener(menuItem -> {
                mActivity.deleteItem(position);
                return true;
            });

//            MenuItem alert = (MenuItem) menu.findItem(R.id.action_alert);
//            alert.setOnMenuItemClickListener(menuItem -> {
//                mActivity.createNotification(position);
//                return true;
//            });

            MenuItem edit = (MenuItem) menu.findItem(R.id.action_edit);
            edit.setOnMenuItemClickListener(menuItem -> {
                mActivity.editItem(position);
                return true;
            });
        }
    }
}
