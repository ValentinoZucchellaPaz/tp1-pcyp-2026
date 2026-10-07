package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.WorkerGroup;

/**
 * Protocolo común de ejecución de una etapa del pipeline.
 *
 * <p>Centraliza la parte concurrente que todas las etapas comparten: esperar
 * el inicio conjunto, retirar trabajo de su cola, aplicar la demora configurada
 * y reaccionar a una píldora de terminación. Las subclases solo definen qué
 * hacer con una orden y, si corresponde, cómo cerrar la etapa siguiente.</p>
 */
public abstract class AbstractStageWorker implements WorkerGroup.InterruptibleTask {
    private final StartGate startGate;
    private final StageQueue input;
    private final StageBarrier barrier;
    private final long delayMillis;

    protected AbstractStageWorker(StartGate startGate, StageQueue input, StageBarrier barrier,
            long delayMillis) {
        this.startGate = startGate;
        this.input = input;
        this.barrier = barrier;
        this.delayMillis = delayMillis;
    }

    /**
     * Ejecuta el ciclo de vida común. Se declara final para que todas las
     * etapas mantengan la misma semántica de inicio y cierre.
     */
    @Override
    public final void run() throws InterruptedException {
        startGate.awaitOpen();
        while (true) {
            WorkItem item = input.take();
            if (item.isPoison()) {
                if (barrier.workerFinished()) {
                    onLastWorkerFinished();
                }
                return;
            }

            PipelineSupport.delay(delayMillis);
            processOrder(item.order());
        }
    }

    /** Ejecuta la regla de negocio de la etapa para una única orden. */
    protected abstract void processOrder(Order order) throws InterruptedException;

    /**
     * Se ejecuta una sola vez: por el último worker que consume una píldora.
     * Las etapas con sucesora propagan aquí sus píldoras; calidad no necesita
     * sobrescribirlo porque no tiene una etapa posterior.
     */
    protected void onLastWorkerFinished() {
        // Sin sucesora: comportamiento correcto para la última etapa.
    }
}
