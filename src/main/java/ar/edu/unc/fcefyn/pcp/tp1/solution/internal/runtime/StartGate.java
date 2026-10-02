package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

/**
 * Impide que una etapa procese trabajo hasta que todos los workers fueron iniciados.
 * Implementa una barrera de arranque con los mecanismos permitidos por la consigna.
 */
public final class StartGate {
    private final int expectedWorkers;
    private int waitingWorkers;
    private boolean open;

    public StartGate(int expectedWorkers) {
        this.expectedWorkers = expectedWorkers;
    }

    public synchronized void awaitOpen() throws InterruptedException {
        waitingWorkers++;
        notifyAll();
        while (!open) {
            wait();
        }
    }

    /** El hilo principal espera que todos estén listos y los libera en conjunto. */
    public synchronized void openWhenAllWorkersAreReady() throws InterruptedException {
        while (waitingWorkers < expectedWorkers) {
            wait();
        }
        open = true;
        notifyAll();
    }
}
