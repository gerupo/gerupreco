package com.vacari.gerupreco.util;

import android.content.Context;

import com.vacari.gerupreco.R;
import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta as opcoes de alcance de um alerta: todos os aparelhos, ou este.
 *
 * Sao so essas duas de proposito. O seletor ja listou todo aparelho registrado,
 * o que permitia criar daqui um alerta mirado no celular de outra pessoa - e
 * quem recebia nao tinha como saber de onde veio nem como desligar, porque o
 * cadastro vive no aparelho que o criou. Alcance so se escolhe para si ou para
 * todos.
 *
 * Como consequencia isto nao depende mais de consulta nenhuma: as duas opcoes
 * saem do proprio aparelho, e o dialogo do long press monta sem esperar rede.
 *
 * Aqui mora tambem o outro lado da mesma regra: o que este aparelho enxerga da
 * colecao, em isVisible. Escolher alcance e ver alcance sao a mesma decisao
 * lida das duas pontas, e separa-las em arquivos diferentes deixaria uma mudar
 * sem a outra.
 */
public class TrackingScopes {

    public static class Option {

        private final String label;
        private final String scope;
        private final String deviceId;

        Option(String label, String scope, String deviceId) {
            this.label = label;
            this.scope = scope;
            this.deviceId = deviceId;
        }

        public String getScope() {
            return scope;
        }

        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private TrackingScopes() {
    }

    public static List<Option> options(Context context) {
        List<Option> options = new ArrayList<>();

        options.add(new Option(context.getString(R.string.tracking_scope_all),
                Tracking.SCOPE_ALL, null));
        options.add(new Option(context.getString(R.string.tracking_scope_this_device),
                Tracking.SCOPE_DEVICE, DeviceIdentity.id(context)));

        return options;
    }

    /**
     * Posicao da opcao que corresponde ao que esta gravado, para o Spinner abrir
     * mostrando o alcance atual.
     *
     * Alerta particular de outro aparelho nao chega aqui - ele e filtrado antes,
     * em visibleTrackings. O recuo para "todos" fica como rede de seguranca, e
     * cai para o lado aberto de proposito, pela mesma regra que o servidor
     * aplica a escopo desconhecido.
     */
    public static int indexOf(List<Option> options, String scope, String deviceId) {
        if (!Tracking.SCOPE_DEVICE.equals(scope) || StringUtil.isEmpty(deviceId)) {
            return 0;
        }

        for (int i = 0; i < options.size(); i++) {
            if (deviceId.equals(options.get(i).getDeviceId())) {
                return i;
            }
        }

        return 0;
    }

    /**
     * O que este aparelho pode ver e mexer.
     *
     * Alerta **geral** aparece em todo aparelho, e qualquer um edita ou apaga:
     * ele vale para todos, entao nao ha dono. Alerta **particular** so aparece
     * no aparelho que ele acerta - noutro celular seria uma linha que a pessoa
     * nao pode desligar e cujo alerta ela nunca vai receber, e apagar por
     * engano tiraria o aviso de quem depende dele.
     *
     * E tambem a garantia de que o rotulo "outro aparelho" nao precisa existir:
     * o que ele nomearia nunca chega na tela.
     */
    public static boolean isVisible(Context context, String scope, String deviceId) {
        if (!Tracking.SCOPE_DEVICE.equals(scope)) {
            return true;
        }

        return DeviceIdentity.id(context).equals(deviceId);
    }

    public static List<TrackingGroup> visibleGroups(Context context, List<TrackingGroup> groups) {
        List<TrackingGroup> visible = new ArrayList<>();

        for (TrackingGroup group : groups) {
            if (isVisible(context, group.getScope(), group.getDeviceId())) {
                visible.add(group);
            }
        }

        return visible;
    }

    /**
     * Dentro de um grupo quem manda no alcance e o grupo, entao a visibilidade
     * do produto e a do grupo: esconder um grupo e deixar os produtos dele na
     * lista mostraria linhas sem alvo visivel e sem como explicar de onde vem.
     *
     * Grupo que sumiu do cadastro e caso a parte - o produto orfao cai no
     * proprio escopo, do mesmo jeito que a linha da lista cai no proprio alvo.
     */
    public static List<Tracking> visibleTrackings(Context context, List<Tracking> trackings,
                                                  List<TrackingGroup> allGroups) {
        Map<String, TrackingGroup> groupsById = TrackingPlan.byId(allGroups);

        List<Tracking> visible = new ArrayList<>();

        for (Tracking tracking : trackings) {
            TrackingPlan plan = TrackingPlan.of(tracking, groupsById);

            if (isVisible(context, plan.getScope(), plan.getDeviceId())) {
                visible.add(tracking);
            }
        }

        return visible;
    }
}
