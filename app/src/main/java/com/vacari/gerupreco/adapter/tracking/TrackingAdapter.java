package com.vacari.gerupreco.adapter.tracking;

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
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.tracking.TrackingActivity;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.util.PriceUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista do rastreamento: grupos primeiro, produtos depois, com um cabecalho
 * anunciando cada bloco.
 *
 * Uma lista so, e nao duas abas como no comparador do carrinho: grupos e
 * produtos nao competem entre si, sao o mesmo cadastro em dois niveis, e o
 * produto e lido junto do grupo que dita o alvo dele.
 */
public class TrackingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_GROUP = 1;
    private static final int TYPE_PRODUCT = 2;

    private final TrackingActivity mActivity;
    private final List<Row> rows = new ArrayList<>();

    /** Grupo por id, para a linha do produto saber de onde vem o alvo dele. */
    private final Map<String, TrackingGroup> groupsById = new HashMap<>();

    private static class Row {
        int type;
        String header;
        TrackingGroup group;
        Tracking tracking;
        int members;
    }

    public TrackingAdapter(TrackingActivity mActivity) {
        this.mActivity = mActivity;
    }

    public void refresh(List<TrackingGroup> groups, List<Tracking> trackings) {
        rows.clear();
        groupsById.clear();

        for (TrackingGroup group : groups) {
            groupsById.put(group.getId(), group);
        }

        if (!groups.isEmpty()) {
            rows.add(header(mActivity.getString(R.string.tracking_groups_header)));
            for (TrackingGroup group : groups) {
                Row row = new Row();
                row.type = TYPE_GROUP;
                row.group = group;
                row.members = countMembers(trackings, group.getId());
                rows.add(row);
            }
        }

        if (!trackings.isEmpty()) {
            rows.add(header(mActivity.getString(R.string.tracking_products_header)));
            for (Tracking tracking : trackings) {
                Row row = new Row();
                row.type = TYPE_PRODUCT;
                row.tracking = tracking;
                rows.add(row);
            }
        }

        notifyDataSetChanged();
    }

    private Row header(String text) {
        Row row = new Row();
        row.type = TYPE_HEADER;
        row.header = text;
        return row;
    }

    private int countMembers(List<Tracking> trackings, String groupId) {
        int members = 0;
        for (Tracking tracking : trackings) {
            if (groupId != null && groupId.equals(tracking.getGroupId())) {
                members++;
            }
        }
        return members;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mActivity);

        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(
                    inflater.inflate(R.layout.tracking_header_listview, parent, false));
        }

        if (viewType == TYPE_GROUP) {
            return new GroupViewHolder(
                    inflater.inflate(R.layout.tracking_group_listview, parent, false));
        }

        return new ProductViewHolder(
                inflater.inflate(R.layout.tracking_listview, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        Row row = rows.get(position);

        if (row.type == TYPE_HEADER) {
            ((HeaderViewHolder) viewHolder).header.setText(row.header);
            return;
        }

        if (row.type == TYPE_GROUP) {
            bindGroup((GroupViewHolder) viewHolder, row);
            return;
        }

        bindProduct((ProductViewHolder) viewHolder, row.tracking);
    }

    private void bindGroup(GroupViewHolder holder, Row row) {
        TrackingGroup group = row.group;

        holder.name.setText(group.getName());
        holder.target.setText(formatTarget(group.getTargetPrice()));
        holder.scope.setText(scopeLabel(group.isForAllDevices()));
        holder.members.setText(mActivity.getResources().getQuantityString(
                R.plurals.tracking_group_members, row.members, row.members));
        holder.paused.setVisibility(group.isActive() ? View.GONE : View.VISIBLE);

        holder.card.setOnClickListener(v -> mActivity.editGroup(group));
    }

    private void bindProduct(ProductViewHolder holder, Tracking tracking) {
        TrackingGroup group = tracking.isInGroup()
                ? groupsById.get(tracking.getGroupId())
                : null;

        holder.description.setText(tracking.getDescription());

        // Dentro de um grupo quem dita alvo e alcance e o grupo. Um grupo que
        // sumiu (apagado noutro aparelho) cai no que o proprio produto guarda,
        // em vez de mostrar linha sem alvo.
        Double target = group != null ? group.getTargetPrice() : tracking.getTargetPrice();
        boolean forAll = group != null ? group.isForAllDevices() : tracking.isForAllDevices();

        holder.target.setText(formatTarget(target));
        holder.scope.setText(scopeLabel(forAll));

        if (group != null) {
            holder.group.setVisibility(View.VISIBLE);
            holder.group.setText(mActivity.getString(R.string.tracking_group_of, group.getName()));
        } else {
            holder.group.setVisibility(View.GONE);
        }

        boolean paused = !tracking.isActive() || (group != null && !group.isActive());
        holder.paused.setVisibility(paused ? View.VISIBLE : View.GONE);

        holder.tracking = tracking;
        holder.card.setOnClickListener(v -> mActivity.editTracking(tracking));
    }

    private String formatTarget(Double value) {
        if (value == null) {
            return "--";
        }
        return mActivity.getString(R.string.tracking_target_value,
                PriceUtil.format(BigDecimal.valueOf(value)));
    }

    /**
     * So ha dois rotulos possiveis porque so ha dois alcances que chegam nesta
     * lista: geral, que vale em todo aparelho, e particular, que so aparece no
     * aparelho que ele acerta - o resto e filtrado em TrackingScopes antes de
     * chegar aqui. Por isso o particular nao precisa dizer qual aparelho: e
     * sempre este.
     */
    private String scopeLabel(boolean forAll) {
        return mActivity.getString(forAll
                ? R.string.tracking_scope_all
                : R.string.tracking_scope_this_device);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        final TextView header;

        HeaderViewHolder(View view) {
            super(view);
            header = view.findViewById(R.id.tracking_header);
        }
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final TextView name;
        final TextView target;
        final TextView scope;
        final TextView members;
        final TextView paused;

        GroupViewHolder(View view) {
            super(view);
            card = view.findViewById(R.id.tracking_group_card);
            name = view.findViewById(R.id.tracking_group_name);
            target = view.findViewById(R.id.tracking_group_target);
            scope = view.findViewById(R.id.tracking_group_scope);
            members = view.findViewById(R.id.tracking_group_members);
            paused = view.findViewById(R.id.tracking_group_paused);
        }
    }

    class ProductViewHolder extends RecyclerView.ViewHolder
            implements View.OnCreateContextMenuListener {

        final MaterialCardView card;
        final TextView description;
        final TextView target;
        final TextView scope;
        final TextView group;
        final TextView paused;

        Tracking tracking;

        ProductViewHolder(View view) {
            super(view);
            view.setOnCreateContextMenuListener(this);

            card = view.findViewById(R.id.tracking_card);
            description = view.findViewById(R.id.tracking_description);
            target = view.findViewById(R.id.tracking_target);
            scope = view.findViewById(R.id.tracking_scope);
            group = view.findViewById(R.id.tracking_group);
            paused = view.findViewById(R.id.tracking_paused);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            MenuInflater inflater = new MenuInflater(v.getContext());
            inflater.inflate(R.menu.context_menu_tracking, menu);

            Tracking current = tracking;

            MenuItem pause = menu.findItem(R.id.action_tracking_pause);
            pause.setVisible(current.isActive());
            pause.setOnMenuItemClickListener(menuItem -> {
                mActivity.setTrackingActive(current, false);
                return true;
            });

            MenuItem resume = menu.findItem(R.id.action_tracking_resume);
            resume.setVisible(!current.isActive());
            resume.setOnMenuItemClickListener(menuItem -> {
                mActivity.setTrackingActive(current, true);
                return true;
            });

            // groupsById ja e a lista de grupos que este aparelho enxerga, entao
            // a acao some sozinha quando nao ha nenhum para escolher.
            MenuItem addToGroup = menu.findItem(R.id.action_tracking_add_to_group);
            addToGroup.setVisible(!groupsById.isEmpty() && !current.isInGroup());
            addToGroup.setOnMenuItemClickListener(menuItem -> {
                mActivity.addToGroup(current);
                return true;
            });

            MenuItem stop = menu.findItem(R.id.action_tracking_stop);
            stop.setOnMenuItemClickListener(menuItem -> {
                mActivity.confirmStop(current);
                return true;
            });
        }
    }
}
