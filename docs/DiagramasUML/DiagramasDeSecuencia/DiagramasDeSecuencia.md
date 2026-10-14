# Justificación y Explicación de los Diagramas de Secuencia UML

## 1. Motivación: ¿Por qué dividir la dinámica en 4 diagramas de secuencia?

En nuestro sistema coexisten **10 hilos de ejecución**, **4 colas intermedias**, **500 órdenes procesadas en paralelo**, **recursos físicos compartidos (impresoras)** y **distintos desenlaces deterministas**.

Modelar toda la dinámica temporal en un único diagrama de secuencia produciría un gráfico saturado, donde resulta imposible seguir el flujo lógico del programa.

Para evitar esto, se decidió descomponer la interacción temporal en **4 diagramas de secuencia temáticos**:

1. **Diagrama 1 — Arranque conjunto:** Modela la fase de inicialización y la barrera de sincronización que garantiza el cumplimiento del enunciado.
2. **Diagrama 2 — Recorrido feliz de una orden:** Modela el flujo en régimen permanente de una orden que atraviesa con éxito todas las etapas hasta ser aprobada (`APPROVED`).
3. **Diagrama 3 — Terminaciones que no son el camino feliz:** Modela las tres bifurcaciones alternativas de descarte (`REJECTED`, `PRINT_FAILED`, `DEFECTIVE`) y el tratamiento de las impresoras.
4. **Diagrama 4 — Terminación en cascada (píldoras y cierre global):** Modela el protocolo de apagado ordenado mediante centinelas (*Poison Pills*) y la recolección de resultados en el hilo principal.

---

## 2. Diagrama 1: Arranque Conjunto (`StartGate`)

### 2.1. Propósito
Demostrar formalmente que todas las etapas se inician al comienzo del programa y que no se ejecuta una etapa completa antes de iniciar la siguiente.

### 2.2. Dinámica del Flujo
1. **Creación y registro:** `ConcurrentSimulation` registra los 10 workers en `WorkerGroup` mediante `registerWorkers` y llama a `startAll()`.
2. **Lanzamiento de hilos:** `WorkerGroup` invoca el método `.start()` de la clase `Thread` para cada uno de los 10 hilos. La JVM crea los hilos nativos e invoca su método `run()` (definido en `AbstractStageWorker`).
3. **Bloqueo en la compuerta:** La primera instrucción de `AbstractStageWorker.run()` es `startGate.awaitOpen()`. Al no estar abierta la compuerta, cada uno de los 10 workers se registra en el contador `waitingWorkers` y se bloquea ejecutando `wait()`.
4. **Apertura coordinada:** El hilo principal ejecuta `startGate.openWhenAllWorkersAreReady()`. Este método espera hasta que los 10 workers hayan alcanzado la compuerta (`waitingWorkers == 10`).
5. **Liberación simultánea:** Cuando se constata que todos los workers están listos, `StartGate` establece `open = true` y ejecuta `notifyAll()`. Todos los hilos despiertan a la vez y comienzan a competir por trabajo en sus respectivas colas.

---

## 3. Diagrama 2: Recorrido Feliz de una Orden

### 3.1. Propósito
Explicar el ciclo de vida completo y estándar de una orden exitosa que pasa de `CREATED` a `APPROVED`, mostrando la transferencia por colas FIFO, las demoras obligatorias y la gestión de recursos.

### 3.2. Dinámica del Flujo
1. **Siembra de trabajo:** `ConcurrentSimulation` encola las 500 órdenes en `createdQueue` mediante `offerOrder(...)`.
2. **Etapa 1 — Asignación:**
    * Un `AssignmentWorker` retira la orden con `take()`. 
    * Aplica la demora obligatoria de su etapa (`delay(50 ms)`).
    * Invoca `PrinterPool.reserveFor(orderId)`. Adquiere un permiso de impresora libre y asocia el identificador de la impresora a la orden.
    * Registra el evento en `EventLog` (`CREATED -> WAITING_VALIDATION`).
    * Encola la orden en `validationQueue.offerOrder(...)`.
3. **Etapa 2 — Validación:**
    * `ValidationWorker` extrae la orden (`take()`) y aplica su demora (`delay(120 ms)`).
    * Consulta a `OutcomeDecider.isModelValid(id, config)`, obteniendo `true`.
    * Registra la transición en `EventLog` (`WAITING_VALIDATION -> READY_TO_PRINT`).
    * Encola la orden en `printingQueue`.
4. **Etapa 3 — Impresión:**
    * `PrintingWorker` retira la orden (`take()`) y aplica su demora (`delay(180 ms)`).
    * Consulta a `OutcomeDecider.isPrintSuccessful(id, config)`, obteniendo `true`.
    * Registra en `EventLog` (`READY_TO_PRINT -> PRINTED`).
    * **Liberación de recurso:** Como la pieza física ya fue impresa, invoca `PrinterPool.release(printerId)`. La impresora vuelve a estado `AVAILABLE` y devuelve su permiso al semáforo para ser reutilizada por la Etapa 1.
    * Encola la orden en `qualityQueue`.
5. **Etapa 4 — Control de Calidad:**
    * `QualityControlWorker` extrae la orden (`take()`) y aplica su demora (`delay(80 ms)`).
    * Consulta a `OutcomeDecider.isQualityApproved(id, config)`, obteniendo `true`.
    * Registra en `EventLog` (`PRINTED -> APPROVED`).
    * Como este es un estado terminal, notifica al rastreador de finalización: `TerminationTracker.orderCompleted()`.

---

## 4. Diagrama 3: Terminaciones que no son el Camino Feliz

### 4.1. Propósito
Aislar y detallar las 3 ramificaciones alternativas de finalización prematura o falla. Esto evita sobrecargar el Diagrama 2 con bloques condicionales anidados y clarifica cómo se gestionan los recursos en situaciones anómalas.

### 4.2. Tres Escenarios Posibles:

| Escenario                           | Decisión de `OutcomeDecider` | Transición de Estado | Destino de la Impresora | 
|:------------------------------------| :---: | :---: | :--- |
| **1. Modelo Inválido** | `isModelValid() == false` | `WAITING_VALIDATION -> REJECTED` | **Se libera:** `release(printerId)`. Vuelve a `AVAILABLE` y devuelve el permiso al semáforo. |
| **2. Fallo de Impresión**           | `isPrintSuccessful() == false` | `READY_TO_PRINT -> PRINT_FAILED` | **Queda inutilizada:** `markOutOfService(printerId)`. Pasa a `OUT_OF_SERVICE` y **NO** devuelve el permiso. |
| **3. Pieza Defectuosa**   | `isQualityApproved() == false` | `PRINTED -> DEFECTIVE` | *Ninguno* (la impresora ya había sido liberada en la etapa de impresión). |

* **Consistencia del rastreador:** En los tres casos, la orden alcanza un estado terminal, por lo que es obligatorio llamar a `TerminationTracker.orderCompleted()` para que el hilo principal sepa cuándo concluyó el lote global de trabajo.

---

## 5. Diagrama 4: Terminación en Cascada (Píldoras y Cierre Global)

### 5.1. Propósito
Explicar cómo se apaga el pipeline sin dejar hilos bloqueados ni recurrir a cancelaciones forzadas (`Thread.stop()` o interrupciones indebidas).

### 5.2. Dinámica del Flujo

#### A. Propagación en Cascada mediante Centinelas (*Poison Pills*)
1. Al inicializar la simulación, `ConcurrentSimulation` insertó tantas píldoras como workers tiene la primera etapa (`offerPoison(3)`). Al ser una cola FIFO, las píldoras quedan ubicadas estrictamente detrás de las órdenes de datos reales.
2. Cuando un worker de asignación agota las órdenes reales, extrae un `WorkItem` que contiene `isPoison() == true`.
3. El worker no propaga la píldora inmediatamente, sino que consulta a su barrera: `barrier.workerFinished()`.
    * **Si devuelve `false` (no es el último worker):** El hilo finaliza limpiamente su método `run()` y muere.
    * **Si devuelve `true` (es el último worker de esa etapa):** Ejecuta el método gancho `onLastWorkerFinished()`, que inyecta las píldoras en la cola de la siguiente etapa (`validationQueue.offerPoison(2)`).
4. Esta lógica se replica secuencialmente: Validación apaga a Impresión, e Impresión apaga a Calidad. En Calidad, como no existe una cola sucesora, el último worker simplemente finaliza.

#### B. Terminación Global y Consolidación de Resultados
1. Mientras los workers procesan las órdenes y las píldoras, `ConcurrentSimulation` permanece bloqueado en `TerminationTracker.awaitAll()`.
2. Cuando el contador de órdenes completadas alcanza el total (`completedOrders == totalOrders`), `TerminationTracker` ejecuta `notifyAll()`, despertando al hilo principal.
3. Invoca `rethrowFailure()` para verificar si algún worker sufrió una excepción no controlada durante la ejecución.
4. `ConcurrentSimulation` llama a `WorkerGroup.joinAll()`. Esto garantiza que el método `execute(...)` no retorne hasta que los 10 hilos hayan muerto definitivamente tras consumir sus píldoras.
5. Se cierra `EventLog` y se invoca `buildResult(...)` para generar `elementos.csv`, `resumen.properties` y devolver la instancia inmutable de `SimulationResult`.

