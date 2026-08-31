package com.vacari.gerupreco.messaging;

import android.content.Context;

import com.google.firebase.messaging.FirebaseMessaging;
import com.vacari.gerupreco.repository.DeviceRepository;

/**
 * Anuncia este aparelho para a rotina de alertas.
 *
 * Sao tres coisas que precisam existir antes do primeiro alerta chegar: o canal
 * de notificacao, a inscricao no topico dos alertas gerais e o token deste
 * aparelho gravado no Firestore - e o token que a rotina usa para acertar um
 * aparelho especifico quando o alerta e particular.
 */
public class TrackingRegistration {

    /**
     * Topico dos alertas de alcance geral. O nome e contrato com a rotina no
     * servidor: mudar aqui sem mudar la deixa o alerta sem ninguem escutando.
     */
    public static final String TOPIC_ALL = "geral";

    private TrackingRegistration() {
    }

    public static void register(Context context) {
        TrackingNotifier.ensureChannel(context);

        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL);

        // O token e o unico motivo de o aparelho existir no Firestore: e por ele
        // que a rotina no servidor acerta um alerta particular. O nome gravado
        // junto serve so para quem abrir o console - na tela o aparelho e sempre
        // "Este aparelho".
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> DeviceRepository.register(context, token, device -> {
                }));
    }
}
