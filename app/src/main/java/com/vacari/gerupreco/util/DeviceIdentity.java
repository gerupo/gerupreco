package com.vacari.gerupreco.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * Identidade do aparelho para o rastreamento de precos.
 *
 * O app nao tem login, entao "notificar um usuario especifico" so pode
 * significar "notificar um aparelho". O ANDROID_ID e a identidade escolhida por
 * sobreviver a atualizacoes do app e a reinstalacoes - some apenas em reset de
 * fabrica, quando o aparelho de fato virou outro. O token do FCM nao serve para
 * isso: ele muda sozinho.
 */
public class DeviceIdentity {

    private DeviceIdentity() {
    }

    @SuppressLint("HardwareIds")
    public static String id(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);

        return StringUtil.isNotEmpty(androidId) ? androidId : "aparelho-sem-id";
    }

    /**
     * Nome inicial do aparelho, so para o usuario reconhecer qual e o dele na
     * lista antes de renomear. Alguns fabricantes ja repetem o proprio nome no
     * modelo ("Xiaomi Redmi Note"), e repetir de novo ficaria estranho.
     */
    public static String defaultName() {
        String manufacturer = StringUtil.or(Build.MANUFACTURER, "");
        String model = StringUtil.or(Build.MODEL, "");

        if (StringUtil.isEmpty(model)) {
            return StringUtil.isEmpty(manufacturer) ? "Aparelho" : capitalize(manufacturer);
        }

        if (StringUtil.normalize(model).startsWith(StringUtil.normalize(manufacturer))
                || StringUtil.isEmpty(manufacturer)) {
            return model;
        }

        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String text) {
        if (text.length() < 2) {
            return text.toUpperCase(Locale.ROOT);
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
