package com.vacari.gerupreco.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.chip.Chip;
import com.vacari.gerupreco.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagUtil {

    private static final int CHIP_BACKGROUND_ALPHA = 36;

    private TagUtil() {
    }

    /**
     * A cor sai do nome da tag, entao a mesma tag tem sempre a mesma cor,
     * sem precisar guardar isso no Firestore.
     */
    public static int colorFor(Context context, String tag) {
        TypedArray palette = context.getResources().obtainTypedArray(R.array.tag_palette);
        try {
            int index = Math.floorMod(normalizeTag(tag).hashCode(), palette.length());
            return palette.getColor(index, 0);
        } finally {
            palette.recycle();
        }
    }

    public static Chip createChip(Context context, String tag, boolean removable) {
        int color = colorFor(context, tag);

        Chip chip = new Chip(context);
        chip.setText(tag);
        chip.setTextAppearanceResource(R.style.TextAppearance_GeruPreco_LabelCaps);
        chip.setTextColor(color);
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(color, CHIP_BACKGROUND_ALPHA)));
        chip.setChipStrokeWidth(0f);
        chip.setChipMinHeight(dp(context, 28));
        chip.setChipStartPadding(dp(context, 10));
        chip.setChipEndPadding(dp(context, 10));
        chip.setTextStartPadding(0f);
        chip.setTextEndPadding(0f);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setCheckable(false);
        chip.setClickable(removable);

        if (removable) {
            chip.setCloseIconVisible(true);
            chip.setCloseIconTint(ColorStateList.valueOf(color));
            chip.setCloseIconSize(dp(context, 16));
            chip.setChipEndPadding(dp(context, 6));
        }

        return chip;
    }

    public static String normalizeTag(String tag) {
        return StringUtil.normalize(tag);
    }

    /**
     * Junta as tags de todos os itens sem repetir, tratando "Bebida" e "bebida"
     * como a mesma coisa e mantendo a grafia que ja estava em uso.
     */
    public static List<String> distinctTags(List<String> tags) {
        Map<String, String> unique = new LinkedHashMap<>();

        for (String tag : tags) {
            String key = normalizeTag(tag);
            if (!key.isEmpty()) {
                unique.putIfAbsent(key, tag.trim());
            }
        }

        List<String> result = new ArrayList<>(unique.values());
        result.sort(StringUtil.textComparator());
        return result;
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
