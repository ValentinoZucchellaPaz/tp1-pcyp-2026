package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Recurso compartido que administra la matriz y la disponibilidad de
 * impresoras.
 * El semáforo bloquea reservas temporariamente imposibles y el monitor protege
 * los cambios de estado de la matriz.
 */
public final class PrinterPool {
    private final Printer[][] printers;
    private final Semaphore availablePrinters;

    public PrinterPool(int rows, int columns) {
        printers = new Printer[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                printers[row][column] = new Printer("P-" + row + "-" + column);
            }
        }
        availablePrinters = new Semaphore(Math.multiplyExact(rows, columns));
    }

    /**
     * Espera una impresora disponible y la reserva exclusivamente para la orden
     * indicada.
     *
     * @throws InterruptedException  si el hilo es interrumpido mientras espera una impresora disponible
     * @throws IllegalStateException si el semáforo y el estado de las impresoras quedan inconsistentes
     */
    public String reserveFor(int orderId) throws InterruptedException {
        availablePrinters.acquire(); // consumo semaforo (impresora disponible)
        synchronized (this) { // protejo matriz de impresoras para que solo 1 hilo tome una
            for (Printer[] row : printers) {
                for (Printer printer : row) {
                    if (printer.state == PrinterState.AVAILABLE) {
                        printer.state = PrinterState.RESERVED;
                        printer.assignedOrderId = orderId;
                        printer.usageCount++;
                        return printer.id; // sale sin hacer release, xq la impresora está tomada
                    }
                }
            }
        }

        // Este punto indicaría una incoherencia entre el semáforo y la matriz.
        availablePrinters.release();
        throw new IllegalStateException("No hay impresora disponible para el permiso adquirido");
    }

    /** Libera una reserva y devuelve su permiso a los workers de asignación. */
    public void release(String id) {
        synchronized (this) {
            Printer printer = find(id);
            requireReserved(printer);
            printer.state = PrinterState.AVAILABLE;
            printer.assignedOrderId = null;
        }
        availablePrinters.release();
    }

    /** La impresora fallida no vuelve a liberar un permiso durante esta ejecución. */
    public synchronized void markOutOfService(String id) {
        Printer printer = find(id);
        requireReserved(printer);
        printer.state = PrinterState.OUT_OF_SERVICE;
        printer.assignedOrderId = null;
    }

    public synchronized List<PrinterSnapshot> snapshots() {
        List<PrinterSnapshot> snapshots = new ArrayList<>();
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                snapshots.add(new PrinterSnapshot(printer.id, printer.state, printer.usageCount,
                        printer.assignedOrderId));
            }
        }
        return snapshots;
    }

    /** Encuentra una impresora por su id, lanza excepcion si no encuentra id */
    private Printer find(String id) {
        for (Printer[] row : printers) {
            for (Printer printer : row) {
                if (printer.id.equals(id)) {
                    return printer;
                }
            }
        }
        throw new IllegalArgumentException("Impresora desconocida: " + id);
    }

    /** Lanza una excepcion si la impresora no está reservada (está libre) */
    private static void requireReserved(Printer printer) {
        if (printer.state != PrinterState.RESERVED) {
            throw new IllegalStateException("La impresora " + printer.id + " no estaba reservada");
        }
    }
}
