package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

/**
 * Cuenta qué workers de una misma etapa consumieron su píldora de terminación.
 *
 * <p>El último worker recibe {@code true} de {@link #workerFinished()} y es el
 * único encargado de insertar las píldoras para la siguiente etapa. El método
 * es sincronizado para que dos workers no se consideren últimos a la vez.</p>
 */
public final class StageBarrier {
    private final int workerCount;
    private int finishedWorkers;

    /**
     * @param workerCount cantidad de workers que integran la etapa
     */
    public StageBarrier(int workerCount) {
        this.workerCount = workerCount;
    }

    /**
     * Registra la finalización de un worker.
     *
     * @return {@code true} solo para el último worker de la etapa
     */
    public synchronized boolean workerFinished() {
        finishedWorkers++;
        return finishedWorkers == workerCount;
    }
}
