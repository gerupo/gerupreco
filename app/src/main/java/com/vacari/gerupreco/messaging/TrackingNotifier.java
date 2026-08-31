package com.vacari.gerupreco.messaging;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.tracking.TrackingActivity;
import com.vacari.gerupreco.util.StringUtil;

/**
 * Monta o alerta de preco na bandeja.
 *
 * Vale tanto para a mensagem que chega com o app aberto - quando o FCM entrega
 * em onMessageReceived e o sistema nao desenha nada sozinho - quanto para o
 * canal que o sistema usa ao desenhar por conta propria com o app fechado.
 */
public class TrackingNotifier {

    private TrackingNotifier() {
    }

    /**
     * O id do canal sai de strings.xml porque o manifesto tambem precisa dele:
     * e o canal padrao do FCM, usado quando o sistema desenha a notificacao
     * sozinho com o app fechado. Duas copias soltas divergiriam, e o sintoma
     * seria uma notificacao caindo num canal generico fora do controle do
     * usuario.
     */
    public static String channelId(Context context) {
        return context.getString(R.string.tracking_channel_id);
    }

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                channelId(context),
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.tracking_channel_description));

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static void show(Context context, String title, String body, String barCode) {
        ensureChannel(context);

        Intent intent = new Intent(context, TrackingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId(context))
                .setSmallIcon(R.drawable.ic_stat_price_alert)
                .setColor(ContextCompat.getColor(context, R.color.primary_container))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(notificationId(barCode), builder.build());
        }
    }

    /**
     * Um id por produto: dois produtos que baixam na mesma rodada precisam
     * aparecer como dois avisos, e um novo aviso do mesmo produto substitui o
     * anterior em vez de empilhar preco velho.
     */
    private static int notificationId(String barCode) {
        return StringUtil.isEmpty(barCode) ? 1 : barCode.hashCode();
    }
}
