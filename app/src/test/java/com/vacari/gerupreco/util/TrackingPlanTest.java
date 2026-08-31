package com.vacari.gerupreco.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vacari.gerupreco.model.firebase.Tracking;
import com.vacari.gerupreco.model.firebase.TrackingGroup;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class TrackingPlanTest {

    private TrackingGroup group(String id, String name, Double target, String scope) {
        TrackingGroup group = new TrackingGroup();
        group.setId(id);
        group.setName(name);
        group.setTargetPrice(target);
        group.setScope(scope);
        return group;
    }

    private Tracking tracking(Double target, String scope, String groupId) {
        Tracking tracking = new Tracking();
        tracking.setDescription("Produto");
        tracking.setTargetPrice(target);
        tracking.setScope(scope);
        tracking.setGroupId(groupId);
        return tracking;
    }

    /**
     * A divergencia que motivou esta classe: a lista mostrava o alvo do grupo e
     * o dialogo do mesmo produto mostrava o alvo proprio, obsoleto, sem nada
     * dizendo qual dos dois o servidor usaria.
     */
    @Test
    public void alvoDoGrupoVenceOAlvoProprioObsoleto() {
        Map<String, TrackingGroup> groups = TrackingPlan.byId(
                Collections.singletonList(group("g1", "Einsenbhan", 5.90, Tracking.SCOPE_DEVICE)));

        TrackingPlan plan = TrackingPlan.of(tracking(6.00, Tracking.SCOPE_ALL, "g1"), groups);

        assertEquals(Double.valueOf(5.90), plan.getTargetPrice());
        assertEquals(Tracking.SCOPE_DEVICE, plan.getScope());
        assertFalse(plan.isForAllDevices());
        assertEquals("Einsenbhan", plan.getGroupName());
    }

    @Test
    public void foraDeGrupoValemOsProprios() {
        TrackingPlan plan = TrackingPlan.of(
                tracking(70.00, Tracking.SCOPE_ALL, null), TrackingPlan.byId(null));

        assertEquals(Double.valueOf(70.00), plan.getTargetPrice());
        assertTrue(plan.isForAllDevices());
        assertNull(plan.getGroup());
        assertFalse(plan.isInGroup());
    }

    /**
     * Grupo apagado noutro aparelho: o produto orfao cai no proprio alvo em vez
     * de ficar sem nenhum.
     */
    @Test
    public void grupoQueSumiuCaiNoAlvoProprio() {
        TrackingPlan plan = TrackingPlan.of(
                tracking(6.00, Tracking.SCOPE_ALL, "apagado"),
                TrackingPlan.byId(Collections.emptyList()));

        assertEquals(Double.valueOf(6.00), plan.getTargetPrice());
        assertNull(plan.getGroupName());
    }

    @Test
    public void grupoPausadoPausaOProduto() {
        TrackingGroup pausado = group("g1", "Cervejas", 5.90, Tracking.SCOPE_ALL);
        pausado.setActive(false);

        Tracking ativo = tracking(6.00, Tracking.SCOPE_ALL, "g1");
        ativo.setActive(true);

        assertFalse(TrackingPlan.of(ativo, TrackingPlan.byId(
                Collections.singletonList(pausado))).isActive());
    }

    @Test
    public void produtoPausadoDentroDeGrupoAtivoSegueParado() {
        Tracking pausado = tracking(6.00, Tracking.SCOPE_ALL, "g1");
        pausado.setActive(false);

        assertFalse(TrackingPlan.of(pausado, TrackingPlan.byId(
                Collections.singletonList(group("g1", "Cervejas", 5.90, Tracking.SCOPE_ALL))))
                .isActive());
    }

    @Test
    public void escopoAusenteContaComoGeral() {
        TrackingPlan plan = TrackingPlan.of(tracking(1.00, null, null), TrackingPlan.byId(null));

        assertTrue(plan.isForAllDevices());
    }

    @Test
    public void byIdIndexaTodosOsGrupos() {
        Map<String, TrackingGroup> groups = TrackingPlan.byId(Arrays.asList(
                group("a", "A", 1.0, Tracking.SCOPE_ALL),
                group("b", "B", 2.0, Tracking.SCOPE_ALL)));

        assertEquals(2, groups.size());
        assertEquals("B", groups.get("b").getName());
    }
}
