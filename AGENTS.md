# Contexto para agentes — TP1: granja concurrente de impresión 3D

## Propósito y alcance

Este repositorio contiene la solución del TP1 de **Programación Concurrente y
Paralela 2026** (FCEFyN, UNC). Simula una granja de impresión 3D mediante un
pipeline concurrente de cuatro etapas:

```text
CREATED -> asignación -> validación -> impresión -> control de calidad
```

Una orden termina exactamente en `APPROVED`, `REJECTED`, `PRINT_FAILED` o
`DEFECTIVE`. La especificación normativa completa está en `ENUNCIADO.md`.
Léela antes de cambiar comportamiento concurrente, estados, sincronización o
formatos de salida.

## Tecnología y comandos

- Java 21 y Maven 3.9+.
- No se admiten dependencias de producción externas a la biblioteca estándar.
- Ejecutar pruebas públicas: `mvn test`
- Ejecutar una verificación limpia: `mvn clean test`
- Empaquetar: `mvn clean package`
- Ejecutar la configuración local: `java -jar target/tp1.jar`

La aplicación ejecutable siempre lee `config/tp1.properties`. En cambio,
`Simulation.execute(SimulationConfig)` debe usar **solo** la configuración que
recibe; no debe leer archivos de configuración por su cuenta.

## Mapa del código

| Ubicación | Responsabilidad |
| --- | --- |
| `src/main/java/.../api` | Contrato provisto por la cátedra. No modificar. |
| `src/main/java/.../app/Main.java` | Carga de configuración y arranque de la aplicación. |
| `src/main/java/.../solution/ConcurrentSimulation.java` | Punto de entrada de la solución; arma el pipeline y devuelve el resultado. |
| `src/main/java/.../solution/internal/model` | Estado mutable seguro de cada orden. |
| `src/main/java/.../solution/internal/pipeline` | Colas FIFO, workers, píldoras de cierre y coordinación entre etapas. |
| `src/main/java/.../solution/internal/resource` | Matriz de impresoras y reserva exclusiva. |
| `src/main/java/.../solution/internal/runtime` | Inicio coordinado, ciclo de vida de workers y terminación. |
| `src/main/java/.../solution/internal/output` | Eventos y archivos de resultados. |
| `src/test/java/.../publictests` | Suite pública y restricciones de APIs. |
| `docs/arquitectura.md` | Explicación del diseño vigente. Actualizarla si cambia la arquitectura. |
| `docs/analisis-rendimiento.md` y `tools/analisis/` | Ejecución y análisis de experimentos de rendimiento. |

El nombre del paquete base es `ar.edu.unc.fcefyn.pcp.tp1`.

## Diseño concurrente actual

`ConcurrentSimulation` crea las órdenes, impresoras, cuatro `StageQueue` y los
workers de asignación, validación, impresión y calidad. Todos los workers se
registran antes de arrancar y esperan `StartGate`; esto garantiza que ninguna
etapa procese una orden antes de que todas estén iniciadas.

Las colas son FIFO propias y bloquean consumidores con `Semaphore`. Cada etapa
recibe píldoras de cierre; el último worker de una etapa propaga las píldoras a
la siguiente mediante `StageBarrier`. `PrinterPool` administra la reserva y
liberación de impresoras. `TerminationTracker` señala al hilo principal cuando
todas las órdenes llegan a estado terminal. Consultar `docs/arquitectura.md`
para el protocolo y los detalles antes de refactorizarlo.

`OutcomeDecider` debe ser la única fuente para decidir validación, éxito de
impresión y control de calidad. Sus resultados son deterministas para una
configuración y un id de orden dados.

## Reglas no negociables de concurrencia

- No modificar nada bajo `src/main/java/.../api`.
- Iniciar todas las etapas al comienzo: no ejecutar una etapa completa antes de
  crear e iniciar las siguientes.
- Cada orden debe pertenecer a un único estado y alcanzar exactamente un estado
  terminal; al retornar no puede haber órdenes intermedias ni impresoras
  `RESERVED`.
- Una impresora puede estar asignada a una sola orden y una impresora
  `OUT_OF_SERVICE` nunca se reutiliza.
- Aplicar exactamente una demora configurada por orden procesada en cada etapa;
  las demoras nunca son un mecanismo de sincronización.
- Propagar o restaurar correctamente la interrupción y garantizar el cierre de
  todos los workers y archivos antes de retornar o abortar.
- Mantener el log de eventos serializado y coherente: cambio de estado y evento
  deben conservar el orden lógico antes de entregar la orden a la siguiente
  cola.

En producción solo se permiten `Thread`, `Runnable`, `synchronized`,
`wait`/`notify`/`notifyAll`, `Lock`, `ReentrantLock`, `Semaphore` y los
ejecutores explícitamente listados en el enunciado. No introducir:

- `BlockingQueue`, `CountDownLatch`, `CyclicBarrier`, `Phaser`, clases atómicas,
  colecciones concurrentes ni `Condition`;
- imports wildcard de `java.util.concurrent`;
- `Collections.synchronized*`, `Vector`, `Hashtable`, streams paralelos ni
  espera activa;
- `Thread.stop`, `System.exit` o dependencias de producción adicionales.

`SourceRestrictionsTest` valida varias de estas restricciones textual y
automáticamente, pero la lista del enunciado es la autoridad final.

## Cómo trabajar de forma segura

1. Antes de tocar concurrencia, revisar `ENUNCIADO.md`,
   `docs/arquitectura.md` y las pruebas públicas relacionadas.
2. Mantener separadas las reglas de negocio de una etapa y el protocolo común
   de workers; `AbstractStageWorker` concentra este último.
3. No codificar cantidades de órdenes, impresoras, hilos, probabilidades,
   demoras ni rutas: usar `SimulationConfig`.
4. Agregar o ajustar pruebas en `src/test/java` para cambios de comportamiento,
   incluyendo configuraciones de demora cero y pocos recursos si corresponde.
5. Ejecutar al menos `mvn test` tras cambios Java. Para cambios de concurrencia
   o de salida, preferir `mvn clean test` y revisar los artefactos generados.
6. No versionar `target/`, metadatos del IDE ni ejecuciones masivas de
   experimentos. Los resultados locales normales van a `resultados/`; solo
   conservarlos si forman parte explícita de un entregable acordado.

## Salidas y documentación

Cada simulación escribe en el directorio configurado (por defecto,
`resultados/`): `eventos.csv`, `elementos.csv` y `resumen.properties`. Los
formatos son parte del comportamiento observable y están cubiertos por pruebas.

Al modificar decisiones arquitectónicas, sincronización, diagramas o el
análisis de rendimiento, actualizar la documentación pertinente en `docs/`.
Antes de la entrega se requieren `docs/informe.pdf`,
`docs/diagrama-clases.png` y `docs/diagrama-secuencia.png`; ver
`docs/README.md`.

## Estado de trabajo

Puede haber cambios locales no relacionados con la tarea actual. Presérvalos:
no los reviertas, no los reformatees masivamente y evita mezclar artefactos
generados con cambios funcionales. Usa `git status --short` antes y después de
trabajar para verificar el alcance real de tus modificaciones.
