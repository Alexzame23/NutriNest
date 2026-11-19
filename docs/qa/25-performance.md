# Fase 25 · Rendimiento de catálogo y cestas

## Entorno y reproducción

La referencia Android es `91c4da1`. Las dos pasadas usan el mismo emulador `Medium_Phone_API_36.0` x86_64, Android 16, APK debug y JDK 21, sobre Windows con AMD Ryzen 7 7735HS. El backend usa Node 24.11.0 y SQLite 3.50.4 en ese equipo con bases locales temporales, un reloj fijo por ciclo y proveedores simulados inyectados. El análisis DAO usa Python 3.13 y SQLite 3.50.4, el esquema exportado v11 y el asset 2026.07.29. [Referencia Android](25-android-before.json) · [Android optimizado](25-android-after.json) · [Planes SQLite](25-sqlite.json).

En un emulador dedicado, instalar ambos APK debug y apuntar ADB de forma explícita al serial de pruebas. El ejecutor recrea únicamente las bases `perf25-*.db` que usa la instrumentación. Arranque con `am start -W` y 11 muestras por estado. El frío usa `-S` para terminar el proceso. El templado recrea la Activity con `NEW_TASK | CLEAR_TASK` y comprueba que el PID se conserva. Para el caliente, Android trae la tarea al frente en el mismo proceso y esta imagen solo devuelve `WaitTime`. Frío y templado usan `TotalTime`, hasta el primer dibujo. La lectura de catálogo hasta su disponibilidad se mide de forma independiente.

Para repetir la referencia, usar un checkout de `91c4da1` con el ejecutor Android y su clase de instrumentación de esta fase. Generar los dos informes con el mismo AVD y opciones de compilación. El benchmark PostgreSQL reutiliza el prefijo de dependencias de la fase 11.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python tools/benchmark_android.py --serial emulator-5554 --output build/android-perf.json
python tools/benchmark_catalog_sql.py
node tools/benchmark_prices.mjs
node tools/benchmark_commercial_sql.cjs build/phase11-verification
```

La instrumentación usa `elapsedRealtimeNanos`, 5–31 muestras por escenario y descarta 1–5 iteraciones de calentamiento. p95 es el rango más próximo hacia arriba. Con 5, 9 u 11 muestras, p95 es el máximo observado. Los datos de catálogo incluyen 923 alimentos del asset, 31.337 nutrientes, 1.306 porciones, 1.163 alias y 250 recetas incorporadas. La receta añade 8 ingredientes y 4 pasos vía el repositorio. La generación usa 7 días, 3 perfiles, 3 franjas y semilla 25. La migración parte de un fixture v8 vacío. Los datos históricos y personalizados se validan en las pruebas de regresión.

Las cestas cubren 10, 50 y 100 líneas, con 100 productos, dos formatos por producto y una observación por formato y tienda. En tres tiendas hay 600 observaciones y en seis hay 1.200. Se prueban hasta tres visitas. El motor evalúa 7 y 41 subconjuntos respectivamente. El escenario cubre el cálculo local desde un snapshot ya validado. Las pruebas funcionales incluyen promociones, sustituciones, precios ambiguos, disponibilidad, vigencia y límites de búsqueda.

## Android

| Operación | Mediana / p95 antes → después (ms) |
| --- | ---: |
| Arranque frío | 4 865,0 / 12 869,0 → 4 785,0 / 6 409,0 |
| Arranque templado | 550,0 / 856,0 → 578,0 / 758,0 |
| Reanudación caliente (WaitTime) | 80,0 / 131,0 → 85,0 / 171,0 |
| Primera apertura del catálogo | 12 735,7 / 16 450,5 → 13 191,5 / 14 428,0 |
| Migración v8→v11 con importación | 12 251,3 / 13 422,2 → 12 979,4 / 13 481,1 |
| Reapertura Room | 284,4 / 300,0 → 297,2 / 343,0 |
| Búsqueda «aceite» | 13,3 / 23,8 → 13,6 / 22,2 |
| Búsqueda «puerco» | 14,8 / 24,9 → 11,6 / 20,9 |
| Búsqueda «platano» | 13,0 / 24,2 → 12,9 / 23,6 |
| Búsqueda sin resultados | 10,9 / 18,0 → 12,5 / 19,6 |
| Crear receta | 4,4 / 20,2 → 4,9 / 25,0 |
| Generar menú semanal | 208,8 / 251,0 → 216,3 / 236,8 |
| Leer y parsear caché | 3,6 / 12,6 → 4,6 / 6,4 |

| Líneas × tiendas | Comparar líneas (ms) | Tienda única (ms) | Varias tiendas (ms) |
| --- | ---: | ---: | ---: |
| 10 × 3 | 3,0 / 6,5 → 3,0 / 4,3 | 14,5 / 21,1 → 16,5 / 26,3 | 31,6 / 53,9 → 28,3 / 35,2 |
| 50 × 3 | 5,1 / 9,6 → 5,8 / 13,3 | 32,4 / 39,4 → 37,5 / 43,2 | 89,2 / 116,8 → 73,2 / 84,7 |
| 100 × 3 | 5,9 / 11,6 → 5,8 / 12,6 | 48,2 / 61,0 → 51,2 / 59,6 | 143,2 / 154,4 → 127,6 / 138,2 |
| 10 × 6 | 1,6 / 3,8 → 1,2 / 3,3 | 8,0 / 15,5 → 11,2 / 20,3 | 85,2 / 93,1 → 62,7 / 71,3 |
| 50 × 6 | 5,2 / 9,0 → 5,6 / 11,9 | 43,0 / 49,0 → 46,2 / 52,5 | 514,4 / 574,8 → 422,5 / 480,6 |
| 100 × 6 | 7,9 / 17,0 → 12,9 / 22,5 | 78,5 / 126,8 → 114,9 / 132,2 | 1 046,2 / 1 197,2 → 819,2 / 896,0 |

La cesta de 100 artículos y seis tiendas mejoró un 22 % en mediana y un 25 % en p95. Para ese caso se pasa de resolver los formatos hasta 9.600 veces en los subconjuntos a prepararlos 600 veces. La mediana de tienda única para los mismos datos subió de 78,5 a 114,9 ms y su p95 subió de 126,8 a 132,2 ms. La primera apertura del catálogo y la migración mantienen medianas en torno a 13 s. La reducción de consultas de existencia no produjo una mejora de mediana de extremo a extremo. El p95 de arranque frío mejoró entre pasadas, con una primera muestra de referencia de 12.869 ms que concentra la diferencia.

El archivo principal del catálogo mide 5.578.752 → 5.566.464 bytes tras cerrar Room. El snapshot persistido mide 2.767 bytes. El PSS de todo el proceso fue de 203.994 → 224.968 KiB al terminar el caso de caché y de 141.022 → 146.401 KiB tras el último ensayo de arranque. El PSS de instrumentación incluye código, ejecución previa y memoria compartida, por lo que no estima la memoria exclusiva de la caché.

Room conserva un payload por región y puede retirar el BLOB vencido conservando la revisión. El servidor limita el snapshot a 1 MiB.

Como techos exploratorios de esta ejecución se usan 160 MiB de PSS tras arranque, 256 MiB durante el caso de caché y 6 MiB para el catálogo cerrado. Ambas pasadas quedan por debajo. La muestra de PSS sirve como referencia puntual para repetir en un dispositivo real.

Todos los escenarios medidos quedaron dentro de sus presupuestos locales p95.

`dumpsys gfxinfo` del último proceso recogió `Total frames rendered: 7, Janky frames: 7 (100.00%), 95th percentile: 2450ms` en la referencia y `Total frames rendered: 6, Janky frames: 6 (100.00%), 95th percentile: 2400ms` en la versión final. La muestra se limita al último proceso de la secuencia de arranque y presenta jank en debug. La ausencia de bloqueo del hilo principal en toda la UI sigue pendiente de trazas en hardware físico.

Los presupuestos de diagnóstico p95 para este emulador debug parten de la referencia. Son 13.500 ms para arranque frío, 1.000 ms para templado, 175 ms de espera para caliente, 17.000 ms para primera apertura de catálogo, 14.500 ms para v8→v11, 350 ms para reapertura, 35 ms para búsqueda, 30 ms para crear receta, 300 ms para generar menú, 25 ms para comparación por línea, 150 ms para tienda única, 180 ms para hasta tres tiendas, 1.300 ms para hasta seis y 20 ms para leer y parsear la caché. Estos límites permiten repetir la comprobación local y sitúan la validación en hardware físico como criterio de la fase 26.

## Consultas, escrituras e integridad

- `observeFiltered` escanea ingredientes, canónicos y alias por `LIKE '%…%'`. La exclusión de ocultos usa el índice por estado y el enlace canónico usa el índice de correspondencias. Los B-tree de nombre normalizado siguen siendo aplicables a búsquedas de igualdad. El informe SQL usa un fixture canónico sintético y `ANALYZE`. El tiempo de Android incluye la presentación del repositorio, los resultados del asset real y las emisiones Room.
- La búsqueda de identidad canónica usa la PK entera. El panel nutricional usa la PK por ingrediente y etiqueta, con una B-tree temporal para ordenar el pequeño resultado. Las comprobaciones de referencia usan los índices por ingrediente de recetas y compras. La caché usa su PK por región. Las cifras y planes sirven para conservar los índices Room vigentes sin pagar nuevas escrituras para las búsquedas por subcadena. [Planes y muestras](25-sqlite.json).
- El importador elimina una lectura de existencia por alimento. Obtiene los IDs ya instalados en una consulta por rango sobre la PK dentro de la misma transacción y mantiene los INSERT que fallan ante conflictos inesperados. La memoria temporal crece con los IDs. Las conversiones hexadecimales de las huellas usan una tabla de 16 caracteres y conservan el formato, los tipos y las validaciones. El backfill todavía hace cinco consultas por huella y la curación vuelve a verificarlas. El seeder aún verifica la existencia por `remoteId` con un escaneo y el nombre con un recorrido del índice binario por la comparación `NOCASE`. Con 250 recetas, cada consulta aislada ronda 0,01–0,02 ms en SQLite de escritorio. Son los siguientes candidatos si sigue dominando la primera apertura.
- Generar menús carga recetas, ingredientes y planes en bloque. El repositorio agrupa los ingredientes y prepara las recetas en `Dispatchers.Default`, junto al generador. En el guardado de una receta, los pasos y los enlaces se guardan como listas en una transacción. El ViewModel actualiza el uso de cada ingrediente después del guardado. Esta escritura por ingrediente queda fuera del cronómetro de creación del repositorio.
- El optimizador conserva durante una comparación los formatos y exclusiones calculados para cada par de demanda y tienda. Los subconjuntos comparten esa preparación y conservan sus validaciones, desempates, promociones y aritmética exacta. El coste es una estructura temporal por par. La búsqueda combinatoria y sus límites previos siguen dominando las cestas grandes.
- El enriquecimiento de listas, las ofertas por ingrediente y el parseo de la caché se ejecutan en `Dispatchers.Default`. El motor de lista comprueba cancelación entre demandas y el buscador de cestas conserva sus comprobaciones dentro de la búsqueda. Room ejecuta el acceso a la base mediante sus ejecutores. Estas verificaciones cubren el despacho del trabajo pesado. La fluidez de UI completa requiere trazas de frames e hilos en una build de distribución.

## Backend y tareas programadas

| Productos × tiendas × regiones × proveedores | Mediana / p95 antes → después (ms) | Histórico al final (filas) | Publicación / bases cerradas finales (bytes) |
| --- | ---: | ---: | ---: |
| 5 × 1 × 1 × 1 | 12,86 / 26,88 → 11,58 / 28,73 | 130 | 4.556 / 118.784 |
| 20 × 1 × 1 × 1 | 17,58 / 40,32 → 13,75 / 29,31 | 520 | 16.216 / 401.408 |
| 20 × 3 × 1 × 1 | 23,32 / 47,08 → 17,00 / 37,07 | 1.560 | 30.912 / 1.085.440 |
| 20 × 3 × 4 × 1 | 89,90 / 142,37 → 72,82 / 103,43 | 6.240 | 123.648 / 4.341.760 |
| 20 × 3 × 4 × 2 | 170,25 / 250,98 → 155,15 / 185,79 | 12.480 | 247.296 / 8.683.520 |

La matriz usa 5 ciclos de calentamiento y 21 medidos. En el caso máximo, el aumento de RSS dentro del escenario fue de aproximadamente 22 → 18 MiB, incluido el muestreo del histórico para verificar su cardinalidad. El matching estable registró 422,66 / 492,58 → 400,84 / 473,78 ms de mediana / p95. Los resultados finales cumplen los presupuestos locales p95 de 300 ms para el ciclo agregado mayor y de 600 ms para matching estable. [Referencia del backend](25-backend.json) · [Backend optimizado](25-backend-after.json).

Se añadió `price_history_fetched_at_idx` sobre `fetched_at`. En 26.000 observaciones sintéticas, `EXPLAIN QUERY PLAN` pasó de `SCAN price_history` a `SEARCH price_history USING INDEX price_history_fetched_at_idx (fetched_at<?)`. Purgar 60 filas pasó de 16,16 a 1,75 ms de mediana (−89 %). Insertar lotes de 1.000 pasó de 5,91 a 7,31 ms (+24 %) y el archivo creció 303.104 bytes (+2,8 %). Los tiempos del ensayo aíslan las sentencias del coste de commit y usan rollback tras cada purga para repetir sobre las mismas filas. El alcance piloto admite 60 cotizaciones por ciclo y hasta 365 días de historial, por lo que se favorece el coste de purgas pequeñas y frecuentes. La primera apertura crea el índice en bases existentes.

Al medir solo el índice, el p95 del mayor escenario pasó a 367,43 ms y superó el presupuesto. La versión final prepara una vez por conexión las tres sentencias de lectura y escritura de evidencias y de inserción de histórico. En la ruta con cambios puede evitar hasta tres preparaciones por cada cotización. La transacción, los límites de revisión y la validación de procedencia se mantienen. La pasada con ambas optimizaciones dejó ese p95 en 185,79 ms. [Muestras con el índice solo](25-backend-index-only.json).

La sincronización mide el ciclo del worker con persistencia SQLite WAL y `synchronous=FULL`, generación de un proveedor local y publicación de snapshots. Los productos y tiendas recorren los límites operativos de 20 y 3 por lote. Cada región/proveedor usa un worker con su propia base. La ejecución serial mide el coste agregado. Las zonas se representan con regiones de sincronización independientes. El publicador actual emite precios por tienda y `zones: []`. Quedan pendientes la latencia de red y el servicio HTTP bajo concurrencia.

El matching evalúa 925 perfiles y 200 envases en un scope completo. Genera 185.000 pruebas de candidatura por pasada antes de valorar candidatas y guardar revisiones. `current()` usa el índice de PK `(ingredient, package, version)` para recuperar la última versión de cada par, con una búsqueda correlacionada por fila histórica. Los tiempos corresponden a reconciliaciones estables tras instalar la primera revisión, por lo que el crecimiento del historial y del número de candidatos puede elevar el coste.

El worker de precios comprueba derechos y cadencia, serializa ejecuciones por región/proveedor y conserva su límite de peticiones de red por 24 horas. La ruta de reintentos y la publicación condicional se mantienen. El recordatorio Android usa WorkManager semanal y puede reencolar al abrir la app para conservar su anclaje de viernes. Ese trabajo aparece incluido en el arranque de proceso, sin una atribución de coste aislada.

### Contrato PostgreSQL

En PostgreSQL 18.6 vía PGlite 0.5.16, el DDL real se pobló con 67.200 observaciones de 300 productos, cuatro tiendas, cuatro zonas, dos proveedores y 14 días. Cada consulta tiene cinco muestras de calentamiento y once medidas de tiempo de ejecución del motor. `EXPLAIN (ANALYZE, BUFFERS)` se ejecuta con el planificador en su configuración normal.

| Consulta | Mediana / p95 (ms) |
| --- | ---: |
| Precio más reciente, con índice compuesto | 0,09 / 0,23 |
| Precio más reciente, solo con índice por alcance | 4,73 / 5,45 |
| Ofertas para 10 ingredientes y 4 alcances | 1,32 / 4,46 |
| Ofertas para 50 ingredientes y 4 alcances | 5,52 / 5,96 |
| Ofertas para 100 ingredientes y 4 alcances | 9,65 / 10,91 |
| Insertar 640 observaciones con índice compuesto | 63,69 / 70,53 |
| Insertar 640 observaciones solo con índice por alcance | 53,56 / 60,72 |

El índice existente `price_observation_lookup_idx` reduce los hits de búfer de la consulta puntual de 1.189 a 4 en el plan representativo. Ocupa 2.056.192 bytes y la mediana de inserción fue un 19 % mayor con él. Las inserciones de prueba usan rollback y el ensayo que retira el índice solo modifica la instancia desechable. Se conserva el DDL comercial. El p95 de 10,91 ms para 100 ingredientes cubre una consulta agrupada de referencia, con la observación más reciente por producto y alcance. Los presupuestos locales iniciales son p95 ≤ 1 ms para la consulta puntual y ≤ 20 ms para 100 ingredientes. El worker que se ejecuta hoy usa SQLite. PGlite mide el motor en WebAssembly en memoria. El ensayo de servicio PostgreSQL con disco, concurrencia y latencia de red queda para la fase 26. [Planes, búferes y muestras PostgreSQL](25-postgresql.json) · [DDL](../prices/11-commercial-schema.sql) · [Dependencias locales](../prices/11-commercial-product-domain.md).

## Verificación y fase 26

Pasaron las 192 pruebas JVM y los 69 casos funcionales Android. El runner Android informó 73 casos sin fallos en 570,481 s, incluidos cuatro supuestos de opt-in de rendimiento. Los cuatro benchmarks se ejecutaron con ese opt-in en ambas pasadas. Las diez actualizaciones v1–v10 a v11 preservaron las filas históricas según el oráculo de fase 24. Las huellas críticas de v1–v8 coinciden además con el informe previo. Los fixtures v9 y v10 generan sus propios identificadores canónicos y fechas antes de comparar contra la copia actualizada. [Oráculo y referencia de migración](24-production-database-upgrades.md).

Pasaron 30 pruebas Node, la verificación del contrato OpenAPI y la publicación simulada. PostgreSQL verificó los decimales exactos, 22 restricciones y dos planes de índices. Las comprobaciones SQLite validaron el esquema Room, prepararon 93 consultas con cinco expansiones delegadas a Room y reprodujeron la recuperación de v3 tras la interrupción de su transacción de migración.

La fase 26 dispone de fixtures y presupuestos locales reproducibles. Antes de usar esos límites como criterio de entrega, medir startup y frames con Perfetto en una build de distribución sobre hardware físico. La primera apertura de catálogo y el perfil de memoria merecen la primera traza. Conviene comparar también la ruta de tienda única con seis tiendas, donde subió la mediana. Para PostgreSQL y la sincronización con IO real, usar un servicio de pruebas autorizado con presupuestos de concurrencia, disco y red propios.
