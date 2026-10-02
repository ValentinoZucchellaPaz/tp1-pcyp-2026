package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.util.Objects;

/** Mensaje interno de una cola: orden real o píldora de terminación. */
final class WorkItem {
    private final Order order;

    private WorkItem(Order order) {
        this.order = order;
    }

    static WorkItem order(Order order) { return new WorkItem(Objects.requireNonNull(order)); }
    static WorkItem poison() { return new WorkItem(null); }
    boolean isPoison() { return order == null; }
    Order order() { return order; }
}
