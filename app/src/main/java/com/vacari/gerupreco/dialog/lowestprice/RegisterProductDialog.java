package com.vacari.gerupreco.dialog.lowestprice;

import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.lowestprice.LowestPriceProduct;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.repository.ItemRepository;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TagUtil;

import java.util.ArrayList;
import java.util.List;

public class RegisterProductDialog {

    private LowestPriceProduct context;
    private String barCode;
    private View dialog;
    private Item item;
    private boolean isNew;
    private List<String> selectedTags = new ArrayList<>();

    public RegisterProductDialog(LowestPriceProduct context, String barCode, Item item) {
        this.context = context;
        this.barCode = barCode;
        this.item = item;
        this.isNew = item == null;

        initGUI();
        setInfo();
    }

    private void initGUI() {
        dialog = context.getLayoutInflater().inflate(R.layout.register_dialog, null);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context,
                R.array.unit_measurement, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner editUnitMeasure = dialog.findViewById(R.id.edit_unitMeasure);
        editUnitMeasure.setAdapter(adapter);
        editUnitMeasure.setSelection(0);

        initTagInput();
    }

    private void initTagInput() {
        AutoCompleteTextView tagInput = dialog.findViewById(R.id.edit_tag_input);

        tagInput.setAdapter(new ArrayAdapter<>(context, R.layout.item_tag_suggestion,
                context.getKnownTags()));

        tagInput.setOnItemClickListener((parent, view, position, id) -> {
            addTag((String) parent.getItemAtPosition(position));
            tagInput.setText("");
            tagInput.dismissDropDown();
        });

        tagInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitTagInput(tagInput);
                return true;
            }
            return false;
        });

        dialog.findViewById(R.id.btn_add_tag).setOnClickListener(view -> commitTagInput(tagInput));
    }

    private void commitTagInput(AutoCompleteTextView tagInput) {
        addTag(tagInput.getText().toString());
        tagInput.setText("");
        tagInput.dismissDropDown();
    }

    /**
     * Reaproveita a grafia de uma tag ja existente quando o texto digitado so
     * difere em acento ou caixa, para nao criar "Bebida" e "bebida" separadas.
     */
    private void addTag(String rawTag) {
        String key = TagUtil.normalizeTag(rawTag);
        if (key.isEmpty()) {
            return;
        }

        for (String selected : selectedTags) {
            if (TagUtil.normalizeTag(selected).equals(key)) {
                return;
            }
        }

        String tagToAdd = rawTag.trim();
        for (String known : context.getKnownTags()) {
            if (TagUtil.normalizeTag(known).equals(key)) {
                tagToAdd = known;
                break;
            }
        }

        selectedTags.add(tagToAdd);
        renderTags();
    }

    private void renderTags() {
        ChipGroup tagGroup = dialog.findViewById(R.id.edit_tags);
        tagGroup.removeAllViews();
        tagGroup.setVisibility(selectedTags.isEmpty() ? View.GONE : View.VISIBLE);

        for (String tag : selectedTags) {
            Chip chip = TagUtil.createChip(context, tag, true);
            chip.setOnCloseIconClickListener(view -> {
                selectedTags.remove(tag);
                renderTags();
            });
            tagGroup.addView(chip);
        }
    }

    private void setInfo() {
        EditText editBarCode = dialog.findViewById(R.id.edit_barCode);
        EditText editDescription = dialog.findViewById(R.id.edit_description);
        EditText editSize = dialog.findViewById(R.id.edit_size);
        Spinner editUnitMeasure = dialog.findViewById(R.id.edit_unitMeasure);

        if(barCode != null) {
            editBarCode.setText(barCode);
        }

        if(!isNew) {
            editBarCode.setText(item.getBarCode());
            editDescription.setText(item.getDescription());
            editSize.setText(item.getSize());

            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context, R.array.unit_measurement, android.R.layout.simple_spinner_item);
            editUnitMeasure.setSelection(adapter.getPosition(item.getUnitMeasure()));

            selectedTags.addAll(TagUtil.distinctTags(item.getTags()));
        }

        renderTags();
    }

    public void show() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(isNew ? R.string.new_product : R.string.edit)
                .setCancelable(false)
                .setView(this.dialog)
                .setPositiveButton(R.string.save, (DialogInterface dialogInterface, int i) -> {
                    if (saveProduct(this.dialog)) {
                        dialogInterface.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, (DialogInterface dialogInterface, int i) -> {
                    dialogInterface.dismiss();
                }).create();

        // Sem isto o teclado cobre os botoes Salvar/Cancelar: a janela do dialogo
        // nao herda o windowSoftInputMode da Activity.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(context.getResources().getColor(R.color.btn_positive));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(context.getResources().getColor(R.color.btn_negative));
        });

        dialog.show();
    }

    private boolean saveProduct(View dialog) {
        EditText editBarCode = dialog.findViewById(R.id.edit_barCode);

        if(isNew && context.existProduct(editBarCode.getText().toString())) {
            return false;
        }

        EditText description = dialog.findViewById(R.id.edit_description);
        EditText size = dialog.findViewById(R.id.edit_size);
        Spinner unitMeasure = dialog.findViewById(R.id.edit_unitMeasure);
        AutoCompleteTextView tagInput = dialog.findViewById(R.id.edit_tag_input);

        // Tag digitada mas nao confirmada com o teclado nao pode ser perdida
        if (StringUtil.isNotEmpty(tagInput.getText().toString())) {
            addTag(tagInput.getText().toString());
        }

        Item item = new Item();
        item.setDescription(description.getText().toString());
        item.setBarCode(editBarCode.getText().toString());
        item.setSize(size.getText().toString());
        item.setUnitMeasure(unitMeasure.getSelectedItem().toString());
        item.setTags(new ArrayList<>(selectedTags));

        if(isNew) {
            ItemRepository.save(item, (Callback<Item>) data -> context.searchItems());
        } else {
            item.setId(this.item.getId());
            ItemRepository.update(item, (Callback<Item>) data -> context.searchItems());
        }
        return true;
    }
}
