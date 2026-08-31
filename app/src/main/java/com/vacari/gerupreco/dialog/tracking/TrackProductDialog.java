package com.vacari.gerupreco.dialog.tracking;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.repository.TrackingGroupRepository;
import com.vacari.gerupreco.repository.TrackingRepository;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.PriceUtil;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TrackingScopes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro do rastreamento de um produto: alvo, grupo e alcance do alerta.
 *
 * Serve tanto para o long press na lista de produtos quanto para a edicao pela
 * tela de rastreamento - as duas preenchem exatamente os mesmos campos, e
 * separar em dois dialogos duplicaria a validacao.
 */
public class TrackProductDialog {

    private final AppCompatActivity context;
    private final Tracking tracking;
    private final boolean isNew;
    private final List<TrackingGroup> groups;
    private final Callback<Tracking> onSaved;

    private View view;
    private List<TrackingScopes.Option> scopeOptions;

    public TrackProductDialog(AppCompatActivity context, Tracking tracking,
                              List<TrackingGroup> groups, Callback<Tracking> onSaved) {
        this.context = context;
        this.tracking = tracking;
        this.isNew = StringUtil.isEmpty(tracking.getId());
        this.groups = groups != null ? groups : new ArrayList<>();
        this.onSaved = onSaved;
    }

    /**
     * A descricao e o codigo de barras sao copiados do catalogo, e nao
     * referenciados: a tela de rastreamento monta sem uma segunda consulta, e
     * excluir o produto do catalogo nao deixa linha orfa aqui.
     */
    public static Tracking newFor(Item item) {
        Tracking tracking = new Tracking();
        tracking.setBarCode(item.getBarCode());
        tracking.setDescription(item.getDescription());
        tracking.setScope(Tracking.SCOPE_ALL);
        tracking.setActive(true);
        return tracking;
    }

    public void show() {
        view = context.getLayoutInflater().inflate(R.layout.dialog_track_product, null);

        TextView description = view.findViewById(R.id.track_description);
        description.setText(tracking.getDescription());

        bindGroups();
        bindScopes();
        bindTarget();

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(isNew ? R.string.tracking_track : R.string.tracking_edit)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                .create();

        // A janela do dialogo nao herda o windowSoftInputMode da Activity, e sem
        // isto o teclado cobre os botoes Salvar/Cancelar.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // O listener e trocado depois do show para o dialogo NAO fechar quando a
        // validacao falha: passado direto ao builder, o AlertDialog fecha assim
        // que o botao e tocado, e a mensagem de erro apareceria sobre a tela ja
        // sem os campos para corrigir.
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    if (save()) {
                        dialog.dismiss();
                    }
                }));

        dialog.show();
    }

    /**
     * O grupo e escrito, nao escolhido numa lista: com o Spinner que havia aqui
     * so dava para entrar em grupo ja existente, e criar um exigia sair do
     * cadastro. Vazio significa sem grupo.
     */
    private void bindGroups() {
        AutoCompleteTextView input = view.findViewById(R.id.track_group);
        input.setAdapter(new ArrayAdapter<>(context, R.layout.item_tag_suggestion, groupNames()));
        input.setText(currentGroupName());

        // Dois gatilhos pelo mesmo motivo do AddToGroupDialog: o primeiro toque
        // num campo sem foco so pede foco, e a lista nao abriria na primeira vez.
        input.setOnClickListener(v -> showSuggestions(input));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> showSuggestions(input));
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyGroupState();
            }
        });

        applyGroupState();
    }

    private void showSuggestions(AutoCompleteTextView input) {
        if (!groups.isEmpty() && input.isAttachedToWindow()) {
            input.showDropDown();
        }
    }

    private List<String> groupNames() {
        List<String> names = new ArrayList<>();
        for (TrackingGroup group : groups) {
            if (StringUtil.isNotEmpty(group.getName())) {
                names.add(group.getName());
            }
        }
        return names;
    }

    private String currentGroupName() {
        if (!tracking.isInGroup()) {
            return "";
        }

        for (TrackingGroup group : groups) {
            if (tracking.getGroupId().equals(group.getId())) {
                return StringUtil.or(group.getName(), "");
            }
        }

        // Grupo que sumiu do cadastro: o campo volta vazio e o produto reassume
        // o alvo proprio, em vez de mostrar o nome de algo que nao existe mais.
        return "";
    }

    /**
     * Nome que so difere em acento ou caixa e o mesmo grupo, e a grafia que vale
     * e a ja cadastrada - a mesma regra das tags.
     */
    private TrackingGroup groupByName(String name) {
        String key = StringUtil.normalize(name);

        if (key.isEmpty()) {
            return null;
        }

        for (TrackingGroup group : groups) {
            if (StringUtil.normalize(group.getName()).equals(key)) {
                return group;
            }
        }

        return null;
    }

    private String typedGroupName() {
        AutoCompleteTextView input = view.findViewById(R.id.track_group);
        return input.getText().toString().trim();
    }

    /**
     * Sao tres estados, e o que muda entre eles e de quem sao o alvo e o
     * alcance:
     *
     * - campo vazio: do proprio produto;
     * - nome de grupo que ja existe: do grupo, e por isso os campos ficam
     *   desligados - em vez de escondidos, porque sumir da tela faria parecer
     *   que o rastreamento perdeu o alvo, quando ele so passou a vir de outro
     *   lugar;
     * - nome novo: os campos seguem ligados, e o que for digitado neles vira o
     *   alvo e o alcance do grupo que sera criado. Nao ha outro lugar de onde
     *   tirar isso, e um grupo sem alvo deixaria o produto mudo.
     */
    private void applyGroupState() {
        String name = typedGroupName();
        TrackingGroup existing = groupByName(name);
        boolean ownsTarget = existing == null;

        view.findViewById(R.id.track_target).setEnabled(ownsTarget);
        view.findViewById(R.id.track_scope).setEnabled(ownsTarget);

        TextView hint = view.findViewById(R.id.track_group_hint);

        if (name.isEmpty()) {
            hint.setVisibility(View.GONE);
            return;
        }

        hint.setVisibility(View.VISIBLE);
        hint.setText(existing != null
                ? R.string.tracking_group_owns_target
                : R.string.tracking_group_will_be_created);
    }

    private void bindScopes() {
        scopeOptions = TrackingScopes.options(context);

        ArrayAdapter<TrackingScopes.Option> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, scopeOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = view.findViewById(R.id.track_scope);
        spinner.setAdapter(adapter);
        spinner.setSelection(TrackingScopes.indexOf(scopeOptions,
                tracking.getScope(), tracking.getDeviceId()));
    }

    private void bindTarget() {
        if (tracking.getTargetPrice() == null) {
            return;
        }

        EditText target = view.findViewById(R.id.track_target);
        target.setText(BigDecimal.valueOf(tracking.getTargetPrice())
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private boolean save() {
        String groupName = typedGroupName();
        TrackingGroup existing = groupByName(groupName);

        // Grupo que ja existe: o alvo e o alcance sao dele, e os campos deste
        // dialogo nem foram habilitados.
        if (existing != null) {
            tracking.setGroupId(existing.getId());
            rearm();
            persist();
            return true;
        }

        EditText targetField = view.findViewById(R.id.track_target);
        BigDecimal target = PriceUtil.parse(targetField.getText().toString());
        if (target == null || target.signum() <= 0) {
            Toast.makeText(context, R.string.tracking_target_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        Spinner scopeSpinner = view.findViewById(R.id.track_scope);
        TrackingScopes.Option option = scopeOptions.get(scopeSpinner.getSelectedItemPosition());

        // O produto guarda alvo e alcance mesmo quando vai entrar num grupo
        // novo: e o que permite a ele voltar com alvo proprio se o grupo for
        // removido depois, em vez de ficar sem nenhum.
        tracking.setTargetPrice(target.doubleValue());
        tracking.setScope(option.getScope());
        tracking.setDeviceId(option.getDeviceId());
        rearm();

        if (groupName.isEmpty()) {
            tracking.setGroupId(null);
            persist();
            return true;
        }

        createGroupThenPersist(groupName, target.doubleValue(), option);
        return true;
    }

    /**
     * Grupo novo nasce com o alvo e o alcance digitados aqui, e so depois o
     * produto e gravado apontando para ele. Na ordem inversa o produto ficaria
     * com um groupId de algo que talvez nao chegue a existir.
     *
     * Se a criacao falhar, o rastreamento e gravado sem grupo em vez de se
     * perder: ele tem alvo proprio e continua avisando, e o aviso diz o que
     * ficou faltando.
     */
    private void createGroupThenPersist(String groupName, double target,
                                        TrackingScopes.Option option) {
        TrackingGroup group = new TrackingGroup();
        group.setName(groupName);
        group.setTargetPrice(target);
        group.setScope(option.getScope());
        group.setDeviceId(option.getDeviceId());
        group.setActive(true);

        TrackingGroupRepository.save(group, saved -> {
            if (saved == null) {
                Toast.makeText(context, R.string.tracking_group_failed, Toast.LENGTH_LONG).show();
                tracking.setGroupId(null);
            } else {
                tracking.setGroupId(saved.getId());
            }
            persist();
        });
    }

    /**
     * Alvo alterado rearma o aviso: o preco ja notificado valia para o alvo
     * anterior, e sem limpar isso um alvo novo e mais alto ficaria calado ate o
     * preco cair abaixo do que ja tinha sido avisado.
     */
    private void rearm() {
        tracking.setLastNotifiedPrice(null);
        tracking.setLastNotifiedAt(null);
    }

    private void persist() {
        if (isNew) {
            TrackingRepository.save(tracking, saved -> onSaved.callback(saved));
        } else {
            TrackingRepository.update(tracking, saved -> onSaved.callback(saved));
        }
    }
}
