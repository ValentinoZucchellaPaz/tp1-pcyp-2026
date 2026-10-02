package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

/** Espera bloqueante del hilo principal hasta que todas las órdenes sean terminales. */
public final class TerminationTracker {
    private final int totalOrders;
    private int completedOrders;
    private Throwable failure;

    public TerminationTracker(int totalOrders) {
        this.totalOrders = totalOrders;
    }

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

    public synchronized void awaitAll() throws InterruptedException {
        while (completedOrders < totalOrders && failure == null) {
            wait();
        }
    }

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
