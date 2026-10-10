package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.util.Objects;

/**
 * Mensaje interno de una cola de etapa: transporta una {@link Order} real
 * o una píldora centinela de terminación (poison pill).
 */
final class WorkItem {
    private final Order order;

    /**
     * Constructor privado para restringir la creación de instancias exclusivamente
     * a los métodos fábrica estáticos {@link #order(Order)} y {@link #poison()}.
     */
    private WorkItem(Order order) {
        this.order = order;
    }

    static WorkItem order(Order order) {
        return new WorkItem(Objects.requireNonNull(order, "order no puede ser null"));
    }

    static WorkItem poison() {
        return new WorkItem(null);
    }

    boolean isPoison() {
        return order == null;
    }

    Order order() {
        return order;
    }
}