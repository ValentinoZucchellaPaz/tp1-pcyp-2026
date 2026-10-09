package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.TerminationTracker;

/**
 * Última etapa: determina si una orden impresa queda aprobada o defectuosa.
 *
 * <p>Toda orden que llega aquí finaliza, por lo que se informa al
 * {@link TerminationTracker}. No existe una cola posterior a esta etapa.</p>
 */
public final class QualityControlWorker extends AbstractStageWorker {
    private final EventLog eventLog;
    private final TerminationTracker termination;
    private final SimulationConfig config;

    public QualityControlWorker(StartGate startGate, StageQueue input, StageBarrier barrier,
            long delayMillis, EventLog eventLog, TerminationTracker termination,
            SimulationConfig config) {
        super(startGate, input, barrier, delayMillis);
        this.eventLog = eventLog;
        this.termination = termination;
        this.config = config;
    }

    @Override
    protected void processOrder(Order order) {
        order.recordQualityControl();
        // Ambas alternativas son estados terminales de la orden.
        OrderState finalState = OutcomeDecider.isQualityApproved(order.id(), config)
                ? OrderState.APPROVED : OrderState.DEFECTIVE;
        PipelineSupport.transition(eventLog, order, "QUALITY_CONTROL", finalState, "");
        termination.orderCompleted();
    }
}
