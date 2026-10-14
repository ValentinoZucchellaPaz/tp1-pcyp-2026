# Análisis de rendimiento y experimentos — TP1 (granja concurrente de impresión 3D)

Este documento es el respaldo completo de la carpeta `tools/analisis/` y del
script `run-experiments.ps1`. No es un resumen: explica **el problema**, **el
modelo teórico**, **la hipótesis del cuello de botella**, **el diseño de los
experimentos**, **cómo funciona la herramienta paso a paso** y **los resultados
obtenidos y esperados**. Está pensado para defender oralmente la parte de
análisis y de scripting del trabajo práctico.

La carpeta contiene:

```text
tools/analisis/
  run-experiments.ps1        script que corre los experimentos y recolecta datos
  configs/<variante>.properties  registro de la config usada en cada variante
  salidas/resultados.csv     una fila por corrida (datos crudos)
  salidas/resumen.md         tabla Markdown por variante (mediana/min/max)
  README.md                  este documento
```

---

## 1. El sistema que medimos

El trabajo práctico simula una **granja de impresión 3D** como un **pipeline
concurrente de cuatro etapas**. Cada orden que se crea recorre, en orden:

```text
CREATED
  -> asignación  -> validación  -> impresión  -> control de calidad
                                                     |
                                                     v
                                       APPROVED / REJECTED / PRINT_FAILED / DEFECTIVE
```

Puntos de la implementación que importan para el análisis:

- Hay **un grupo de workers por etapa**. Cada etapa tiene una **cola FIFO propia**
  (`StageQueue`, implementada con un `Semaphore` más un lock interno). Los
  workers de la etapa consumen de su cola y, al terminar, encolan la orden en la
  cola de la etapa siguiente.
- Los workers de validación, impresión y control de calidad aplican una
  **demora configurable por orden** (`delay.<etapa>.ms`). Esa demora representa
  el trabajo real (revisar el modelo, imprimir la pieza, inspeccionarla).
- La etapa de impresión, además, **reserva una impresora** de una matriz de
  `printers.rows × printers.columns` (10×20 = 200 en la config oficial) a través
  de `PrinterPool`. Es el único recurso con exclusión física.
- Una orden termina en **exactamente uno** de los cuatro estados terminales. La
  decisión la toma `OutcomeDecider`, que es **determinista** para una semilla y
  un id de orden dados.
- Hay un `EventLog` que serializa todos los eventos (`eventos.csv`).


## 2. Variables del sistema

Para razonar el modelo conviene fijar notación.

| Símbolo | Significado | En la config oficial |
| --- | --- | --- |
| `N` | cantidad total de órdenes | 500 |
| `d_i` | demora por orden de la etapa `i` | 50 / 120 / 180 / 80 ms |
| `W_i` | cantidad de workers de la etapa `i` | 3 / 2 / 3 / 2 |
| `P` | cantidad de impresoras | 200 |

Las etapas son, en orden: **asignación** (`i=1`), **validación** (`i=2`),
**impresión** (`i=3`), **control de calidad** (`i=4`).

Aclaración importante: la demora de la etapa de asignación (`delay.assignment.ms
= 50`) también se aplica por orden. Las cuatro etapas suman `Σ d_i = 430 ms` de
trabajo por orden repartido entre etapas.

---

## 3. Modelo teórico: la granja como línea de producción

### 3.1 Capacidad y ciclo de una etapa

Una etapa con `W_i` workers que tardan `d_i` por orden procesa, en régimen
estacionario (todos los workers ocupados), una orden cada

```text
ciclo_i = d_i / W_i
```

Es decir, su **capacidad** (órdenes por unidad de tiempo) es `W_i / d_i` y su
**tiempo entre órdenes** es `d_i / W_i`. Agregar workers divide el ciclo; agregar
demora lo multiplica.

### 3.2 La etapa gobernante (cuello de botella del throughput)

En una línea de ensamblaje en régimen permanente, **el sistema entero avanza al
ritmo de la etapa más lenta**. La etapa más lenta es la que maximiza `d_i / W_i`:

```text
ciclo = max_i ( d_i / W_i )
```

Ninguna etapa más rápida puede "ayudar" a una más lenta: las órdenes se acumulan
en la cola de entrada de la etapa lenta y el resto se queda esperando. Por eso
**el throughput del pipeline es el de su cuello de botella**.

### 3.3 Latencia de llenado y makespan

El tiempo total (`makespan`, medido como `durationMillis`) tiene dos partes:

1. **Latencia de llenado/vaciado**: la primera orden tiene que atravesar las
   cuatro etapas, aportando aproximadamente `Σ d_i`. Es el "arranque" del
   pipeline, no se puede pipelinear.
2. **Régimen estacionario**: a partir de la primera orden que sale, el pipeline
   entrega una orden cada `ciclo`. Las `N − 1` órdenes restantes aportan
   `(N − 1) · ciclo`.

De ahí:

```text
makespan ≈ ( Σ_i d_i ) + (N − 1) · max_i ( d_i / W_i )
```

### 3.4 Aplicación a la configuración oficial

| Etapa | `d_i` (ms) | `W_i` | `d_i / W_i` (ms) |
| --- | ---: | ---: | ---: |
| Asignación | 50 | 3 | 16.7 |
| Validación | 120 | 2 | **60** |
| Impresión | 180 | 3 | **60** |
| Control de calidad | 80 | 2 | 40 |

**Validación e impresión empatan** en 60 ms por orden, así que en el modelo
simple el cuello de botella es el empate. Predicción:

```text
makespan ≈ 430 + 499 · 60 = 30 370 ms ≈ 30.4 s
```

> Matiz importante : el "empate" es de **ciclo**, pero validación
> procesa las 500 órdenes e impresión solo ~429 (las rechazadas salen antes).
> Por eso, medido empíricamente, **validación es la etapa verdaderamente
> gobernante**. El modelo simple sirve igual para la oficial porque el conteo de
> la etapa gobernante resulta ser 500; pero se desvía en configuraciones donde
> impresión queda como único cuello.

### 3.5 Supuestos del modelo y por qué igual sirve

El modelo es una **cota inferior ideal**; ignora:

- arranque de la JVM y calentamiento del JIT;
- contención real sobre estructuras compartidas (lock del `EventLog`);
- desperdicio de CPU por cambio de contexto y planificación del SO;
- tiempos de E/S al escribir `eventos.csv`, `elementos.csv`, `resumen.properties`;
- granularidad del reloj (medimos en ms).

Por eso el observado debería quedar **ligeramente por encima** de la predicción,
no por debajo. Que la diferencia sea chica (del orden del 1 %) es lo que valida
el modelo: confirma que las etapas gobernantes son efectivamente validación e
impresión con ciclo de 60 ms.

---

## 4. La hipótesis del cuello de botella

### 4.1 Qué es un cuello de botella

Un **cuello de botella** es la etapa que limita el throughput del sistema: su
ciclo es el máximo. Mejorar cualquier otra etapa no cambia el ritmo global.

### 4.2 El caso interesante: validación e impresión empatadas

En la config oficial, validación (120/2) e impresión (180/3) tienen **ambas ciclo
60 ms**. La etapa de calidad (80/2 = 40) y asignación (50/3 ≈ 16.7) son más
rápidas. Entonces el sistema está gobernado por **dos** etapas simultáneamente.

### 4.3 Hipótesis 1: mejorar UN solo cuello NO reduce el makespan

Si optimizamos **solo** validación (por ejemplo, 4 workers → 120/4 = 30 ms) pero
impresión sigue en 60 ms, entonces:

```text
max(30, 60, 40, 16.7) = 60   -> sin cambios
```

El makespan cambia, pero no mejora, porque impresión sigue siendo el límite. Lo
mismo si optimizamos **solo** impresión (4 workers → 180/4 = 45 ms): validación
queda en 60 y `max = 60`.

**Predicción (modelo simple):** las variantes `3-4-3-2` (sube validación) y
`3-2-4-2` (sube impresión) deben dar **~30.4 s**, igual que la oficial, dentro
del ruido.

> Refinamiento (ver secciones 9.4-9.6): el experimento mostró que esta predicción
> es **incorrecta para `3-4-3-2`**, porque validación e impresión **no procesan
> la misma cantidad de órdenes** y por lo tanto no están realmente empatadas. La
> etapa gobernante es la de mayor **ocupación total** (`M_i · d_i/W_i`), no la de
> mayor ciclo aislado.

### 4.4 Hipótesis 2: mejorar AMBOS cuellos SÍ reduce el makespan

Si subimos validación **e** impresión (4 y 4 → 30 y 45 ms):

```text
max(30, 45, 40, 16.7) = 45   -> mejora
```

El nuevo límite pasa a ser impresión con 45 ms por orden. Makespan ≈
`430 + 499·45 = 22 885 ms ≈ 22.9 s`.

**Predicción (modelo simple):** la variante `3-4-4-2` debe dar **~22.9 s**,
notablemente menos que la oficial.

> Refinamiento (sección 9.6): medido 19 800 ms ≈ 19.8 s; el ajuste por conteo de
> órdenes predice 19 690 ms. El modelo simple sobreestima porque impresión procesa
> menos órdenes que las 500.

### 4.5 Por qué (la clave está en el `max`)

El makespan depende de `max_i(d_i/W_i)`. Si dos etapas están empatadas en el
máximo y bajamos **una**, el `max` sigue siendo el valor de la otra → no cambia
el ritmo. Recién cuando bajamos **las dos** (o bajamos la que queda como nuevo
máximo) el `max` se reduce. Es un efecto de "el techo lo fija la viga más alta de
un empate".

Además, el aporte de la latencia de llenado (`Σ d_i = 430 ms`) se mantiene
constante porque no cambiamos demoras, solo hilos.

### 4.6 Efectos de segundo orden a vigilar

El modelo predice **empates exactos** (30 370 ms en tres variantes y 22 885 ms en
otras). En la práctica puede haber diferencias porque:

- al agregar workers hay **más contención** sobre las colas y el `EventLog`;
- el sistema operativo puede planificar de forma no ideal;
- la latencia de llenado real incluye arranque de hilos (`StartGate`) y JIT.

Por eso el experimento no solo confirma o refuta el modelo, sino que **mide
cuánto se despega** el mundo real de la predicción ideal.

---

## 5. Diseño experimental

### 5.1 Variables

- **Independientes (las que movemos):**
  - cantidad de hilos por etapa (`threads.*`), barrido `Threads`;
  - demoras por etapa (`delay.*`), barrido `Delays`.
- **Dependientes (las que medimos):**
  - `durationMillis` (tiempo total de la simulación);
  - secundariamente, la **distribución de estados finales**
    (approved/rejected/printFailed/defective).
- **Controladas (fijas para aislar el efecto):**
  - semilla (`random-seed=20261001`), cantidad de órdenes (500),
    cantidad de impresoras (10×20), probabilidades (85/90/95).

### 5.2 El determinismo es una herramienta, no una curiosidad

`OutcomeDecider` es determinista para una semilla y un id de orden. Con la
**misma config**, las 500 órdenes caen siempre en la **misma** distribución de
estados. Eso es clave: si los estados fueran aleatorios, no podríamos separar
"cambios por azar" de "cambios por la variable que movimos". Con semilla fija,
**la única fuente de variación entre corridas idénticas es el tiempo**. Por eso
el experimento mide tiempo y, de paso, **verifica el determinismo**: la
distribución de estados debe ser idéntica entre corridas de la misma variante.

### 5.3 Métrica: mediana de `durationMillis`, y por qué 5 corridas

- Se corre cada variante **5 veces** (parámetro `-Runs`, default 5). Es un
  equilibrio: suficiente para ver dispersión y obtener una mediana estable, sin
  volver el barrido eterno.
- Se reporta la **mediana** (con mín. y máx.) en vez del promedio porque el tiempo
  tiene *outliers* hacia arriba; la mediana es robusta a esos valores.
- El **mínimo** es una estimación del "mejor caso" (menos ruido) y el **máximo**
  del "peor caso". La diferencia `max − min` da una idea de la dispersión.

### 5.4 Un factor por vez y por qué no se combinan

Combinar hilos **y** demoras a la vez haría imposible atribuir el efecto. El
diseño corre:

1. barrido de hilos con demoras oficiales fijas;
2. barrido de demoras con hilos oficiales fijos.

Así cada tabla tiene una única causa. 


## 6. La herramienta: `run-experiments.ps1`

### 6.1 Cómo correrlo

Desde PowerShell, en la raíz del repo:

```powershell
# Corrida oficial (5 corridas) — el caso más común
powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1

# Forzar el uso del jar existente (sin Maven)
powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1 -SkipBuild

# Barrido de hilos
powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1 -Scenario Threads

# Barrido de demoras (o todo junto)
powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1 -Scenario Delays
powershell -ExecutionPolicy Bypass -File tools\analisis\run-experiments.ps1 -Scenario All
```

Para una prueba rápida se puede bajar `-Orders` (por ejemplo `-Orders 50`).

---

## 7. Resultados: una corrida

Cada simulación escribe en `resultados/`:

- `resumen.properties`: el resumen escalar. Ejemplo real (corrida de 500 órdenes):

```properties
allThreadsTerminated=true
approvedOrders=359
defectiveOrders=25
durationMillis=26201
printFailedOrders=45
processedOrders=500
rejectedOrders=71
remainingIntermediateOrders=0
totalOrders=500
```

Lectura de los campos:

- `totalOrders=500` y `processedOrders=500`: entraron y salieron las 500.
- `approvedOrders=359`, `rejectedOrders=71`, `printFailedOrders=45`,
  `defectiveOrders=25`: suma `359+71+45+25 = 500`, es decir, **cada orden llegó a
  exactamente un estado terminal**.
- `remainingIntermediateOrders=0`: no quedó ninguna orden a medio camino.
- `allThreadsTerminated=true`: todos los workers cerraron.
- `durationMillis`: el tiempo total que medimos.

- `elementos.csv`: una fila por orden con su estado final y contadores por etapa:


## 8. Resultados: las 5 corridas oficiales

### 8.1 Tabla 

| Corrida | durationMillis | approved | rejected | printFailed | defective |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 30 558 | 359 | 71 | 45 | 25 |
| 2 | 30 567 | 359 | 71 | 45 | 25 |
| 3 | 30 577 | 359 | 71 | 45 | 25 |
| 4 | 30 562 | 359 | 71 | 45 | 25 |
| 5 | 30 569 | 359 | 71 | 45 | 25 |
| **Mediana** | **30 567** | 359 | 71 | 45 | 25 |
| **mín / máx** | **30 558 / 30 577** | | | | |

### 8.2 Teórico vs observado

- Predicción: **30 370 ms**.
- Mediana observada: **30 567 ms**.
- Diferencia: `(30 567 − 30 370) / 30 370 ≈ 0.65 %`.

La coincidencia es muy buena. El pequeño exceso es exactamente lo esperable:
arranque de la JVM, llenado/vaciado real del pipeline, contención del `EventLog`,
E/S de archivos y planificación del SO. El modelo de cuello de botella queda
validado. En rigor (ver sección 9.6), **validación es la etapa gobernante real**:
impresión empata en ciclo (60 ms/orden) pero procesa menos órdenes (429 contra
500), así que la ocupación total la fija validación.

### 8.3 Dispersión entre corridas

`máx − mín = 30 577 − 30 558 = 19 ms`, es decir **~0.06 %**. Es una dispersión
muy baja: la corrida es esencialmente estable. Esto es coherente con un
`durationMillis` dominado por las demoras configuradas y no por ruido aleatorio.

### 8.4 Determinismo de estados

Las cinco corridas dieron **exactamente la misma** distribución
`359 / 71 / 45 / 25`. Como la semilla es fija y `OutcomeDecider` es determinista,
los estados no cambian; **solo cambia el tiempo**. Este es el punto que
demuestra que el determinismo de negocio está desacoplado del *timing*.

## 9. Barrido de hilos (`Threads`)

### 9.1 Por qué se eligen estas combinaciones

Las combinaciones no son arbitrarias: están armadas para poner a prueba la
hipótesis del cuello de botella (sección 4) con el **mínimo** de variantes
necesarias.

| Variante | asig/val/imp/cal | `d/W` (ms) | Cuello resultante | Qué prueba |
| --- | --- | --- | --- | --- |
| `threads-1-1-1-1` | 1/1/1/1 | 50 / 120 / 180 / 80 | impresión (180) | Base mínima: todas lentas, un worker por etapa. |
| `threads-2-2-2-2` | 2/2/2/2 | 25 / 60 / 90 / 40 | impresión (90) | Duplicar todo respecto de 1-1-1-1. |
| `threads-3-2-3-2` | 3/2/3/2 | 16.7 / 60 / 60 / 40 | val = imp (60) | **Oficial** (línea base). |
| `threads-4-4-4-4` | 4/4/4/4 | 12.5 / 30 / 45 / 20 | impresión (45) | Máximo de hilos: mejor caso. |
| `threads-3-4-3-2` | 3/4/3/2 | 16.7 / 30 / 60 / 40 | impresión (60) | Sube **solo** validación → según H1, **no mejora**. |
| `threads-3-2-4-2` | 3/2/4/2 | 16.7 / 60 / 45 / 40 | validación (60) | Sube **solo** impresión → según H1, **no mejora**. |
| `threads-3-4-4-2` | 3/4/4/2 | 16.7 / 30 / 45 / 40 | impresión (45) | Sube **ambos** → según H2, **sí mejora**. |

La lógica es un experimento controlado sobre el empate validación/impresión:

- dos variantes "de control" (`1-1-1-1`, `2-2-2-2`, `4-4-4-4`) que escalan todos
  los hilos en conjunto;
- la línea base (`3-2-3-2`);
- dos variantes que suben **un solo** cuello (`3-4-3-2`, `3-2-4-2`), que se
  esperan **iguales** a la base;
- una variante que sube **ambos** (`3-4-4-2`), que se espera claramente más
  rápida.

### 9.2 Predicciones (con `Σ d_i = 430 ms` fijo y `N = 500`)

| Variante | ciclo teórico (ms) | makespan teórico (ms) |
| --- | ---: | ---: |
| 1/1/1/1 | 180 | ≈ 90 250 |
| 2/2/2/2 | 90 | ≈ 45 340 |
| 3/2/3/2 (oficial) | 60 | ≈ 30 370 |
| 4/4/4/4 | 45 | ≈ 22 885 |
| 3/4/3/2 | 60 | ≈ 30 370 (sin mejora) |
| 3/2/4/2 | 60 | ≈ 30 370 (sin mejora) |
| 3/4/4/2 | 45 | ≈ 22 885 |

### 9.3 Resultados medidos

Datos de `salidas/resumen.md` (5 corridas por variante, demoras oficiales fijas):

| Variante | asig/val/imp/cal | mediana (ms) | mín | máx | pred. simple (ms) | pred. ajustada (ms) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| threads-1-1-1-1 | 1/1/1/1 | 77 808 | 77 798 | 77 810 | 90 250 | 77 470 |
| threads-2-2-2-2 | 2/2/2/2 | 39 145 | 39 131 | 39 154 | 45 340 | 38 950 |
| threads-3-2-3-2 | 3/2/3/2 | 30 561 | 30 557 | 30 566 | 30 370 | 30 370 |
| threads-4-4-4-4 | 4/4/4/4 | 19 808 | 19 801 | 19 810 | 22 885 | 19 690 |
| threads-3-4-3-2 | 3/4/3/2 | 26 214 | 26 201 | 26 219 | 30 370 | 26 110 |
| threads-3-2-4-2 | 3/2/4/2 | 30 507 | 30 501 | 30 516 | 30 370 | 30 370 |
| threads-3-4-4-2 | 3/4/4/2 | 19 800 | 19 798 | 19 812 | 22 885 | 19 690 |

Observaciones inmediatas:

- **Dispersión mínima**: en todas las variantes `máx − mín ≤ 27 ms` (≈0.1 %). El
  tiempo es muy estable.
- **Estados idénticos**: las siete variantes dieron la misma distribución
  `359 / 71 / 45 / 25`. Cambiar la cantidad de hilos **no altera el resultado de
  negocio**, solo el tiempo (determinismo confirmado también acá).
- **El modelo simple falla** en todas las variantes gobernadas por impresión:
  `1-1-1-1` medido 77 808 vs 90 250 predicho (−14 %); `3-4-3-2` medido 26 214 vs
  30 370 predicho (−14 %); etc.
- **La predicción ajustada** (sección 9.6) cae a ≤0.6 % en **todas** las
  variantes.

### 9.5 Discusión: por qué el modelo simple se equivoca

El modelo de la sección 3 supone que **las 500 órdenes pasan por las cuatro
etapas**. Eso es falso: las órdenes rechazadas en validación salen del pipeline y
las que fallan en impresión no llegan a calidad. Con la semilla oficial:

- asignación y validación procesan **500** órdenes;
- impresión procesa **429** (500 − 71 rechazadas);
- calidad procesa **384** (429 − 45 fallos de impresión).

Como impresión procesa ~14 % menos órdenes que validación, una etapa de impresión
"gobernante" tarda ~14 % menos de lo que predice el modelo simple. De ahí el
exceso sistemático de ~14-16 % en todas las variantes con impresión como cuello.

Esto **explica la asimetría** que el modelo simple no veía:

- `3-4-3-2` (sube **solo** validación): validación baja a 30 ms/orden y deja de
  gobernar; **impresión pasa a gobernar** y, como procesa menos órdenes, el
  makespan **baja a 26 214 ms**. El modelo simple decía que no mejoraría; **sí
  mejora**.
- `3-2-4-2` (sube **solo** impresión): validación sigue gobernando (procesa las
  **500** órdenes) → **no mejora** (30 507 ms).

Es decir: **la hipótesis 1 se cumple solo si las etapas empatadas procesan la
misma cantidad de órdenes.** Cuando no, el empate de ciclos se rompe por el
conteo. Esta es la conclusión más fuerte del barrido.

### 9.6 Modelo refinado: el peso del conteo de órdenes

Definimos la **ocupación total** de la etapa `i` como

```text
T_i = M_i · ( d_i / W_i )
```

donde `M_i` es la cantidad de órdenes que **efectivamente** pasan por la etapa
`i` (`M = N` para asignación y validación; `N − rechazadas` para impresión;
`N − rechazadas − printFailed` para calidad). La etapa gobernante es la de **mayor
`T_i`** (no la de mayor `d_i/W_i` aislado), y:

```text
makespan ≈ ( Σ_i d_i ) + ( M_gov − 1 ) · ( d_gov / W_gov )
```

Para la config oficial (3/2/3/2) y `N = 500`:

| Etapa | `M_i` | `d_i/W_i` (ms) | `T_i` (ms) |
| --- | ---: | ---: | ---: |
| Asignación | 500 | 16.7 | 8 333 |
| Validación | 500 | 60 | **30 000** |
| Impresión | 429 | 60 | 25 740 |
| Calidad | 384 | 40 | 15 360 |

La etapa gobernante es **validación** (T = 30 000), no un empate real: aunque
impresión tiene el mismo ciclo, procesa menos órdenes. Por eso el makespan
ajustado coincide a ≤0.6 % con lo medido.

Etapa gobernante y predicción por variante (`T_val`, `T_imp`, `T_cal`; `T_asig`
nunca gobierna):

| Variante | `T_val` | `T_imp` | `T_cal` | Gobernante | makespan ajustado (ms) | medido (ms) |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| 1/1/1/1 | 60 000 | **77 220** | 30 720 | impresión | 77 470 | 77 808 |
| 2/2/2/2 | 30 000 | **38 610** | 15 360 | impresión | 38 950 | 39 145 |
| 3/2/3/2 | **30 000** | 25 740 | 15 360 | validación | 30 370 | 30 561 |
| 4/4/4/4 | 15 000 | **19 305** | 7 680 | impresión | 19 690 | 19 808 |
| 3/4/3/2 | 15 000 | **25 740** | 15 360 | impresión | 26 110 | 26 214 |
| 3/2/4/2 | **30 000** | 19 305 | 15 360 | validación | 30 370 | 30 507 |
| 3/4/4/2 | 15 000 | **19 305** | 15 360 | impresión | 19 690 | 19 800 |

Conclusión de este barrido: el experimento **no solo valida el modelo de cuello
de botella, lo refina**. La lección es que el cuello de botella depende de
`ciclo × cantidad de órdenes`, y que en pipelines con etapas que descartan
órdenes, el conteo puede romper un empate de ciclos. El modelo simple de la
sección 3 es correcto cuando todas las etapas procesan la misma cantidad de
órdenes (por ejemplo, en el barrido de demoras con validación como gobernante).

---

## 10. Barrido de demoras (`Delays`)

### 10.1 Por qué se cambian las demoras

En el barrido de hilos movemos la **capacidad** (workers). En el de demoras
movemos la **carga de trabajo por orden** (cuánto tarda cada etapa). Sirve para:

1. **Verificar la linealidad del modelo**: el makespan debe crecer de forma
   aproximadamente lineal con el factor `k` aplicado a todas las demoras.
2. **Explorar otro régimen**: con demoras chicas el sistema está más cerca del
   "puro overhead" (arranque, planificación), que es justo lo que el modelo
   ignora. El caso `k=0` (demoras nulas) mide ese piso.
3. **Confirmar que el cuello de botella no cambia**: al escalar **todas** las
   demoras por el mismo `k`, las razones `d_i/W_i` se conservan, así que
   validación e impresión siguen empatadas. Esto aísla el efecto "magnitud" del
   efecto "forma".

Definimos `ciclo_i(k) = k · d_i / W_i`, entonces
`makespan ≈ k·(Σ d_i) + (N−1)·max_i(k·d_i/W_i) = k·(Σ d_i) + k·(N−1)·ciclo_base`.
Es decir, para `k > 0` el makespan escala **linealmente** con `k`.

### 10.2 Predicciones (hilos oficiales 3/2/3/2, `Σ d_i = 430`)

| factor `k` | demoras (ms) | cuello | ciclo (ms) | makespan teórico (ms) |
| --- | --- | --- | ---: | ---: |
| ×0 | 0/0/0/0 | — | 0 | ≈ 0 (solo overhead) |
| ×0.5 | 25/60/90/40 | val = imp | 30 | ≈ 15 185 |
| ×1 (oficial) | 50/120/180/80 | val = imp | 60 | ≈ 30 370 |
| ×2 | 100/240/360/160 | val = imp | 120 | ≈ 60 740 |

### 10.3 Resultados medidos (oficial)

Datos de `salidas/resumen.md` (5 corridas por variante, hilos oficiales
3/2/3/2):

| variante | factor `k` | demoras (ms) | mediana (ms) | mín | máx | pred. teórica (ms) | exceso (ms) |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| delays-0 | 0 | 0/0/0/0 | 61 | 59 | 67 | 0 | 61 |
| delays-half | 0.5 | 25/60/90/40 | 15 398 | 15 376 | 15 484 | 15 185 | 213 |
| delays-official | 1 | 50/120/180/80 | 30 564 | 30 557 | 30 576 | 30 370 | 194 |
| delays-double | 2 | 100/240/360/160 | 60 931 | 60 919 | 60 962 | 60 740 | 191 |

Observaciones:

- **Linealidad confirmada**: `delays-half / delays-official = 0.504` y
  `delays-double / delays-official = 1.994`. Duplicar demoras duplica el tiempo;
  partirlas a la mitad lo reduce a la mitad.
- **Exceso cuasi-constante** (~190-215 ms) para `k > 0`: es el overhead fijo
  (arranque de la JVM, cierre de hilos, E/S de archivos y contención), que no
  depende del factor.
- **`k = 0` → 61 ms**: con demoras nulas el pipeline queda reducido a su costo de
  infraestructura puro. Es menos de 1/500 del tiempo oficial, lo que confirma que
  el tiempo medido está dominado por las demoras de trabajo, no por el andamiaje
  concurrente.
- **Estados idénticos** `359 / 71 / 45 / 25` en las cuatro variantes: cambiar las
  demoras tampoco altera el resultado de negocio (determinismo).

### 10.4 Interpretación

- El makespan crece de forma **aproximadamente lineal** con `k`, consistente con
  `makespan ≈ k · ( Σ d_i ) + ( M_gov − 1 ) · k · ( d_gov / W_gov )`.
- La etapa gobernante **no cambia** al escalar todas las demoras por el mismo
  factor: las razones `d_i / W_i` se conservan, así que validación sigue
  gobernando (con `M = 500`). Por eso acá el modelo simple con `N = 500` **es
  correcto**.
- El desvío respecto de la predicción es chico (≤1 %) y **constante**: otra
  evidencia de que es overhead y no un error del modelo.

### 10.5 Lectura conjunta de los dos barridos

- El de **hilos** mueve la **capacidad** (`W_i`) y mostró que el cuello de botella
  depende de `ciclo × cantidad de órdenes`.
- El de **demoras** mueve la **carga** (`d_i`) y mostró un escalado lineal limpio,
  con la etapa gobernante fija.
- En ambos, los estados finales fueron idénticos: **el tiempo cambia, el negocio
  no**.

---

## 11. Amenazas a la validez

- **Arranque de la JVM y JIT**: la primera parte de cada corrida paga
  calentamiento; por eso el observado queda por encima del ideal.
- **Recolección de basura (GC)**: introduce pausas no deterministas en el tiempo
  (no en los estados).
- **Planificación del SO y contención**: con más workers hay más contención
  (colas, `EventLog`), lo que puede despegar los empates teóricos.
- **Procesos en segundo plano**: IDE, *language server*, navegador, etc. inflan
  el tiempo. Para la toma "oficial" conviene la máquina ociosa.
- **E/S**: escribir tres archivos de salida aporta un costo que el modelo ignora.
- **Resolución de ms**: medición con granularidad de milisegundos.
- **Tamaño de muestra**: 5 corridas es un compromiso; el *outlier* de una corrida
  se mitiga con la mediana.
- **Máquina única**: no se compara con otro hardware.

Ninguna de estas amenazas invalida el modelo; todas empujan el observado **hacia
arriba** de la predicción, que es exactamente lo observado.

---

