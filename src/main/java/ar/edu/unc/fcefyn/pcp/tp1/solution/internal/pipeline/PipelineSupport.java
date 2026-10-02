package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;

/** Operaciones repetidas por etapas, sin ocultar la lógica de cada worker. */
final class PipelineSupport {
    private PipelineSupport() {
    }

    static void delay(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    static void transition(EventLog log, Order order, String stage, OrderState target,
            String printerId) {
        OrderState previous = order.transitionTo(target);
        log.transition(Thread.currentThread().getName(), order.id(), stage, previous, target, printerId);
    }
}
