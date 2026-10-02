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

/** Segunda etapa: valida el modelo y rechaza o deriva la orden a impresión. */
public final class ValidationWorker implements WorkerGroup.InterruptibleTask {
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

    public ValidationWorker(StartGate startGate, StageQueue input, StageQueue output,
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
                if (barrier.workerFinished()) {
                    output.offerPoison(nextWorkerCount);
                }
                return;
            }

            Order order = item.order();
            PipelineSupport.delay(delayMillis);
            order.recordValidation();
            if (OutcomeDecider.isModelValid(order.id(), config)) {
                PipelineSupport.transition(eventLog, order, "VALIDATION",
                        OrderState.READY_TO_PRINT, order.printerId());
                output.offerOrder(order);
            } else {
                PipelineSupport.transition(eventLog, order, "VALIDATION",
                        OrderState.REJECTED, order.printerId());
                printers.release(order.printerId());
                termination.orderCompleted();
            }
        }
    }
}
