# Fase 23 · Pruebas de nutrición y precios

## Matriz de cobertura

| Área | Pruebas existentes reutilizadas | Cobertura ampliada en fase 23 | Verificación |
| --- | --- | --- | --- |
| Unidades y conversiones | `MeasureConversionsTest`, `FoodQuantityConversionsTest`, `RecipeQuantityTest`, `IngredientValidatorTest`. Masa, volumen, porciones, rendimiento, procedencia y entradas inválidas. | Factores imperiales exactos, preservación de masa neta y rechazo de evidencia doméstica contradictoria en ambos órdenes. | JVM, obligatoria en CI |
| Identidad canónica | `CuratedCatalogTest`, `CatalogReleaseGateTest` y `verify_curated_catalog.py`. Variantes crudas y cocinadas, estados pendientes, alias, revisión y reaplicación. | CI exige reconstruir las reglas editoriales y comprobar los vínculos de las recetas incluidas. La ambigüedad de medidas se prueba también en el dominio. | Python en CI, Room en emulador |
| Nutrición de recetas | `RecipeNaturalQuantitiesTest` y `CuratedCatalogTest`. Conservación de cantidades, evidencia, macros y referencias históricas de 250 recetas. | Room revierte la receta y su primer vínculo al fallar una FK. Reintentar conserva los cuatro valores esperados tanto en SQL como en Kotlin. Borrar retira la fila del agregado y sus ingredientes. | Room en emulador |
| Generador y listas | `MenuGeneratorTest`, `MenuResolverTest`, `IntakeAdjusterTest`, `ShoppingQuantityTest` y `NormalizedShoppingTest`. Perfiles, platos, bloqueos, franjas libres, agregación y cantidades de compra. | Un perfil sintético con una restricción en español impide que un plato bloqueado llegue al menú o a la lista. Si solo hay platos excluidos, la generación informa de falta de recetas. | JVM en CI, persistencia en emulador |
| Equivalencias producto–ingrediente | `matching.test.mjs` y `SingleStoreBasketOptimizerTest`. Evidencia, GTIN, preparación, revisiones, abstenciones y sustituciones autorizadas. | `PriceComparisonEngineTest` rechaza dos correspondencias del mismo ingrediente y envase en ambos órdenes. Una relación con otro ingrediente no interfiere. | Node y JVM, obligatorias en CI |
| €/kg, €/L, €/ud y envases | `FixedPriceComparisonTest` y `PriceComparisonEngineTest`. Tarifas, descuentos, precio proporcional, masas comestibles y pesada. | Demanda justo por debajo, en y por encima del límite de dos envases para kg, L y unidades. Comprueba el redondeo monetario después de multiplicar. | JVM, obligatoria en CI |
| Optimización | `SingleStoreBasketOptimizerTest` y `MultiStoreBasketOptimizerTest`. Oráculos exhaustivos con semillas, múltiples formatos, falta de cobertura, costes de visita, stock y límites de búsqueda. | Una sola visita coincide con el optimizador monotienda en tienda, asignación y coste para un ganador único, aunque se inviertan las ofertas. | JVM, obligatoria en CI |
| Preferencias | `RetailerSelectionRepositoryTest` y `ShoppingComparisonTest`. Reinicio, selección explícitamente vacía y restauración de valores iniciales. | La selección vacía de sucursales persiste aunque su cadena siga seleccionada. Otra cadena sin restricción conserva acceso. | JVM en CI, `SharedPreferences` en emulador |
| Errores y caducidad | `provider.test.mjs`, `RetailPriceViewModelTest`, `RetailPriceExperienceTest` y `PriceSnapshotCacheTest`. Caídas, gzip/JSON inválido, preservación de caché y estados UI. | Android verifica HTTP 302/429/503, timeout, ausencia de confirmación, clave duplicada, tamaño, hash y coherencia de revisión. El ViewModel comprueba la retirada de ofertas en el instante de expiración mediante tiempo virtual. | Node y JVM en CI, lector Android en emulador |
| Contratos REST y proveedores | `provider.test.mjs` comprueba Fake y Open Prices con exportaciones sintéticas. `price-sync.test.mjs` cubre SQLite, planificación y revalidación. `verify_commercial_contract.cjs` valida OpenAPI, JSON y PostgreSQL. | HTTP real en `127.0.0.1` contra el publicador y SQLite con puerto efímero. Verifica bytes, SHA-256, ETag con 304, método 405 y caducidad con 404/410. `HttpsRetailPriceProviderTest` consume el contrato con el parser Android real y una conexión HTTPS controlada. | Node en CI, lector Android en emulador, OpenAPI/PostgreSQL local |
| Migración e importación | `MigrationTest`, `FoodImportTest`, `PriceSnapshotCacheTest`, `verify_migration.py`, `check_dao_sql.py` y `check_room_schema.py`. Son comprobaciones complementarias de SQL, esquema exportado y apertura con Room. | La comparación de los exports Room es bloqueante y CI comprueba si KSP deja cambios de esquema pendientes. | Python y compilación en CI, Room en emulador |

Los optimizadores ya tienen oráculos independientes sobre entradas reproducibles. Las nuevas fronteras usan valores adyacentes de `Double`. No se añadió una biblioteca de property-based testing porque los oráculos y las tablas dirigidas cubren estos riesgos sin ampliar el classpath.

## Fixtures y límites

- `app/src/androidTest/assets/price-snapshot-v1.json` es el ejemplo sintético compartido con OpenAPI. El test Android calcula el SHA-256 y la longitud sobre los bytes que recibe, incluidos los finales de línea de la copia local.
- `app/src/androidTest/assets/price-manifest-v1-duplicate-revision.invalid.json` contiene dos claves `revision`. Conservar el JSON literal permite probar el parser antes de que otra capa normalice las claves.
- `backend/prices/fixtures/` contiene exportaciones y etiquetas simuladas versionadas en Git. El publicador local usa una base desechable y la suite de proveedores reemplaza el acceso externo en el límite de transporte. Los perfiles y precios de prueba son sintéticos y de uso exclusivo para QA.
- El contrato Android sustituye la construcción de `HttpsURLConnection` para controlar fallos y verificar el uso de la conexión. El contrato Node usa sockets HTTP reales. Quedan fuera TLS real, certificados del publicador de producción, disponibilidad de Open Prices y validación comercial. El publicador autorizado debe aportar `X-NutriNest-Price-Data: live` a ambas respuestas. El servidor local de pruebas carece de esa confirmación por diseño.
- Las comprobaciones de DAO preparan SQL en SQLite. Room expande los cinco parámetros de colección que omite el analizador Python. Un emulador verifica la ejecución Android de la suite. Siguen fuera las diferencias de fabricantes y la matriz completa de versiones hasta API 26.

## Reproducción

Usar JDK 17 o 21, el SDK de compilación del proyecto, Python 3 y Node 24. En Windows, sustituir `./gradlew` por `.\gradlew.bat`. Si la caché local está completa se puede añadir `--offline`. Para la suite instrumentada conectar un único emulador o fijar `ANDROID_SERIAL` y esperar a que `adb devices -l` lo muestre como `device`.

```sh
python tools/verify_migration.py
python tools/check_dao_sql.py
python tools/check_room_schema.py
python tools/check_strings.py
python tools/verify_curated_catalog.py
node --experimental-test-coverage --test backend/prices/provider.test.mjs backend/prices/price-sync.test.mjs backend/prices/matching.test.mjs
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

`verify_migration.py` crea la base que inspecciona `check_room_schema.py` y ejecuta las comprobaciones del módulo `verify_catalog_migration.py`. Los verificadores editoriales y de diseño pueden regenerar sus informes versionados. El verificador comercial externo se ejecuta con las [dependencias fijadas para OpenAPI y PostgreSQL](../prices/11-commercial-product-domain.md#verificación-reproducible).

Las pruebas críticas para un PR son la paridad de esquemas, el importador, las identidades canónicas, la aritmética y las optimizaciones, los contratos del backend y la compilación de ambos APK. Para preparar una entrega, ejecutar además los casos instrumentados de migración, nutrición, preferencias, caché y lectura HTTPS junto con la suite completa. Los XML de Gradle conservan resultados y tiempos por caso en `app/build/test-results/testDebugUnitTest/` y `app/build/outputs/androidTest-results/connected/debug/`.

## Resultados locales del 19 de noviembre de 2026

| Ejecución | Resultado | Duración |
| --- | --- | ---: |
| `testDebugUnitTest` | 192 correctos en 29 clases, cero fallos, errores u omisiones. | 32,94 s de shell |
| `assembleDebug` | APK debug generado en la primera compilación. Verificación incremental posterior correcta. | 8,18 s de shell, incremental |
| `assembleDebugAndroidTest` | APK instrumentado compilado y empaquetado. | 18,27 s de shell |
| `connectedDebugAndroidTest` | 59 correctos en `Medium_Phone_API_36.0`, cero fallos, errores u omisiones. | 9 min 18 s de shell, 507,78 s en XML |
| `verify_migration.py` | Correcto. 923 paneles de macros, 31 337 filas nutricionales exactas y 20 escrituras inválidas rechazadas en cada esquema examinado. | 3,71 s |
| `check_dao_sql.py` | 93 sentencias preparadas y cinco expansiones de colecciones delegadas a Room. | 0,22 s |
| `check_room_schema.py` | 27 entidades coinciden con el export de Room v11. | 0,33 s |
| `check_strings.py` | Recursos EN/ES consistentes. | 0,19 s |
| `verify_curated_catalog.py` | 936 reglas reproducibles, 923 alimentos importables y 1 421 vínculos de 250 recetas comprobados. | 4,93 s |
| `verify_canonical_design.py` | 55 comprobaciones superadas con SQLite. | 1,27 s |
| Contratos Node con cobertura V8 | 30 correctos, cero fallos u omisiones. | 6,35 s |
| `verify_commercial_contract.cjs` | OpenAPI y siete casos negativos correctos. PostgreSQL en PGlite verificó 22 casos de restricciones y dos planes de índices. | 4,60 s de shell |

En los XML finales, los casos nuevos de conversiones, precios, optimización y caducidad JVM tardaron como máximo 8 ms por caso y el caso del generador tardó 54 ms. En Android, la reversión de receta tardó 107 ms, las preferencias 106 ms y los cuatro casos de consumo HTTPS tardaron 160, 32, 6 y 30 ms en el orden del informe.

La cobertura V8 de los módulos cargados, incluido el fixture JavaScript, fue del 98,08 % de líneas, 89,95 % de ramas y 98,73 % de funciones. Los puntos pendientes incluyen el arranque de los CLI y ramas de error de publicación y SQLite. La cobertura de código Kotlin no se instrumentó en estas ejecuciones.

En la primera compilación el test nuevo de Room usó `use` para un tipo que exige cierre explícito en este proyecto. Se corrigió con `finally`. El fixture del generador necesitaba siete usos de su única receta permitida. Se ajustó la configuración del fixture y se comprobó también que un máximo de seis produce `NOT_ENOUGH_RECIPES`. El entorno restringido bloqueó conexiones locales de Gradle, ADB y HTTP. La verificación se repitió con acceso local al host.

Para la fase 24, la batería sirve de base para los cambios de dominio, persistencia y contrato v1. La validación de una publicación externa exige un publicador autorizado, su configuración TLS y una fuente comercial permitida. La ejecución instrumentada local cubre Android 16.
