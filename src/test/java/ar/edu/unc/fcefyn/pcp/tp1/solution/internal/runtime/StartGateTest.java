package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de la barrera de largada StartGate")
class StartGateTest {

    @Test
    @DisplayName("Ningún worker debe continuar hasta que todos alcancen la compuerta y main la abra")
    void workersDoNotProceedUntilGateIsOpen() throws InterruptedException {
        int workerCount = 5;
        StartGate gate = new StartGate(workerCount);
        AtomicInteger passedWorkers = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(workerCount);

        for (int i = 0; i < workerCount; i++) {
            new Thread(() -> {
                try {
                    gate.awaitOpen();
                    passedWorkers.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    allDone.countDown();
                }
            }).start();
        }

        // Damos tiempo y verificamos que ninguno pasó porque main todavía no abrió
        Thread.sleep(150);
        assertEquals(0, passedWorkers.get());

        // Ahora main abre la compuerta
        gate.openWhenAllWorkersAreReady();

        // Todos deben pasar
        assertTrue(allDone.await(2, TimeUnit.SECONDS));
        assertEquals(workerCount, passedWorkers.get());
    }
}
