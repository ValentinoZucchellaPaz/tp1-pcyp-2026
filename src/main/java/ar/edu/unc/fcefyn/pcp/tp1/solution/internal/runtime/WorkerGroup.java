package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

import java.util.ArrayList;
import java.util.List;

/** Centraliza el ciclo de vida y la cancelación de los threads del pipeline. */
public final class WorkerGroup {
    private final TerminationTracker termination;
    private final List<Thread> threads = new ArrayList<>();
    private boolean cancelling;

    public WorkerGroup(TerminationTracker termination) {
        this.termination = termination;
    }

    public void add(String name, InterruptibleTask task) {
        threads.add(new Thread(() -> runTask(task), name));
    }

    public void startAll() {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    public void joinAll() throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

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

    public boolean allTerminated() {
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

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
