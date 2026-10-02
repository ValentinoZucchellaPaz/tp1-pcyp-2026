package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

/** Datos de una impresora; PrinterPool es su único propietario. */
final class Printer {
    final String id;
    PrinterState state = PrinterState.AVAILABLE;
    int usageCount;
    Integer assignedOrderId;

    Printer(String id) {
        this.id = id;
    }
}
