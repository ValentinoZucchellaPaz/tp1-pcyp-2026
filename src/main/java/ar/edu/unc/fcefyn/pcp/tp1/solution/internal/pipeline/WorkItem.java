package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline;

import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.util.Objects;

/** Mensaje interno de una cola: orden real o píldora de terminación. */
final class WorkItem {
    private final Order order;

    private WorkItem(Order order) { // wtf constructor privado????
        this.order = order;
    }

    static WorkItem order(Order order) { return new WorkItem(Objects.requireNonNull(order)); }
    static WorkItem poison() { return new WorkItem(null); }
    boolean isPoison() { return order == null; }
    Order order() { return order; }
}

/**
 * Los métodos sin public, protected o private tienen acceso de paquete (package-private): solo se pueden usar desde clases del mismo paquete, en este caso pipeline. Eso mantiene WorkItem y sus operaciones como detalles internos de esa parte del programa 
 * 
 * 
 * El constructor es private para que nadie cree objetos WorkItem directamente. Así, la clase controla las dos formas válidas de crearlos mediante sus métodos fábrica: order() y poison()
 * 
 * order(order): crea un mensaje con una orden no nula.
 * poison(): crea el mensaje especial, que internamente se representa con order == null.
*/