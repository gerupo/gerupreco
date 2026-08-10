package com.vacari.gerupreco.dialog.cart;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.firebase.Item;
import com.vacari.gerupreco.repository.CartRepository;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.StringUtil;
import com.vacari.gerupreco.util.TagUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Escolha de uma tag para jogar todos os produtos dela no carrinho de uma vez.
 */
public class AddByTagDialog {

    private final AppCompatActivity mActivity;
    private final List<Item> items;
    private final Callback<Integer> onAdded;

    public AddByTagDialog(AppCompatActivity mActivity, List<Item> items, Callback<Integer> onAdded) {
        this.mActivity = mActivity;
        this.items = items;
        this.onAdded = onAdded;
    }

    public void show() {
        List<String> tags = TagUtil.distinctTags(allTags());

        if (tags.isEmpty()) {
            onAdded.callback(0);
            return;
        }

        View view = LayoutInflater.from(mActivity).inflate(R.layout.dialog_add_by_tag, null);
        LinearLayout container = view.findViewById(R.id.tag_pick_container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.cart_add_by_tag)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .create();

        for (String tag : tags) {
            container.addView(buildRow(container, tag, dialog));
        }

        dialog.show();
    }

    private View buildRow(LinearLayout container, String tag, AlertDialog dialog) {
        View row = LayoutInflater.from(mActivity).inflate(R.layout.tag_pick_row, container, false);

        ChipGroup chipGroup = row.findViewById(R.id.tag_pick_chip);
        chipGroup.addView(TagUtil.createChip(mActivity, tag, false));

        List<Item> matches = itemsWithTag(tag);

        TextView count = row.findViewById(R.id.tag_pick_count);
        count.setText(mActivity.getResources().getQuantityString(
                R.plurals.cart_products, matches.size(), matches.size()));

        row.setOnClickListener(v -> {
            CartRepository.addAll(mActivity, matches);
            dialog.dismiss();
            onAdded.callback(matches.size());
        });

        return row;
    }

    private List<String> allTags() {
        List<String> tags = new ArrayList<>();
        for (Item item : items) {
            tags.addAll(item.getTags());
        }
        return tags;
    }

    private List<Item> itemsWithTag(String tag) {
        String normalized = StringUtil.normalize(tag);
        List<Item> matches = new ArrayList<>();

        for (Item item : items) {
            for (String itemTag : item.getTags()) {
                if (StringUtil.normalize(itemTag).equals(normalized)) {
                    matches.add(item);
                    break;
                }
            }
        }
        return matches;
    }
}
