package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Escritura serializada de eventos: secuencia y fila se confirman bajo el mismo lock. */
public final class EventLog {
    private static final String HEADER =
            "sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer";

    private final BufferedWriter writer;
    private final long startedAtNanos;
    private long sequence;

    public EventLog(Path path, long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
        try {
            writer = ResultFiles.newWriter(path);
            writer.write(HEADER);
            writer.newLine();
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo abrir eventos.csv", exception);
        }
    }

    public void created(int orderId) {
        append("main", orderId, "INITIALIZATION", "ORDER_CREATED", "", OrderState.CREATED.name(), "");
    }

    public void transition(String thread, int orderId, String stage, OrderState from, OrderState to,
            String printerId) {
        append(thread, orderId, stage, "ORDER_STATE_CHANGED", from.name(), to.name(), printerId);
    }

    private synchronized void append(String thread, int orderId, String stage, String event,
            String fromState, String toState, String printerId) {
        sequence++;
        try {
            writer.write(sequence + ";" + ResultFiles.elapsedMillis(startedAtNanos) + ";" + thread
                    + ";" + orderId + ";" + stage + ";" + event + ";" + fromState
                    + ";" + toState + ";" + ResultFiles.emptyWhenNull(printerId));
            writer.newLine();
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo escribir eventos.csv", exception);
        }
    }

    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo cerrar eventos.csv", exception);
        }
    }
}
