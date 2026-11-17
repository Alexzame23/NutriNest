# Propuesta de transición Room 8 → 9 y activación posterior

Estado: diseño; **no aplicada** a una base de usuario, asset ni aplicación. Complementa [03-canonical-catalog.md](03-canonical-catalog.md) y [ADR-0001](adr/0001-additive-canonical-catalog.md). La v9 es propuesta; `NutriNestDatabase.kt` y el esquema exportado siguen en v8.

## 1. Invariantes de la transición

- Mismos IDs y contenidos de todas las filas existentes en `ingredients`, `ingredient_nutrients`, `ingredient_portions`, `ingredient_aliases`, recetas, menús y compras al terminar el backfill.
- Ningún CREATE/DROP/RENAME/rebuild de `ingredients`, `recipe_ingredients` o `shopping_items`. No desactivar `foreign_keys`, no usar `writable_schema`, no cambiar `sqlite_sequence` salvo la reserva de IDs para inserciones nuevas en el importador futuro.
- Mismos enlaces, gramos, unidades de presentación, favoritos, cantidades compradas y totales nutricionales; las nuevas consultas históricas no filtran visibilidad.
- No recalcular menús, `meal_portions.factor`, `baseFactor` ni compras durante la normalización.
- Las seis tablas nuevas deben existir tanto en creación fresca como en migración. No registrar `fallbackToDestructiveMigration` ni permitir volver a escribir la base con un binario v8.

## 2. Paso 0 — preparar compatibilidad antes de activar v9

Implementación futura, no realizada en fase 03:

1. Introducir repositorio de catálogo con transacciones centralizadas y validador de publicación. Desactivar borrado de importados y escritura destructiva de hijos/definiciones; mantener la lectura separada del asset, sin ATTACH, conforme al motivo documentado en el importador actual.
2. Adaptar `FoodDatabaseImporter` al contrato de instantáneas: source head explícita; no OR IGNORE que mezcle un panel nuevo con macros retenidos; no OR REPLACE ni borrado de referencias. Registrar la revisión y metadatos solo tras éxito completo.
3. Adaptar `BuiltInRecipeSeeder`: conservar enlaces de recetas existentes; nuevas recetas fijadas por fuente + clave + fingerprint; detectar ausentes/ambiguos sin marcar la biblioteca completada. La normalización del catálogo por sí sola no incrementa ni fuerza reejecución de la biblioteca v3.
4. Adaptar los escritores del editor: conservar la composición anterior, versionar cambios y devolver el nuevo ID al flujo que los solicitó. Una acción sobre favoritos, fotos o frecuencia no crea una composición nueva. Una acción de eliminación ordinaria se convierte en ocultación/archivo, con mensajes coherentes; un borrado integral explícito de datos requeriría otro procedimiento.
5. Incluir todas estas adaptaciones en la misma entrega que registra `MIGRATION_8_9`. Un feature flag de UI **no** protege contra el borrado del importador en `onOpen`; no se admite publicar solamente las tablas.

## 3. Paso 1 — migración estructural aditiva, una transacción

Orden propuesto dentro de la transacción de migración administrada por Room:

1. Leer baseline de la **instalación**, no asumir que coincide con el asset. Capturar IDs, conteos, huellas de composición/hijos y relaciones; incluir firmas de definiciones nutricionales que usa esa instalación.
2. Crear tablas en orden: `catalog_revision`, `canonical_ingredients`, `ingredient_canonical_map`, `ingredient_catalog_status`, `conversion_evidence`, `ingredient_unit_options`; crear los índices del [DDL](fixtures/03-canonical-schema.sql).
3. Crear revisión BASELINE STAGED. Capturar fuente verificada cuando exista, procedencia desconocida cuando no; no atribuir un hash del asset actual a filas antiguas que no coincidan con él. Copiar la instantánea de definiciones originales al manifiesto.
4. Para cada ingrediente existente, crear un canonical provisional independiente y un mapeo. La firma lleva `concept_key=legacy:<id>` y `variant_key=legacy:<id>`: no hay deduplicación. Mantener preparación conocida; separar CANNED/FROZEN/DRIED/CURED/SMOKED como conservación, dejando preparación UNSPECIFIED cuando no haya dato térmico independiente. POWDERED puede informar forma, sin deducir preparación. Otros campos desconocidos permanecen UNSPECIFIED.
5. `isCustom=true` usa ámbito y fuente LOCAL/LEGACY local incluso si conserva `sourceKey=BEDCA:*`; registrar ese sourceKey como procedencia histórica. No asumir que `isCustom=false` demuestra coincidencia con el asset: comprobar huella real. Si no puede acreditarse la identidad externa única, usar LEGACY y guardar el indicio BEDCA en el manifiesto, sin cabecera arbitraria. Una revisión posterior puede reclasificar esa procedencia con trazabilidad.
6. Solo asignar `source_head_slot=1` si la fila coincide con la fuente acreditada y no hay otra candidata incompatible. En ambiguos, mantener los IDs, cabeceras NULL, incidencia y ninguna resolución automática. La estructura no depende de que todos los BEDCA tengan una cabecera.
7. Estados iniciales: VISIBLE para entradas que ya presenta el catálogo, salvo exclusión/obsolescencia **acreditada para esa fila**; HIDDEN para exclusión existente acreditada; OBSOLETE para baja confirmada; REVIEW_REQUIRED cuando la identidad fuente sea contradictoria y no permita nuevas selecciones seguras. No marcar baja por ausencia en un asset distinto ni por poco uso. Los 12 obsoletos del asset no se insertan automáticamente en cada instalación: solo se archivan si ya existen allí. Si la política editorial alterase visibilidad actual, mantener esa decisión sin activar filtros hasta el paso 3.
8. Capturar porciones y densidades heredadas como evidencias UNVERIFIED con localizador e instantánea; no llamarlas mediciones BEDCA. Opciones deshabilitadas, default_slot NULL. No fabricar un UNIT donde solo existe una ración. Densidad ausente → no conversión volumétrica. Es admisible diferir esta captura a una revisión posterior, conservando mientras tanto los valores originales.
9. Validar cobertura, FKs, firmas y referencias; comprobar que todas las tablas v8 conservan exactamente su contenido. Construir manifiesto final, verificar su hash, publicar revisión como APPLIED y secuencia 1. Cualquier fallo revierte todas las altas; no registrar éxito parcial.

Los UUID y tiempos se generan una vez por transacción y se conservan al publicar. Un rollback no deja IDs/revisiones intermedios; el reintento aplica de nuevo la operación. La idempotencia de revisiones aplicadas se comprueba por `revision_key`, no por asumir que Room volverá a ejecutar una migración ya registrada.

Ejemplo conceptual de validaciones de salida:

```sql
-- Debe devolver cero filas.
SELECT i.id FROM ingredients i
LEFT JOIN ingredient_canonical_map m ON m.ingredient_id=i.id
LEFT JOIN ingredient_catalog_status s ON s.ingredient_id=i.id
WHERE m.ingredient_id IS NULL OR s.ingredient_id IS NULL;

PRAGMA foreign_key_check; -- cero filas

-- Defensa adicional aunque la PK ya impide ambigüedad física.
SELECT ingredient_id FROM ingredient_canonical_map
GROUP BY ingredient_id HAVING COUNT(*) <> 1;

-- Debe devolver cero filas antes de publicar cualquier opción.
SELECT o.option_id FROM ingredient_unit_options o
JOIN conversion_evidence e ON e.evidence_id=o.evidence_id
WHERE o.enabled=1 AND (e.status<>'ACCEPTED' OR e.mass_basis<>'EDIBLE');
```

La conservación de tablas anteriores se verifica mediante snapshots ordenados por PK, incluyendo todos los valores, antes y después. Conteos iguales por sí solos no prueban conservación. Para bases grandes usar hashes incrementales deterministas y conteos de control, sin almacenar otra copia de todas las filas en RAM.

## 4. Compatibilidad Room y SQLite de Android

El DDL entregado expresa el contrato relacional, **no es una migración Kotlin lista para registrar**. Declarar en entidades futuras todos los tipos, nullability, PK, índices únicos/normales y FKs con nombres estables. Mantener iguales estos elementos entre creación nueva y migrada, y exportar `9.json` mediante KSP solo durante fase 04.

Los CHECK de la especificación necesitan tratamiento explícito: no asumir que Room los genera a partir de una validación Kotlin. La implementación debe instalar un conjunto común de triggers de validación INSERT/UPDATE sobre las **seis tablas nuevas**, tanto al crear la base como al migrar, que reproduzcan las condiciones locales del DDL (dominios, masa finita, labels, hashes y aceptación). La migración puede conservar los CHECK como defensa adicional. La equivalencia se comprueba con el mismo conjunto de intentos válidos e inválidos contra ambas rutas. Si se escoge otro mecanismo, documentarlo y demostrar esa equivalencia antes de publicar.

La cobertura total, el manifiesto/hash, la cadena de revisiones, inmutabilidad de filas publicadas y compatibilidad semántica se garantizan por el repositorio/validador transaccional, no por esos CHECK. Para los registros publicados, sus APIs rechazan modificaciones de composición/evidencia y crean nuevas instantáneas; los campos editoriales permitidos generan una revisión. No se afirma protección frente a modificación manual privilegiada de SQLite.

La fase 03 ha ejecutado SQLite 3.50.4 en Python; no ha ejecutado el SQLite de API 26. Evitar funciones recientes en la futura migración: índices únicos ordinarios, FKs compuestas y sentencias INSERT/UPDATE simples. Ejecutar pruebas en dispositivo/emulador mínimo antes de certificar compatibilidad. Tampoco basta con que `check_room_schema.py` pase: el baseline documenta los límites de ese comprobador.

## 5. Paso 2 — reapertura e importación atómica

Una vez completada la migración, `onOpen` usa exclusivamente los escritores compatibles. `catalog_revision` mantiene separadas versión de esquema, versión del dataset y revisión editorial. No comparar únicamente `food_dataset_meta.dataset_version` para decidir que la entrada ya se aplicó.

Pseudocódigo del contrato:

```text
abrir asset en conexión separada de solo lectura
verificar hash, formato, integridad y manifiesto de entrega completa/parcial
preparar decisiones sin cambiar ninguna referencia existente
transacción de escritura serializada:
    si revision_key ya APPLIED: retornar sin cambios
    validar padre = última revisión aplicada
    crear revisión STAGED
    para cada clave externa:
        si snapshot acreditado idéntico: reutilizar ID
        si snapshot nuevo: insertar nueva fila + hijos + singleton o mapeo revisado
        si conflicto ambiguo: registrar candidato, conservar cabecera anterior
        si nueva cabecera aprobada: retirar slot anterior y asignar slot=1 al nuevo
    aplicar únicamente estados/evidencias aprobados
    comprobar cobertura, hashes, FKs, identidad, unidades y referencias previas
    finalizar manifiesto y revisión APPLIED con nueva secuencia
    actualizar metadatos de importación solo ahora
commit
```

El reemplazo de cabecera usa UPDATE anterior→NULL seguido de INSERT/UPDATE nuevo→1 en la misma transacción. Lectores no observan el intervalo. Si falla la nueva cabecera, rollback conserva la anterior. En conflicto no se publica la decisión de cabecera; la revisión puede registrar la incidencia y el candidato sin prometer que la fuente está completamente resuelta. El seeder posterior exige las instantáneas que necesita y no toma un candidato ni un registro LOCAL por su `sourceKey`.

Un rollback recupera el estado anterior sin borrados de historia. Si el importador sigue usando `runCatching`, debe exponer el fallo y conservar el marcador de revisión anterior; el seeder no debe interpretar ese fallo como catálogo nuevo aplicado correctamente. No capturar y silenciar fallos de la migración estructural para abrir una base incompleta.

## 6. Paso 3 — lectura de catálogo y unidades, activación independiente

La entrega inicial v9 puede operar en modo de observación con interfaz y consultas anteriores, siempre con escritores compatibles. Comparar resultados de consultas, métricas y planes sin cambiar datos. Activar posteriormente el selector canónico y estados, después de revisar la primera lista editorial. Este cambio de datos/lectura no obliga a inventar una v10 si el esquema no cambia.

Los detalles de recetas y compras resuelven siempre sus IDs originales. El catálogo agrupa visualmente y presenta las muestras concretas dentro del grupo. Las compras pueden añadir encabezados canónicos, pero persistencia, sumas y `purchased` siguen por `ingredientId`; no consolidar automáticamente líneas con composiciones o preparación distintas.

Mantener los gramos de recetas anteriores incluso si `quantityUnit` ya no tiene conversión acreditada. Al mostrarlas, conservar la etiqueta histórica y ofrecer gramos; **no recalcular gramos desde una evidencia nueva**. Al editar, una selección explícita de nueva unidad usa una conversión aceptada o solicita masa. No confundir `ingredient_portions.isDefault` heredado con `ingredient_unit_options.default_slot` revisado.

## 7. Matriz obligatoria de pruebas de fase 04

| Caso | Resultado requerido | Estado en fase 03 |
| --- | --- | --- |
| DDL v8 exportado + backfill + restricciones SQL | 20 tablas intactas, seis nuevas, FKs válidas | Ejecutado en Python: 55 comprobaciones del conjunto |
| Room `MigrationTestHelper` 8→9 y 4→…→9 | Esquema exportado validado y contenido conservado | Pendiente |
| Instalación nueva v9 frente base migrada | Mismos defaults, índices, FKs, rechazo de datos inválidos/triggers | Pendiente |
| API 26 y API objetivo | Migración, apertura y consultas sin SQL incompatible | Pendiente |
| Reabrir con dataset idéntico | Cero filas/IDs/revisiones adicionales | Contrato definido; integración pendiente |
| Hash cambiado con misma versión declarada | Revisión distinta, sin sobrescritura | Pendiente |
| Nutrientes, medidas o unidad de tag cambiados | Nueva instantánea o rechazo explícito; anteriores intactos | Pendiente |
| `sourceKey` duplicado, importado editado, BEDCA ausente | Conflicto visible; sin selección arbitraria ni marker de seeder incorrecto | Pendiente |
| Excluido/obsoleto referido por receta y compra | Consultable, mismos gramos/totales/estado de compra | Conservación SQL probada; UI pendiente |
| Importación interrumpida y dos escritores concurrentes | Rollback/idempotencia/cabecera única y cadena lineal | Rollback SQL probado; concurrencia Room pendiente |
| Fuente parcial frente entrega completa | Ausencia no implica obsolescencia en parcial | Pendiente |
| Evidencia con bruto/escurrido/desconocido/negativo | No habilitar conversión nutricional sin aceptación comestible | Restricciones SQL probadas; UI pendiente |
| Editor personalizado y seeder actualizado | Nuevas versiones no alteran recetas anteriores | Pendiente |
| Vectores JSON/fingerprint Kotlin y Python | Bytes/hashes equivalentes, sin redondeo de nutrientes | Pendiente |
| `testDebugUnitTest`, `assembleDebug`, scripts DAO/Room/strings | Todos los checks aplicables y CI sin ocultar fallos de esquema | No ejecutado en fase 03 |

## 8. Recuperación y criterio de salida

Antes de una distribución real, probar una copia de una instalación representativa bajo control del usuario. El baseline advierte que el backup Android actual excluye la base: no presumir que hay restauración automática. No crear una copia de datos de usuario durante este trabajo de diseño.

Ante fallo dentro de la migración, rollback completo. Ante error editorial después de publicar, revisión compensatoria. Desactivar el selector canónico puede restaurar la lectura anterior mientras se conservan los escritores compatibles; **no** equivale a instalar de nuevo el binario v8. No se propone downgrade destructivo ni eliminación de las nuevas tablas.

Fase 04 lista para comenzar cuando se acepten vocabulario, responsables de revisión, política de edición por copia y formato de fingerprint/manifiesto. Lista para publicar únicamente cuando pasen ambas rutas Room, API mínima, integración importador/seeder y regresiones de referencias; las 55 pruebas de diseño no sustituyen esos requisitos.
