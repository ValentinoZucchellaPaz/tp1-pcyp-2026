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

    /** 
     * Cambia de estado verificando que la transición sea válida según las reglas del pipeline.
     * Devuelve el estado anterior para registrarlo en el log de eventos.
     */
    public OrderState transitionTo(OrderState target) {
        validateTransition(this.state, target); // -> Actua como "guardia de seguridad"
        OrderState previous = this.state;       // Guarda estado previo
        this.state = target;                    // Aplica el estado nuevo
        return previous;
    }

    private void validateTransition(OrderState from, OrderState to) {
        boolean valid = switch (from) {
            case CREATED -> to == OrderState.WAITING_VALIDATION;
            case WAITING_VALIDATION -> to == OrderState.READY_TO_PRINT || to == OrderState.REJECTED;
            case READY_TO_PRINT -> to == OrderState.PRINTED || to == OrderState.PRINT_FAILED;
            case PRINTED -> to == OrderState.APPROVED || to == OrderState.DEFECTIVE;
            default -> false; // Ningún estado terminal (APPROVED, REJECTED, etc.) puede cambiar de estado
        };

        if (!valid) {
            throw new IllegalStateException("Transición inválida para la orden " + id + ": de " + from + " a " + to);
        }
    }
}