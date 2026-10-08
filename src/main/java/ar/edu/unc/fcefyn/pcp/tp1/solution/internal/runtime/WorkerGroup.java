package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Centraliza el ciclo de vida de los threads que ejecutan las etapas del pipeline.
 *
 * <p>Además de iniciarlos y esperarlos, captura los fallos de cada tarea. El
 * primer fallo se informa al {@link TerminationTracker} y se interrumpe a todos
 * los workers para detener el pipeline de manera coordinada.</p>
 */
public final class WorkerGroup {
    private final TerminationTracker termination;
    private final List<Thread> threads = new ArrayList<>();
    private boolean cancelling;

    /**
     * @param termination destino donde se comunica el primer fallo de un worker
     */
    public WorkerGroup(TerminationTracker termination) {
        this.termination = termination;
    }

    /**
     * Crea y registra un thread, pero todavía no lo inicia.
     *
     * @param name nombre del thread, usado para identificarlo en el log
     * @param task tarea que ejecutará el thread
     */
    public void add(String name, InterruptibleTask task) {
        threads.add(new Thread(() -> runTask(task), name));
    }

    /** Inicia todos los threads registrados. */
    public void startAll() {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    /**
     * Espera la finalización de todos los workers.
     *
     * @throws InterruptedException si el hilo que coordina la simulación es
     *         interrumpido durante la espera
     */
    public void joinAll() throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /**
     * Espera a todos los workers incluso si el hilo llamador es interrumpido.
     * Restaura la interrupción antes de volver para no perder esa señal.
     */
    public void joinAllUninterruptibly() {
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** @return {@code true} si ningún thread registrado sigue en ejecución */
    public boolean allTerminated() {
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Solicita la detención de todos los workers mediante interrupciones.
     * Los workers bloqueados en una cola, una barrera o una demora se despiertan
     * con {@link InterruptedException}.
     */
    public void cancel() {
        List<Thread> copy;
        synchronized (this) {
            cancelling = true;
            copy = new ArrayList<>(threads);
        }
        for (Thread thread : copy) {
            thread.interrupt();
        }
    }

    private synchronized boolean isCancelling() {
        return cancelling;
    }

    private void runTask(InterruptibleTask task) {
        try {
            task.run();
        } catch (InterruptedException exception) {
            if (!isCancelling()) {
                fail(exception);
            }
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error exception) {
            fail(exception);
        }
    }

    private void fail(Throwable exception) {
        termination.fail(exception);
        cancel();
    }

    @FunctionalInterface
    public interface InterruptibleTask {
        void run() throws InterruptedException;
    }
}
