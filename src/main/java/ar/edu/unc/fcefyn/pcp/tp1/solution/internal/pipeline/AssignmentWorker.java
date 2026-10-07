package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource.PrinterPool;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;

/**
 * Primera etapa del pipeline: reserva una impresora para cada orden creada y
 * la deriva a validación.
 *
 * <p>No completa órdenes: toda orden asignada continúa en la cola de validación.
 * Si recibe una píldora, termina; el último worker de esta etapa propaga las
 * píldoras necesarias a la etapa siguiente.</p>
 */
public final class AssignmentWorker extends AbstractStageWorker {
    private final StageQueue output;
    private final int nextWorkerCount;
    private final EventLog eventLog;
    private final PrinterPool printers;

    public AssignmentWorker(StartGate startGate, StageQueue input, StageQueue output,
            StageBarrier barrier, int nextWorkerCount, long delayMillis, EventLog eventLog,
            PrinterPool printers) {
        super(startGate, input, barrier, delayMillis);
        this.output = output;
        this.nextWorkerCount = nextWorkerCount;
        this.eventLog = eventLog;
        this.printers = printers;
    }

    @Override
    protected void processOrder(Order order) throws InterruptedException {
        // La reserva permanece asociada a la orden hasta su rechazo o impresión.
        String printerId = printers.reserveFor(order.id());
        order.assignPrinter(printerId);
        order.recordAssignment();
        PipelineSupport.transition(eventLog, order, "ASSIGNMENT",
                OrderState.WAITING_VALIDATION, printerId);
        output.offerOrder(order);
    }

    @Override
    protected void onLastWorkerFinished() {
        output.offerPoison(nextWorkerCount);
    }
}
