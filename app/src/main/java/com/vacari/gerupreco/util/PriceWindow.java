package com.vacari.gerupreco.util;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.vacari.gerupreco.R;

/**
 * Janela de validade dos precos, compartilhada pelas abas do carrinho e pela
 * tela de precos de um produto.
 *
 * O recorte por data e sempre local (ver CartCompare), entao trocar o chip so
 * refaz o calculo em memoria - sem consulta nova. A escolha e guardada e vale
 * para todas as telas: ver o mesmo preco listado numa e sumido na outra daria a
 * impressao de resultado inconsistente.
 */
public class PriceWindow {

    private static final String PREFS = "cart_compare";
    private static final String PREF_WINDOW = "window_days";
    private static final int DEFAULT_WINDOW = 7;

    private static final int[] DAYS = {1, 2, 3, 7, CartCompare.ANY_AGE};

    private PriceWindow() {
    }

    /**
     * Janela guardada que nao esta mais na fila de chips volta para o padrao.
     *
     * As opcoes ja mudaram uma vez (15 e 30 dias sairam), e quem tinha uma
     * delas gravada abriria a tela filtrando por um valor sem chip marcado: a
     * lista recortada por uma janela que a tela nao mostra.
     */
    public static int load(Context context) {
        int stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_WINDOW, DEFAULT_WINDOW);

        for (int days : DAYS) {
            if (days == stored) {
                return stored;
            }
        }
        return DEFAULT_WINDOW;
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

        revealSelected(group);
    }

    /**
     * Traz o chip marcado para dentro da tela.
     *
     * A fila de chips nao cabe na largura do aparelho, e a janela guardada pode
     * ser uma das ultimas. Sem isso a tela abre mostrando so chips apagados, o
     * que se le como "nenhuma janela escolhida" - e o usuario nao tem como saber
     * que precisa rolar a fila para achar a marcada.
     */
    private static void revealSelected(ChipGroup group) {
        group.post(() -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof Chip && ((Chip) child).isChecked()) {
                    child.requestRectangleOnScreen(
                            new Rect(0, 0, child.getWidth(), child.getHeight()), true);
                    return;
                }
            }
        });
    }

    private static String label(Context context, int days) {
        if (days == CartCompare.ANY_AGE) {
            return context.getString(R.string.price_window_any);
        }
        return context.getResources().getQuantityString(R.plurals.price_window_days, days, days);
    }
}
