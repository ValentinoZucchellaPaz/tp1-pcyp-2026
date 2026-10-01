# ADR-01: Estrategia de sincronización (colas por etapa con semáforos)

- **Status:** Proposed
- **Fecha:** 2026-09-30
- **Decisores:** <completar integrantes>
- **Requisitos / invariantes afectados:** STG-01..10, INV-01..10, EVT-08, TRM-01..03, SYN-01..05

## El flujo, en criollo

Pensá la granja como **cuatro estaciones de trabajo en fila**, como una línea de ensamblaje: Asignación → Validación → Impresión → Control de Calidad. En cada estación hay 2 o 3 operarios (hilos) haciendo lo mismo en paralelo, y entre estación y estación hay una **bandeja de entrada** donde se van amontonando los trabajos listos para esa etapa.

Un operario de Validación no le pregunta a Asignación "¿tenés algo para mí?" cada dos segundos — eso sería espera activa, y además nadie trabaja así en una fábrica real. En cambio, se queda esperando tranquilo frente a su bandeja, y en el momento en que un operario de Asignación deja algo ahí, el de Validación lo toma y sigue. Nadie coordina a mano quién hace qué: **el estado de la orden en su bandeja es la única coordinación que existe.**

Como hay varias estaciones a la vez y cada una tiene su propio ritmo, el sistema no procesa "todas las órdenes por etapa 1, después todas por etapa 2"; en cualquier instante puede haber órdenes siendo asignadas, otras siendo validadas y otras siendo impresas **simultáneamente**. Cada orden avanza a su propio ritmo por la fila, empujada únicamente por la disponibilidad de un operario libre en la siguiente estación.

El otro recurso compartido de la fábrica es el **depósito de impresoras**: antes de empezar a trabajar en una orden, un operario de Asignación tiene que ir al depósito y reservar una impresora para esa orden en exclusiva — nadie más puede tocar esa impresora mientras esté reservada.

Y hay una **planilla central** (el log de eventos) donde cada vez que una orden cambia de estado, alguien anota la fila correspondiente, en el momento exacto en que pasa, en orden.

El resto de este documento formaliza estas tres ideas — bandejas, depósito, planilla — con las herramientas de sincronización permitidas.

## Contexto

El enunciado exige un pipeline de 4 etapas concurrentes, con cantidad fija de hilos por etapa, sin espera activa, sin colecciones ni utilidades de alto nivel de `java.util.concurrent` (solo `synchronized`, `Lock`/`ReentrantLock`, `Semaphore`, `wait/notify`). Se necesita un mecanismo de coordinación entre etapas que no requiera que un hilo sepa nada del funcionamiento interno de la etapa siguiente ni anterior, y una forma de terminar limpiamente sin conocer de antemano cuántas órdenes llegan a cada etapa intermedia (depende de `OutcomeDecider`, no es determinable sin ejecutar).

## Opciones consideradas

### Opción A: un lock global único para todo el estado compartido

Un solo `synchronized` protege listas de órdenes por estado, impresoras y log. **Pros:** trivial de razonar, cero riesgo de deadlock. **Contras:** serializa etapas que no compiten por el mismo dato, cuello de botella innecesario, no representa bien que cada etapa es un recurso lógicamente distinto.

### Opción B: una cola por transición de etapa, sincronizada con semáforo + lock (elegida)

Una `StageQueue<Order>` por cada frontera entre etapas (4 en total), cada una con su propio `Semaphore` (cuenta ítems disponibles) y su propio lock interno (protege la lista). **Pros:** cada etapa espera solo por su propia bandeja de entrada, sin bloquear a las demás; el semáforo resuelve el bloqueo sin espera activa; el modelo es el mismo para las 4 transiciones, así que hay una sola clase que reutilizar. **Contras:** hay que coordinar el cierre de 4 colas en cascada (ver sección de terminación); más piezas que la opción A.

### Opción C: `wait/notifyAll` directo sobre las listas, sin semáforo

Listas protegidas por `synchronized`, con `wait()` cuando están vacías y `notifyAll()` al insertar. **Pros:** no usa una clase extra (`Semaphore`), es el patrón "de manual" más clásico. **Contras:** hay que reimplementar a mano el conteo de "cuántos hay esperando" y el chequeo de la condición en un `while`; con `notifyAll` hay riesgo de _thundering herd_ (se despiertan todos los workers de la etapa aunque solo haya un ítem nuevo). El semáforo ya encapsula ese conteo de forma correcta.

## Decisión

Elegimos la **Opción B**: `StageQueue<Order>` (FIFO + `Semaphore` + lock interno) para cada una de las 4 fronteras entre etapas, más un lock simple para el pool de impresoras y otro para el log de eventos. Esta elección se apoya en tres hilos concurrentes por etapa como máximo (config oficial: 2 o 3), donde el semáforo modela exactamente "cuántas órdenes hay esperando" sin que cada worker tenga que re-chequear una condición a mano.

## Diseño

### Recursos compartidos y su protección

| Recurso                                | Clase                          | Mecanismo                                | Quién escribe                                                                  | Quién lee                |
| -------------------------------------- | ------------------------------ | ---------------------------------------- | ------------------------------------------------------------------------------ | ------------------------ |
| Órdenes `CREATED` esperando asignación | `createdQueue`                 | `Semaphore` + lock interno               | `main` (inicial)                                                               | 3 workers de asignación  |
| Órdenes `WAITING_VALIDATION`           | `waitingValidationQueue`       | ídem                                     | workers de asignación                                                          | 2 workers de validación  |
| Órdenes `READY_TO_PRINT`               | `readyToPrintQueue`            | ídem                                     | workers de validación                                                          | 3 workers de impresión   |
| Órdenes `PRINTED`                      | `printedQueue`                 | ídem                                     | workers de impresión                                                           | 2 workers de calidad     |
| Matriz de impresoras                   | `PrinterPool`                  | `synchronized` (lock simple, sin espera) | workers de asignación (reservan), validación/impresión (liberan o inhabilitan) | todos los anteriores     |
| `eventos.csv` + contador `sequence`    | `EventLog`                     | `synchronized`                           | los 10 workers + `main`                                                        | — (solo escritura)       |
| Contador de órdenes terminales         | `TerminationTracker`           | `synchronized` + `wait/notifyAll`        | workers de validación/impresión/calidad                                        | `main` (espera el total) |
| Cierre de cada etapa                   | `StageBarrier` (uno por etapa) | `synchronized`                           | los workers de esa etapa                                                       | —                        |

Cada `Order` y `Printer` en sí **no tienen lock propio**: en todo momento, una orden es tocada por un solo hilo a la vez (el que la sacó de una `StageQueue`), y una impresora solo se modifica dentro de la sección crítica de `PrinterPool`. La exclusión mutua la dan los recursos de arriba, no los objetos de datos.

### Condiciones de carrera identificadas y cómo se evitan

- **Dos workers de asignación reservan la misma impresora**: evitado porque `reserveAny()` busca y marca `RESERVED` dentro de la misma sección `synchronized` de `PrinterPool` (INV-04).
- **Una orden es tomada por dos workers de la misma etapa**: evitado porque `take()` de `StageQueue` hace `acquire()` + extracción de la lista como una operación atómica respecto a otros `take()`/`offer()` (INV-01, INV-03).
- **El evento se escribe en un orden distinto al que ocurrió la transición**: evitado registrando en `EventLog.append(...)` (que asigna `sequence` y escribe) inmediatamente dentro de la misma sección lógica que confirma el cambio de estado, antes de soltar la orden hacia la siguiente cola (EVT-08).
- **Una orden queda "perdida" entre que cambia de estado y se encola**: evitado porque el cambio de estado, el registro del evento y el `offer()` a la siguiente cola se hacen en secuencia dentro del mismo worker, sin que la orden pase por manos de otro hilo en el medio.
- **Píldoras que se emiten antes de que todas las órdenes reales fueron encoladas** (ver sección de terminación): evitado porque el `StageBarrier` solo confirma "etapa cerrada" cuando el worker que lo consulta ya consumió su propia píldora, momento en el cual —por construcción secuencial de cada worker— ya encoló todo lo que tenía para encolar.

### Terminación: píldoras + barrera, en cascada

**Píldora**: un valor centinela (distinto de una orden real) que se encola junto con el trabajo real para indicarle a un worker "no va a llegar nada más, terminá". Como cada etapa tiene varios workers, se necesita una píldora por worker.

**`StageBarrier`**: un contador por etapa que decide quién es el **último** worker de esa etapa en terminar. Es quien tiene la responsabilidad de fabricar e inyectar las píldoras en la cola de la etapa siguiente.

Secuencia de cierre:

1. `main` encola las 500 órdenes en `createdQueue` y, al final, agrega 3 píldoras (una por worker de asignación).
2. Cada worker de asignación procesa hasta sacar su píldora. El que hace que el `StageBarrier` de asignación llegue a 3 (el último) inyecta 2 píldoras en `waitingValidationQueue`.
3. Se repite el patrón: validación cierra impresión (3 píldoras), impresión cierra calidad (2 píldoras).
4. Los workers de calidad, al recibir su píldora, simplemente terminan (no hay etapa siguiente).
5. En paralelo a todo esto, cada vez que una orden llega a un estado terminal (`APPROVED`, `REJECTED`, `PRINT_FAILED`, `DEFECTIVE`), el worker correspondiente avisa a `TerminationTracker`, que lleva la cuenta global.
6. `main` está bloqueado en `TerminationTracker.awaitAll()` (`wait` hasta que el contador llegue a `totalOrders`, con `notifyAll` en cada incremento).
7. Al cumplirse, `main` hace `join()` de los 10 hilos worker, cierra los archivos y retorna `SimulationResult`.

Por qué es seguro: el `StageBarrier` nunca se confirma "cerrado" por un número fijo calculado de antemano (que podría llegar tarde o estar mal si no conocemos cuántas órdenes sobreviven a cada etapa), sino por la cuenta real de workers que efectivamente terminaron, y cada worker termina solo después de haber encolado todo lo que le tocaba encolar. Esto también es lo que garantiza que, al finalizar, no queden órdenes en estados intermedios (INV-09) ni impresoras `RESERVED` (INV-10): toda orden que entra a una etapa, sale de ella hacia un estado válido antes de que el worker pase a la píldora siguiente.

### Por qué no hace falta lock explícito en `Order`/`Printer`

Porque el diseño respeta _ownership_ de un solo hilo por objeto en cada instante: una orden vive dentro de exactamente una `StageQueue` (o en la mano de un worker que la sacó), nunca en dos a la vez, y eso ya lo garantiza el `Semaphore`+lock de la cola. Agregar `synchronized` a los getters/setters de `Order` sería redundante y, peor, podría esconder el verdadero punto de exclusión mutua.

## Consecuencias

- El paralelismo real entre etapas es alto: una etapa lenta no bloquea a las demás, solo hace crecer su propia cola de entrada.
- El costo es la cascada de cierre: si se rompe el orden (ej. una píldora se encola antes que órdenes reales), el sistema puede terminar con trabajo sin procesar. Por eso todo `offer()` de órdenes reales debe ocurrir antes del `offer()` de píldoras dentro del mismo worker, nunca al revés ni desde otro hilo.
- Los 10 workers + `main` son los únicos hilos creados por la simulación; el `join()` final es lo que garantiza TRM-02/TRM-03 sin `Thread.stop` ni `System.exit`.
- Queda abierto (para discutir en el grupo) si `EventLog` con un solo `synchronized` es o no cuello de botella con la config oficial (500 órdenes, demoras reales) — es parte del análisis experimental del informe (DOC-01), no algo a resolver en este ADR.
