package com.vacari.gerupreco.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.activity.lowestprice.LowestPriceProduct;
import com.vacari.gerupreco.activity.simpleproportion.SimpleProportionActivity;
import com.vacari.gerupreco.activity.tracking.TrackingActivity;
import com.vacari.gerupreco.dialog.ChangelogDialog;
import com.vacari.gerupreco.messaging.TrackingRegistration;
import com.vacari.gerupreco.update.UpdateJob;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    /**
     * O listener do Firestore que libera a tela pode chamar configureActions
     * mais de uma vez. Pedir a permissao de novo empilharia um segundo dialogo
     * do sistema sobre o primeiro ainda sem resposta, e reanunciar o aparelho
     * so gastaria escrita.
     */
    private boolean trackingReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Registrado aqui, e nao em configureActions, porque um launcher so pode
        // ser registrado antes da Activity ficar STARTED - disparar depois vale,
        // registrar nao.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.makeText(this, R.string.tracking_permission_denied,
                                Toast.LENGTH_LONG).show();
                    }
                });

        UpdateJob.initJobUpdate(this);
    }

    public void configureActions() {
        findViewById(R.id.card_gerometro).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SimpleProportionActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.card_gerupreco).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LowestPriceProduct.class);
            startActivity(intent);
        });

        findViewById(R.id.card_tracking).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TrackingActivity.class);
            startActivity(intent);
        });

        // Junto dos cards, pelo mesmo motivo do changelog: so faz sentido pedir
        // permissao e anunciar o aparelho depois que a versao foi validada. Numa
        // versao bloqueada o usuario nao chega a usar nada disso.
        if (!trackingReady) {
            trackingReady = true;
            requestNotificationPermission();
            TrackingRegistration.register(this);
        }

        // Aqui, e nao no onCreate: este e o ponto em que a versao ja foi
        // validada e o app esta liberado. Com a versao desatualizada quem
        // ocupa a tela e o dialogo de atualizacao.
        ChangelogDialog.showIfNeeded(this);
    }

    /**
     * A partir do Android 13 a notificacao so aparece com permissao concedida.
     * Sem ela o alerta de preco chega ao aparelho e morre em silencio, que e
     * indistinguivel de "nenhum produto atingiu o alvo".
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

}
