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
 * Tercera etapa: imprime las órdenes válidas y resuelve el destino de su impresora.
 *
 * <p>Una impresión exitosa libera la impresora y envía la orden a control de
 * calidad. Un fallo deja la impresora fuera de servicio y completa la orden.</p>
 */
public final class PrintingWorker extends AbstractStageWorker {
    private final StageQueue output;
    private final int nextWorkerCount;
    private final EventLog eventLog;
    private final PrinterPool printers;
    private final TerminationTracker termination;
    private final SimulationConfig config;

    public PrintingWorker(StartGate startGate, StageQueue input, StageQueue output,
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
        order.recordPrinting();
        if (OutcomeDecider.isPrintSuccessful(order.id(), config)) {
            // La orden continúa; la impresora ya puede reutilizarse.
            PipelineSupport.transition(eventLog, order, "PRINTING", OrderState.PRINTED,
                    order.printerId());
            printers.release(order.printerId());
            output.offerOrder(order);
        } else {
            // El fallo finaliza la orden y retira definitivamente la impresora.
            PipelineSupport.transition(eventLog, order, "PRINTING", OrderState.PRINT_FAILED,
                    order.printerId());
            printers.markOutOfService(order.printerId());
            termination.orderCompleted();
        }
    }

    @Override
    protected void onLastWorkerFinished() {
        output.offerPoison(nextWorkerCount);
    }
}
