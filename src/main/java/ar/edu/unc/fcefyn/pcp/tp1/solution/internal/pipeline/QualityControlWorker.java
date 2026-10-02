package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.TerminationTracker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.WorkerGroup;

/** Cuarta etapa: decide el estado final de calidad, sin usar impresoras. */
public final class QualityControlWorker implements WorkerGroup.InterruptibleTask {
    private final StartGate startGate;
    private final StageQueue input;
    private final StageBarrier barrier;
    private final long delayMillis;
    private final EventLog eventLog;
    private final TerminationTracker termination;
    private final SimulationConfig config;

    public QualityControlWorker(StartGate startGate, StageQueue input, StageBarrier barrier,
            long delayMillis, EventLog eventLog, TerminationTracker termination,
            SimulationConfig config) {
        this.startGate = startGate;
        this.input = input;
        this.barrier = barrier;
        this.delayMillis = delayMillis;
        this.eventLog = eventLog;
        this.termination = termination;
        this.config = config;
    }

    @Override
    public void run() throws InterruptedException {
        startGate.awaitOpen();
        while (true) {
            WorkItem item = input.take();
            if (item.isPoison()) {
                barrier.workerFinished();
                return;
            }

            Order order = item.order();
            PipelineSupport.delay(delayMillis);
            order.recordQualityControl();
            OrderState finalState = OutcomeDecider.isQualityApproved(order.id(), config)
                    ? OrderState.APPROVED : OrderState.DEFECTIVE;
            PipelineSupport.transition(eventLog, order, "QUALITY_CONTROL", finalState, "");
            termination.orderCompleted();
        }
    }
}
