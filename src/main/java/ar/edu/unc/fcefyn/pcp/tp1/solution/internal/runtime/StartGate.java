package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

/**
 * Barrera de arranque para los workers del pipeline.
 *
 * <p>Cada worker llama a {@link #awaitOpen()}, se registra como listo y queda
 * bloqueado. El hilo principal llama a {@link #openWhenAllWorkersAreReady()},
 * espera que todos se hayan registrado y los libera a la vez.</p>
 */
public final class StartGate {
    private final int expectedWorkers;
    private int waitingWorkers;
    private boolean open;

    /**
     * @param expectedWorkers cantidad total de workers que deben llegar a la barrera
     */
    public StartGate(int expectedWorkers) {
        this.expectedWorkers = expectedWorkers;
    }

    /**
     * Registra al worker actual como listo y espera la apertura de la barrera.
     *
     * @throws InterruptedException si el worker es interrumpido mientras espera
     *         la señal de inicio
     */
    public synchronized void awaitOpen() throws InterruptedException {
        waitingWorkers++;
        notifyAll();
        while (!open) {
            wait();
        }
    }

    /**
     * Espera que todos los workers se registren y abre la barrera para todos.
     * Este método debe ejecutarlo el hilo principal después de iniciar los workers.
     *
     * @throws InterruptedException si el hilo principal es interrumpido mientras
     *         espera que los workers lleguen a la barrera
     */
    public synchronized void openWhenAllWorkersAreReady() throws InterruptedException {
        while (waitingWorkers < expectedWorkers) {
            wait();
        }
        open = true;
        notifyAll();
    }
}
