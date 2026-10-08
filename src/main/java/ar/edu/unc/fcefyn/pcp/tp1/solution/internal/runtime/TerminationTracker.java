package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

/**
 * Permite al hilo principal esperar el resultado global de la simulación.
 *
 * <p>Cada orden que llega a un estado final invoca {@link #orderCompleted()}.
 * Si un worker falla, {@link WorkerGroup} registra el fallo aquí, lo que también
 * despierta al hilo principal para que cancele el resto del pipeline.</p>
 */
public final class TerminationTracker {
    private final int totalOrders;
    private int completedOrders;
    private Throwable failure;

    /**
     * @param totalOrders cantidad de órdenes que deben completar la simulación
     */
    public TerminationTracker(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    /**
     * Registra que una orden alcanzó un estado terminal y despierta al hilo
     * principal cuando se completaron todas las órdenes.
     */
    public synchronized void orderCompleted() {
        completedOrders++;
        if (completedOrders == totalOrders) {
            notifyAll();
        }
    }

    synchronized void fail(Throwable exception) {
        if (failure == null) {
            failure = exception;
            notifyAll();
        }
    }

    /**
     * Bloquea al hilo principal hasta que todas las órdenes terminen o falle un worker.
     *
     * @throws InterruptedException si el hilo principal es interrumpido durante la espera
     */
    public synchronized void awaitAll() throws InterruptedException {
        while (completedOrders < totalOrders && failure == null) {
            wait();
        }
    }

    /**
     * Relanza en el hilo principal el primer fallo registrado por un worker.
     *
     * @throws InterruptedException si un worker fue interrumpido sin que se haya
     *         iniciado una cancelación controlada
     * @throws RuntimeException si un worker terminó con una excepción de ejecución
     * @throws Error si un worker terminó con un error de la JVM o de la aplicación
     * @throws IllegalStateException si el worker produjo un tipo de fallo no esperado
     */
    public synchronized void rethrowFailure() throws InterruptedException {
        if (failure instanceof InterruptedException exception) {
            throw exception;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Fallo inesperado de un worker", failure);
        }
    }
}
