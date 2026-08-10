package com.vacari.gerupreco.util;

import android.content.Context;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.vacari.gerupreco.R;

/**
 * Janela de validade dos precos, compartilhada pelas telas do carrinho.
 *
 * O recorte por data e sempre local (ver CartCompare), entao trocar o chip so
 * refaz o calculo em memoria - sem consulta nova. A escolha e guardada e vale
 * para as duas telas: alternar entre elas com janelas diferentes daria a
 * impressao de resultado inconsistente.
 */
public class PriceWindow {

    private static final String PREFS = "cart_compare";
    private static final String PREF_WINDOW = "window_days";
    private static final int DEFAULT_WINDOW = 7;

    private static final int[] DAYS = {1, 2, 3, 7, 15, 30, CartCompare.ANY_AGE};

    private PriceWindow() {
    }

    public static int load(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_WINDOW, DEFAULT_WINDOW);
    }

    public static void save(Context context, int days) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PREF_WINDOW, days).apply();
    }

    public static void buildChips(Context context, ChipGroup group, int selected,
                                  Callback<Integer> onChange) {
        for (int days : DAYS) {
            Chip chip = new Chip(context);
            chip.setId(View.generateViewId());
            chip.setText(label(context, days));
            chip.setTag(days);
            chip.setCheckable(true);
            chip.setChecked(days == selected);
            chip.setTextAppearanceResource(R.style.TextAppearance_GeruPreco_LabelCaps);
            // Cor por estado, senao o chip marcado fica igual aos outros.
            chip.setChipBackgroundColor(
                    ContextCompat.getColorStateList(context, R.color.chip_window_background));
            chip.setTextColor(
                    ContextCompat.getColorStateList(context, R.color.chip_window_text));
            chip.setChipStrokeWidth(0f);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(false);

            chip.setOnClickListener(view -> {
                int chosen = (int) view.getTag();
                save(context, chosen);
                onChange.callback(chosen);
            });

            group.addView(chip);
        }
    }

    private static String label(Context context, int days) {
        if (days == CartCompare.ANY_AGE) {
            return context.getString(R.string.cart_window_any);
        }
        return context.getResources().getQuantityString(R.plurals.cart_window_days, days, days);
    }
}
