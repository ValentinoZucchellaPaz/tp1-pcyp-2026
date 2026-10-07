package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource.PrinterPool;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.TerminationTracker;

/**
 * Segunda etapa: valida el modelo y decide si la orden continúa a impresión.
 *
 * <p>Una orden inválida termina aquí: libera su impresora y se registra en
 * {@link TerminationTracker}. Una orden válida conserva la reserva y pasa a
 * impresión.</p>
 */
public final class ValidationWorker extends AbstractStageWorker {
    private final StageQueue output;
    private final int nextWorkerCount;
    private final EventLog eventLog;
    private final PrinterPool printers;
    private final TerminationTracker termination;
    private final SimulationConfig config;

    public ValidationWorker(StartGate startGate, StageQueue input, StageQueue output,
            StageBarrier barrier, int nextWorkerCount, long delayMillis, EventLog eventLog,
            PrinterPool printers, TerminationTracker termination, SimulationConfig config) {
        super(startGate, input, barrier, delayMillis);
        this.output = output;
        this.nextWorkerCount = nextWorkerCount;
        this.eventLog = eventLog;
        this.printers = printers;
        this.termination = termination;
        this.config = config;
    }

    @Override
    protected void processOrder(Order order) {
        order.recordValidation();
        if (OutcomeDecider.isModelValid(order.id(), config)) {
            // La impresora sigue reservada para la impresión posterior.
            PipelineSupport.transition(eventLog, order, "VALIDATION",
                    OrderState.READY_TO_PRINT, order.printerId());
            output.offerOrder(order);
        } else {
            // Rechazo es un estado final: la impresora vuelve al pool.
            PipelineSupport.transition(eventLog, order, "VALIDATION",
                    OrderState.REJECTED, order.printerId());
            printers.release(order.printerId());
            termination.orderCompleted();
        }
    }

    @Override
    protected void onLastWorkerFinished() {
        output.offerPoison(nextWorkerCount);
    }
}
