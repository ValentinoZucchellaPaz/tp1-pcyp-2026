package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

/**
 * Estado mutable de una orden.
 *
 * <p>Una orden es propiedad de un único worker desde que sale de una cola hasta
 * que se encola en la siguiente etapa o termina; por eso no necesita un lock propio.</p>
 */
public final class Order {
    private final int id;
    private OrderState state = OrderState.CREATED;
    private String printerId;
    private int assignmentCount;
    private int validationCount;
    private int printingCount;
    private int qualityControlCount;

    public Order(int id) {
        this.id = id;
    }

    public int id() { return id; }
    public OrderState state() { return state; }
    public String printerId() { return printerId; }
    public int assignmentCount() { return assignmentCount; }
    public int validationCount() { return validationCount; }
    public int printingCount() { return printingCount; }
    public int qualityControlCount() { return qualityControlCount; }

    public void assignPrinter(String id) { printerId = id; }
    public void recordAssignment() { assignmentCount++; }
    public void recordValidation() { validationCount++; }
    public void recordPrinting() { printingCount++; }
    public void recordQualityControl() { qualityControlCount++; }

    /** Cambia de estado y devuelve el anterior para registrarlo en el log. */
    public OrderState transitionTo(OrderState target) {
        OrderState previous = state;
        state = target;
        return previous;
    }
}
