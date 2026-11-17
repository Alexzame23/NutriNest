# 06 — Catálogo canónico práctico y curación idempotente

Fecha: 17 de noviembre de 2026. Esquema Room: **9, sin cambios**.
Checkpoint: `feat: curated canonical ingredient catalog`.

## Resultado y alcance

La curación modifica exclusivamente las etiquetas, dimensiones semánticas y
estados de las tablas canónicas de fase 05. Mantiene un canónico independiente
por registro; **no establece equivalencias entre alimentos distintos**. Las
identidades continúan `PROVISIONAL`: una política editorial de presentación no
es una acreditación científica de identidad o de composición.

| Indicador, instalación nueva | Antes | Después |
| --- | ---: | ---: |
| Ingredientes importados y recuperables | 923 | 923 |
| Principales (`VISIBLE`) | 923 | 820 |
| Variantes secundarias (`SECONDARY`) | 0 | 99 |
| Candidatos ambiguos (`REVIEW_REQUIRED`) | 0 | 4 |
| Cobertura de mapeos y nombres ES/EN | 923 | 923 |
| Recetas integradas | 250 | 250 |
| Enlaces de recetas | 1.421 | 1.421 |
| IDs BEDCA distintos en recetas | 122 | 122 |
| Referencias de recetas ausentes | 0 | 0 |

Se normalizan **923 metadatos canónicos**; **11 registros** reciben una etiqueta
distinta en ES o EN. No confundir esta cifra con 923 nombres corregidos. De los
122 alimentos usados en recetas, 117 son principales y 5 secundarios; sus
nutrientes y cantidades se conservan, sea cual sea su prioridad de presentación.
La comparación de recetas corresponde a reprocesar la biblioteca ya instalada.
En el JSON de la primera instalación, `before.recipeLinks` es 0 porque la primera
curación se ejecuta antes del seeder; el resultado posterior tiene 1.421 enlaces.

El asset completo conserva **936 alimentos y 31.696 filas de nutrientes fuente**.
Sus 12 obsoletos y la exclusión anterior BEDCA:2494 no se añaden a una instalación
nueva. Las reglas los incluyen como `OBSOLETE`/`HIDDEN` para documentar la cobertura,
sin introducir nuevas eliminaciones ni redirigir reemplazos. Cualquier registro
histórico ya instalado se conserva, aunque no sea importable actualmente.
Secundarios y ambiguos siguen consultables y seleccionables, claramente rotulados;
la cifra de 820 mide prioridad, no una eliminación de 103 filas del selector.

## Reglas versionadas y evidencia

Fuente ejecutable: `app/src/main/assets/catalog-curation-v1.json`, compilada por
`tools/build_curated_catalog.py`. Versión `practical-catalog-v1`, dataset
`2026.07.29`, SHA-256 del asset:
`66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359`.
El compilador rechaza otro asset; adaptar una entrega requiere una revisión
editorial explícita, no volver a generar silenciosamente el mismo nombre de versión.

Cada decisión identifica BEDCA, regla, estado, nombres, preparación, conservación,
parte, forma, formulación, fuente y referencia original. Incluye la proyección
exacta de atributos y todos los valores de nutrientes, porciones, alias e
incidencias que importa la aplicación. Los IDs de hijos, tiempos, imágenes,
favoritos y frecuencia no forman parte de la comparación de procedencia.
Las claves `conceptKey` y `variantKey` conservan el ID BEDCA: no agrupamos por
nombre, alias, panel parecido ni por preparación supuestamente equivalente.

| Regla | Decisión |
| --- | --- |
| C01_PRESERVE_PURCHASE_FORM | Preservar la forma declarada o no especificada, sin afirmar que UNSPECIFIED sea RAW. Mantiene panes, lácteos, conservas, aceites, congelados, etc. |
| C02_RAW_PRIORITY | Preparación RAW declarada: principal, salvo una excepción explícita de parte/ambigüedad. |
| C03_THERMAL_VARIANT | Preparaciones térmicas: secundarias. Se conservan como principales los tostados de LEGUMES, categoría que incluye frutos secos habituales de compra. |
| C04_EGG_PART | Clara 1173, yema 2125 y yema seca 762: secundarias; canónicos y nutrientes distintos. |
| C05_EXPLICIT_EGG_PREPARATION | IDs concretos de huevo cocinado con preparación fuente UNSPECIFIED: especificar solo la dimensión canónica, sin tocar el registro fuente. |
| C06_AVAILABLE_WHOLE_EGG | Huevo entero escalfado 758 como opción principal disponible, siempre etiquetado como escalfado. |
| C07_AMBIGUOUS_IDENTITY | 1725/1793 y 2433/2434: candidatos del informe 02, pendientes de revisión, sin fusión. |
| C08_DECLARED_OBSOLETE | Documentar los 12 obsoletos de la fuente sin importar ni borrar automáticamente sus históricos. |
| C09_EXISTING_EXCLUSION | Mantener la exclusión previa 2494, sin ampliarla. |

La equivalencia documentada ES/EN es **de etiqueta dentro del mismo registro**.
Por ejemplo, `Clara de huevo de gallina, cruda` / `Chicken egg white, raw` solo
designa BEDCA:1173. `Arroz (preparación no especificada)` preserva la incertidumbre
de BEDCA:2660. No permite calcular rendimiento ni sustituir arroz hervido.

**Carencia del dataset:** no existe huevo de gallina entero crudo. No se añade
una composición inventada ni se renombra un huevo cocido como crudo. Clara,
yema, huevos de otras especies y huevo entero escalfado siguen siendo registros
distintos. Incorporar el huevo entero crudo exige una fuente nueva acreditada.

## Publicación y protección de datos

`FoodDatabaseImporter` aplica la curación también cuando versión y hash ya están
instalados. La revisión usa clave `curation:<versión-reglas>:<hash-asset>` y guarda
el SHA-256 exacto del archivo de reglas. Reprocesar es una lectura; modificar el
archivo sin incrementar versión se rechaza. Los bytes UTF-8/LF de las reglas se
fijan con el compilador y `.gitattributes` para que un cambio de plataforma no
cambie el hash. El manifiesto aplicado es inmutable y guarda estados antes/después,
decisiones por ingrediente, pruebas de procedencia y
hashes de todas las tablas anteriores. El archivo versionado conserva la evidencia
completa; no se duplica su panel nutricional en la fila del manifiesto, para no
superar el límite de `CursorWindow` de Android.

Una transacción contiene cambios canónicos, validación, manifiesto y publicación.
Se verifican FKs, cobertura y hashes de las tablas originales antes de publicar.
Un fallo intermedio revierte también las decisiones ya ejecutadas en esa revisión.
No hay `DELETE`, `REPLACE`, cambio de ID ni cambio de composición en la curación.
No se modifican mapeos, cabeceras BEDCA, porciones o conversiones de fase 04.

Solo se normalizan filas sin decisión editorial previa y con alguna prueba:

1. Importación BEDCA acreditada contra ese mismo hash; se verifica además su
   fingerprint local completo.
2. Fila LEGACY con coincidencia exacta de atributos importados y todos sus hijos
   contra la evidencia fijada. La coincidencia incluye valores numéricos sin
   tolerancia, NULL, preparación y etiquetas; no basta `sourceKey` o nombre.
   Su procedencia LEGACY y cabecera siguen intactas: esto acredita presentación,
   no reescribe su origen ni habilita al seeder a sustituir recetas antiguas.

Los personalizados, ediciones no coincidentes, decisiones manuales, archivos y
canónicos modificados previamente quedan preservados. Los recuentos de una base
migrada pueden diferir de 923: su manifiesto registra qué se normalizó y qué se
preservó. El editor sigue creando una instantánea LOCAL por copia; las recetas y
compras anteriores mantienen el ID antiguo. Favoritos y frecuencia no se tocan.

Las etiquetas de presentación son campos `@Ignore` en las instancias devueltas
por los selectores; nunca se persisten sobre `IngredientEntity`. El orden por
nombre aplica primero la prioridad; el orden nutricional elegido explícitamente
por el usuario sigue respetándose. La búsqueda DAO incluye nombres canónicos
ES/EN y todos los alias originales. El selector de recetas incluye ambos nombres
canónicos en su filtro local. Las lecturas por ID y `observeHistorical()` no
filtran estados; Favoritos incluye también los registros archivados favoritos.

## Revisión manual

Los candidatos 1725/1793 tienen igualdad de panel pero no identidad/procedencia
demostrada; 2433/2434 incluyen café normal y descafeinado. Se conservan separados.
La cola se consulta mediante `ingredient_catalog_status.state='REVIEW_REQUIRED'`;
`detail` contiene IDs relacionados y el hallazgo del informe 02.

Política para el revisor:

1. Consultar ambos IDs, la fuente y el hallazgo; comprobar preparación, parte,
   conservación, formulación, panel, unidades y cobertura, incluidos NULL.
2. Registrar evidencia concreta y responsable identificable. La igualdad textual
   o nutricional aislada no autoriza equivalencia ni intercambio de cantidades.
3. Para una decisión de **visibilidad**, usar `CuratedCatalog.review` con
   `expectedRevision`, estado, responsable y evidencia. Una revisión obsoleta se
   rechaza; los imports posteriores no deshacen la decisión. La API no fusiona
   identidades ni mueve referencias. No se incorpora una pantalla administrativa
   de revisión en esta fase.
4. Una equivalencia entre IDs requiere un publicador de identidad separado y
   revisión explícita; mientras no exista, conservar los singleton y la duda.

## Actualizaciones del dataset

La fase 06 soporta reapertura y actualización editorial sobre el dataset fijado.
Una entrega de datos con versión o hash diferente continúa **rechazada de forma
atómica**, como en fase 05. No se presenta este rechazo como importación exitosa
de una nueva composición. No cambia marcadores, favoritos, macros, referencias,
alias ni decisiones; tampoco ejecuta el seeder como si la nueva entrega estuviese
publicada. Se prueba una copia real con cambio nutricional manteniendo versión y
otra con nueva versión más un registro adicional. Ambas quedan pendientes de un
manifiesto y publicador revisados. No se abrió una base real del usuario.

## Validación reproducible

```powershell
python tools/build_curated_catalog.py
python tools/verify_curated_catalog.py
python tools/verify_migration.py
python tools/check_room_schema.py
python tools/check_dao_sql.py
python tools/check_strings.py
$env:JAVA_HOME = Join-Path (Get-Location) '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat testDebugUnitTest assembleDebug connectedDebugAndroidTest --offline --no-configuration-cache --console=plain
git diff --check
```

`verify_curated_catalog.py` comprueba reglas y cobertura contra las recetas Kotlin
reales; no sustituye las pruebas del ETL. `CuratedCatalogTest` ejecuta el importador
y curador reales: reejecución con hashes lógicos, FKs, integridad, 1.421 cantidades
y unidades, nutrientes, alias, favoritos, compras, edición por copia, igualdad
LEGACY completa, rollback intermedio, revisión manual, reapertura y entregas
modificadas. Los fixtures manuales se identifican como prueba y no son revisiones
alimentarias acreditadas. El emulador se ejecuta sin guardar snapshots.

Resultados: se registran en `06-curation-statistics.json` y
`06-android-verification.json`. La suite completa pasó con **127 pruebas JVM y
34 instrumentadas**, cero fallos/errores. La compilación APK fue correcta.
Los verificadores offline pasaron: **26 tablas**, **88 sentencias DAO** (cuatro
consultas con colecciones verificadas por KSP), conservación e idempotencia del
fixture de migración, rechazo de **20 escrituras inválidas**, recursos ES/EN y
`git diff --check`. El esquema exportado 9 conserva su identity hash.

Las **nueve pruebas de curación volvieron a pasar** tras los últimos ajustes de
protección editorial, trazabilidad de filas externas y fijación de LF. Se ejecutó
además la prueba de hashes de forma independiente para exportar la evidencia antes
de desinstalar el APK de prueba; su resultado queda en el JSON Android.
Los hashes incluyen UUID y tiempos de una instalación
de prueba: deben coincidir entre pasadas de esa instalación, no entre dispositivos.
El manifiesto de la instalación nueva ocupa aproximadamente 1,15 MB, por debajo
del límite de fila de `CursorWindow` comprobado en el emulador.

Durante desarrollo se corrigieron el alias SQL reservado `rank`, un cierre del
fixture Room y el número de parámetros del verificador SQL. El primer intento
aislado de Gradle no pudo conectarse a su daemon local; la validación final se
ejecutó fuera de ese aislamiento. No quedan fallos conocidos de esas pruebas.
No se certifica API 26: la imagen disponible y utilizada fue Android 36.

## Archivos de la entrega

- `app/src/main/assets/catalog-curation-v1.json`: decisiones versionadas y evidencia.
- `app/src/main/java/com/zame/nutrinest/data/local/CuratedCatalog.kt`: publicación,
  revisión y estadísticas; `FoodDatabaseImporter.kt`: integración y entrega de prueba;
  `CanonicalCatalogStore.kt`: revisión compartida parametrizada.
- `app/src/main/java/com/zame/nutrinest/data/local/dao/IngredientDao.kt`: búsqueda,
  presentación, recuperación y favoritos; `entity/IngredientEntity.kt`: etiquetas
  transitorias; `data/repository/Repositories.kt`: composición de selectores.
- `app/src/main/java/com/zame/nutrinest/ui/ingredients/IngredientsViewModel.kt`,
  `IngredientsScreen.kt` y `ui/recipes/create/CreateRecipeScreen.kt`: orden y señales.
- `app/src/main/res/values/strings.xml` y `values-es/strings.xml`: textos ES/EN.
- `app/src/androidTest/java/com/zame/nutrinest/data/local/CuratedCatalogTest.kt`:
  nueve pruebas nuevas.
- `tools/build_curated_catalog.py`, `verify_curated_catalog.py` y
  `verify_catalog_migration.py`: compilación, cobertura y adaptación del fixture
  a los parámetros de revisión.
- Este informe y los dos JSON de evidencias.
- `.gitignore`: excluir las sesiones locales del compilador Kotlin generadas
  durante las pruebas.
- `.gitattributes`: fijar LF para los bytes firmados de las reglas.

El asset BEDCA, los esquemas Room históricos y v9, el seeder, la biblioteca de
recetas y las entidades canónicas persistidas no cambian.

## Fase 07

1. Añadir una pantalla de revisión/recuperación sobre las APIs conservadoras,
   con comparación de fuente y trazabilidad por revisión.
2. Resolver los cuatro candidatos con evidencia externa suficiente y cubrir
   la ausencia de huevo entero crudo mediante una entrega acreditada.
3. Implementar el publicador de entregas nutricionales revisadas: instantáneas
   nuevas, aprobación de cabeceras y conflictos, sin mover recetas guardadas.
4. Acreditar unidades/porciones y sus bases comestibles de manera independiente;
   no derivar rendimientos de la clasificación canónica.
5. Ejecutar la suite en API 26 además de Android 36 antes de certificar ese mínimo.

Rollback editorial: crear una revisión compensatoria STAGED a partir del
`before` del manifiesto, validarla y publicarla. No borrar revisiones aplicadas,
reemplazar bases activas ni ejecutar un downgrade destructivo.
