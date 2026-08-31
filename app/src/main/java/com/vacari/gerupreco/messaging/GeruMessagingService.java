package com.vacari.gerupreco.messaging;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.vacari.gerupreco.repository.DeviceRepository;
import com.vacari.gerupreco.util.StringUtil;

import java.util.Map;

/**
 * Recebe os alertas de preco enviados pela rotina agendada.
 *
 * Com o app em segundo plano o proprio sistema desenha a notificacao e este
 * metodo nem e chamado; com o app aberto a entrega vem para ca e o desenho e
 * por nossa conta. Por isso a mensagem carrega titulo e texto nos dois lugares,
 * e a leitura aceita os dois.
 */
public class GeruMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();

        String title = data.get("title");
        String body = data.get("body");

        if (remoteMessage.getNotification() != null) {
            title = StringUtil.or(remoteMessage.getNotification().getTitle(), title);
            body = StringUtil.or(remoteMessage.getNotification().getBody(), body);
        }

        if (StringUtil.isEmpty(title) && StringUtil.isEmpty(body)) {
            return;
        }

        TrackingNotifier.show(this, title, body, data.get("barCode"));
    }

    /**
     * O token muda sozinho, e um token velho no Firestore vira alerta que nao
     * chega. Reescreve na hora em vez de esperar a proxima abertura do app.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        DeviceRepository.register(this, token, device -> {
        });
    }
}
