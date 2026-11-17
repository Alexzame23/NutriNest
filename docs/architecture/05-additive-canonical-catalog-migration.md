# 05 — Migración aditiva del catálogo canónico

Fecha: 2026-11-17. Versión inicial comprobada en `@Database`: **8**.
Versión implementada: **9**, con `exportSchema = true`. Se conservan los JSON
4, 5, 6, 7 y 8; el nuevo `9.json` lo genera KSP, no se escribe manualmente.
No hay esquemas exportados 1–3 en este repositorio.

## SQL y estructura

`MIGRATION_8_9` está registrada en el `databaseBuilder` junto a las migraciones
anteriores. Room ejecuta la migración en su transacción de actualización.

Orden:

1. Capturar firmas de todas las tablas originales, incluyendo cada valor y tipo.
2. Ejecutar el DDL de `CanonicalCatalogSchema`: **6 tablas**, **25 índices** y
   **42 triggers**. El SQL exacto se incluye en
   [05-canonical-catalog-ddl.sql](05-canonical-catalog-ddl.sql).
3. Crear una revisión BASELINE STAGED y metadatos para cada ingrediente existente.
4. Comprobar cobertura de mapeos/estados y `PRAGMA foreign_key_check`.
5. Publicar la revisión como APPLIED con manifiesto, SHA-256 y secuencia.
6. Comparar nuevamente todas las firmas originales; una diferencia aborta.

| Tabla | Función y restricciones principales |
| --- | --- |
| `catalog_revision` | Revisión, fuente, versión/hash, manifiesto, actor y cadena; claves y secuencia únicas; una revisión aplicada no admite UPDATE/DELETE |
| `canonical_ingredients` | Identidad provisional/revisada, nombres e identidad semántica; unicidad de la clave semántica y `(canonical_id,scope_key)` |
| `ingredient_canonical_map` | Una identidad por `ingredientId`; FK compuesta al ámbito; cabecera externa única mediante slot 1, históricos con NULL |
| `ingredient_catalog_status` | Visibilidad y eventual reemplazo informativo; no redirige referencias ni cantidades |
| `conversion_evidence` | Ingrediente, unidad, gramos, base, método, revisión y evidencia; append-only; aceptación requiere masa comestible, confianza y revisor |
| `ingredient_unit_options` | FK compuesta a `(evidence_id,ingredient_id,unit_key)`; unidad única por ingrediente; un default como máximo; habilitación exige evidencia aceptada |

Todas las nuevas FKs usan `ON DELETE RESTRICT ON UPDATE RESTRICT`, con índices
para las claves hijas. No se añaden FKs hacia tablas de etiquetas/definiciones que
el antiguo importador sustituía. No se necesita reconstruir ninguna tabla v8.
Las definiciones de las **20 tablas anteriores son idénticas en los JSON 8 y 9**.
No se añade una tabla separada de “equivalencias”: el mapeo al mismo canonical
representa una equivalencia revisada; el backfill no establece ninguna entre
ingredientes distintos.

El DDL de migración incluye CHECKs. Room no los genera en la creación nueva;
por eso el callback `onCreate` instala el mismo conjunto de triggers de validación
que la migración. Se comprueban dominios, hashes, bases, valores finitos positivos,
etiquetas, aceptación, defaults y estado STAGED para mutaciones editoriales.
Las mismas escrituras inválidas se prueban contra ambas rutas.

Se amplía el diseño de fase 03 con `evidence_version`, `confidence` y la base NET,
de acuerdo con el motor de fase 04. NET y DRAINED no acreditan EDIBLE. No se han
inventado ni aprobado factores nutricionales durante esta migración.

## Backfill y firmas

`CanonicalCatalogStore.backfill` es transaccional e idempotente. La primera
revisión usa la clave estable `schema-9-baseline-v1`. Su UUID sirve de ámbito
local persistente. Cada ingrediente obtiene su propio UUID canónico provisional,
`concept_key=legacy:<id>` y `variant_key=legacy:<id>`. Los estados conservan la
visibilidad anterior, incluida la exclusión existente `BEDCA:2494`.

Los personalizados siguen siendo LOCAL aunque conserven un `sourceKey` BEDCA.
Los importados antiguos sin acreditación se registran como LEGACY y no reciben
una cabecera externa arbitraria. La procedencia original permanece en la fila y
en el detalle de estado. Preparación, conservación y forma se separan solo cuando
el campo existente lo declara; no se interpretan nombres ni se deduplican filas.

La migración no toca densidades/porciones heredadas y difiere su captura como
evidencia, opción expresamente permitida en fase 03. `conversion_evidence` y
`ingredient_unit_options` empiezan vacías. Las consultas del DAO requieren una
revisión aplicada y evidencia comestible aceptada antes de devolver factores.

Formato de firma implementado: **sqlite-typed-v1**, distinto del `record-v1` JSON
propuesto en fase 03. Se versiona expresamente para no simular una compatibilidad
de bytes inexistente. Cada fila usa campos ordenados por nombre, prefijos de
longitud en bytes UTF-8, código de tipo SQLite, NULL explícito y representación
hexadecimal de los bits IEEE-754 de los reales. Se conserva el texto original,
sin normalizar ni redondear datos. SHA-256 se calcula sobre esos bytes.

La firma de ingrediente incluye su fila y los hijos nutricionales, porciones,
alias e incidencias; excluye favoritos, uso, imágenes, timestamps y remoteId.
Los IDs de hijos quedan incluidos en esta versión de firma: es una instantánea
local, no un algoritmo para deduplicar alimentos entre instalaciones. El manifiesto
baseline conserva también las definiciones nutricionales originales y las firmas
completas de las tablas antiguas. Hay vectores JVM para Unicode, decimales y cero
con signo, contrastados con `hashlib`/`struct` de Python.

## Protección de la reapertura y escritores

Las nuevas relaciones no se publican con los escritores destructivos anteriores:

- **Importador:** abre el asset por separado y en solo lectura, sin ATTACH.
  Solo inserta ingredientes ausentes y sus hijos; nunca añade un panel nuevo a
  una fila anterior. Conserva referencias existentes, favoritos y uso. Las
  definiciones nutricionales existentes se comparan y no se sobrescriben.
  Las filas recién copiadas reciben metadatos con procedencia de la entrega,
  hash del asset y cabecera BEDCA, dentro de la misma transacción.
- **Actualización de dataset:** versión o hash distinto de lo ya registrado
  produce un rechazo explícito (`false` y log), sin actualizar marcadores ni
  ejecutar el seeder. En una instalación heredada sin hash solo se completa el
  bootstrap aditivo para la misma versión declarada; no se acredita con ello el
  contenido de las filas anteriores. El publicador de futuras entregas revisadas
  queda pendiente; no se presenta este bootstrap como tal publicador.
- **Seeder:** las recetas existentes conservan enlaces, gramos, favoritos,
  traducciones y pasos. Solo crea recetas ausentes con filas acreditadas de la
  entrega instalada. Ausencias o conflictos no marcan la biblioteca completada.
  La biblioteca sigue en versión 3; la migración no la fuerza a reejecutarse.
- **Editor:** una edición completa crea una instantánea LOCAL con ID nuevo y
  archiva la anterior, sin mover recetas ni compras. El repositorio devuelve ese
  ID y la navegación abre la nueva versión. Favoritos y frecuencia siguen siendo
  cambios de metadatos en la fila original. Las imágenes antiguas se conservan.
- **Archivo:** la acción antes denominada eliminación oculta el ingrediente de
  nuevas selecciones. Las lecturas históricas por ID siguen disponibles. Los
  textos ES/EN explican archivo y conservación de valores anteriores.

Los triggers impiden modificar composiciones/hijos ya mapeados y reinterpretar
definiciones nutricionales existentes. No se utiliza REPLACE para las nuevas
claves referenciadas. Las altas de evidencia/opciones requieren revisión STAGED;
la publicación revisada completa no se activa desde la UI en esta fase.

## Pruebas y reproducibilidad

`MigrationTest` cubre cada versión exportada 4–8 hasta 9, conservación celda a
celda de las 20 tablas en 8→9, backfill repetido, FKs compuestas, cantidades
decimales, claves, favoritos, purchased, factor/baseFactor distintos, perfiles,
pesos e ingestas. Abre además una instalación v8 mediante el builder real para
ejecutar migración, importador y seeder, y vuelve a abrirla.

`FoodImportTest` verifica bootstrap/reapertura, catálogo real, edición por copia,
rechazo de una entrega distinta y conservación de cantidades/pasos incluso al
forzar una versión antigua de la biblioteca. Se actualiza el test anterior que
esperaba reemplazar los pasos de recetas existentes: esa conducta contradice
ahora el contrato de conservación.

`MigrationTestHelper` abre una conexión de fixture sin activar FKs. Los ensayos
de rechazo las activan explícitamente y comprueban `PRAGMA foreign_keys=1`.
La prueba del builder real comprueba que Room ya las tiene activadas.

Los verificadores Python ejecutan el SQL extraído del código. El backfill de
datos del verificador nuevo es un **fixture**, no una segunda implementación
certificada del algoritmo Kotlin; la validación real se realiza en Android.
`check_room_schema.py` ahora compara **todas las columnas de cada FK compuesta**,
su agrupación, tablas, `user_version` e integridad; antes solo comparaba la primera
columna de la FK esperada. Se corrige también la codificación de URI de rutas
temporales con `#`. `check_dao_sql.py` sigue declarando las consultas de colecciones
que deja para KSP.

Comandos usados:

```powershell
$env:JAVA_HOME = Join-Path (Get-Location) '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat testDebugUnitTest assembleDebug connectedDebugAndroidTest --no-configuration-cache --console=plain
python tools/verify_migration.py
python tools/check_room_schema.py
python tools/check_dao_sql.py
python tools/check_strings.py
git diff --check
```

El primer intento offline no disponía de componentes UTP; se descargaron las
dependencias de pruebas necesarias. Se detectó además JSON serialization 1.8.1
de Room testing con core 1.7.3 heredado del APK probado. Se alinea **solo debug**
a core 1.8.1; release no cambia. El diagnóstico se basó en `dependencyInsight`
y en el error anterior a ejecutar la migración; es coherente con los
[cambios de interfaces JVM documentados por kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.8.0).

Resultados definitivos (17 de noviembre de 2026):

- `testDebugUnitTest`: **127 tests**, cero fallos y cero errores.
- `connectedDebugAndroidTest`: **25 tests** en Android 36, cero fallos.
- `assembleDebug`: correcto; ejecución conjunta de Gradle `BUILD SUCCESSFUL`.
- `verify_migration.py`: correcto; conservación de filas, rollback del DDL en
  copia desechable, fixture idempotente y rechazo de **20 escrituras inválidas**
  con paridad entre creación nueva y migración.
- `check_room_schema.py`: **26 tablas** verificadas e integridad correcta.
- `check_dao_sql.py`: **86 sentencias** compiladas; cuatro consultas con
  colecciones omitidas por el verificador y compiladas por KSP en el build.
- `check_strings.py` y `git diff --check`: correctos.
- Las definiciones de las **20 tablas anteriores** coinciden entre los esquemas
  8 y 9; los JSON históricos y el activo BEDCA se conservan sin cambios.

El esquema exportado 9 tiene identity hash
`3fe09d5bbcc4d9f6d3c851b8ca9eed87`. Los logs locales de esta ejecución quedan en
`build/catalog-validation.log`, `build/verify-migration.log`,
`build/check-room-schema.log` y `build/check-dao-sql.log`.

## Límites y rollback

- El emulador disponible es Android 36. Se usa SQL compatible con API 26:
  índices normales, FKs compuestas, triggers, INSERT/UPDATE y funciones SQLite
  básicas; sin UPSERT moderno, índices parciales, JSON1 ni tablas STRICT.
  No se ha ejecutado un emulador API 26 porque esa imagen no está instalada.
- Los esquemas históricos 1–3 no existen en el repositorio. El verificador SQL
  recorre 1→9 mediante su fixture v1; Room instrumentado valida 4→9 en adelante.
- Falta el publicador editorial de futuras entregas, con decisiones de conflicto,
  manifiestos revisados y nuevas instantáneas de datos modificados. Se rechazan
  esas entregas hasta implementarlo. Tampoco se activa el selector canónico ni
  se acredita automáticamente ninguna porción heredada.
- No se abrió ninguna base real del usuario. El emulador se inició con
  `-read-only -no-snapshot` y las pruebas operan sobre bases desechables.
- Un fallo durante 8→9 revierte la transacción. Se prueba rollback exclusivamente
  sobre un fixture. **No hay downgrade 9→8, ni rollback destructivo, ni fallback**.
  Para ensayar una restauración se utiliza únicamente una copia de prueba previa,
  nunca se sustituye una base activa del usuario.

Commit recomendado: `db: additive canonical catalog migration`.
