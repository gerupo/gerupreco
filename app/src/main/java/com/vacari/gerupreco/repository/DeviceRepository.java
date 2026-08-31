package com.vacari.gerupreco.repository;

import android.content.Context;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.vacari.gerupreco.model.firebase.Device;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.DeviceIdentity;

import java.util.HashMap;
import java.util.Map;

/**
 * Este aparelho na colecao "device", com o ANDROID_ID como id do documento.
 *
 * O documento existe por um motivo so: guardar o token do FCM, que e por onde a
 * rotina no servidor acerta um alerta de alcance particular. Ninguem le esta
 * colecao pelo app.
 *
 * Ja houve aqui uma lista de aparelhos conhecidos, com cache e renomeacao, que
 * alimentava um seletor de "notificar somente o aparelho X". Saiu junto com o
 * seletor: alcance so se escolhe para si ou para todos, entao nao ha o que
 * listar nem o que nomear.
 */
public class DeviceRepository {

    private static final String COLLECTION = "device";

    private DeviceRepository() {
    }

    /**
     * Anuncia este aparelho e atualiza o token do FCM.
     *
     * O nome so e gravado quando o documento ainda nao existe, e nunca e lido
     * pelo app - na tela o aparelho e sempre "Este aparelho". Ele fica no
     * Firestore para quem abrir o console conseguir dizer de qual celular e cada
     * documento; "Este aparelho" repetido em todas as linhas nao diria nada.
     */
    public static void register(Context context, String fcmToken, Callback<Device> callback) {
        String deviceId = DeviceIdentity.id(context);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(COLLECTION).document(deviceId)
                .get()
                .addOnCompleteListener(task -> {
                    Map<String, Object> values = new HashMap<>();
                    values.put("fcmToken", fcmToken);
                    values.put("lastSeen", System.currentTimeMillis());

                    boolean known = task.isSuccessful()
                            && task.getResult() != null
                            && task.getResult().exists();

                    if (!known) {
                        values.put("name", DeviceIdentity.defaultName());
                    }

                    db.collection(COLLECTION).document(deviceId)
                            .set(values, SetOptions.merge())
                            .addOnSuccessListener(ignored -> {
                                Device device = new Device();
                                device.setId(deviceId);
                                device.setFcmToken(fcmToken);
                                callback.callback(device);
                            })
                            .addOnFailureListener(e -> callback.callback(null));
                });
    }
}
