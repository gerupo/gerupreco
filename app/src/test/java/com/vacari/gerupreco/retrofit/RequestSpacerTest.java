package com.vacari.gerupreco.retrofit;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RequestSpacerTest {

    /**
     * O caso do carrinho: varias consultas partindo ao mesmo tempo, uma por
     * produto. Sem o espacador elas saiam todas juntas, e era a rajada que
     * fazia a Nota Parana devolver registros forjados.
     */
    @Test
    public void consultasSimultaneasSaemEspacadas() throws Exception {
        int requests = 4;
        long interval = RequestSpacer.minIntervalMillis();

        List<Long> departures = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch done = new CountDownLatch(requests);

        for (int i = 0; i < requests; i++) {
            new Thread(() -> {
                ready.countDown();
                RequestSpacer.awaitSlot();
                synchronized (departures) {
                    departures.add(System.currentTimeMillis());
                }
                done.countDown();
            }).start();
        }

        ready.await();
        long start = System.currentTimeMillis();
        done.await();
        long elapsed = System.currentTimeMillis() - start;

        // A primeira pode partir na hora; as outras esperam a sua vez.
        long expected = (requests - 1) * interval;
        assertTrue("as consultas sairam em rajada: " + elapsed + "ms para " + requests,
                elapsed >= expected - interval);
    }

    /**
     * Duas consultas seguidas - abrir a tela de um produto logo depois de outra
     * - tambem respeitam o intervalo.
     */
    @Test
    public void consultasSeguidasRespeitamOIntervalo() {
        RequestSpacer.awaitSlot();

        long start = System.currentTimeMillis();
        RequestSpacer.awaitSlot();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("saiu cedo demais: " + elapsed + "ms",
                elapsed >= RequestSpacer.minIntervalMillis() - 20);
    }
}
