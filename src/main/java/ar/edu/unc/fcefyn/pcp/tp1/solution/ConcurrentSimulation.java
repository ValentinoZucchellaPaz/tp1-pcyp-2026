package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.Simulation;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationResult;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.model.Order;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.EventLog;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.output.ResultFiles;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.AssignmentWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.PrintingWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.QualityControlWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.StageBarrier;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.StageQueue;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.pipeline.ValidationWorker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.resource.PrinterPool;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.StartGate;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.TerminationTracker;
import ar.edu.unc.fcefyn.pcp.tp1.solution.internal.runtime.WorkerGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Punto de entrada obligatorio de la solución.
 *
 * <p>
 * Su única responsabilidad es construir el grafo del pipeline, iniciar los
 * workers y reunir los resultados. La lógica de cada etapa vive en su worker.
 * </p>
 */
public final class ConcurrentSimulation implements Simulation {

    @Override
    public SimulationResult execute(SimulationConfig config) throws InterruptedException {
        // 1. Validar la configuración y preparar el directorio de salida.
        Objects.requireNonNull(config, "config no puede ser null");
        long startedAtNanos = System.nanoTime();
        ResultFiles.createOutputDirectory(config.outputDirectory());

        // 2. Construir los recursos compartidos y las colas entre etapas.
        List<Order> orders = createOrders(config.totalOrders());
        PrinterPool printerPool = new PrinterPool(config.printerRows(), config.printerColumns());
        StageQueue createdQueue = new StageQueue();
        StageQueue validationQueue = new StageQueue();
        StageQueue printingQueue = new StageQueue();
        StageQueue qualityQueue = new StageQueue();
        TerminationTracker termination = new TerminationTracker(config.totalOrders());
        WorkerGroup workers = new WorkerGroup(termination);
        StartGate startGate = new StartGate(totalWorkerCount(config));
        EventLog eventLog = null;

        try {
            // 3. Cargar las órdenes iniciales. Las píldoras quedan detrás de ellas
            // en la FIFO, por lo que los workers las recibirán al terminar el trabajo.
            eventLog = new EventLog(config.outputDirectory().resolve("eventos.csv"), startedAtNanos);
            initializeOrders(orders, createdQueue, eventLog);
            createdQueue.offerPoison(config.assignmentThreads());

            // 4. Registrar los threads de cada etapa; add() aún no los inicia.
            // El orden refleja el recorrido: asignación -> validación -> impresión -> calidad.
            registerAssignmentWorkers(workers, startGate, createdQueue, validationQueue,
                    printerPool, eventLog, config);
            registerValidationWorkers(workers, startGate, validationQueue, printingQueue,
                    printerPool, termination, eventLog, config);
            registerPrintingWorkers(workers, startGate, printingQueue, qualityQueue,
                    printerPool, termination, eventLog, config);
            registerQualityWorkers(workers, startGate, qualityQueue, termination, eventLog, config);

            workers.startAll();
            // 5. Todos los threads llegan a StartGate y comienzan en conjunto.
            startGate.openWhenAllWorkersAreReady();

            // 6. Esperar que todas las órdenes finalicen o que un worker informe un fallo.
            termination.awaitAll();
            termination.rethrowFailure();

            // 7. En una ejecución normal, las píldoras ya hicieron terminar a todos los workers.
            workers.joinAll();

            // 8. Cerrar el log y construir los archivos y el resultado final.
            eventLog.close();
            eventLog = null;
            return buildResult(config, orders, printerPool, workers, startedAtNanos);
        } catch (InterruptedException exception) {
            // La cancelación despierta a quienes estén bloqueados y luego se espera su cierre.
            workers.cancel();
            workers.joinAllUninterruptibly();
            throw exception;
        } catch (RuntimeException | Error exception) {
            // Un fallo de infraestructura o de un worker también cancela todo el pipeline.
            workers.cancel();
            workers.joinAllUninterruptibly();
            throw exception;
        } finally {
            if (eventLog != null) {
                eventLog.close();
            }
        }
    }

    private static void registerAssignmentWorkers(WorkerGroup workers, StartGate startGate,
            StageQueue input, StageQueue output, PrinterPool printers, EventLog eventLog,
            SimulationConfig config) {
        // El último worker que termina propaga las píldoras a la etapa siguiente.
        StageBarrier barrier = new StageBarrier(config.assignmentThreads());
        for (int number = 1; number <= config.assignmentThreads(); number++) {
            workers.add("assignment-" + number, new AssignmentWorker(startGate, input, output,
                    barrier, config.validationThreads(), config.assignmentDelayMillis(), eventLog, printers));
        }
    }

    private static void registerValidationWorkers(WorkerGroup workers, StartGate startGate,
            StageQueue input, StageQueue output, PrinterPool printers, TerminationTracker termination,
            EventLog eventLog, SimulationConfig config) {
        // Una sola vez, el último worker de validación cierra la entrada de impresión.
        StageBarrier barrier = new StageBarrier(config.validationThreads());
        for (int number = 1; number <= config.validationThreads(); number++) {
            workers.add("validation-" + number, new ValidationWorker(startGate, input, output,
                    barrier, config.printingThreads(), config.validationDelayMillis(), eventLog,
                    printers, termination, config));
        }
    }

    private static void registerPrintingWorkers(WorkerGroup workers, StartGate startGate,
            StageQueue input, StageQueue output, PrinterPool printers, TerminationTracker termination,
            EventLog eventLog, SimulationConfig config) {
        // Una sola vez, el último worker de impresión cierra la entrada de calidad.
        StageBarrier barrier = new StageBarrier(config.printingThreads());
        for (int number = 1; number <= config.printingThreads(); number++) {
            workers.add("printing-" + number, new PrintingWorker(startGate, input, output,
                    barrier, config.qualityControlThreads(), config.printingDelayMillis(), eventLog,
                    printers, termination, config));
        }
    }

    private static void registerQualityWorkers(WorkerGroup workers, StartGate startGate,
            StageQueue input, TerminationTracker termination, EventLog eventLog,
            SimulationConfig config) {
        // En la última etapa la barrera solo contabiliza cierres: no hay cola posterior.
        StageBarrier barrier = new StageBarrier(config.qualityControlThreads());
        for (int number = 1; number <= config.qualityControlThreads(); number++) {
            workers.add("quality-" + number, new QualityControlWorker(startGate, input, barrier,
                    config.qualityControlDelayMillis(), eventLog, termination, config));
        }
    }

    private static SimulationResult buildResult(SimulationConfig config, List<Order> orders,
            PrinterPool printerPool, WorkerGroup workers, long startedAtNanos) {
        // A esta altura los workers terminaron; las instantáneas son consistentes.
        long durationMillis = ResultFiles.elapsedMillis(startedAtNanos);
        var totals = ResultFiles.totalsByState(orders);
        int remaining = ResultFiles.remainingIntermediateOrders(orders);
        boolean allThreadsTerminated = workers.allTerminated();
        ResultFiles.writeOrders(config.outputDirectory().resolve("elementos.csv"), orders);
        ResultFiles.writeSummary(config.outputDirectory().resolve("resumen.properties"),
                config.totalOrders(), totals, durationMillis, allThreadsTerminated, remaining);

        return new SimulationResult(config.totalOrders(), config.totalOrders() - remaining, totals,
                ResultFiles.orderSnapshots(orders), printerPool.snapshots(), durationMillis,
                allThreadsTerminated);
    }

    private static List<Order> createOrders(int totalOrders) {
        List<Order> orders = new ArrayList<>(totalOrders);
        for (int id = 1; id <= totalOrders; id++) {
            orders.add(new Order(id));
        }
        return orders;
    }

    private static void initializeOrders(List<Order> orders, StageQueue createdQueue, EventLog eventLog) {
        for (Order order : orders) {
            eventLog.created(order.id());
            createdQueue.offerOrder(order);
        }
    }

    private static int totalWorkerCount(SimulationConfig config) {
        return Math.addExact(
                Math.addExact(config.assignmentThreads(), config.validationThreads()),
                Math.addExact(config.printingThreads(), config.qualityControlThreads()));
    }
}
