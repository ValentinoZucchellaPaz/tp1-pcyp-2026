package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pruebas de concurrencia y comportamiento de StageQueue")
class StageQueueTest {

    @Test
    @DisplayName("Debe preservar estrictamente el orden FIFO de las órdenes")
    void preservesFifoOrdering() throws InterruptedException {
        StageQueue queue = new StageQueue();
        int count = 50;

        for (int i = 1; i <= count; i++) {
            queue.offerOrder(new Order(i));
        }

        for (int i = 1; i <= count; i++) {
            WorkItem item = queue.take();
            assertFalse(item.isPoison());
            assertEquals(i, item.order().id());
        }
    }

    @Test
    @DisplayName("El consumidor debe bloquearse pasivamente si la cola está vacía y despertarse al insertar")
    void consumerBlocksWhenQueueIsEmpty() throws InterruptedException {
        // 1. Creo cola vacia
        StageQueue queue = new StageQueue();
        CountDownLatch threadStarted = new CountDownLatch(1);
        CountDownLatch itemReceived = new CountDownLatch(1);
        List<Order> received = new ArrayList<>();

        // 2. Lanzo un hilo consumidor en 2do plano que intenta hacer take()
        Thread consumer = new Thread(() -> {
            try {
                threadStarted.countDown();
                WorkItem item = queue.take(); // <-- Se queda esperando
                received.add(item.order());
                itemReceived.countDown();
            } catch (InterruptedException ignored) {
            }
        });

        consumer.start();
        assertTrue(threadStarted.await(1, TimeUnit.SECONDS));

        // Verificamos que todavía no recibió nada porque la cola está vacía
        Thread.sleep(100);
        assertTrue(received.isEmpty());

        // Ahora producimos una orden
        Order order = new Order(99);
        queue.offerOrder(order);

        // El consumidor debe despertarse de inmediato
        assertTrue(itemReceived.await(2, TimeUnit.SECONDS));
        consumer.join(2000);

        // Verifico que el consumidor se despertó y recibió la orden 99
        assertEquals(1, received.size());
        assertEquals(99, received.get(0).id());
    }

    @Test
    @DisplayName("Las píldoras de veneno deben encolarse después de las órdenes reales")
    void poisonPillsDeliveredAfterRealOrders() throws InterruptedException {
        StageQueue queue = new StageQueue();
        queue.offerOrder(new Order(1));
        queue.offerOrder(new Order(2));
        queue.offerPoison(2); // Dos píldoras para dos workers

        // Primeras dos deben ser órdenes
        WorkItem item1 = queue.take();
        assertFalse(item1.isPoison());
        assertEquals(1, item1.order().id());

        WorkItem item2 = queue.take();
        assertFalse(item2.isPoison());
        assertEquals(2, item2.order().id());

        // Las siguientes dos deben ser píldoras
        WorkItem poison1 = queue.take();
        assertTrue(poison1.isPoison());

        WorkItem poison2 = queue.take();
        assertTrue(poison2.isPoison());
    }
}
