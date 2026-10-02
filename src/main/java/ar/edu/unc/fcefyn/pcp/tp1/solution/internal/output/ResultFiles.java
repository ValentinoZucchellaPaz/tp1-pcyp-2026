package ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Construye snapshots y escribe los artefactos de salida al finalizar la simulación. */
public final class ResultFiles {
    private ResultFiles() {
    }

    public static void createOutputDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo crear el directorio de salida", exception);
        }
    }

    public static void writeOrders(Path path, List<Order> orders) {
        try (BufferedWriter writer = newWriter(path)) {
            writer.write("orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount");
            writer.newLine();
            for (Order order : orders) {
                writer.write(order.id() + ";" + order.state() + ";" + emptyWhenNull(order.printerId())
                        + ";" + order.assignmentCount() + ";" + order.validationCount() + ";"
                        + order.printingCount() + ";" + order.qualityControlCount());
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo escribir elementos.csv", exception);
        }
    }

    public static void writeSummary(Path path, int totalOrders, Map<OrderState, Integer> totals,
            long durationMillis, boolean allThreadsTerminated, int remainingIntermediateOrders) {
        Properties summary = new Properties();
        summary.setProperty("totalOrders", Integer.toString(totalOrders));
        summary.setProperty("processedOrders", Integer.toString(totalOrders - remainingIntermediateOrders));
        summary.setProperty("approvedOrders", Integer.toString(totals.get(OrderState.APPROVED)));
        summary.setProperty("rejectedOrders", Integer.toString(totals.get(OrderState.REJECTED)));
        summary.setProperty("printFailedOrders", Integer.toString(totals.get(OrderState.PRINT_FAILED)));
        summary.setProperty("defectiveOrders", Integer.toString(totals.get(OrderState.DEFECTIVE)));
        summary.setProperty("durationMillis", Long.toString(durationMillis));
        summary.setProperty("allThreadsTerminated", Boolean.toString(allThreadsTerminated));
        summary.setProperty("remainingIntermediateOrders", Integer.toString(remainingIntermediateOrders));
        try (BufferedWriter writer = newWriter(path)) {
            summary.store(writer, null);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo escribir resumen.properties", exception);
        }
    }

    public static List<OrderSnapshot> orderSnapshots(List<Order> orders) {
        List<OrderSnapshot> snapshots = new ArrayList<>(orders.size());
        for (Order order : orders) {
            snapshots.add(new OrderSnapshot(order.id(), order.state(), order.printerId(),
                    order.assignmentCount(), order.validationCount(), order.printingCount(),
                    order.qualityControlCount()));
        }
        return snapshots;
    }

    public static Map<OrderState, Integer> totalsByState(List<Order> orders) {
        Map<OrderState, Integer> totals = new EnumMap<>(OrderState.class);
        for (OrderState state : OrderState.values()) {
            totals.put(state, 0);
        }
        for (Order order : orders) {
            totals.put(order.state(), totals.get(order.state()) + 1);
        }
        return totals;
    }

    public static int remainingIntermediateOrders(List<Order> orders) {
        int remaining = 0;
        for (Order order : orders) {
            if (!order.state().isTerminal()) {
                remaining++;
            }
        }
        return remaining;
    }

    static BufferedWriter newWriter(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    static String emptyWhenNull(String value) {
        return value == null ? "" : value;
    }

    public static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
