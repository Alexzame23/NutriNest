# Fase 14 · Correspondencia entre ingredientes y envases

El servicio editorial de Node 24 recibe un lote completo de ingredientes y envases, calcula candidaturas y conserva las decisiones en SQLite. Un ingrediente genérico puede corresponder a varios envases de distintas marcas. La unidad de decisión es la pareja clave editorial–ID de envase. El servicio solo escribe en su base de correspondencias. Las cantidades, nutrientes, recetas e identidades locales siguen en el dominio canónico.

## Entrada editorial

Usar una base por ámbito editorial y entregar un lote completo en cada reconciliación. La ausencia de una pareja del nuevo lote retira la decisión anterior. Serializar la entrega de lotes en orden de revisión. No ejecutar una página parcial del proveedor como lote completo.

- `ingredients[]` identifica cada canónico por `key` comercial estable, `canonicalId`, `revision` del catálogo y `name`. El editor consigna `reviewed` tras comprobar concepto, preparación, parte, variante y estado. Copiar el estado del catálogo a `catalogState`. REVIEW_REQUIRED, HIDDEN y OBSOLETE bloquean el match. El par de arroz integral BEDCA 904/1009 distingue crudo y hervido. BEDCA 2660 carece de estado inequívoco. La fase 06 también separa los duplicados dudosos 1725/1793 y las variantes de café 2433/2434. [Auditoría](../catalog/02-food-audit.md) · [Curación](../catalog/06-curated-canonical-catalog.md)
- `catalog[]` contiene las parejas `product` y `package` de PriceProvider.listCatalog. Reunir todas las páginas. El editor añade `facts` con referencia a la ficha o etiqueta versionada en `sourceRef`, `reviewed: true` y `labelDigest` calculado sobre los objetos completos mediante el helper exportado. Revisar marca, variante, declaraciones y contenido del envase antes de firmar esa proyección con su huella. El digest detecta cambios posteriores de SKU, atributos o envase. La confianza en el editor depende del control de acceso al proceso de ingesta.
- En ambos perfiles se usan claves editoriales exactas para `category`, `concept`, `preparation`, `composition` (p. ej., clase grasa o corte), `form`, `basis` (incluido peso escurrido) y `diet` (perfil de todos los atributos dietéticos relevantes). `ingredients` contiene la lista ordenada de componentes codificados. Traducir las declaraciones a un vocabulario común antes de evaluar. Usar null para valores ausentes o imposibles de codificar. Una preparación desconocida, una declaración contradictoria o una ficha incompleta exige revisión.
- `unit` expresa la unidad de compra del ingrediente. Solo se comparan G, ML y UNIT en una misma base y formato. Un envase fijo exige cantidad total positiva y número de unidades. La venta variable por KG admite el objetivo G. Los pasos de volumen a masa, pieza a gramos, neto a escurrido y crudo a cocinado necesitan evidencia específica y quedan fuera de la conversión automática. [Conversiones de fase 04](../architecture/04-validated-food-quantity-conversions.md)
- `excludeAllergens` enumera restricciones explícitas. Una lista vacía representa una consulta sin exclusiones. Por cada exclusión, la ficha debe declarar FREE. CONTAINS y MAY_CONTAIN son incompatibles, y la omisión queda pendiente.
- Un objetivo comercial concreto añade `target` con GTIN, cantidad total en la unidad comparada, unidades por envase y marca si procede. La comparación usa el dígito de control y la representación de 14 dígitos de fase 12. Un GTIN compartido por varios registros del lote bloquea su aprobación hasta resolver la duplicidad.

## Reglas y revisión

Las candidaturas surgen de tokens de nombre normalizado, categoría, concepto o GTIN. Las reglas guardan señales verificadas, coincidencia de nombre, conflictos y faltantes. Un cambio relevante explícito bloquea la equivalencia. Un concepto incluido en `substituteConcepts` puede proponerse como SUSTITUTO si los únicos conflictos son concepto e ingredientes. Su aprobación requiere un editor y todas las comprobaciones de seguridad.

La puntuación suma 9 puntos por dimensión estructurada coincidente y 10 por nombre idéntico normalizado, con máximo de 100. Se guardan umbral (80 por defecto), puntuación y versión de reglas. Es una puntuación heurística. Calibrarla con ejemplos independientes por fuente y familia antes de atribuirle una probabilidad. Los vetos y faltantes siguen aplicándose con umbral cero. Cuando varios canónicos plausibles compiten por el mismo envase, el editor debe resolver los rivales antes de aceptar uno.

Cada cambio crea una revisión inmutable con fecha UTC, entradas completas, procedencia, política y resultado. Un reintento idéntico conserva la versión. Las escrituras de un lote son atómicas. La revisión humana exige versión vigente, actor y motivo. Para corregir datos, rectificar la evidencia editorial y reconciliar el lote. Una decisión revisada vuelve a revisión cuando cambia su evidencia y sigue siendo admisible.

## Operación local

Desde la raíz, con Node 24 y un directorio build existente, generar un lote mínimo simulado y recorrer el flujo.

```powershell
node --input-type=module -e "import {writeFileSync} from 'node:fs'; import {cases,comparison} from './backend/prices/fixtures/matching.simulated.mjs'; writeFileSync('build/match-demo.json', JSON.stringify(comparison(cases[0])))"
node backend/prices/run-matching.mjs reconcile build/match-demo.sqlite build/match-demo.json
node backend/prices/run-matching.mjs history build/match-demo.sqlite arroz pack.arroz
node backend/prices/run-matching.mjs accepted build/match-demo.sqlite
node --test backend/prices/matching.test.mjs
```

`current` devuelve el último evento por pareja, incluidas las retiradas. `review DATABASE review.json` recibe por ejemplo:

```json
{"ingredientKey":"arroz","packageId":"pack.arroz","version":1,"status":"PENDING","actor":"editor.catalogo","reason":"Verificar una nueva etiqueta del proveedor"}
```

`accepted` proyecta exclusivamente aprobaciones a IngredientProductMatch v1. MISMO_PRODUCTO y EQUIVALENTE se proyectan a EXACT, y SUSTITUTO aprobado a ALTERNATIVE. La revisión conserva la distinción completa. La clave externa se deriva de ambas identidades, con origen editorial nutrinest-editorial. Registrar esta fuente al componer el snapshot. El publicador de fase 13 continúa separado de la evaluación editorial.

## Evaluación y paso a fase 15

| Etiqueta de referencia | Aceptada | Rechazada | Pendiente |
| --- | ---: | ---: | ---: |
| MISMO_PRODUCTO | 1 | 0 | 0 |
| EQUIVALENTE | 10 | 0 | 0 |
| NO_COMPATIBLE | 0 | 7 | 0 |
| SUSTITUTO | 0 | 0 | 1 |
| Ambigua | 0 | 0 | 5 |

24 pares simulados de ocho familias, con dos marcas de leche. Precisión automática 11/11 (100 %), incompatibles aceptados 0/7, pendientes 6/24 (25 %). Casos pendientes: estado del arroz, huevos por peso, grado del aceite, declaración de huevo en pasta, sustitución pollo–pavo y base de la conserva. El fixture contiene las 24 comparaciones y sus etiquetas. La suite añade colisiones de identidad, reapertura, vigencia de evidencia y pruebas de revisión. Estas métricas describen el fixture y requieren contraste con etiquetas comerciales independientes.

Para fase 15 se necesitan claves editoriales resueltas a las revisiones canónicas de cada cliente, fichas de envase autorizadas, validación independiente por marca/familia y umbral acordado. Integrar las aprobaciones y retiradas en la publicación regional, con control de acceso editorial, copia de seguridad del historial y comprobación de derechos. Para comparar coste por cantidad comestible, usar las conversiones validadas de fase 04 y mostrar alternativas aprobadas como tales. Mantener separadas las recomendaciones de compra y la composición nutricional de la receta.
