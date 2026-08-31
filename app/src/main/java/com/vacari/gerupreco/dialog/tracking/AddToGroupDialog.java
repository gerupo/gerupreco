package com.vacari.gerupreco.dialog.tracking;

import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Poe um produto num grupo escrevendo o nome dele.
 *
 * E o mesmo arranjo do campo de tags do cadastro de produto, e pelo mesmo
 * motivo: grupo aqui e um nome, nao um cadastro que se abre antes de usar.
 * Escrever um nome que ainda nao existe cria o grupo; escrever um que existe
 * reaproveita, com a grafia ja em uso.
 *
 * Foi o que substituiu o botao de "novo grupo" no rodape da tela. Aquele botao
 * pedia que o usuario criasse um grupo vazio primeiro e so depois lembrasse de
 * voltar nos produtos para popula-lo - e grupo sem produto nao faz nada.
 */
public class AddToGroupDialog {

    private final AppCompatActivity context;
    private final List<TrackingGroup> groups;
    private final Callback<String> onChosen;

    public AddToGroupDialog(AppCompatActivity context, List<TrackingGroup> groups,
                            Callback<String> onChosen) {
        this.context = context;
        this.groups = groups;
        this.onChosen = onChosen;
    }

    public void show() {
        View view = context.getLayoutInflater().inflate(R.layout.dialog_add_to_group, null);

        AutoCompleteTextView input = view.findViewById(R.id.add_to_group_name);
        input.setAdapter(new ArrayAdapter<>(context, R.layout.item_tag_suggestion, names()));

        // Sem isto as sugestoes so aparecem depois de digitar, e um grupo que
        // ja existe fica invisivel para quem nao lembra o nome exato. Tocar no
        // campo abre a lista inteira; digitar filtra.
        //
        // Sao dois gatilhos porque o primeiro toque num campo sem foco so pede
        // o foco - o clique nao chega, e a lista nao abria justamente na
        // primeira vez, que e quando o usuario mais precisa ver o que existe.
        // O post e necessario: no instante do foco a janela do popup ainda nao
        // tem onde se ancorar.
        input.setOnClickListener(v -> showSuggestions(input));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.post(() -> showSuggestions(input));
            }
        });

        TextView hint = view.findViewById(R.id.add_to_group_hint);
        hint.setText(groups.isEmpty()
                ? R.string.tracking_group_first_hint
                : R.string.tracking_group_new_hint);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tracking_add_to_group)
                .setView(view)
                .setPositiveButton(R.string.save, (d, which) -> confirm(input))
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Dialogo tem janela propria e nao herda o windowSoftInputMode da
        // Activity; sem isto o teclado cobre Salvar e Cancelar.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.show();
    }

    private void showSuggestions(AutoCompleteTextView input) {
        if (!groups.isEmpty() && input.isAttachedToWindow()) {
            input.showDropDown();
        }
    }

    private void confirm(AutoCompleteTextView input) {
        String name = input.getText().toString().trim();

        if (StringUtil.isEmpty(name)) {
            return;
        }

        onChosen.callback(name);
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (TrackingGroup group : groups) {
            if (StringUtil.isNotEmpty(group.getName())) {
                names.add(group.getName());
            }
        }
        return names;
    }
}
