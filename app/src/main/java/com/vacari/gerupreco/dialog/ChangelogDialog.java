package com.vacari.gerupreco.dialog;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vacari.gerupreco.R;

/**
 * Novidades da versao, exibidas uma unica vez depois de atualizar.
 *
 * O controle e por versionCode gravado em SharedPreferences: enquanto o valor
 * guardado for igual ao instalado, a tela nao volta a aparecer.
 */
public class ChangelogDialog {

    private static final String PREFS = "changelog";
    private static final String PREF_LAST_VERSION = "last_version";
    private static final int NEVER_SHOWN = 0;

    private ChangelogDialog() {
    }

    /**
     * Deve ser chamado so quando o app esta liberado para uso - se a versao
     * estiver desatualizada, quem manda na tela e o dialogo de atualizacao.
     */
    public static void showIfNeeded(AppCompatActivity context) {
        int versionCode = currentVersionCode(context);

        if (versionCode == NEVER_SHOWN) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, AppCompatActivity.MODE_PRIVATE);
        if (prefs.getInt(PREF_LAST_VERSION, NEVER_SHOWN) >= versionCode) {
            return;
        }

        String[] titles = context.getResources().getStringArray(R.array.changelog_titles);
        String[] descriptions = context.getResources().getStringArray(R.array.changelog_descriptions);

        // Marca antes de exibir: o listener do Firestore pode chamar
        // configureActions() mais de uma vez, e sem isso o dialogo empilharia.
        prefs.edit().putInt(PREF_LAST_VERSION, versionCode).apply();

        if (titles.length == 0 || context.isFinishing()) {
            return;
        }

        show(context, titles, descriptions);
    }

    private static void show(AppCompatActivity context, String[] titles, String[] descriptions) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_changelog, null);

        TextView version = view.findViewById(R.id.changelog_version);
        version.setText(context.getString(R.string.changelog_version, versionName(context)));

        LinearLayout container = view.findViewById(R.id.changelog_container);

        for (int i = 0; i < titles.length; i++) {
            View row = LayoutInflater.from(context)
                    .inflate(R.layout.changelog_row, container, false);

            TextView title = row.findViewById(R.id.changelog_item_title);
            TextView description = row.findViewById(R.id.changelog_item_description);

            title.setText(titles[i]);
            // Os arrays sao paralelos, mas um descuido na edicao do XML nao
            // pode derrubar o app na abertura.
            description.setText(i < descriptions.length ? descriptions[i] : "");

            container.addView(row);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.changelog_title)
                .setView(view)
                .setPositiveButton(R.string.changelog_dismiss, null)
                .show();
    }

    private static int currentVersionCode(AppCompatActivity context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return NEVER_SHOWN;
        }
    }

    private static String versionName(AppCompatActivity context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
