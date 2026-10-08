package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource.PrinterPool;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.WorkerGroup;

/**
 * Primera etapa del pipeline: reserva una impresora para cada orden creada y
 * la deriva a validación.
 *
 * <p>No completa órdenes: toda orden asignada continúa en la cola de validación.
 * Si recibe una píldora, termina; el último worker de esta etapa propaga las
 * píldoras necesarias a la etapa siguiente.</p>
 */
public final class AssignmentWorker implements WorkerGroup.InterruptibleTask {
    private final StartGate startGate;
    private final StageQueue input;
    private final StageQueue output;
    private final StageBarrier barrier;
    private final int nextWorkerCount;
    private final long delayMillis;
    private final EventLog eventLog;
    private final PrinterPool printers;

    public AssignmentWorker(StartGate startGate, StageQueue input, StageQueue output,
            StageBarrier barrier, int nextWorkerCount, long delayMillis, EventLog eventLog,
            PrinterPool printers) {
        this.startGate = startGate;
        this.input = input;
        this.output = output;
        this.barrier = barrier;
        this.nextWorkerCount = nextWorkerCount;
        this.delayMillis = delayMillis;
        this.eventLog = eventLog;
        this.printers = printers;
    }

    @Override
    public void run() throws InterruptedException {
        // Ningún worker procesa órdenes hasta que el hilo principal abra la barrera.
        startGate.awaitOpen();
        while (true) {
            WorkItem item = input.take();
            if (item.isPoison()) {
                // Cada worker consume una píldora; solo el último cierra la próxima etapa.
                if (barrier.workerFinished()) {
                    output.offerPoison(nextWorkerCount);
                }
                return;
            }

            Order order = item.order();
            PipelineSupport.delay(delayMillis);
            // La reserva permanece asociada a la orden hasta su rechazo o impresión.
            String printerId = printers.reserveFor(order.id());
            order.assignPrinter(printerId);
            order.recordAssignment();
            PipelineSupport.transition(eventLog, order, "ASSIGNMENT",
                    OrderState.WAITING_VALIDATION, printerId);
            output.offerOrder(order);
        }
    }
}
