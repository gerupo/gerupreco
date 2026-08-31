package com.vacari.gerupreco.dialog.tracking;

import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.repository.TrackingGroupRepository;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.PriceUtil;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TrackingScopes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Edicao de um grupo de rastreamento. O alvo e o alcance ficam aqui e valem
 * para todo produto do grupo: basta um deles atingir o alvo para o grupo
 * notificar.
 *
 * So edita, nunca cria: grupo nasce pelo AddToGroupDialog, junto com o primeiro
 * produto que entra nele. Por isso o botao de remover esta sempre presente e a
 * gravacao e sempre update - nao existe grupo sem id chegando aqui.
 */
public class TrackingGroupDialog {

    private final AppCompatActivity context;
    private final TrackingGroup group;
    private final Callback<TrackingGroup> onSaved;
    private final Callback<TrackingGroup> onDeleted;

    private View view;
    private List<TrackingScopes.Option> scopeOptions;

    public TrackingGroupDialog(AppCompatActivity context, TrackingGroup group,
                               Callback<TrackingGroup> onSaved, Callback<TrackingGroup> onDeleted) {
        this.context = context;
        this.group = group;
        this.onSaved = onSaved;
        this.onDeleted = onDeleted;
    }

    public void show() {
        view = context.getLayoutInflater().inflate(R.layout.dialog_tracking_group, null);

        EditText name = view.findViewById(R.id.group_name);
        name.setText(StringUtil.or(group.getName(), ""));

        if (group.getTargetPrice() != null) {
            EditText target = view.findViewById(R.id.group_target);
            target.setText(BigDecimal.valueOf(group.getTargetPrice())
                    .setScale(2, RoundingMode.HALF_UP).toPlainString());
        }

        bindScopes();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tracking_edit_group)
                .setCancelable(false)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());

        builder.setNeutralButton(R.string.tracking_group_delete, null);

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // Listeners trocados depois do show para o dialogo nao fechar sozinho:
        // salvar precisa poder recusar, e remover precisa de confirmacao antes.
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                if (save()) {
                    dialog.dismiss();
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button ->
                    confirmDelete(dialog));
        });

        dialog.show();
    }

    private void bindScopes() {
        scopeOptions = TrackingScopes.options(context);

        Spinner spinner = view.findViewById(R.id.group_scope);
        ArrayAdapter<TrackingScopes.Option> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, scopeOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(TrackingScopes.indexOf(scopeOptions,
                group.getScope(), group.getDeviceId()));
    }

    private boolean save() {
        EditText nameField = view.findViewById(R.id.group_name);
        String name = nameField.getText().toString().trim();
        if (StringUtil.isEmpty(name)) {
            Toast.makeText(context, R.string.tracking_group_name_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        EditText targetField = view.findViewById(R.id.group_target);
        BigDecimal target = PriceUtil.parse(targetField.getText().toString());
        if (target == null || target.signum() <= 0) {
            Toast.makeText(context, R.string.tracking_target_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        Spinner scopeSpinner = view.findViewById(R.id.group_scope);
        TrackingScopes.Option option = scopeOptions.get(scopeSpinner.getSelectedItemPosition());

        group.setName(name);
        group.setTargetPrice(target.doubleValue());
        group.setScope(option.getScope());
        group.setDeviceId(option.getDeviceId());

        TrackingGroupRepository.update(group, saved -> onSaved.callback(saved));

        return true;
    }

    private void confirmDelete(AlertDialog parent) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tracking_group_delete)
                .setMessage(R.string.tracking_group_delete_confirm)
                .setPositiveButton(R.string.delete, (dialogInterface, i) -> {
                    parent.dismiss();
                    onDeleted.callback(group);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
