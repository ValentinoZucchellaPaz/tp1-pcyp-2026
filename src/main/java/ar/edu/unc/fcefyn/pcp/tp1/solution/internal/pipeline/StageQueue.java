package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;

/**
 * Cola FIFO que comunica dos etapas del pipeline.
 *
 * <p>Cada elemento es un {@link WorkItem}: puede transportar una {@code Order}
 * para procesar o una píldora de terminación. Una píldora no representa una
 * orden; indica a un worker consumidor que ya no recibirá más trabajo y debe
 * finalizar.</p>
 *
 * <p>La {@link ArrayDeque} {@code items} no es segura para concurrencia, por lo que todos sus
 * accesos se protegen con su monitor -> synchronized(items). </p>
 * 
 * <p>El semáforo {@code availableItems} mantiene un permiso por cada
 * elemento insertado: evita la espera activa, bloqueando a los consumidores
 * hasta que haya trabajo disponible.</p>
 */
public final class StageQueue {
    private final ArrayDeque<WorkItem> items = new ArrayDeque<>();
    // Su cantidad de permisos debe coincidir con la cantidad de elementos en items.
    private final Semaphore availableItems = new Semaphore(0);

    /**
     * Inserta una orden al final de la cola para que la procese la etapa siguiente.
     *
     * @param order orden que se envía a la etapa siguiente
     * @throws NullPointerException si {@code order} es {@code null}, porque un
     *         {@code null} está reservado internamente para representar una píldora
     *         de terminación
     */
    public void offerOrder(Order order) {
        offer(WorkItem.order(order));
    }

    /**
     * Inserta una píldora de terminación por cada worker consumidor de esta cola.
     *
     * <p>Cada worker debe consumir exactamente una píldora antes de terminar. Por
     * eso {@code workerCount} debe ser igual a la cantidad de workers que consumen
     * esta cola; este método no valida dicha relación.</p>
     *
     * @param workerCount cantidad de workers consumidores que deben finalizar
     */
    public void offerPoison(int workerCount) {
        for (int index = 0; index < workerCount; index++) {
            offer(WorkItem.poison());
        }
    }

    /**
     * Extrae el siguiente mensaje de la cola, esperando si todavía no hay ninguno.
     *
     * <p>Tras adquirir un permiso, el elemento ya fue insertado en la deque: el
     * productor agrega primero el elemento y recién después publica su permiso.</p>
     *
     * @return la siguiente orden o una píldora de terminación
     * @throws InterruptedException si el hilo es interrumpido mientras espera que
     *         un productor inserte un elemento
     */
    WorkItem take() throws InterruptedException {
        availableItems.acquire();
        synchronized (items) {
            return items.removeFirst();
        }
    }

    /**  Inserta y publica un mensaje respetando el orden necesario para los consumidores. */
    private void offer(WorkItem item) {
        synchronized (items) {
            items.addLast(item);
        }
        // El elemento se inserta antes de habilitar a un consumidor.
        availableItems.release();
    }
}
