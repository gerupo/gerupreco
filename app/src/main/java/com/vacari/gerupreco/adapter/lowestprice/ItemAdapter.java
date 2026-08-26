package com.vacari.gerupreco.adapter.lowestprice;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.lowestprice.LowestPriceProduct;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TagUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemAdapter extends RecyclerView.Adapter {

    private List<Item> itemList;
    private List<Item> allItemList;
    private LowestPriceProduct mActivity;

    /**
     * Codigos de barras que estao no carrinho. Fica guardado aqui em vez de ser
     * consultado a cada bind: o carrinho vive em SQLite e a lista rola.
     */
    private final Set<String> cartBarCodes = new HashSet<>();

    public ItemAdapter(LowestPriceProduct mActivity) {
        this.itemList = new ArrayList<>();
        this.allItemList = new ArrayList<>();
        this.mActivity = mActivity;
    }

    /**
     * Marca quais produtos ja estao no carrinho. Quem chama e a Activity,
     * sempre que o carrinho muda - inclusive na volta da tela do carrinho, que
     * pode ter esvaziado tudo.
     */
    public void setCartBarCodes(Set<String> barCodes) {
        cartBarCodes.clear();
        cartBarCodes.addAll(barCodes);
        notifyDataSetChanged();
    }

    public boolean isInCart(int position) {
        if (position < 0 || position >= itemList.size()) {
            return false;
        }
        return isInCart(itemList.get(position));
    }

    private boolean isInCart(Item item) {
        return StringUtil.isNotEmpty(item.getBarCode()) && cartBarCodes.contains(item.getBarCode());
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
        bindCartMark(holder, item);
    }

    /**
     * A marca de canto cobre o cabecalho, entao o chip de tamanho precisa
     * recuar enquanto ela estiver visivel. A borda do card muda junto: de longe
     * e ela que faz o produto no carrinho saltar na lista.
     */
    private void bindCartMark(ViewHolder holder, Item item) {
        boolean inCart = isInCart(item);

        holder.inCartMark.setVisibility(inCart ? View.VISIBLE : View.GONE);

        holder.card.setStrokeColor(ContextCompat.getColor(mActivity,
                inCart ? R.color.primary_container : R.color.outline_variant));

        int inset = inCart
                ? mActivity.getResources().getDimensionPixelSize(R.dimen.cart_mark_inset)
                : 0;
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) holder.header.getLayoutParams();
        if (params.getMarginEnd() != inset) {
            params.setMarginEnd(inset);
            holder.header.setLayoutParams(params);
        }
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
     * Catalogo inteiro, ignorando o filtro da busca.
     */
    public List<Item> getAllItems() {
        return new ArrayList<>(allItemList);
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
        final View header;
        final ImageView inCartMark;
        final TextView description;
        final TextView size;
        final TextView unitMeasure;
        final ChipGroup tags;

        public ViewHolder(View view) {
            super(view);
            view.setOnCreateContextMenuListener(this);

            card = view.findViewById(R.id.item_card);
            header = view.findViewById(R.id.item_header);
            inCartMark = view.findViewById(R.id.item_in_cart);
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
            boolean inCart = isInCart(position);

            // Adicionar continua valendo para produto ja no carrinho: soma uma
            // unidade, como o arrasto para a direita. Remover so aparece quando
            // ha o que remover.
            MenuItem addToCart = menu.findItem(R.id.action_add_to_cart);
            addToCart.setOnMenuItemClickListener(menuItem -> {
                mActivity.addToCart(position);
                return true;
            });

            MenuItem removeFromCart = menu.findItem(R.id.action_remove_from_cart);
            removeFromCart.setVisible(inCart);
            removeFromCart.setOnMenuItemClickListener(menuItem -> {
                mActivity.removeFromCart(position);
                return true;
            });

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
