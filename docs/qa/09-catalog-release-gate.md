# Fase 09 — Puerta de calidad del catálogo

Ejecución del 18 de noviembre de 2026 sobre la base `1d2f8ca`.

## Alcance y criterio de salida

La puerta exige que las migraciones abran en Room, conserven los IDs y datos existentes, mantengan los pesos y nutrientes, y superen la suite instrumentada. Un fallo de apertura, una pérdida de datos o un cambio nutricional sin respaldo bloquea la fase 10. Se usaron instalaciones desechables y el asset de producción versionado.

La revisión partió de los informes [01](../architecture/01-baseline-audit.md), [02](../catalog/02-food-audit.md), [03](../architecture/03-canonical-catalog.md), [04](../architecture/04-validated-food-quantity-conversions.md), [05](../architecture/05-additive-canonical-catalog-migration.md) y [06](../catalog/06-curated-canonical-catalog.md), de los commits `c2744e5` y `1d2f8ca` para las fases 07–08, y de los esquemas Room exportados. Se trazaron las escrituras del importador y el seeder y las lecturas de gramos de `MenuGenerator` y `ShoppingListCalculator`. La demanda de compra después de sustituir una receta usa `baseFactor`, mientras que la ingesta modifica el factor servido.

## Matriz de regresiones

| Caso | Evidencia y criterio |
| --- | --- |
| Migraciones 1 → 10 | El verificador SQLite reproduce la cadena desde un fixture v1 reconstruido. Las exportaciones Room v4–v9 se prueban individualmente hasta v10 en Android. |
| Instalación v6 parcial | Fixtures con menús por perfil, bloqueos, raciones y compra marcada. Se retira una tabla de perfiles o ingestas por escenario. La tabla faltante se recupera vacía y las filas existentes se comparan celda a celda. |
| Instalación v8 con biblioteca interrumpida | Catálogo y paneles del asset real, reconstruidos en el esquema v8. Incluye una receta integrada editada, una personalizada, perfiles, peso, menú bloqueado, ingesta, compras marcadas y una fila histórica oculta. Se comparan todos los campos antiguos, incluidos `NULL`, tipo SQLite y bits de los valores reales. |
| Catálogo y biblioteca, instalación limpia | El fixture SQL v10 conserva exactamente los 923 paneles de kcal y macros y las 31.337 filas de nutrientes importadas. El análisis del asset y las pruebas Android cubren 250 recetas, 1.421 enlaces, sus IDs BEDCA, cantidades y pasos bilingües. |
| Reapertura y actualizaciones | Se repiten importación, curación y seeder. Se comprueba el rollback con una revisión modificada o incompleta, cambios de versión/hash y un fallo editorial intermedio. |
| Acceso histórico y datos incompletos | La fila oculta queda fuera del selector y sigue accesible por ID, receta y compra marcada. Un cero explícito del panel sigue presente; los nutrientes ausentes siguen sin fila. |
| Masa, volumen y conteo | Las pruebas cubren g/kg, ml/L y piezas con evidencia comestible. Densidad ausente, densidad heredada sin acreditar, unidad incompatible, cero, negativos, NaN e infinito se rechazan para entrada nueva. |
| Recetas, menús y compra | Se contrastan los gramos, kcal y macros en Room y Compose, la edición y reapertura de recetas personalizadas, y los perfiles, bloqueos, comidas libres e ingestas. El fixture de compra conserva 87 g de huevo, 1.390,5 g de leche, 144 g de arroz crudo y 15 g de arroz cocido. |
| Integridad y compatibilidad | `PRAGMA foreign_key_check` vacío y `PRAGMA integrity_check` igual a `ok` en el fixture v8 migrado. El esquema v10 tiene 26 tablas; 90 sentencias DAO se preparan en SQLite y las cuatro consultas con colecciones se verifican al compilar con Room/KSP. |

## Incidencias y riesgos

| ID | Gravedad | Hallazgo y tratamiento |
| --- | --- | --- |
| QA09-01 | Crítica de actualización | Una instalación v6 parcial podía conservar `profile_meal_plans` pero carecer de `intake_entries`. El criterio de recuperación dependía de una sola tabla. La migración comprueba por separado columnas y tablas, y solo reconstruye el formato antiguo de menús. Se conservan los factores y el ámbito por perfil ya presentes. |
| QA09-02 | Alta | En v8, un catálogo ya importado podía quedar sin completar la biblioteca si el seeder había quedado interrumpido. Sus filas migradas eran históricas y el seeder solo consultaba cabeceras BEDCA acreditadas. Ahora puede completar entradas pendientes con la coincidencia editorial exacta del registro, el hash del asset y el fingerprint verificado. Conserva la procedencia histórica y las recetas guardadas. |
| QA09-R1 | Media, residual | Android 16/API 36 es la única imagen local. El mínimo declarado es API 26. Se revisó el SQL de la corrección para usar las mismas operaciones de tabla y consulta ya empleadas por la app. Falta una ejecución en API 26. |
| QA09-R2 | Media, residual | El caso v1–v3 es una reproducción SQLite porque faltan exportaciones Room de esas versiones. La reparación v6 conserva filas existentes y no puede reconstruir datos perdidos en instalaciones que ya sufrieron el fallo antiguo. |
| QA09-R3 | Media, residual | La publicación de una nueva composición BEDCA sigue aplazada hasta disponer de un manifiesto revisado. El marcador de biblioteca completo conserva su significado histórico, también cuando se borró una receta integrada. |
| QA09-R4 | Media, residual | El asset mantiene los hallazgos de calidad de fuente de fase 02, la falta de huevo entero de gallina crudo y opciones de unidad limitadas. La fase 10 debe tratar la identidad de producto y el tamaño de envase como datos propios. |
| QA09-R5 | Alta, deuda previa | La selección por alergias sigue siendo heurística y la sustitución manual se rige por la ruta previa. La aprobación tiene alcance de catálogo y precios. |
| QA09-R6 | Media, residual | CI todavía depende de esta ejecución local para las pruebas Android instrumentadas. La comprobación de esquema en el flujo de CI permite errores. |

Los dos fixtures dirigidos fallaron contra `1d2f8ca` después de verificar su preparación. Room informó que faltaba `intake_entries` en la variante v6. La instalación v8 conservó la única receta integrada del fixture y se quedó en 1 de 250. Esos fallos se reproducen con los mismos tests de regresión que verifican las correcciones.

## Comandos reproducibles

```powershell
$env:JAVA_HOME = (Resolve-Path '.gradle/jdks/eclipse_adoptium-21-amd64-windows.2').Path
$env:GRADLE_USER_HOME = (Resolve-Path '.gradle').Path
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat testDebugUnitTest assembleDebug connectedDebugAndroidTest --offline --no-configuration-cache --console=plain
python tools/verify_migration.py
python tools/check_room_schema.py
python tools/check_dao_sql.py
python tools/check_strings.py
python tools/verify_curated_catalog.py
python docs/architecture/fixtures/verify_canonical_design.py
python tools/audit_food_catalog.py --output-dir build/qa-phase09/food-audit
python tools/build_curated_catalog.py
python tools/build_food_asset.py app/src/main/assets/nutrinest_foods.db --output build/qa-phase09/repacked-smoke.db
python tools/audit_food_catalog.py --asset build/qa-phase09/repacked-smoke.db --output-dir build/qa-phase09-repacked-audit
git diff --check
```

El empaquetador se probó sobre una copia del asset publicado, ya que el dataset bruto no está versionado. La auditoría confirmó 936 registros fuente, 923 importables y cero IDs de receta ausentes. La regeneración del JSON editorial produjo los mismos bytes. El SHA-256 del asset publicado es `66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359`. La suite instrumentada usó un Pixel 5 con Android API 36 y SQLite 3.44.3. El manifiesto del APK confirma mínimo 26 y objetivo 36.

## Resultados y decisión

| Ejecución final | Resultado |
| --- | --- |
| `testDebugUnitTest` | 134 de 134, sin fallos, errores ni omisiones. |
| `assembleDebug` | APK generado correctamente. |
| `connectedDebugAndroidTest` | 42 de 42, sin fallos, errores ni omisiones. Pasaron los dos fixtures añadidos y la cadena Room v4–v9 → v10. La v8 completó la biblioteca de 1 a 250 recetas y conservó las filas históricas verificadas. |
| Migración, esquema y DAO en SQLite | Cadena 1 → 10 aprobada. 26 tablas compatibles con Room, 90 consultas preparadas y 20 escrituras inválidas rechazadas. `foreign_key_check` vacío e `integrity_check` igual a `ok`. La comparación BEDCA pasó con igualdad exacta. |
| Catálogo, diseño y recursos | Auditoría y verificación editorial correctas. El fixture de diseño superó 55 comprobaciones. Los recursos ES/EN pasaron la comprobación de consistencia. |

**APROBADA para iniciar la fase 10.** Las dos incidencias reproducidas quedan corregidas y la ejecución final no dejó fallos críticos conocidos en el alcance de catálogo, recetas o migraciones. Los gramos y las referencias de ingrediente siguen siendo el contrato de compatibilidad sobre el que se apoyarán los precios.
