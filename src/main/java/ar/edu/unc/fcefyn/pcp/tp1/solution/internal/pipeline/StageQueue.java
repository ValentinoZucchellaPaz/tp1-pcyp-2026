package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;

/**
 * Bandeja FIFO entre dos etapas. El semáforo bloquea a consumidores sin espera
 * activa y el monitor protege la deque, que no es thread-safe.
 */
public final class StageQueue {
    private final ArrayDeque<WorkItem> items = new ArrayDeque<>();
    private final Semaphore availableItems = new Semaphore(0);

    public void offerOrder(Order order) {
        offer(WorkItem.order(order));
    }

    public void offerPoison(int workerCount) {
        for (int index = 0; index < workerCount; index++) {
            offer(WorkItem.poison());
        }
    }

    WorkItem take() throws InterruptedException {
        availableItems.acquire();
        synchronized (items) {
            return items.removeFirst();
        }
    }

    private void offer(WorkItem item) {
        synchronized (items) {
            items.addLast(item);
        }
        // El ítem se inserta antes de habilitar al consumidor.
        availableItems.release();
    }
}
