package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;

/**
 * Operaciones comunes a las etapas, sin ocultar sus decisiones de negocio.
 * La clase no se instancia porque solo agrupa funciones auxiliares.
 */
final class PipelineSupport {
    private PipelineSupport() {
    }

    /**
     * Simula el tiempo de trabajo de una etapa.
     *
     * @param millis duración de la demora simulada
     * @throws InterruptedException si se cancela el worker durante la demora
     */
    static void delay(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * Actualiza el estado de una orden y registra la transición en el log.
     * El nombre del thread se obtiene aquí para asociar el evento al worker que realmente procesó la orden.
     */
    static void transition(EventLog log, Order order, String stage, OrderState target,
            String printerId) {
        OrderState previous = order.transitionTo(target);
        log.transition(Thread.currentThread().getName(), order.id(), stage, previous, target, printerId);
    }
}
