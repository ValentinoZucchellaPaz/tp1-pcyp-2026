package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource.PrinterPool;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.TerminationTracker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.WorkerGroup;

/**
 * Tercera etapa: imprime las órdenes válidas y resuelve el destino de su impresora.
 *
 * <p>Una impresión exitosa libera la impresora y envía la orden a control de
 * calidad. Un fallo deja la impresora fuera de servicio y completa la orden.</p>
 */
public final class PrintingWorker implements WorkerGroup.InterruptibleTask {
    private final StartGate startGate;
    private final StageQueue input;
    private final StageQueue output;
    private final StageBarrier barrier;
    private final int nextWorkerCount;
    private final long delayMillis;
    private final EventLog eventLog;
    private final PrinterPool printers;
    private final TerminationTracker termination;
    private final SimulationConfig config;

    public PrintingWorker(StartGate startGate, StageQueue input, StageQueue output,
            StageBarrier barrier, int nextWorkerCount, long delayMillis, EventLog eventLog,
            PrinterPool printers, TerminationTracker termination, SimulationConfig config) {
        this.startGate = startGate;
        this.input = input;
        this.output = output;
        this.barrier = barrier;
        this.nextWorkerCount = nextWorkerCount;
        this.delayMillis = delayMillis;
        this.eventLog = eventLog;
        this.printers = printers;
        this.termination = termination;
        this.config = config;
    }

    @Override
    public void run() throws InterruptedException {
        startGate.awaitOpen();
        while (true) {
            WorkItem item = input.take();
            if (item.isPoison()) {
                // El último impresor propaga el cierre a control de calidad.
                if (barrier.workerFinished()) {
                    output.offerPoison(nextWorkerCount);
                }
                return;
            }

            Order order = item.order();
            PipelineSupport.delay(delayMillis);
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
    }
}
