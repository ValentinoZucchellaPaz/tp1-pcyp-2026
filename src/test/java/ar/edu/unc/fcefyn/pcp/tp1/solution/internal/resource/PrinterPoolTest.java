package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de concurrencia y exclusión mutua de PrinterPool")
class PrinterPoolTest {

    //Verifica que luego de terminar los IDs de las impresoras sean unicos 
    //Demuestra que la SC synchronized de PrinterPool funciona 
    @Test
    @DisplayName("Reservas concurrentes garantizan exclusión mutua (no se asigna la misma impresora a dos órdenes)")
    void concurrentReservationsAreExclusive() throws InterruptedException {
        int rows = 3;
        int cols = 4;
        int totalPrinters = rows * cols; // 12 impresoras
        PrinterPool pool = new PrinterPool(rows, cols);

        List<String> assignedPrinters = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalPrinters);

        for (int i = 1; i <= totalPrinters; i++) {
            final int orderId = i;
            new Thread(() -> {
                try {
                    startSignal.await();
                    String printerId = pool.reserveFor(orderId);
                    assignedPrinters.add(printerId);
                } catch (InterruptedException ignored) {
                } finally {
                    doneSignal.countDown();
                }
            }).start();
        }

        // Liberamos a todos los hilos a la vez para que compitan
        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));

        assertEquals(totalPrinters, assignedPrinters.size());
        // Cada ID debe ser único (cero colisiones)
        Set<String> uniquePrinters = new HashSet<>(assignedPrinters);
        assertEquals(totalPrinters, uniquePrinters.size(), "Hubo impresoras asignadas por duplicado");
    }

    @Test
    @DisplayName("Liberar una impresora la devuelve al estado AVAILABLE y reinicia la orden asignada")
    void releasingPrinterRestoresAvailableState() throws InterruptedException {
        PrinterPool pool = new PrinterPool(2, 2);
        String printerId = pool.reserveFor(42);

        pool.release(printerId);

        PrinterSnapshot snapshot = pool.snapshots().stream()
                .filter(p -> p.id().equals(printerId))
                .findFirst()
                .orElseThrow();

        assertEquals(PrinterState.AVAILABLE, snapshot.state());
        assertNull(snapshot.assignedOrderId());
        assertEquals(1, snapshot.usageCount());
    }

    //Verifica que luego de marcar una impresora como fuera de servicio el hilo debe quedar
    //bloqueado (ya q está rota y no libera permiso)
    @Test
    @DisplayName("Marcar fuera de servicio retira la impresora definitivamente sin reutilizarla")
    void markOutOfServiceRetiresPrinter() throws InterruptedException {
        PrinterPool pool = new PrinterPool(1, 1);
        String printerId = pool.reserveFor(10);

        pool.markOutOfService(printerId);

        PrinterSnapshot snapshot = pool.snapshots().get(0);
        assertEquals(PrinterState.OUT_OF_SERVICE, snapshot.state());
        assertNull(snapshot.assignedOrderId());

        // Al tener 1 sola impresora y estar OUT_OF_SERVICE, intentar reservar de nuevo debe bloquearse
        CountDownLatch attempted = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                pool.reserveFor(11); // Bloqueado, no hay impresoras disponibles
            } catch (InterruptedException ignored) {
                attempted.countDown();
            }
        });
        t.start();
        Thread.sleep(150);
        t.interrupt();
        assertTrue(attempted.await(1, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Intentar liberar una impresora no reservada o desconocida debe fallar")
    void releaseErrorsOnInvalidState() {
        PrinterPool pool = new PrinterPool(2, 2);
        // Intentar liberar una que está AVAILABLE
        assertThrows(IllegalStateException.class, () -> pool.release("P-0-0"));
        // Intentar liberar un ID inválido
        assertThrows(IllegalArgumentException.class, () -> pool.release("INVALID-ID"));
    }
}
