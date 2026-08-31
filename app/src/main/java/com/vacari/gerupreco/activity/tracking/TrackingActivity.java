package com.vacari.gerupreco.activity.tracking;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.adapter.tracking.TrackingAdapter;
import com.vacari.gerupreco.dialog.tracking.AddToGroupDialog;
import com.vacari.gerupreco.dialog.tracking.TrackProductDialog;
import com.vacari.gerupreco.dialog.tracking.TrackingGroupDialog;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.repository.TrackingGroupRepository;
import com.vacari.gerupreco.repository.TrackingRepository;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TrackingScopes;

import java.util.ArrayList;
import java.util.List;

/**
 * Rastreamento de precos: o que esta sendo vigiado, com que alvo e para quem o
 * alerta vai.
 *
 * Quem consulta preco e compara com o alvo e a rotina agendada no servidor -
 * esta tela e so o cadastro que ela le. Por isso nao ha consulta a Nota Parana
 * aqui, e o que se ve e sempre o que o servidor vai usar na proxima rodada.
 */
public class TrackingActivity extends AppCompatActivity {

    private TrackingAdapter mAdapter;

    private List<TrackingGroup> groups = new ArrayList<>();
    private List<Tracking> trackings = new ArrayList<>();

    /**
     * Segura o aviso de vazio ate a consulta voltar: antes disso a lista esta
     * vazia por falta de dado, e anunciar "nenhum produto rastreado" mandaria
     * cadastrar o que talvez ja exista.
     */
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);
        setTitle(R.string.tracking_title);

        initGUI();
        load();
    }

    private void initGUI() {
        RecyclerView recyclerView = findViewById(R.id.tracking_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mAdapter = new TrackingAdapter(this);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    /**
     * As duas consultas sao encadeadas de proposito: os grupos precisam vir
     * antes dos produtos, que leem o alvo deles.
     *
     * A consulta de aparelhos que vinha na frente saiu junto com o seletor de
     * aparelho: a lista nao escreve mais nome nenhum, so "este" ou "outro", e
     * isso o proprio ANDROID_ID responde sem rede.
     */
    private void load() {
        ProgressDialog loading = ProgressDialog.show(this, getString(R.string.app_name),
                getString(R.string.search), true);

        TrackingGroupRepository.searchAll(loadedGroups ->
                TrackingRepository.searchAll(loadedTrackings -> runOnUiThread(() -> {
                    // A lista inteira e recortada para o que este aparelho
                    // pode ver e mexer. A lista completa segue indo para
                    // visibleTrackings, senao um produto cujo grupo esta oculto
                    // nao teria como ser distinguido de um produto orfao.
                    groups = TrackingScopes.visibleGroups(this, loadedGroups);
                    trackings = TrackingScopes.visibleTrackings(this, loadedTrackings, loadedGroups);
                    loaded = true;

                    if (loading.isShowing()) {
                        loading.dismiss();
                    }
                    render();
                })));
    }

    private void render() {
        mAdapter.refresh(groups, trackings);

        boolean empty = groups.isEmpty() && trackings.isEmpty();
        findViewById(R.id.tracking_empty).setVisibility(loaded && empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.tracking_recycler).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    public void editTracking(Tracking tracking) {
        new TrackProductDialog(this, tracking, groups, saved -> runOnUiThread(this::load)).show();
    }

    public void editGroup(TrackingGroup group) {
        openGroupDialog(group);
    }

    private void openGroupDialog(TrackingGroup group) {
        new TrackingGroupDialog(this, group,
                saved -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.tracking_group_saved, Toast.LENGTH_SHORT).show();
                    load();
                }),
                deleted -> deleteGroup(deleted)).show();
    }

    /**
     * Solta os produtos antes de apagar o grupo. Na ordem inversa, uma falha na
     * segunda escrita deixaria produtos apontando para um grupo que nao existe
     * mais - e eles perderiam o alvo sem aviso.
     */
    private void deleteGroup(TrackingGroup group) {
        TrackingRepository.releaseFromGroup(trackings, group.getId(), group.getTargetPrice(),
                ignored -> TrackingGroupRepository.delete(group.getId(),
                        removed -> runOnUiThread(this::load)));
    }

    public void setTrackingActive(Tracking tracking, boolean active) {
        tracking.setActive(active);

        // Retomar rearma o aviso: enquanto pausado o preco pode ter subido e
        // caido de novo, e o ultimo preco avisado ficou falando de outra queda.
        if (active) {
            tracking.setLastNotifiedPrice(null);
        }

        TrackingRepository.update(tracking, saved -> runOnUiThread(this::load));
    }

    /**
     * Poe o produto num grupo pelo nome, criando o grupo se ele ainda nao
     * existir. E o unico caminho para criar grupo desde que o botao do rodape
     * saiu: grupo nasce junto com o primeiro produto que entra nele, porque
     * grupo sem produto nao faz nada.
     */
    public void addToGroup(Tracking tracking) {
        new AddToGroupDialog(this, groups, name -> assignToGroup(tracking, name)).show();
    }

    /**
     * Nome que so difere em acento ou caixa e o mesmo grupo, e a grafia que
     * vale e a ja cadastrada - a mesma regra das tags. Sem isso "Cervejas" e
     * "cervejas" viveriam como dois grupos, cada um com o proprio alvo, e a
     * lista nao denunciaria a duplicata.
     */
    private void assignToGroup(Tracking tracking, String name) {
        TrackingGroup existing = groupByName(name);

        if (existing != null) {
            moveToGroup(tracking, existing);
            return;
        }

        // O grupo novo nasce com o alvo e o alcance do produto que o criou.
        // Nascer sem alvo o deixaria mudo: dentro do grupo quem manda e o alvo
        // dele, e o produto sairia da rodada do servidor sem nada na tela
        // explicando por que parou de avisar.
        TrackingGroup group = new TrackingGroup();
        group.setName(name);
        group.setTargetPrice(tracking.getTargetPrice());
        group.setScope(tracking.getScope());
        group.setDeviceId(tracking.getDeviceId());
        group.setActive(true);

        TrackingGroupRepository.save(group, saved -> runOnUiThread(() -> {
            if (saved == null) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
                return;
            }
            moveToGroup(tracking, saved);
        }));
    }

    private TrackingGroup groupByName(String name) {
        String key = StringUtil.normalize(name);

        for (TrackingGroup group : groups) {
            if (StringUtil.normalize(group.getName()).equals(key)) {
                return group;
            }
        }

        return null;
    }

    /**
     * O alvo e o alcance proprios ficam onde estao, sem serem apagados: e o que
     * o dialogo de edicao ja faz, e e o que permite ao produto voltar com alvo
     * proprio se o grupo for removido depois.
     */
    private void moveToGroup(Tracking tracking, TrackingGroup group) {
        tracking.setGroupId(group.getId());

        // Rearma o aviso, pelo mesmo motivo de mudar o alvo na mao: dentro do
        // grupo quem manda e o alvo dele, e o ultimo preco avisado valia para o
        // alvo anterior.
        tracking.setLastNotifiedPrice(null);
        tracking.setLastNotifiedAt(null);

        TrackingRepository.update(tracking, saved -> runOnUiThread(() -> {
            Toast.makeText(this,
                    getString(R.string.tracking_added_to_group,
                            tracking.getDescription(), group.getName()),
                    Toast.LENGTH_SHORT).show();
            load();
        }));
    }

    public void confirmStop(Tracking tracking) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tracking_stop)
                .setMessage(R.string.tracking_stop_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        TrackingRepository.delete(tracking.getId(), removed -> runOnUiThread(() -> {
                            Toast.makeText(this,
                                    getString(R.string.tracking_removed, tracking.getDescription()),
                                    Toast.LENGTH_SHORT).show();
                            load();
                        })))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
