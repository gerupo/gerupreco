package com.vacari.gerupreco.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Produtos rastreados, na colecao "tracking" do Firestore. Vive no Firestore, e
 * nao em SQLite como o carrinho, porque quem le esta lista e a rotina agendada
 * no servidor - o aparelho e so mais um cliente dela.
 */
public class TrackingRepository {

    private static final String COLLECTION = "tracking";

    public static void searchAll(Callback<List<Tracking>> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.callback(new ArrayList<>());
                        return;
                    }

                    List<Tracking> trackings = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> data = document.getData();
                        Tracking tracking = objectMapper.convertValue(data, Tracking.class);
                        tracking.setId(document.getId());
                        trackings.add(tracking);
                    }

                    trackings.sort(Comparator.comparing(Tracking::getDescription,
                            StringUtil.textComparator()));
                    callback.callback(trackings);
                });
    }

    public static void save(Tracking tracking, Callback<Tracking> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION)
                .add(toValues(tracking))
                .addOnSuccessListener(reference -> {
                    tracking.setId(reference.getId());
                    callback.callback(tracking);
                })
                .addOnFailureListener(e -> callback.callback(null));
    }

    public static void update(Tracking tracking, Callback<Tracking> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION).document(tracking.getId())
                .set(toValues(tracking))
                .addOnSuccessListener(ignored -> callback.callback(tracking))
                .addOnFailureListener(e -> callback.callback(null));
    }

    public static void delete(String id, Callback<Tracking> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION).document(id)
                .delete()
                .addOnSuccessListener(ignored -> callback.callback(null))
                .addOnFailureListener(e -> callback.callback(null));
    }

    /**
     * Solta do grupo os produtos que estavam nele, herdando o alvo que o grupo
     * ditava. Sem herdar, o produto voltaria sem alvo nenhum e sairia do
     * rastreamento em silencio junto com o grupo apagado.
     */
    public static void releaseFromGroup(List<Tracking> trackings, String groupId,
                                        Double groupTarget, Callback<Void> callback) {
        List<Tracking> affected = new ArrayList<>();
        for (Tracking tracking : trackings) {
            if (groupId.equals(tracking.getGroupId())) {
                affected.add(tracking);
            }
        }

        if (affected.isEmpty()) {
            callback.callback(null);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        for (Tracking tracking : affected) {
            tracking.setGroupId(null);
            if (tracking.getTargetPrice() == null) {
                tracking.setTargetPrice(groupTarget);
            }
            batch.set(db.collection(COLLECTION).document(tracking.getId()), toValues(tracking));
        }

        batch.commit()
                .addOnSuccessListener(ignored -> callback.callback(null))
                .addOnFailureListener(e -> callback.callback(null));
    }

    /**
     * O id e a identidade do documento, entao nao se repete dentro dele.
     */
    private static Map<String, Object> toValues(Tracking tracking) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> values = objectMapper.convertValue(tracking, Map.class);
        values.remove("id");
        return values;
    }
}
