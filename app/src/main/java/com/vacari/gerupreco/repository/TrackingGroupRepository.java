package com.vacari.gerupreco.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.vacari.gerupreco.model.firebase.TrackingGroup;
import com.vacari.gerupreco.util.Callback;
import com.vacari.gerupreco.util.StringUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Grupos de rastreamento, na colecao "trackingGroup".
 */
public class TrackingGroupRepository {

    private static final String COLLECTION = "trackingGroup";

    public static void searchAll(Callback<List<TrackingGroup>> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.callback(new ArrayList<>());
                        return;
                    }

                    List<TrackingGroup> groups = new ArrayList<>();
                    ObjectMapper objectMapper = new ObjectMapper();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> data = document.getData();
                        TrackingGroup group = objectMapper.convertValue(data, TrackingGroup.class);
                        group.setId(document.getId());
                        groups.add(group);
                    }

                    groups.sort(Comparator.comparing(TrackingGroup::getName,
                            StringUtil.textComparator()));
                    callback.callback(groups);
                });
    }

    public static void save(TrackingGroup group, Callback<TrackingGroup> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION)
                .add(toValues(group))
                .addOnSuccessListener(reference -> {
                    group.setId(reference.getId());
                    callback.callback(group);
                })
                .addOnFailureListener(e -> callback.callback(null));
    }

    public static void update(TrackingGroup group, Callback<TrackingGroup> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION).document(group.getId())
                .set(toValues(group))
                .addOnSuccessListener(ignored -> callback.callback(group))
                .addOnFailureListener(e -> callback.callback(null));
    }

    public static void delete(String id, Callback<TrackingGroup> callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION).document(id)
                .delete()
                .addOnSuccessListener(ignored -> callback.callback(null))
                .addOnFailureListener(e -> callback.callback(null));
    }

    private static Map<String, Object> toValues(TrackingGroup group) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> values = objectMapper.convertValue(group, Map.class);
        values.remove("id");
        return values;
    }
}
