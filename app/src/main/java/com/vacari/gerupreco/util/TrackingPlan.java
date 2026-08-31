package com.vacari.gerupreco.util;

import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O alvo e o alcance que de fato valem para um rastreamento.
 *
 * Existe porque a regra "dentro de um grupo quem manda e o grupo" estava
 * reescrita em quatro lugares - a lista, o dialogo de cadastro, o filtro de
 * visibilidade e a rotina do servidor -, e bastou um deles divergir para a tela
 * mentir: a lista mostrava o alvo do grupo (R$ 5,90) enquanto o dialogo do mesmo
 * produto mostrava o alvo proprio, obsoleto (R$ 6,00), sem nada indicando qual
 * dos dois o servidor usaria.
 *
 * Sem dependencia do Android de proposito: e o que permite testar a regra em
 * teste de unidade, que e a segunda metade da defesa contra a divergencia
 * voltar.
 *
 * O equivalente do lado do servidor e o resolvePlan do functions/src/
 * trackingRules.js. Sao duas linguagens, entao a duplicacao ali e inevitavel -
 * mas as duas sao cobertas por teste, e mudar uma sem a outra quebra o teste
 * correspondente.
 */
public class TrackingPlan {

    private final Double targetPrice;
    private final String scope;
    private final String deviceId;
    private final TrackingGroup group;
    private final boolean active;

    private TrackingPlan(Double targetPrice, String scope, String deviceId,
                         TrackingGroup group, boolean active) {
        this.targetPrice = targetPrice;
        this.scope = scope;
        this.deviceId = deviceId;
        this.group = group;
        this.active = active;
    }

    public static Map<String, TrackingGroup> byId(List<TrackingGroup> groups) {
        Map<String, TrackingGroup> byId = new HashMap<>();

        if (groups != null) {
            for (TrackingGroup group : groups) {
                byId.put(group.getId(), group);
            }
        }

        return byId;
    }

    /**
     * Um grupo que sumiu do cadastro cai no que o proprio produto guarda, em vez
     * de deixar a linha sem alvo: e o mesmo recuo que a lista ja fazia, agora
     * num lugar so.
     */
    public static TrackingPlan of(Tracking tracking, Map<String, TrackingGroup> groupsById) {
        TrackingGroup group = tracking.isInGroup() && groupsById != null
                ? groupsById.get(tracking.getGroupId())
                : null;

        if (group == null) {
            return new TrackingPlan(tracking.getTargetPrice(), tracking.getScope(),
                    tracking.getDeviceId(), null, tracking.isActive());
        }

        // Pausar o grupo pausa quem esta dentro dele: o alcance e o alvo vem
        // dali, e seria estranho o produto seguir avisando por um grupo parado.
        return new TrackingPlan(group.getTargetPrice(), group.getScope(),
                group.getDeviceId(), group, tracking.isActive() && group.isActive());
    }

    public Double getTargetPrice() {
        return targetPrice;
    }

    public String getScope() {
        return scope;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /** Nulo quando o produto nao esta em grupo, ou o grupo dele nao existe mais. */
    public TrackingGroup getGroup() {
        return group;
    }

    public String getGroupName() {
        return group == null ? null : group.getName();
    }

    public boolean isInGroup() {
        return group != null;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Escopo desconhecido ou ausente conta como geral, como no resto do app:
     * alerta que chega a todo mundo incomoda, alerta que nao chega a ninguem
     * passa despercebido.
     */
    public boolean isForAllDevices() {
        return !Tracking.SCOPE_DEVICE.equals(scope);
    }
}
