package com.vacari.gerupreco.dialog.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.cart.MarketOption;
import com.vacari.gerupreco.model.cart.MarketSelection;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.MarketFilter;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Escolha do mercado que o comparador deve considerar.
 *
 * Sao dois campos encadeados: o segundo lista as lojas do mercado marcado no
 * primeiro, e troca junto com ele. A primeira opcao de endereco e "todos", que
 * e o unico jeito de comparar a rede inteira - as filiais tem codigos
 * diferentes e sem ela o filtro so caberia numa loja.
 *
 * "Limpar filtro" devolve null; e a mesma acao de nunca ter filtrado, e nao um
 * terceiro estado.
 */
public class MarketFilterDialog {

    private final AppCompatActivity mActivity;
    private final List<MarketOption> options;
    private final MarketSelection current;
    private final Callback<MarketSelection> onApply;

    private final List<String> names;
    private List<String> addresses = new ArrayList<>();

    /** Mercado cujas lojas estao no segundo campo agora. */
    private String boundName;

    private Spinner marketSpinner;
    private Spinner addressSpinner;

    public MarketFilterDialog(AppCompatActivity mActivity, List<MarketOption> options,
                              MarketSelection current, Callback<MarketSelection> onApply) {
        this.mActivity = mActivity;
        this.options = options;
        this.current = current;
        this.onApply = onApply;
        this.names = MarketFilter.names(options);
    }

    public void show() {
        View view = LayoutInflater.from(mActivity).inflate(R.layout.dialog_market_filter, null);

        marketSpinner = view.findViewById(R.id.filter_market);
        addressSpinner = view.findViewById(R.id.filter_address);

        marketSpinner.setAdapter(adapterFor(names));
        marketSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                // O Spinner reavisa a selecao que ja restauramos, e sem esta
                // guarda o reaviso jogaria o endereco de volta para "todos".
                String name = names.get(position);
                if (!name.equals(boundName)) {
                    bindAddresses(name, null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        restoreSelection();

        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.cart_filter)
                .setView(view)
                .setNeutralButton(R.string.cart_filter_clear, (dialog, which) -> onApply.callback(null))
                .setPositiveButton(R.string.cart_filter_apply, (dialog, which) -> apply())
                .show();
    }

    /**
     * Reabrir o dialogo com um filtro ativo mostra o que esta valendo: sem isso
     * ele voltaria no primeiro mercado da lista e o "Aplicar" trocaria o filtro
     * sem o usuario ter escolhido nada.
     */
    private void restoreSelection() {
        int index = current == null ? -1 : indexOfName(current.getName());

        if (index < 0) {
            bindAddresses(names.isEmpty() ? null : names.get(0), null);
            return;
        }

        marketSpinner.setSelection(index);
        bindAddresses(names.get(index), current.getAddress());
    }

    private void bindAddresses(String name, String selected) {
        boundName = name;
        addresses = new ArrayList<>();
        addresses.add(mActivity.getString(R.string.cart_filter_any_address));
        addresses.addAll(MarketFilter.addresses(options, name));

        addressSpinner.setAdapter(adapterFor(addresses));
        addressSpinner.setSelection(Math.max(0, indexOf(addresses, selected)));
    }

    private void apply() {
        if (names.isEmpty()) {
            onApply.callback(null);
            return;
        }

        String name = names.get(marketSpinner.getSelectedItemPosition());
        // Posicao 0 e "todos os enderecos", que nao e endereco nenhum.
        int addressPosition = addressSpinner.getSelectedItemPosition();
        String address = addressPosition <= 0 ? null : addresses.get(addressPosition);

        MarketSelection selection = new MarketSelection();
        selection.setName(name);
        selection.setAddress(address);
        selection.setCodes(MarketFilter.codesFor(options, name, address));

        onApply.callback(selection);
    }

    private ArrayAdapter<String> adapterFor(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivity,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private int indexOfName(String name) {
        return indexOf(names, name);
    }

    private int indexOf(List<String> values, String wanted) {
        if (StringUtil.isEmpty(wanted)) {
            return -1;
        }

        String normalized = StringUtil.normalize(wanted);
        for (int i = 0; i < values.size(); i++) {
            if (StringUtil.normalize(values.get(i)).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }
}
