package ar.edu.unc.fcefyn.pcp.tp1.grouptests;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Pruebas de máquina de estados y transiciones de Order")
class OrderStateTransitionTest {

    @Test
    @DisplayName("Una orden recién creada debe iniciar en estado CREATED")
    void orderStartsInCreatedState() {
        Order order = new Order(1);
        assertEquals(OrderState.CREATED, order.state());
        assertEquals(1, order.id());
    }

    @Test
    @DisplayName("Transiciones válidas en el camino exitoso completo")
    void validHappyPathTransitions() {
        Order order = new Order(1);

        assertEquals(OrderState.CREATED, order.transitionTo(OrderState.WAITING_VALIDATION));
        assertEquals(OrderState.WAITING_VALIDATION, order.state());

        assertEquals(OrderState.WAITING_VALIDATION, order.transitionTo(OrderState.READY_TO_PRINT));
        assertEquals(OrderState.READY_TO_PRINT, order.state());

        assertEquals(OrderState.READY_TO_PRINT, order.transitionTo(OrderState.PRINTED));
        assertEquals(OrderState.PRINTED, order.state());

        assertEquals(OrderState.PRINTED, order.transitionTo(OrderState.APPROVED));
        assertEquals(OrderState.APPROVED, order.state());
    }

    @Test
    @DisplayName("Transición válida hacia rechazo en validación")
    void validRejectionTransition() {
        Order order = new Order(2);
        order.transitionTo(OrderState.WAITING_VALIDATION);
        order.transitionTo(OrderState.REJECTED);
        assertEquals(OrderState.REJECTED, order.state());
    }

    @Test
    @DisplayName("Transición válida hacia fallo de impresión")
    void validPrintFailedTransition() {
        Order order = new Order(3);
        order.transitionTo(OrderState.WAITING_VALIDATION);
        order.transitionTo(OrderState.READY_TO_PRINT);
        order.transitionTo(OrderState.PRINT_FAILED);
        assertEquals(OrderState.PRINT_FAILED, order.state());
    }

    @Test
    @DisplayName("Transición válida hacia pieza defectuosa en control de calidad")
    void validDefectiveTransition() {
        Order order = new Order(4);
        order.transitionTo(OrderState.WAITING_VALIDATION);
        order.transitionTo(OrderState.READY_TO_PRINT);
        order.transitionTo(OrderState.PRINTED);
        order.transitionTo(OrderState.DEFECTIVE);
        assertEquals(OrderState.DEFECTIVE, order.state());
    }

    @Test
    @DisplayName("No debe permitir saltear etapas (ej. de CREATED directo a READY_TO_PRINT o APPROVED)")
    void invalidSkippingStagesTransitions() {
        Order order1 = new Order(5);
        assertThrows(IllegalStateException.class, () -> order1.transitionTo(OrderState.READY_TO_PRINT));

        Order order2 = new Order(6);
        assertThrows(IllegalStateException.class, () -> order2.transitionTo(OrderState.APPROVED));
    }

    @Test
    @DisplayName("No debe permitir transicionar desde un estado terminal (invariante de finitud)")
    void cannotTransitionFromTerminalStates() {
        Order rejected = new Order(7);
        rejected.transitionTo(OrderState.WAITING_VALIDATION);
        rejected.transitionTo(OrderState.REJECTED);
        assertThrows(IllegalStateException.class, () -> rejected.transitionTo(OrderState.READY_TO_PRINT));

        Order approved = new Order(8);
        approved.transitionTo(OrderState.WAITING_VALIDATION);
        approved.transitionTo(OrderState.READY_TO_PRINT);
        approved.transitionTo(OrderState.PRINTED);
        approved.transitionTo(OrderState.APPROVED);
        assertThrows(IllegalStateException.class, () -> approved.transitionTo(OrderState.CREATED));
    }
}
