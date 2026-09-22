package com.vacari.gerupreco.adapter.tracking;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.tracking.TrackingActivity;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.util.PriceUtil;
import com.vacari.gerupreco.util.TrackingPlan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lista do rastreamento: grupos primeiro, produtos sem grupo depois, com um
 * cabecalho anunciando cada bloco.
 *
 * Uma lista so, e nao duas abas como no comparador do carrinho: grupos e
 * produtos nao competem entre si, sao o mesmo cadastro em dois niveis, e o
 * produto e lido junto do grupo que dita o alvo dele.
 *
 * Produto que esta num grupo nao aparece no bloco de produtos: ele mora dentro
 * do grupo, e o toque no card do grupo abre e fecha a lista dele logo abaixo.
 * Repetir o produto nos dois lugares fazia o bloco de produtos crescer com
 * linhas cujo alvo nem e o delas.
 */
public class TrackingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_GROUP = 1;
    private static final int TYPE_PRODUCT = 2;

    private final TrackingActivity mActivity;
    private final List<Row> rows = new ArrayList<>();

    /** Grupo por id, para a linha do produto saber de onde vem o alvo dele. */
    private final Map<String, TrackingGroup> groupsById = new HashMap<>();

    /**
     * Grupos abertos, por id. Sobrevive ao refresh de proposito: toda edicao
     * recarrega a lista, e editar um produto de dentro do grupo fecharia o
     * grupo que o usuario acabou de abrir para chegar nele.
     */
    private final Set<String> expandedGroupIds = new HashSet<>();

    private List<TrackingGroup> groups = new ArrayList<>();
    private List<Tracking> trackings = new ArrayList<>();

    private static class Row {
        int type;
        String header;
        TrackingGroup group;
        Tracking tracking;
        int members;
        /** Produto listado dentro do grupo, e nao no bloco de produtos. */
        boolean nested;
    }

    public TrackingAdapter(TrackingActivity mActivity) {
        this.mActivity = mActivity;
    }

    public void refresh(List<TrackingGroup> groups, List<Tracking> trackings) {
        this.groups = groups;
        this.trackings = trackings;

        groupsById.clear();
        for (TrackingGroup group : groups) {
            groupsById.put(group.getId(), group);
        }

        rebuild();
    }

    private void rebuild() {
        rows.clear();

        Map<String, List<Tracking>> membersByGroup = new HashMap<>();
        List<Tracking> ungrouped = new ArrayList<>();

        // "Esta num grupo" e o que o TrackingPlan resolve, nao o groupId cru: o
        // produto cujo grupo sumiu do cadastro cai no proprio alvo, e se fosse
        // separado pelo groupId nao apareceria em lugar nenhum.
        for (Tracking tracking : trackings) {
            TrackingPlan plan = TrackingPlan.of(tracking, groupsById);
            if (plan.isInGroup()) {
                membersByGroup.computeIfAbsent(tracking.getGroupId(), id -> new ArrayList<>())
                        .add(tracking);
            } else {
                ungrouped.add(tracking);
            }
        }

        if (!groups.isEmpty()) {
            rows.add(header(mActivity.getString(R.string.tracking_groups_header)));
            for (TrackingGroup group : groups) {
                List<Tracking> members = membersByGroup.get(group.getId());
                if (members == null) {
                    members = new ArrayList<>();
                }

                Row row = new Row();
                row.type = TYPE_GROUP;
                row.group = group;
                row.members = members.size();
                rows.add(row);

                if (expandedGroupIds.contains(group.getId())) {
                    for (Tracking member : members) {
                        rows.add(product(member, true));
                    }
                }
            }
        }

        if (!ungrouped.isEmpty()) {
            rows.add(header(mActivity.getString(R.string.tracking_products_header)));
            for (Tracking tracking : ungrouped) {
                rows.add(product(tracking, false));
            }
        }

        notifyDataSetChanged();
    }

    private void toggle(TrackingGroup group) {
        if (!expandedGroupIds.remove(group.getId())) {
            expandedGroupIds.add(group.getId());
        }
        rebuild();
    }

    private Row header(String text) {
        Row row = new Row();
        row.type = TYPE_HEADER;
        row.header = text;
        return row;
    }

    private Row product(Tracking tracking, boolean nested) {
        Row row = new Row();
        row.type = TYPE_PRODUCT;
        row.tracking = tracking;
        row.nested = nested;
        return row;
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

        bindProduct((ProductViewHolder) viewHolder, row.tracking, row.nested);
    }

    private void bindGroup(GroupViewHolder holder, Row row) {
        TrackingGroup group = row.group;

        holder.name.setText(group.getName());
        holder.target.setText(formatTarget(group.getTargetPrice()));
        holder.scope.setText(scopeLabel(group.isForAllDevices()));
        holder.members.setText(mActivity.getResources().getQuantityString(
                R.plurals.tracking_group_members, row.members, row.members));
        holder.paused.setVisibility(group.isActive() ? View.GONE : View.VISIBLE);

        // Grupo vazio nao tem o que abrir. O chevron some para o toque nao
        // prometer uma lista que nao vem.
        boolean expandable = row.members > 0;
        holder.chevron.setVisibility(expandable ? View.VISIBLE : View.INVISIBLE);
        holder.chevron.setRotation(expandedGroupIds.contains(group.getId()) ? 90f : 0f);

        holder.group = group;
        holder.card.setOnClickListener(expandable ? v -> toggle(group) : null);
    }

    private void bindProduct(ProductViewHolder holder, Tracking tracking, boolean nested) {
        // Dentro do grupo a linha recua, para ler como parte dele. Por isso o
        // card do produto nao diz mais de que grupo e: so aparece agrupado
        // logo abaixo do card do grupo, que ja diz.
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) holder.card.getLayoutParams();
        params.setMarginStart(nested
                ? mActivity.getResources().getDimensionPixelSize(R.dimen.space_gutter)
                : 0);
        holder.card.setLayoutParams(params);

        // Quem resolve "grupo manda em alvo e alcance" e o TrackingPlan, o mesmo
        // que o dialogo de cadastro usa: enquanto cada tela resolvia por conta
        // propria, a lista mostrava o alvo do grupo e o dialogo o alvo proprio
        // obsoleto, sem nada dizendo qual valia.
        TrackingPlan plan = TrackingPlan.of(tracking, groupsById);

        holder.description.setText(tracking.getDescription());
        holder.target.setText(formatTarget(plan.getTargetPrice()));
        holder.scope.setText(scopeLabel(plan.isForAllDevices()));

        holder.paused.setVisibility(plan.isActive() ? View.GONE : View.VISIBLE);

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

    class GroupViewHolder extends RecyclerView.ViewHolder
            implements View.OnCreateContextMenuListener {

        final MaterialCardView card;
        final TextView name;
        final TextView target;
        final TextView scope;
        final TextView members;
        final TextView paused;
        final ImageView chevron;

        TrackingGroup group;

        GroupViewHolder(View view) {
            super(view);
            view.setOnCreateContextMenuListener(this);

            card = view.findViewById(R.id.tracking_group_card);
            name = view.findViewById(R.id.tracking_group_name);
            target = view.findViewById(R.id.tracking_group_target);
            scope = view.findViewById(R.id.tracking_group_scope);
            members = view.findViewById(R.id.tracking_group_members);
            paused = view.findViewById(R.id.tracking_group_paused);
            chevron = view.findViewById(R.id.tracking_group_chevron);
        }

        /**
         * Editar saiu do toque, que agora abre o grupo, e veio para o long
         * press - o mesmo gesto que ja guarda as acoes da linha de produto.
         */
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                        ContextMenu.ContextMenuInfo menuInfo) {
            MenuInflater inflater = new MenuInflater(v.getContext());
            inflater.inflate(R.menu.context_menu_tracking_group, menu);

            TrackingGroup current = group;

            menu.findItem(R.id.action_tracking_group_edit).setOnMenuItemClickListener(menuItem -> {
                mActivity.editGroup(current);
                return true;
            });
        }
    }

    class ProductViewHolder extends RecyclerView.ViewHolder
            implements View.OnCreateContextMenuListener {

        final MaterialCardView card;
        final TextView description;
        final TextView target;
        final TextView scope;
        final TextView paused;

        Tracking tracking;

        ProductViewHolder(View view) {
            super(view);
            view.setOnCreateContextMenuListener(this);

            card = view.findViewById(R.id.tracking_card);
            description = view.findViewById(R.id.tracking_description);
            target = view.findViewById(R.id.tracking_target);
            scope = view.findViewById(R.id.tracking_scope);
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
