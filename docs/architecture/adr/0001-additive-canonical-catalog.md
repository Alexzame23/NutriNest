# ADR-0001 — Catálogo canónico aditivo con composición histórica por ingrediente

- Fecha: 2026-11-16.
- Estado: **propuesto**, verificado parcialmente mediante especificación SQLite; pendiente de implementación y aceptación de producto/datos.
- Base: `Development` / `c3ff612004229d7d9c57a3fbff6bf7ad8ce405c2`, esquema Room 8.
- Diseño: [03-canonical-catalog.md](../03-canonical-catalog.md).
- Transición: [03-canonical-migration-proposal.md](../03-canonical-migration-proposal.md).

## Contexto

Las recetas y compras referencian IDs de `ingredients` con FK RESTRICT. Los cálculos consumen macros de esa fila por 100 g y gramos persistidos. No hay snapshots nutricionales en recetas. Una sustitución de ID o de composición puede cambiar recetas, planes y compras existentes.

La fase 02 no demostró nuevos duplicados exactos. Los doce reemplazos declarados tienen diferencias de macros; nombres ingleses y alias iguales no garantizan equivalencia. También existen estados de preparación desconocidos, unidades sin procedencia individual suficiente e importados editados por usuarios que conservan `sourceKey`.

El importador actual elimina parte de los ingredientes y repuebla referencias. El seeder puede rehacer enlaces al aumentar versión. Añadir FKs sin modificar esos escritores en la implementación futura no es compatible.

## Decisión

1. Añadir seis tablas: `canonical_ingredients`, `ingredient_canonical_map`, `ingredient_catalog_status`, `ingredient_unit_options`, `conversion_evidence`, `catalog_revision`.
2. Conservar las tablas v8 y todos sus IDs/referencias. Un ID de ingrediente sigue representando una composición concreta; un ID canónico sirve para identidad y presentación.
3. Backfill conservador: un canonical provisional por ingrediente. Consolidar visualmente solo mediante revisión de identidad, sin mover referencias ni promediar nutrientes. Preparación, parte, conservación, forma y formulación forman parte de la clave semántica; alcance local separado para personalizados.
4. Un mapeo y un estado por ingrediente al publicar. Todas las FKs nuevas RESTRICT. Los estados de catálogo no se aplican a consultas históricas por ID.
5. Distinguir fuente externa de ID local y de snapshot. Una única cabecera acreditada por fuente/clave, múltiples versiones históricas conservadas. Nuevas composiciones obtienen nuevo ID, incluso para ediciones personalizadas después de activar el contrato. Las diferencias no se vuelcan sobre una fila anterior.
6. No resolver una receta por el representante del grupo canónico. Nuevas selecciones eligen un ingrediente concreto; nuevas recetas integradas fijan fuente/clave/fingerprint. Recetas existentes conservan ID y gramos.
7. Separar opciones de unidad de evidencias. Una evidencia aceptada debe identificar ingrediente, unidad, gramos comestibles y responsable. No transferir pesos/densidades entre variantes; GRAM/KILOGRAM se mantienen como conversiones dimensionales exactas.
8. Registrar decisiones en revisiones con manifiesto recuperable, hash, actor, reglas y secuencia; publicar atómicamente. SQL garantiza relaciones/unicidad local; el repositorio valida cobertura, identidad, manifiesto, historial y procedencia.
9. Mantener alias y nombres originales en sus tablas; añadir etiquetas bilingües canónicas. No introducir tabla de alias canónicos ahora; las exclusiones editoriales de búsqueda se versionan por contenido en el manifiesto.
10. Congelar definiciones nutricionales originales en esta transición. Una entrega que cambie la semántica/unidad de un tag exige un diseño adicional de versionado antes de su publicación; no reinterpretar valores históricos mediante un UPDATE global.

## Alternativas descartadas

| Alternativa | Motivo |
| --- | --- |
| Borrar duplicados y reasignar todos los enlaces | Destruye trazabilidad y cambia composición; no hay equivalencias demostradas suficientes |
| Mover macros al canonical y promediarlos | No conserva muestras originales ni resultados de recetas |
| Usar solo nombre normalizado o alias como UNIQUE | Homónimos, traducciones amplias y alias erróneos; confunde variantes legítimas |
| Identidad única sin preparación/parte/conservación | Fusiona arroz crudo/cocido, huevo/clara/yema o fresco/conserva |
| Añadir `canonicalId` directamente a `ingredients` | Posible técnicamente, pero acopla el contrato legacy a identidad/procedencia y no separa revisión; se prefiere tabla de mapeo auditable sin tocar v8 |
| Reemplazar FKs de recetas/compras por IDs canónicos | Las recetas pasarían a depender de una elección mutable de composición |
| Crear otra base nutricional y rehacer las tablas relacionadas | Coste y riesgo innecesarios para normalización visual |
| Alias canónicos nuevos en una séptima tabla | No requerido para lectura inicial; los alias originales ya están ligados a ingredientes y pueden resolverse por mapeo |
| Índice parcial para cabecera/default | Se usa UNIQUE ordinario con slot 1/NULL, declarable como índice compuesto, sin depender de soporte específico de índices parciales en entidades |
| Considerar todas las medidas heredadas ACCEPTED | El dataset no acredita temperatura, fracción comestible ni medición individual para todas ellas |
| Borrar viejos snapshots no referenciados | Viola la preservación histórica solicitada y puede perder trazabilidad externa |
| Aplicar v9 sin cambiar `onOpen` | El importador actual chocaría con RESTRICT y el seeder seguiría pudiendo reescribir recetas |

## Consecuencias

La interfaz puede presentar un catálogo organizado sin modificar el cálculo ni la persistencia histórica. Los casos ambiguos siguen siendo legibles y revisables; la opción conservadora es mantenerlos separados.

Aumentarán tablas y filas, y será necesario medir tamaño/latencia. Los escritores futuros deberán usar un contrato de publicación y los editores deberán explicar la creación de una versión nueva. La interfaz tiene que distinguir identidad de grupo y muestra seleccionada, especialmente cuando sus nutrientes difieren.

El manifiesto evita añadir tablas temporales de auditoría a cada entidad, pero no facilita consultas SQL ad hoc del historial. Se acepta ese compromiso para la primera transición; si auditoría histórica o sincronización lo exige, un ADR posterior puede añadir un registro relacional de eventos sin destruir estos manifiestos.

La semántica completa no queda garantizada solo por DDL. En particular, ninguna FK demuestra que dos alimentos sean equivalentes ni que una densidad sea correcta. Es obligatoria la revisión humana para cambios de identidad ambiguos y la validación transaccional de cobertura/procedencia.

## Verificación y límites

La especificación y el arnés en `docs/architecture/fixtures/` verifican unicidad, FKs, estados, referencias, conversión vinculada, preservación de datos previos y rollback sobre una base `:memory:` construida desde el esquema v8 real. El resultado final tiene 55 comprobaciones aprobadas con salida 0; el asset conserva su hash.

No se ha aplicado migración ni se ha compilado un modelo Room v9. Faltan creación fresca/migrada, enforcement de CHECK equivalente, API 26, validador completo de manifiesto, integración importador/seeder/editor y CI. El ADR no autoriza cambiar datos ni aprobar equivalencias científicas.

Checkpoint recomendado, no creado: `docs: canonical food catalog design`.
