package com.vacari.gerupreco.model.firebase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Um produto no rastreamento de precos (colecao "tracking").
 *
 * A descricao e copia do catalogo, pelo mesmo motivo do CartItem: a tela monta
 * sem depender de uma segunda consulta, e excluir o produto do catalogo nao
 * deixa linha orfa aqui.
 *
 * Preco e Double porque o Firestore nao tem tipo decimal - toda comparacao na
 * tela passa por BigDecimal antes de virar texto.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tracking {

    /** Notifica todo aparelho que tem o app. */
    public static final String SCOPE_ALL = "GERAL";

    /** Notifica so o aparelho de deviceId. */
    public static final String SCOPE_DEVICE = "PARTICULAR";

    private String id;
    private String barCode;
    private String description;

    /** Vale so quando groupId e nulo; dentro de um grupo quem manda e o grupo. */
    private Double targetPrice;

    private String groupId;

    private String scope;

    /** ANDROID_ID do aparelho, quando o escopo e particular. */
    private String deviceId;

    private boolean active = true;

    /**
     * Ultimo preco que ja rendeu notificacao, e o estado inteiro da regra de
     * "uma vez por queda": nulo significa armado. A rotina no servidor volta a
     * nulo assim que o menor preco sobe acima do alvo.
     */
    private Double lastNotifiedPrice;

    private Long lastNotifiedAt;
    private Long lastCheckedAt;

    /**
     * Escopo desconhecido ou ausente conta como geral: alerta que chega a todo
     * mundo incomoda, alerta que nao chega a ninguem passa despercebido.
     */
    @JsonIgnore
    public boolean isForAllDevices() {
        return !SCOPE_DEVICE.equals(scope);
    }

    @JsonIgnore
    public boolean isInGroup() {
        return groupId != null && !groupId.trim().isEmpty();
    }
}
