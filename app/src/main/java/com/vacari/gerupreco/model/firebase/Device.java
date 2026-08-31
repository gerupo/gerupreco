package com.vacari.gerupreco.model.firebase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Um aparelho com o app instalado (colecao "device"), gravado com o ANDROID_ID
 * como id do documento. E a unica identidade que o app tem: nao ha login, entao
 * "notificar um usuario especifico" so pode significar "notificar um aparelho".
 *
 * O token do FCM muda sozinho (reinstalacao, limpeza de dados, decisao do
 * proprio servico), por isso e reescrito a cada abertura do app.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {

    private String id;
    private String name;
    private String fcmToken;
    private Long lastSeen;
}
