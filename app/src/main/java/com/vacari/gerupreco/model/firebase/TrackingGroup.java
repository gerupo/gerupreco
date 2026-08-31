package com.vacari.gerupreco.model.firebase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Grupo de produtos rastreados (colecao "trackingGroup"). O alvo e o escopo
 * ficam aqui, e valem para todo produto que apontar para este grupo: basta um
 * deles atingir o alvo para o grupo notificar.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackingGroup {

    private String id;
    private String name;
    private Double targetPrice;
    private String scope;
    private String deviceId;
    private boolean active = true;

    @JsonIgnore
    public boolean isForAllDevices() {
        return !Tracking.SCOPE_DEVICE.equals(scope);
    }
}
