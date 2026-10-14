# Justificación y Explicación de los Diagramas de Clases UML

## 1. Motivación: ¿Por qué dividir el modelo en dos diagramas?

Al intentar condensar toda la arquitectura en un único diagrama de clases monolítico, nos encontramos con que la cantidad de clases y relaciones entre estas produce una sobrecarga y desorden visual que no aportan claridad a la hora de tomar las decisiones de diseño.

Es por esto que se decidió desdoblar la vista estructural en dos diagramas complementarios:

1. **Diagrama 1 — Arquitectura y Orquestación General (Vista Macroscópica):**  
   Muestra la perspectiva del sistema completo: cómo el punto de entrada (`ConcurrentSimulation`) cumple el contrato provisto por la cátedra (`api`), cómo se estructuran los recursos compartidos (`PrinterPool`), las colas intermedias (`StageQueue`) y los mecanismos de coordinación global (`StartGate`, `TerminationTracker`, `WorkerGroup`). En este diagrama, los *workers* se tratan como un subsistema para no saturar la vista.

2. **Diagrama 2 — Detalle del Pipeline y Jerarquía de Workers (Vista Microscópica):**  
   Hace foco en la dinámica concurrente interna. Modela la abstraccion en la jerarquía de *workers* (AbstractStageWorkers es una clase abstracta y cada etapa tiene su worker, el cual hereda de dicha clase abstracta), el flujo de mensajes mediante colas FIFO (StageQueue), el manejo de ítems centinela (*Poison Pills*) y la interacción de cada etapa con las reglas deterministas de negocio (`OutcomeDecider`).

---

## 2. Código de Colores por Diagrama
Para facilitar la lectura y permitir que cualquier miembro del equipo o evaluador identifique de inmediato la responsabilidad de cada componente, se implementó una paleta de colores normalizada basada en tonos pastel (alto contraste y fácil lectura).
### 2.1. Paleta de Colores — Diagrama 1: Arquitectura y Orquestación General
En este diagrama se visualiza la infraestructura global, los contratos base y los recursos:

| Clase / Elemento | Paquete / Capa | Color    | Justificación Semántica |
| :--- | :--- |:---------| :--- |
| **`Simulation`** («interface») | `api` | Celeste  | Interfaz contractual inmutable provista por la cátedra. |
| **`SimulationConfig`** | `api` | Celeste  | Parámetros de configuración de la simulación. |
| **`SimulationResult`** | `api` | Celeste  | Objeto de resultado consolidado final. |
| **`OrderState`** («enum») | `api` | Celeste  | Enumeración de estados posibles de una orden. |
| **`PrinterState`** («enum») | `api` | Celeste  | Enumeración de estados posibles de una impresora. |
| **`ConcurrentSimulation`** | `solution` | Púrpura  | El "cerebro" y ensamblador principal de la solución. |
| **`Order`** | `internal.model` | Amarillo | Entidad central del negocio que viaja por el sistema. |
| **`PrinterPool`** | `internal.resource` | Naranja  | Administrador del recurso físico limitado de impresoras. |
| **`Printer`** | `internal.resource` | Naranja  | Representación de cada impresora individual de la matriz. |
| **`StageQueue`** | `internal.pipeline` | Verde    | Colas FIFO intermedias que comunican las etapas. |
| **`StartGate`** | `internal.runtime` | Rojo     | Barrera de arranque coordinado simultáneo. |
| **`TerminationTracker`** | `internal.runtime` | Rojo     | Rastreador de finalización global de órdenes. |
| **`WorkerGroup`** | `internal.runtime` | Rojo     | Administrador del ciclo de vida de los hilos (`Thread`). |
| **`EventLog`** | `internal.output` | Gris     | Registro serializado de eventos en disco (`eventos.csv`). |
---
### 2.2. Paleta de Colores — Diagrama 2: Detalle del Pipeline y Jerarquía de Workers
En este diagrama el foco está en los hilos de ejecución, el procesamiento y la mensajería entre etapas:

| Clase / Elemento | Paquete / Capa | Color             |  Justificación Semántica |
| :--- | :--- |:------------------| :--- |
| **`InterruptibleTask`** («interface») | `internal.runtime` | Celeste claro     | Contrato para tareas ejecutables por `WorkerGroup`. |
| **`AbstractStageWorker`** («abstract») | `internal.pipeline` | Lila              | Molde común que implementa el *Template Method*. |
| **`AssignmentWorker`** | `internal.pipeline` | Violeta           | Worker concreto: reserva impresoras (Etapa 1). |
| **`ValidationWorker`** | `internal.pipeline` | Violeta           | Worker concreto: valida modelos 3D (Etapa 2). |
| **`PrintingWorker`** | `internal.pipeline` | Violeta           | Worker concreto: ejecuta la impresión (Etapa 3). |
| **`QualityControlWorker`** | `internal.pipeline` | Violeta           | Worker concreto: control de calidad final (Etapa 4). |
| **`StageQueue`** | `internal.pipeline` | Verde             | Colas de entrada y salida entre etapas. |
| **`WorkItem`** | `internal.pipeline` | Verde             | Mensaje que envuelve una orden o una píldora venenosa. |
| **`StageBarrier`** | `internal.pipeline` | Verde             | Barrera contadora para cierre y propagación de píldoras. |
| **`TerminationTracker`** | `internal.runtime` | Verde             | Notificado ante órdenes que llegan a estado final. |
| **`Order`** | `internal.model` | Amarillo          | Instancia procesada dentro de `processOrder(...)`. |
| **`PrinterPool`** | `internal.resource` | Naranja           | Recurso consultado para reservar o liberar impresoras. |
| **`StartGate`** | `internal.runtime` | Rojo              | Barrera donde esperan todos los workers al iniciar. |
| **`OutcomeDecider`** | `api` | Celeste     | Reglas deterministas de validación, impresión y calidad. |
---

## 3. Diagrama 1: Arquitectura y Orquestación General

### 3.1. Propósito
Explicar cómo se construye el sistema antes de iniciar la ejecución y cómo se recogen los resultados una vez finalizado el procesamiento.

### 3.2. Decisiones de Modelado y Relaciones Clave

* **Realización de `Simulation` (`ConcurrentSimulation ..|> Simulation`):**  
  Indica que `ConcurrentSimulation` es la implementación concreta requerida por el contrato de la cátedra para ejecutar simulaciones parametrizadas.
* **Composiciones fuertes desde `ConcurrentSimulation` (`◆——`):**  
  `ConcurrentSimulation` es la dueña del ciclo de vida de los componentes de infraestructura:
    * Posee **1** `PrinterPool` (el recurso físico).
    * Posee **4** instancias de `StageQueue` (conectan Asignación, Validación, Impresión y Calidad).
    * Posee **1** `StartGate`, **1** `TerminationTracker`, **1** `WorkerGroup` y **1** `EventLog`.  
      *El uso del rombo relleno (`◆`) explicita que estos objetos nacen, se administran y mueren exclusivamente con la simulación.*
* **Agregación de `Order` (`ConcurrentSimulation ◇—— Order`):**  
  Se utilizó agregación (rombo hueco `◇`) porque las órdenes son instancias de datos que se inicializan en lote al comienzo y luego se traspasan a las colas; la simulación conserva la lista para generar el reporte estadístico final.
* **Composición de la matriz de impresoras (`PrinterPool ——◇ Printer`):**  
  `PrinterPool` administra la matriz `Printer[][]`. 
* **Dependencias de entrada y salida (`-->`):**  
  `ConcurrentSimulation` recibe `SimulationConfig` en su método `execute(...)` y genera un `SimulationResult`. 

---

## 4. Diagrama 2: Detalle del Pipeline y Jerarquía de Workers

### 4.1. Propósito
Detallar la mecánica interna del procesamiento concurrente, justificando el diseño orientado a objetos aplicado a la sincronización.

### 4.2. Decisiones de Modelado y Patrones de Diseño

#### A. Protocolo común de ejecución (`AbstractStageWorker`)
En lugar de repetir la lógica concurrente en cada etapa, se definió una clase base abstracta:
* **`AbstractStageWorker`** implementa `InterruptibleTask` y define el método `run()` como `final`.  
  Este método centraliza el protocolo invariante:
    1. Esperar la apertura coordinada en `StartGate`.
    2. Extraer trabajo bloqueante de su cola de entrada (`input.take()`).
    3. Detectar si el elemento es una píldora venenosa y gestionar el apagado en `StageBarrier`.
    4. Aplicar la demora obligatoria por orden (`Thread.sleep`).
    5. Delegar el procesamiento específico al método protegido abstracto `processOrder(order)`.
  
* **Herencia (`——|>`):**   
Las 4 subclases concretas (`AssignmentWorker`, `ValidationWorker`, `PrintingWorker`, `QualityControlWorker`) heredan de `AbstractStageWorker` y únicamente sobreescriben la regla de negocio que les compete.

#### B. Traspaso y Desacoplamiento entre Etapas
* **Asociaciones dirigidas (`——>`):**  
  Cada worker conoce su cola de entrada (`input: StageQueue`) a través de la clase padre. Las etapas 1, 2 y 3 poseen además una referencia a su cola de salida (`output: StageQueue`).  
  *Nótese que `QualityControlWorker` no tiene cola de salida*, reflejando con exactitud que es la etapa terminal.
* **Estructura de la cola y píldoras venenosas (`StageQueue` y `WorkItem`):**
    * `StageQueue` compone `WorkItem` (`◆——`).
    * `WorkItem` agrega `Order` (`◇——`). Si la referencia es `null`, el ítem representa una **Poison Pill** (`isPoison() == true`). Esto modela cómo se apagan los workers sin recurrir a interrupciones bruscas en el flujo normal.
* **Coordinación de cierre (`StageBarrier`):**  
  Asociada a cada etapa (`AbstractStageWorker ——> StageBarrier`). El último worker en consumir una píldora ejecuta `onLastWorkerFinished()`, propagando las píldoras a la etapa sucesora.
* **Interacción con recursos y finalización:**
    * `AssignmentWorker`, `ValidationWorker` y `PrintingWorker` se conectan con `PrinterPool` para reservar o liberar impresoras.
    * Las etapas donde una orden puede terminar (`ValidationWorker` por rechazo, `PrintingWorker` por fallo, y `QualityControlWorker` por aprobación/defecto) poseen una asociación con `TerminationTracker` para descontar las órdenes completadas mediante `orderCompleted()`.
* **Dependencias de reglas de negocio (`-->` a `OutcomeDecider`):**  
  Los workers de validación, impresión y calidad invocan los métodos estáticos de `OutcomeDecider` para determinar el destino determinista de cada pieza.

---

## 5. Principios Concurrentes Reflejados en el Diseño

1. **Confinamiento Secuencial de Hilos (*Serial Thread Confinement*):**  
   En el modelo se observa que la clase `Order` no posee cerrojos (`synchronized`). La seguridad sobre los datos mutables de la orden queda garantizada por el traspaso de propiedad (*hand-off*) a través de las `StageQueue`: en un instante de tiempo dado, solo el worker que extrajo la orden con `take()` tiene acceso a ella.
2. **Máquina de Estados Defensiva:**  
   `Order` valida internamente con `validateTransition(...)` que ninguna orden salte etapas ni sea alterada una vez alcanzado un estado final.
3. **Arranque e Invariantes Simultáneos:**  
   La presencia de `StartGate` en la base de todos los workers garantiza el cumplimiento estricto del enunciado: todas las etapas arrancan en conjunto, sin procesar órdenes de forma secuenciada previa.