# R5 · Del precio de un envase al ingrediente canónico

El snapshot R4 de Madrid contiene **una observación real y un envase**. El precio de 1,69 € corresponde a una botella Nestlé de 900 g. La etiqueta y la ficha del fabricante permiten ofrecerla para «Yogur líquido, aromatizado sabor s/e». La correspondencia describe una compra culinariamente equivalente. La confianza del match es `MEDIUM` (81/100) y la observación conserva su confianza original `LOW`. [Observación 357963](https://prices.openfoodfacts.org/api/v1/prices/357963) · [Ficha Nestlé](https://www.yoguresnestle.es/nestle/yogures-liquidos/yogur-liquido-fresa-platano-semidesnatado) · [Ingredientes del envase de 900 g](https://supermercado.eroski.es/es/productdetail/26922831-yogur-liquido-sabor-fresa-platano-nestle-botella-900-g/)

## Cobertura medida

Cada fila cuenta observaciones distintas. «Candidatas» cuenta observaciones con al menos un ingrediente candidato. Las tres columnas siguientes son desenlaces excluyentes. Ambas filas evalúan la **misma** observación del 2 de noviembre de 2026, tienda 7394, prueba 142969.

| Evidencia disponible | Recibidas | Candidatas | Aceptadas | Ambiguas | Incompatibles | Sin candidata |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Solo el snapshot R4 | 1 | 1 | 0 | 1 | 0 | 0 |
| Snapshot y ficha comercial revisada | 1 | 1 | 1 | 0 | 0 | 0 |

Sobrevive **1/1 (100 %)** con la ficha y **0/1 (0 %)** sin ella. En el lote enriquecido se evaluaron tres parejas. Se aceptó 1/3 y se rechazaron 2/3. Se observaron cero aceptaciones entre los dos negativos etiquetados. Una tienda, una marca y un SKU resultan insuficientes para estimar precisión general.

| Ingrediente evaluado | Evidencia decisiva | Resultado |
| --- | --- | --- |
| Yogur líquido aromatizado, sabor sin especificar (950) | Lácteo fermentado, aromatizado y bebible, con leche, aromas y cultivos. Formato y precio por envase, masa neta en gramos. | `CULINARY_EQUIVALENT` |
| Yogur líquido **entero**, sabor fresa y plátano (2538) | Coinciden sabor y forma. La variante comercial es semidesnatada. | `INCOMPATIBLE` |
| Yogur líquido **natural**, azucarado (1133) | Los aromas y los zumos distinguen el producto de un yogur natural. | `INCOMPATIBLE` |
| 2538 evaluado solo con R4 | El nombre incluye fresa y plátano. Faltan la revisión de composición y la clase grasa. | `AMBIGUOUS` |

De las ocho familias solicitadas, la revisión publicada contiene yogur. Leche, arroz, huevos, pasta, aceite, tomate y pollo tienen cero observaciones en ese snapshot. El corpus conserva sus bytes originales, atribución ODbL, identificadores y SHA-256 `18412f437f92a54e84b49ea210e412b0480888ab0cf23e5c0a34a30490caffb9`. Los 24 pares simulados de regresión se contabilizan aparte. [Snapshot R4](../../../backend/prices/fixtures/madrid-r4.snapshot.json) · [Etiquetas del corpus](../../../backend/prices/fixtures/madrid-r4.validation.json) · [Procedencia de R4](04-live-publisher.md)

## Identidades y lectura del precio

| Identidad | Valor en el caso real | Ámbito |
| --- | --- | --- |
| `canonicalIngredientId` | `nn.yogurt.drinking.flavoured` | Clave editorial pública del ingrediente. |
| `commercialProductId` | `open-prices.product.8435257073279` | ID estable del producto del snapshot, también disponible como `products[].id`. |
| `providerProductId` | `8435257073279` | Clave original dentro de `open-prices`, conservada en `origin.recordKey`. |
| `packageId` | `open-prices.pack.8435257073279` | Envase vendible al que apuntan el match y la observación. |
| `retailerSku` | Ausente | Código de surtido opcional, con `retailerId` y valor textual que conserva los ceros iniciales. |
| GTIN | `8435257073279` | Código opcional del envase. Se valida el dígito de control y se compara en 14 posiciones. |

Open Prices usa el código de barras como clave externa en este piloto. La resolución atraviesa el envase y una decisión editorial versionada para llegar al ingrediente. Room asigna UUID canónicos propios en cada instalación. Android enlaza el ID histórico local mediante `ingredient_canonical_map.canonical_id` con `canonical_ingredients`, comprueba ámbito global, visibilidad, revisión aplicada, hash de la fuente, clave de concepto y nombre revisado. El registro compartido vincula esa entrada al ID público. La ficha de ingrediente y la lista de compra usan la misma resolución. Las publicaciones anteriores siguen admitiendo las claves históricas `bedca.<id>` acreditadas. [Registro de perfiles canónicos](../../../app/src/main/assets/retail-canonical-profiles.json)

## Decisión, auditoría y publicación

El nombre normalizado, categoría, concepto, GTIN o SKU de la cadena generan candidaturas. La evaluación exige revisión de ambas fichas y concordancia en preparación, composición, forma, base de contenido, atributos y unidad. Un perfil genérico revisado puede exigir componentes culinarios concretos. Los perfiles de composición exacta comparan los ingredientes en orden. La ficha comercial queda fijada al producto y envase completos, incluida la marca. La compra en gramos, mililitros o piezas mantiene su dimensión. Convertir un envase a masa comestible para presupuestar una receta requiere evidencia específica. [Fichas comerciales revisadas](../../../backend/prices/reviewed-labels.json)

| Clasificación | Condición de uso |
| --- | --- |
| `EXACT_SKU` | Objetivo comercial explícito, GTIN o SKU de la misma cadena, formato, cantidad, unidades, marca exigida y perfil verificados. |
| `CULINARY_EQUIVALENT` | Perfil culinario revisado compatible y unidad comparable, con candidato único y umbral alcanzado. |
| `ALLOWED_SUBSTITUTE` | Sustitución prevista expresamente en el perfil. Requiere revisión humana para publicarse como alternativa. |
| `AMBIGUOUS` | Evidencia insuficiente, competidores plausibles, código duplicado o puntuación insuficiente. Queda pendiente. |
| `INCOMPATIBLE` | Conflicto demostrado, por ejemplo crudo frente a cocido, entero frente a semidesnatado o alérgeno excluido. |

Las decisiones quedan en `match_revision` de SQLite junto con ambas entradas, fuentes, versión de reglas, señales, conflictos, faltantes, puntuación, umbral y confianza. El publicador reconcilia el catálogo del plan dentro de la transacción del snapshot y emite únicamente las aprobaciones. Cada match lleva la revisión, la clasificación y la huella de la evidencia. Una etiqueta cambiada invalida su acreditación. Una revisión editorial nueva puede reprocesar el último lote vigente durante la cadencia sin consumir otra petición. La actualización conserva los IDs y la fecha del precio. Las tablas y los valores de nutrientes BEDCA quedan intactos.

## Reproducción

En el emulador se verificó la revisión 2, obtenida desde una copia SQLite de R4 durante la cadencia de ingesta. El APK validó el SHA-256 `24196150b1988b629317f98990083bc8bcfe27c2d5b0b35d906d7e6dd9b629ab`, mostró una oferta para el perfil aromatizado, cero para el entero y recuperó la misma observación al reabrir Room. Pasaron las 34 pruebas Node deterministas, la prueba instrumentada, el verificador OpenAPI y 214 pruebas JVM del árbol de trabajo. Dos pruebas JVM opcionales de red se omitieron.

Desde la raíz, con Node 24.11 o posterior, el primer comando contrasta las etiquetas del corpus y cuenta las observaciones. El segundo ejecuta también las regresiones de preparación, formato, alérgenos, colisiones y revisión.

```powershell
node --test backend/prices/commercial-matching.test.mjs
node --test backend/prices/*.test.mjs
node backend/prices/run-matching.mjs history build/live-publisher/madrid.sqlite nn.yogurt.drinking.flavoured open-prices.pack.8435257073279
```

Para la verificación HTTPS se usa el procedimiento de R4 con el publicador y los dos APK de esta revisión. El test instrumentado abre el catálogo real, busca el ID local por su canónico, comprueba la oferta aceptada y la ausencia de oferta para el yogur entero y verifica los bytes de Room tras reabrir la base. La publicación de producción requiere desplegar el worker y una aplicación con el registro correspondiente. [Ejecución con el emulador](04-live-publisher.md)
