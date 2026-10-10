package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de seguimiento de terminación con TerminationTracker")
class TerminationTrackerTest {

    @Test
    @DisplayName("awaitAll debe bloquearse hasta que se completen todas las órdenes esperadas")
    void awaitAllBlocksUntilTotalOrdersCompleted() throws InterruptedException {
        int totalOrders = 10;
        TerminationTracker tracker = new TerminationTracker(totalOrders);
        AtomicBoolean unblocked = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        Thread coordinator = new Thread(() -> {
            try {
                tracker.awaitAll();
                unblocked.set(true);
            } catch (InterruptedException ignored) {
            } finally {
                done.countDown();
            }
        });

        coordinator.start();

        // Completamos 9 órdenes (falta 1)
        for (int i = 0; i < totalOrders - 1; i++) {
            tracker.orderCompleted();
        }

        Thread.sleep(100);
        assertFalse(unblocked.get(), "No debería haberse desbloqueado con órdenes pendientes");

        // Completamos la última orden
        tracker.orderCompleted();

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(unblocked.get(), "Debería haberse desbloqueado al alcanzar el total");
    }

    @Test
    @DisplayName("Si un worker falla, awaitAll debe desbloquearse y rethrowFailure debe relanzar el error")
    void workerFailureUnblocksAndRethrows() throws InterruptedException {
        TerminationTracker tracker = new TerminationTracker(5);
        RuntimeException expectedException = new RuntimeException("Fallo simulado en worker");

        new Thread(() -> {
            try {
                Thread.sleep(50);
                tracker.fail(expectedException);
            } catch (InterruptedException ignored) {
            }
        }).start();

        tracker.awaitAll();

        RuntimeException thrown = assertThrows(RuntimeException.class, tracker::rethrowFailure);
        assertTrue(thrown.getMessage().contains("Fallo simulado en worker"));
    }
}
