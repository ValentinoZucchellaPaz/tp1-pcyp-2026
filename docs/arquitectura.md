# Arquitectura de la simulación

## Cómo navegar el código

`ConcurrentSimulation` es el único punto de entrada de la solución. Crea los
recursos, registra los workers en el orden funcional y reúne el resultado.

| Paquete | Responsabilidad |
| --- | --- |
| `solution.internal.model` | Estado de una orden. |
| `solution.internal.pipeline` | Colas entre etapas, píldoras, cierre de etapas y los cuatro workers. |
| `solution.internal.resource` | Matriz de impresoras, reserva exclusiva y disponibilidad. |
| `solution.internal.runtime` | Inicio conjunto, ciclo de vida de threads y terminación global. |
| `solution.internal.output` | Log de eventos, snapshots y archivos de resultado. |

Todo lo que está bajo `internal` es implementación propia: no forma parte del
contrato de la cátedra. Ese contrato permanece en el paquete `api`.

## Recorrido de una orden

```text
CREATED
  │ asignación: reserva una impresora
  ▼
WAITING_VALIDATION
  │ validación
  ├── modelo inválido ──► REJECTED
  ▼
READY_TO_PRINT
  │ impresión
  ├── falla ───────────► PRINT_FAILED
  ▼
PRINTED
  │ control de calidad
  ├── aprobada ────────► APPROVED
  └── defectuosa ──────► DEFECTIVE
```

La orden avanza por cuatro `StageQueue`: creada → validación → impresión →
calidad. Cada cola es FIFO y bloquea a sus consumidores con un `Semaphore`;
no hay espera activa. Una vez retirada de una cola, la orden es propiedad del
worker que la procesa hasta que se encola de nuevo o llega a un estado terminal.

## Inicio, recursos y cierre

1. El hilo principal crea todas las órdenes, registra `ORDER_CREATED` y coloca
   las píldoras de asignación después de las órdenes reales.
2. Registra los threads en el orden asignación → validación → impresión → calidad.
   Todos se detienen inicialmente en `StartGate`.
3. Cuando todos alcanzaron la compuerta, el principal los libera con
   `notifyAll()`. Esto garantiza que ninguna etapa empiece a procesar antes de
   que todas hayan sido iniciadas.
4. `PrinterPool` contiene tanto la matriz como el semáforo de impresoras
   disponibles. Reservar espera un permiso; liberar una impresora devuelve el
   permiso; una impresora fuera de servicio no lo devuelve.
5. El último worker que consume una píldora de una etapa agrega las píldoras
   necesarias para la siguiente. Así el cierre se propaga sin conocer cuántas
   órdenes llegaron a cada cola.
6. Validación, impresión y calidad notifican a `TerminationTracker` cuando una
   orden se vuelve terminal. Al llegar al total, el principal hace `join()` de
   los workers y escribe los resultados finales.

## Eventos y demoras

`EventLog` serializa cada fila con `synchronized`: el contador `sequence` y la
escritura de la fila son una única operación lógica. Cada worker cambia el
estado de su orden y registra el evento antes de ofrecerla a la siguiente cola.

Las demoras son un requisito del enunciado: la sección **Demoras** indica que
cada hilo debe aplicar la demora configurada una vez por cada orden que procesa
en su etapa. Por eso cada worker ejecuta `Thread.sleep(delayMillis)` una vez
antes de resolver su operación. Nunca se usan para coordinar threads.
