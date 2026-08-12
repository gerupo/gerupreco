package com.vacari.gerupreco.retrofit;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Garante um intervalo minimo entre duas consultas a Nota Parana.
 *
 * A API pune volume por IP: quando marca o cliente como raspagem, para de
 * responder erro e passa a devolver registros forjados (ver DecoyFilter). O
 * carrinho e quem atrai a marcacao, porque dispara uma consulta por produto de
 * uma vez - a tela de produto unico faz uma so e escapa.
 *
 * Medido contra a API, com o mesmo conjunto de codigos de barras:
 *
 * <pre>
 * 9 consultas em paralelo    5 respostas HTTP 503, e 1 das 4 restantes
 *                            veio inteira forjada
 * 10 consultas a cada 400ms  168 registros, nenhum forjado, nenhum erro
 * </pre>
 *
 * O espacamento reduz a chance de ser marcado, mas nao desfaz a marcacao: uma
 * vez punido, o IP recebe resposta forjada por um bom tempo mesmo consultando
 * devagar. Por isso o DecoyFilter continua sendo a defesa que garante que preco
 * inventado nunca chega na tela - este aqui so evita chegar naquele estado.
 *
 * O estado e estatico de proposito: o limite e por IP, e o RetrofitConfig
 * constroi um cliente HTTP novo a cada chamada.
 *
 * O bloqueio segura a thread do dispatcher do OkHttp, nunca a da UI, e acontece
 * antes do proceed - os timeouts de conexao e leitura so comecam a contar
 * depois, entao a espera nao provoca timeout.
 */
public class RequestSpacer implements Interceptor {

    /**
     * Um carrinho de 20 produtos gasta 8s so de espera. Baixar este valor
     * acelera o carrinho e aumenta o risco de marcacao; subir faz o contrario.
     */
    private static final long MIN_INTERVAL_MS = 400;

    private static final Object LOCK = new Object();

    private static long lastRequestAt;

    @Override
    public Response intercept(Chain chain) throws IOException {
        awaitSlot();
        return chain.proceed(chain.request());
    }

    /**
     * Dorme segurando o monitor de proposito: quem espera atras fica na fila e
     * so mede o proprio intervalo depois que o da frente partiu. Liberar o
     * monitor durante a espera deixaria as consultas do carrinho sairem todas
     * juntas de novo.
     */
    static void awaitSlot() {
        synchronized (LOCK) {
            long remaining = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis();

            if (remaining > 0) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException e) {
                    // Nao ha o que abortar aqui: a consulta ainda nem partiu.
                    // Repassar o sinal deixa quem cancelou decidir.
                    Thread.currentThread().interrupt();
                }
            }

            lastRequestAt = System.currentTimeMillis();
        }
    }

    static long minIntervalMillis() {
        return MIN_INTERVAL_MS;
    }
}
