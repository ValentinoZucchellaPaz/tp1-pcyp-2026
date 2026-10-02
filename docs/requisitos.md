# Especificación de requisitos — TP1 PCP 2026

Derivada de `ENUNCIADO.md`. Cada fila es trazable: **ID → Test → Clase**.
Las columnas `Test` y `Clase` se completan durante la implementación (`—` = pendiente).
Estados de orden intermedios: `CREATED`, `WAITING_VALIDATION`, `READY_TO_PRINT`, `PRINTED`. Terminales: `APPROVED`, `REJECTED`, `PRINT_FAILED`, `DEFECTIVE`.

## GEN — General y build

| ID | Requisito | Test | Clase |
|---|---|---|---|
| GEN-01 | Java 21 y Maven. | — | `pom.xml` |
| GEN-02 | `mvn clean test` corre la suite; `mvn clean package` genera `target/tp1.jar`; `java -jar target/tp1.jar` ejecuta la simulación. | — | — |
| GEN-03 | Funciona desde un clon limpio, sin configuración adicional ni argumentos ni IDE. | — | — |
| GEN-04 | Todas las etapas se inician al comienzo; no se ejecuta una etapa completa antes de iniciar la siguiente. | — | — |
| GEN-05 | Se procesan todas las órdenes, con estados consistentes, y se finaliza sin hilos activos. | — | — |

## API — Contrato con la cátedra

| ID | Requisito | Test | Clase |
|---|---|---|---|
| API-01 | `ar.edu.unc.fcefyn.pcp.tp1.solution.ConcurrentSimulation` implementa `Simulation` y tiene constructor público sin parámetros. | — | — |
| API-02 | `SimulationResult execute(SimulationConfig) throws InterruptedException`. | — | — |
| API-03 | No se modifica ninguna clase del paquete `ar.edu.unc.fcefyn.pcp.tp1.api`. | — | — |
| API-04 | Clases y paquetes internos de la solución son libres. | — | — |

## CFG — Configuración

| ID | Requisito | Test | Clase |
|---|---|---|---|
| CFG-01 | `Main` carga siempre `config/tp1.properties`. | — | — |
| CFG-02 | `Simulation.execute(config)` usa exclusivamente la config recibida; no relee el archivo. | — | — |
| CFG-03 | Sin valores hardcodeados: órdenes, impresoras, hilos, probabilidades, tiempos, rutas. | — | — |
| CFG-04 | Config oficial: 500 órdenes, matriz de 200 impresoras, hilos 3/2/3/2 (asignación/validación/impresión/calidad), 85 % modelos válidos, 90 % impresiones exitosas, 95 % aprobadas. | — | — |
| CFG-05 | Las configs oficial y de evaluación garantizan que no se agotan las impresoras; no hay que resolver el agotamiento. | — | — |

## MOD — Modelo

| ID | Requisito | Test | Clase |
|---|---|---|---|
| MOD-01 | Impresora: id único, posición en la matriz, estado, contador de usos, orden asignada (si corresponde). | — | — |
| MOD-02 | Estados de impresora: `AVAILABLE`, `RESERVED`, `OUT_OF_SERVICE` (no reutilizable en la ejecución). | — | — |
| MOD-03 | Toda orden comienza en `CREATED`. | — | — |
| MOD-04 | Toda orden termina en exactamente uno de: `APPROVED`, `REJECTED`, `PRINT_FAILED`, `DEFECTIVE`. | — | — |
| MOD-05 | Ids de orden consecutivos de `1` a `totalOrders`. | — | — |

## STG — Etapas

| ID | Requisito | Test | Clase |
|---|---|---|---|
| STG-01 | Etapa 1 (asignación): cada hilo toma una orden `CREATED`, obtiene una impresora disponible, la reserva en exclusiva, incrementa su contador de usos y la asocia a la orden. | — | — |
| STG-02 | Etapa 1: transiciones Orden `CREATED→WAITING_VALIDATION`, Impresora `AVAILABLE→RESERVED`. | — | — |
| STG-03 | Etapa 2 (validación): toma orden `WAITING_VALIDATION` y decide con `OutcomeDecider.isModelValid`. | — | — |
| STG-04 | Etapa 2, modelo válido: Orden `→READY_TO_PRINT`; la impresora sigue reservada. | — | — |
| STG-05 | Etapa 2, modelo inválido: Orden `→REJECTED`, Impresora `RESERVED→AVAILABLE`. | — | — |
| STG-06 | Etapa 3 (impresión): toma orden `READY_TO_PRINT` y decide con `OutcomeDecider.isPrintSuccessful`. | — | — |
| STG-07 | Etapa 3, éxito: Orden `→PRINTED`, Impresora `RESERVED→AVAILABLE`. | — | — |
| STG-08 | Etapa 3, falla: Orden `→PRINT_FAILED`, Impresora `RESERVED→OUT_OF_SERVICE`. | — | — |
| STG-09 | Etapa 4 (calidad): toma orden `PRINTED`, decide con `OutcomeDecider.isQualityApproved`; `→APPROVED` o `→DEFECTIVE`. No usa impresoras. | — | — |
| STG-10 | Cantidad de hilos por etapa según config (oficial 3/2/3/2). | — | — |

## OUT — Determinismo

| ID | Requisito | Test | Clase |
|---|---|---|---|
| OUT-01 | Validación, impresión y calidad se deciden exclusivamente con `OutcomeDecider`. | — | — |
| OUT-02 | Misma configuración ⇒ mismos estados finales por orden, independiente del interleaving. | — | — |
| OUT-03 | No se exige igualdad de: impresora asignada, contadores individuales, tiempos, orden global de eventos. | — | — |

## INV — Invariantes

| ID | Invariante | Test | Clase |
|---|---|---|---|
| INV-01 | Cada orden existe en un solo estado a la vez. | — | — |
| INV-02 | Cada orden alcanza exactamente un estado terminal. | — | — |
| INV-03 | Ninguna orden se valida, imprime o audita más veces que lo que establece su recorrido. | — | — |
| INV-04 | Una impresora no está asignada a dos órdenes simultáneamente. | — | — |
| INV-05 | Toda orden en `WAITING_VALIDATION` o `READY_TO_PRINT` tiene una impresora reservada. | — | — |
| INV-06 | Una orden terminal no mantiene una impresora reservada. | — | — |
| INV-07 | Una impresora `OUT_OF_SERVICE` no se reutiliza. | — | — |
| INV-08 | La suma de contadores de uso de impresoras = cantidad total de asignaciones. | — | — |
| INV-09 | Al finalizar no quedan órdenes en estados intermedios. | — | — |
| INV-10 | Al finalizar no quedan impresoras `RESERVED`. | — | — |

## SYN — Restricciones de sincronización (solo `src/main/java`)

| ID | Requisito | Test | Clase |
|---|---|---|---|
| SYN-01 | Permitido: `Thread`, `Runnable`, `synchronized`, `Lock`, `ReentrantLock`, `Semaphore`, `Executor`, `ExecutorService`, `Executors`, `ThreadPoolExecutor`, `Object.wait/notify/notifyAll`, colecciones comunes no thread-safe. | `ForbiddenApiScanTest` | — |
| SYN-02 | Prohibido: otros tipos de `java.util.concurrent` (y wildcards), colecciones concurrentes, `Condition`, ejecutores programados/`ForkJoinPool`, `BlockingQueue`, barreras/latches, atómicos, `Collections.synchronized*`, `Vector`/`Hashtable` como sincronización, streams paralelos, librerías externas de producción. | `ForbiddenApiScanTest` | — |
| SYN-03 | Prohibida la espera activa. | `ForbiddenApiScanTest` (parcial) + revisión | — |
| SYN-04 | Las demoras no se usan como mecanismo de sincronización. | revisión | — |
| SYN-05 | Ejecutores (opcionales) solo para correr workers y esperar su fin. | revisión | — |
| SYN-06 | JUnit solo en `src/test/java`. | — | `pom.xml` |

## DLY — Demoras

| ID | Requisito | Test | Clase |
|---|---|---|---|
| DLY-01 | Cada hilo aplica la demora configurada una vez por cada orden efectivamente procesada en su etapa. | — | — |
| DLY-02 | Config oficial: 30–45 s en el entorno de referencia. | medición | — |
| DLY-03 | Debe funcionar con demoras nulas o reducidas. | — | — |

## TRM — Terminación

| ID | Requisito | Test | Clase |
|---|---|---|---|
| TRM-01 | La simulación termina cuando todas las órdenes alcanzaron un estado terminal. | — | — |
| TRM-02 | Antes de retornar de `execute`: órdenes procesadas, registros intermedios vacíos, todos los hilos creados finalizados, sin impresoras reservadas, archivos escritos y cerrados. | — | — |
| TRM-03 | Prohibido `Thread.stop`, `System.exit` o equivalentes. | `ForbiddenApiScanTest` | — |

## FIL — Archivos de salida

| ID | Requisito | Test | Clase |
|---|---|---|---|
| FIL-01 | Todos los archivos se escriben dentro de `output.directory`. | — | — |

### `eventos.csv`

| ID | Requisito | Test | Clase |
|---|---|---|---|
| EVT-01 | Cabecera exacta: `sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer`. Separador `;`. | — | — |
| EVT-02 | Un evento por cada cambio de estado de orden. No se registran cambios de estado de impresoras. | — | — |
| EVT-03 | `sequence` creciente, única, y respeta el orden de escritura. | — | — |
| EVT-04 | `stage` ∈ `INITIALIZATION`, `ASSIGNMENT`, `VALIDATION`, `PRINTING`, `QUALITY_CONTROL`. | — | — |
| EVT-05 | `event` ∈ `ORDER_CREATED` (creación inicial), `ORDER_STATE_CHANGED` (toda otra transición). | — | — |
| EVT-06 | Por cada orden, una fila `ORDER_CREATED` con `fromState` y `printer` vacíos y `toState=CREATED` (ejemplo: stage `INITIALIZATION`, thread `main`). | — | — |
| EVT-07 | En transiciones: `fromState`/`toState` son `OrderState`; `printer` = id histórico de la impresora usada, o vacío si no corresponde; `thread` = hilo que hizo la transición; `elapsedMs` ≥ 0. | — | — |
| EVT-08 | Las filas se escriben en el mismo orden en que las transiciones quedan confirmadas (registro dentro de la sección crítica de la transición). | — | — |

### `elementos.csv`

| ID | Requisito | Test | Clase |
|---|---|---|---|
| ELM-01 | Cabecera exacta: `orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount`. | — | — |
| ELM-02 | Una fila por orden, ordenada por `orderId`. | — | — |
| ELM-03 | `printer` conserva el id de la impresora usada como dato histórico, aun liberada. | — | — |
| ELM-04 | Una impresora liberada informa `assignedOrderId` vacío en su `PrinterSnapshot`. | — | — |
| ELM-05 | Los contadores por etapa reflejan las ejecuciones reales (ver INV-03). | — | — |

### `resumen.properties`

| ID | Requisito | Test | Clase |
|---|---|---|---|
| SUM-01 | Contiene al menos: `totalOrders`, `processedOrders`, `approvedOrders`, `rejectedOrders`, `printFailedOrders`, `defectiveOrders`, `durationMillis`, `allThreadsTerminated`, `remainingIntermediateOrders`. | — | — |
| SUM-02 | Valores coherentes: `processedOrders=totalOrders`, suma de terminales = `totalOrders`, `allThreadsTerminated=true`, `remainingIntermediateOrders=0`. | — | — |

## DOC — Documentación

| ID | Requisito | Test | Clase |
|---|---|---|---|
| DOC-01 | `docs/informe.pdf` incluye: recursos compartidos; condiciones de carrera; estrategia de sincronización; condiciones de espera y despertar; mecanismo de terminación; análisis teórico del tiempo; resultados de múltiples ejecuciones; teórico vs observado; efecto de variar hilos y demoras; justificación de diseño. | — | — |
| DOC-02 | `docs/diagrama-clases.png`. | — | — |
| DOC-03 | `docs/diagrama-secuencia.png` (puede haber más de uno). | — | — |

## ENT — Entrega y presentación

| ID | Requisito | Test | Clase |
|---|---|---|---|
| ENT-01 | Repo Git con: código, `pom.xml`, config oficial, tests del grupo, informe, diagramas, resultados de una ejecución oficial, `integrantes.json` completo, `README.md` (compilación y ejecución). | — | — |
| ENT-02 | Tag `{nombre-del-grupo}-entrega-tp1`. | — | — |
| ENT-03 | ZIP generado con `git archive --format=zip --prefix=TP1-GRUPO-XX/ --output=TP1-GRUPO-XX.zip {tag}`; su contenido coincide con el tag. | — | — |
| ENT-04 | La cátedra tiene acceso al repositorio; el ZIP se sube al aula virtual. | — | — |
| PRS-01 | Presentación ≤ 3 min: arquitectura, recursos protegidos, estrategia de sincronización, un resultado del análisis experimental. | — | — |
| PRS-02 | Defensa con el grupo completo; puede haber nota individual y preguntas sobre código/resultados. | — | — |

## OPEN — Ambigüedades a confirmar con la cátedra

| ID | Duda | Supuesto provisorio |
|---|---|---|
| OPEN-01 | Formato del id de impresora. | `P-<fila>-<col>` según el ejemplo (`P-0-0`). |
| OPEN-02 | ¿Se permite `volatile`? No está en la lista. | No usarlo. |
| OPEN-03 | Nombre de hilos en `eventos.csv`. | `main`, `assignment-N`, `validation-N`, `printing-N`, `quality-N` (N desde 1). |
| OPEN-04 | ¿`sequence` arranca en 1? | Sí, según el ejemplo. |
