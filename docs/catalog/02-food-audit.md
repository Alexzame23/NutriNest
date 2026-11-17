# 02 — Auditoría del catálogo alimentario

Informe generado por `tools/audit_food_catalog.py`. Solo lectura; ninguna clasificación autoriza eliminación o modificación de datos.

Asset SHA-256 antes y después: `66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359`. Tamaño: 2207744 bytes. Dataset: `2026.07.29`.

## Resultados

| Indicador | Valor |
| --- | --- |
| foods | 936 |
| active | 924 |
| deprecated | 12 |
| importable | 923 |
| recipes | 250 |
| recipe_links | 1421 |
| recipe_unique_food_ids | 122 |
| recipe_absent_ids | [] |
| recipe_not_importable_ids | [] |
| raw_cooked_groups | 27 |
| no_countable_unit | 753 |
| no_household_measure | 530 |
| existing_issue_count | 105 |
| findings | 3629 |

## Distribuciones

Categorías: total / activos / importables. Activo significa is_deprecated=0; importable usa el SELECT real del importador con sus JOIN y exclusión, sin ejecutar INSERT.

| Categoría | Total | Activos | Importables |
| --- | --- | --- | --- |
| BEVERAGES | 56 | 56 | 56 |
| CEREALS | 117 | 116 | 116 |
| DAIRY | 120 | 118 | 117 |
| EGGS | 12 | 12 | 12 |
| FATS_OILS | 22 | 22 | 22 |
| FISH | 135 | 131 | 131 |
| FRUITS | 47 | 47 | 47 |
| LEGUMES | 56 | 56 | 56 |
| MEAT | 136 | 134 | 134 |
| MISC | 98 | 98 | 98 |
| SWEETS | 37 | 35 | 35 |
| VEGETABLES | 100 | 99 | 99 |

| prep_state | Total | Activos | Importables |
| --- | --- | --- | --- |
| BOILED | 30 | 30 | 30 |
| CANNED | 37 | 34 | 34 |
| COOKED | 4 | 4 | 4 |
| CURED | 6 | 6 | 6 |
| DRIED | 9 | 9 | 9 |
| FRIED | 22 | 22 | 22 |
| FROZEN | 10 | 10 | 10 |
| GRILLED | 5 | 5 | 5 |
| POWDERED | 13 | 13 | 13 |
| RAW | 187 | 183 | 183 |
| ROASTED | 31 | 31 | 31 |
| SMOKED | 4 | 4 | 4 |
| STEAMED | 2 | 2 | 2 |
| UNSPECIFIED | 576 | 571 | 570 |

El cruce completo categoría × preparación y distribuciones de calidad, completitud y densidades están en JSON (all/active/importable).

### Densidades y medidas

Densidades presentes: `{"count": 115, "max": 1.42, "median": 1.032, "min": 0.918, "p10": 0.918, "p90": 1.045}`. Gramos de porciones: `{"count": 1309, "max": 500.0, "median": 110.0, "min": 0.3, "p10": 9.0, "p90": 200.0}`.

| Tipo de porción | Filas |
| --- | --- |
| BOTTLE | 6 |
| CAN | 26 |
| CUP | 51 |
| GLASS | 64 |
| HANDFUL | 25 |
| PINCH | 20 |
| PORTION | 627 |
| SLICE | 49 |
| SPLASH | 28 |
| TBSP | 93 |
| TSP | 35 |
| UNIT | 285 |

Fuentes de porciones: `[{"count": 1309, "source": "NUTRINEST"}]`.

Entre los importables, 740 no tienen UNIT y 518 no tienen medida doméstica no genérica. Sin ninguna porción válida: 12 alimentos; importables afectados: 0.

UNIT significa pieza/unidad según catálogo (incluye envases). Ausencia de UNIT no es error: aceite o harina pueden usarse por masa o volumen. Medida doméstica significa cualquier tipo válido distinto de PORTION; una ración genérica no acredita peso de pieza, neto comercial, escurrido ni fracción comestible. No se inventan densidades, rendimientos o pesos. Las densidades no tienen procedencia individual/temperatura en el esquema; las porciones son adiciones NutriNest según metadata, no mediciones BEDCA acreditadas.

## Duplicados, variantes y preparación

Candidatos por igualdad de nombre ES/EN normalizado, alias por idioma, base+categoría, LanguaL+categoría y panel completo+atributos; también replaced_by_id. Se evalúan categoría, preparación, estado líquido/densidad, seis macros, resumen completo (incluidos NULL), panel con valor/tipo/cero lógico, fuente y LanguaL. No se usa distancia textual como decisión.

| Clasificación de pares | Cantidad |
| --- | --- |
| AMBIGUOUS_IDENTITY | 2 |
| DECLARED_REPLACEMENT | 12 |
| DISTINCT_OR_NUTRITIONAL_VARIANT | 453 |

Duplicados exactos demostrados con el criterio estricto: **0**. De los 12 reemplazos ya declarados en este asset, 12 tienen alguna diferencia en los seis macros comparados: no son intercambiables sin decidir qué composición conservar.

| Origen del grupo candidato | Grupos (pueden solaparse) |
| --- | --- |
| alias | 88 |
| base_name_norm | 74 |
| exact_attributes_and_panel | 2 |
| langual | 80 |
| name_en_norm | 49 |
| name_norm | 11 |

Grupos con al menos dos alimentos importables: `{"alias": 88, "base_name_norm": 64, "exact_attributes_and_panel": 2, "langual": 73, "name_en_norm": 44}`. Los recuentos anteriores incluyen obsoletos; un grupo puede aparecer bajo varias claves y no equivale a un duplicado confirmado.

EXACT_RECORD_DUPLICATE exige igualdad cuantitativa completa, cobertura >=10 nutrientes, atributos, procedencia y nombre ES o EN exacto. Es duplicación del registro representado, no prueba de identidad biológica/comercial. DECLARED_REPLACEMENT refleja una decisión anterior del dataset, no la revalida. DISTINCT_OR_NUTRITIONAL_VARIANT conserva filas con preparación/categoría/macros diferentes; AMBIGUOUS_IDENTITY exige revisión. Diferencias mínimas pueden ser redondeo y producir candidatos conservadores, nunca fusión automática.

Los grupos crudo/cocido exigen misma base normalizada y categoría, RAW y al menos un estado térmico explícito. No incluyen UNSPECIFIED como crudo, ni consideran CANNED/FROZEN equivalentes a cocción. No son pares de muestras equivalentes ni permiten deducir rendimiento a partir de kcal o agua.

## Hallazgos y revisión

Cada fila de findings.csv/JSON contiene finding_id, food_id, fuente, referencia, nombre, estado, preparación, causa, recomendación y evidencia; los pares identifican ambos extremos. Códigos y recuentos (hallazgos, no alimentos únicos):

| Código | Hallazgos | Alimentos únicos | Usados en recetas |
| --- | --- | --- | --- |
| ALIAS_CROSS_CATEGORY | 86 | 37 | 5 |
| AMBIGUOUS_IDENTITY | 4 | 4 | 0 |
| DECLARED_REPLACEMENT | 24 | 24 | 2 |
| DEFAULT_PORTION_COUNT | 12 | 12 | 0 |
| DISTINCT_OR_NUTRITIONAL_VARIANT | 906 | 329 | 43 |
| DRIED_UNIT_REVIEW | 2 | 2 | 0 |
| ENERGY_PANEL_UNIT | 799 | 799 | 113 |
| ENERGY_SCREEN | 16 | 16 | 3 |
| EXCLUDED_BY_IMPORTER | 1 | 1 | 0 |
| EXISTING_CARBS_FIBRE_NORMALISED | 12 | 12 | 3 |
| EXISTING_DEPRECATION | 12 | 12 | 0 |
| EXISTING_DUPLICATE_MERGED | 12 | 12 | 0 |
| EXISTING_ENERGY_MISMATCH | 26 | 26 | 4 |
| EXISTING_MASS_BALANCE_OUT_OF_RANGE | 37 | 37 | 7 |
| EXISTING_MONOFAT_EXCEEDS_FAT | 2 | 2 | 0 |
| EXISTING_POLYFAT_EXCEEDS_FAT | 2 | 2 | 0 |
| EXISTING_SATFAT_EXCEEDS_FAT | 3 | 3 | 0 |
| EXISTING_SPARSE_NUTRIENT_COVERAGE | 7 | 7 | 0 |
| EXISTING_STARCH_EXCEEDS_CARBS | 3 | 3 | 2 |
| EXISTING_SUGARS_EXCEED_CARBS | 1 | 1 | 0 |
| FAT_SUM_EXCEEDS_TOTAL | 16 | 16 | 1 |
| LOW_COVERAGE_OR_QUALITY | 26 | 26 | 3 |
| MASS_BALANCE_SCREEN | 68 | 68 | 14 |
| MISSING_SOURCE_NUTRIENTS | 169 | 169 | 13 |
| NO_COUNTABLE_UNIT | 753 | 753 | 87 |
| NO_HOUSEHOLD_MEASURE | 530 | 530 | 53 |
| NO_VALID_PORTION | 12 | 12 | 0 |
| PANEL_SUMMARY_DIVERGENCE | 43 | 40 | 7 |
| PORTION_LABEL_MISMATCH | 8 | 8 | 0 |
| PREPARATION_TEXT_UNSPECIFIED | 27 | 27 | 2 |
| SOURCE_CHILD_EXCEEDS_PARENT | 10 | 9 | 1 |

### Casos ilustrativos trazables

| ID | Fuente / nombre / estado | Causa | Recomendación |
| --- | --- | --- | --- |
| F000155 / BEDCA:680 | BEDCA: Bacón, ahumado, a la parrilla; ACTIVE/GRILLED | ENERC etiquetado kcal coincide numéricamente con kj, no con kcal. | Revisar unidad de definición y valor antes de mostrar energía; no corregir automáticamente. |
| F000157 / BEDCA:681 | BEDCA: Café, con leche; ACTIVE/UNSPECIFIED | ENERC etiquetado kcal coincide numéricamente con kj, no con kcal. | Revisar unidad de definición y valor antes de mostrar energía; no corregir automáticamente. |
| F000704 / BEDCA:913 | BEDCA: Leche merengada; ACTIVE/UNSPECIFIED | Subcomponente fuente mayor que su total. | Revisión humana del panel original; un clamp del resumen no lo resuelve. |
| F000705 / BEDCA:913 | BEDCA: Leche merengada; ACTIVE/UNSPECIFIED | Subcomponente fuente mayor que su total. | Revisión humana del panel original; un clamp del resumen no lo resuelve. |
| F000152 / BEDCA:606 | CESNID: Pudin de pasas; ACTIVE/DRIED | Panel fuente y resumen usan cifras distintas. | Revisar clamp o base de carbohidrato; no intercambiar ambos valores sin procedencia. |
| F000200 / BEDCA:730 | BEDCA: Berenjena, frita, en aceite de girasol; ACTIVE/FRIED | Panel fuente y resumen usan cifras distintas. | Revisar clamp o base de carbohidrato; no intercambiar ambos valores sin procedencia. |
| F000275 / BEDCA:762 | BEDCA: Huevo de gallina, yema, desecada; ACTIVE/UNSPECIFIED | Alimento seco/polvo con medida de unidad posiblemente heredada del fresco. | Validar que gramos corresponden al estado seco, no a unidad fresca ni peso con cáscara. |
| F000789 / BEDCA:946 | BEDCA: Melocotón, desecado; ACTIVE/UNSPECIFIED | Alimento seco/polvo con medida de unidad posiblemente heredada del fresco. | Validar que gramos corresponden al estado seco, no a unidad fresca ni peso con cáscara. |
| F000146 / BEDCA:606 | CESNID: Pudin de pasas; ACTIVE/DRIED | Alias compartido entre categorías distintas. | Revisar alcance del alias; no tratarlo como equivalencia alimentaria. |
| F000524 / BEDCA:870 | BEDCA: Patatas chips, "lights"; ACTIVE/UNSPECIFIED | Alias compartido entre categorías distintas. | Revisar alcance del alias; no tratarlo como equivalencia alimentaria. |
| F003072 / BEDCA:2494 | BEDCA2: Leche materna; ACTIVE/UNSPECIFIED | Activo en asset pero ausente del SELECT real de importación. | Mantener exclusión de interfaz existente; conservar registro fuente. |
| F000014 / BEDCA:74 | UGR: Batido lácteo, de fresa; ACTIVE/UNSPECIFIED | Flag del dataset o completitud <50%. | Revisar fuente y ausencias; no convertir faltantes en ceros. |
| F000036 / BEDCA:119 | UGR: Yogur líquido, desnatado, natural; DEPRECATED/UNSPECIFIED | Flag del dataset o completitud <50%. | Revisar fuente y ausencias; no convertir faltantes en ceros. |

### Pares que evitan falsos positivos

| Par | Nombres | Clasificación y evidencia |
| --- | --- | --- |
| PAIR:71:540 | Batido de almendras, en polvo / Crema de almendras, envasada, sin reconstituir | DISTINCT_OR_NUTRITIONAL_VARIANT; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 4.3, "fat_g": 7.638, "fiber_g": 5.5, "kcal": 0.94, "protein_g": 9.9}`; panel igual: False |
| PAIR:481:2423 | Tomate, pelado y triturado, enlatado / Tomate, maduro, pelado y triturado, enlatado | DISTINCT_OR_NUTRITIONAL_VARIANT; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 2.55, "fat_g": 0.425, "fiber_g": 0.204, "kcal": 18.05, "protein_g": 1.4}`; panel igual: False |
| PAIR:788:1267 | Chocolate, negro / Chocolate negro | DECLARED_REPLACEMENT; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 21.2, "fat_g": 6.3, "fiber_g": 12.1, "kcal": 2.69, "protein_g": 1.6}`; panel igual: False |
| PAIR:861:1282 | Espinaca, picada, congelada, cruda / Espinaca, congelada, cruda | DISTINCT_OR_NUTRITIONAL_VARIANT; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 0.2, "fat_g": 0.5, "fiber_g": 3.4, "kcal": 8.34, "protein_g": 0.112}`; panel igual: False |
| PAIR:1216:2132 | Atún, en escabeche / Atún en escabeche | DECLARED_REPLACEMENT; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 0.8, "fat_g": 11.4, "fiber_g": 0.0, "kcal": 70.41, "protein_g": 8.5}`; panel igual: False |
| PAIR:1725:1793 | Pan blanco, integral / Pan blanco, integral, de molde, tostado | AMBIGUOUS_IDENTITY; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0, "fiber_g": 0.0, "kcal": 0.0, "protein_g": 0.0}`; panel igual: True |
| PAIR:2433:2434 | Café, infusión / Café, infusión, descafeinado | AMBIGUOUS_IDENTITY; diferencias absolutas por 100 g: `{"alcohol_g": 0.0, "carbs_g": 0.0, "fat_g": 0.0, "fiber_g": 0.0, "kcal": 0.0, "protein_g": 0.0}`; panel igual: True |

### Calidad nutricional: interpretación

La discrepancia ENERGY_PANEL_UNIT afecta a 799 alimentos (113 usados por recetas). En 799 coincide con kj. No se ha aplicado conversión alguna.

Se contrastan panel fuente y food_macros por separado. ENERC se compara con kcal y kj: coincidencia con kj bajo definición kcal se reporta como inconsistencia interna observable, sin asumir que todos los valores requieran una misma conversión. El importador copia ambos conjuntos sin reconciliarlos. CHO puede usar una base diferente; clamps en macros no alteran necesariamente food_nutrients. Las incidencias EXISTING_* pueden describir correcciones históricas y se conservan separadas del cribado actual.

Se distinguen ausencias de tags, NULL y ceros lógicos; completeness_pct es metadata heredada, no porcentaje recalculado de 81 tags. La cobertura completa por alimento está en foods.csv. El cribado 4/9/4/2/7 considera proteína/grasa/carbohidratos/fibra/alcohol; diferencia >20 kcal y >20% es motivo de revisión, no diagnóstico. Puede omitir ácidos orgánicos, polialcoholes y factores específicos. Balance 95–105 g, densidad 0.5–2 y porción 0.1–2000 g son bandas explícitas de auditoría, no límites clínicos ni prohibiciones. No se suman indiscriminadamente nutrientes solapados ni se penaliza un cero por sí mismo.

## Uso en recetas y decisiones propuestas

El parser sigue únicamente listOf de BuiltInRecipeLibrary y las expansiones .recipes alcanzables. Acepta llamadas literales r/expandedRecipe e i/bedca, verifica helpers, rechaza expresiones desconocidas, cuenta llamadas independientemente y contrasta el total con BuiltInRecipeLibraryTest. No ejecuta Kotlin; cualquier cambio del DSL exige revisar el extractor. recipe_usage.csv conserva clave/nombre/archivo de receta, ID, gramos y unidad; recipe_coverage en JSON enumera cada ID requerido, incluidos ausentes/no importables.

| Recomendación principal por alimento | Cantidad |
| --- | --- |
| CONSERVAR | 6 |
| CONSERVAR_OCULTACION_EXISTENTE | 13 |
| CONSERVAR_Y_REVISAR | 917 |

Consolidación solo lógica y pendiente de evidencia; no se selecciona automáticamente un ID ganador. Ocultación solo conserva la política existente (deprecated/excluido por importador); no se recomienda ocultar alimentos por no aparecer en las 250 recetas, por ser poco comunes o por categoría. Sin telemetría ni inventario del usuario no puede medirse utilidad real. Todo alimento permanece conservado y exportado.

## Reproducción y garantías

```powershell
python tools/audit_food_catalog.py
python tools/audit_food_catalog.py --output-dir build/food-audit-repeat
```

Python >=3.10, biblioteca estándar y tools/ksql.py. Conexión URI mode=ro, query_only=ON; PRAGMA integrity_check y foreign_key_check; SHA-256 antes/después. Los resultados no contienen reloj, rutas absolutas ni identificadores aleatorios. Orden explícito, UTF-8, LF, JSON ordenado y manifest de hashes de salidas. Comprobar igualdad binaria de los archivos indicados por exports/manifest.json entre ambas carpetas. Las huellas de scripts/Kotlin se incluyen como procedencia; cambios en esas entradas cambian el informe aunque el asset sea igual. Código 0 indica auditoría completada, no ausencia de hallazgos; código 1 indica error de integridad/lectura/DSL/salida.

## Archivos de revisión

- `02-food-audit.md`: este informe reproducible.
- `02-verification.json`: evidencia de las dos ejecuciones y comprobaciones de esta fase; se registra por separado de las salidas deterministas del script.
- `exports/02-food-audit.json`: schema real, entradas/hashes, distribuciones, catálogo y evidencias completas.
- `exports/foods.csv`: 936 registros, cobertura, uso, medidas y recomendación principal.
- `exports/human_review.csv`: alimentos con hallazgos HIGH/REVIEW, códigos y uso en recetas; prioridades clínicas no inferidas.
- `exports/findings.csv`: hallazgos individuales, incluidos motivos de conservación.
- `exports/duplicate_pairs.csv`: comparación de ambos alimentos y evidencia cuantitativa.
- `exports/raw_cooked.csv`: grupos de preparación y IDs; no factores de conversión.
- `exports/recipe_usage.csv`: todos los enlaces de la biblioteca.
- `exports/portions.csv`: medidas y procedencia originales, con identidad del alimento.
- `exports/manifest.json`: SHA-256 de cada salida (excepto del propio manifest).

## Límites y decisiones para fase 03

No se ha contrastado cada estudio fuente con BEDCA externo ni se ha certificado el significado de todos los códigos LanguaL/value_type. Se audita el asset derivado entregado: el origen crudo no está en Git. Traducciones, aliases genéricos, etiquetas de piezas secas y pares con diferencias por redondeo requieren revisión humana; compartir LanguaL no implica idéntica composición. No se han ejecutado Android/Room ni modificado catálogos, recetas, identificadores o macros.

Fase 03 debe acordar identidad canónica frente a variantes, política de aliases y traducciones, contrato energético/CHO entre panel y resumen, procedencia y estado comestible de las porciones, y preservación de IDs/referencias históricas. Revisar primero HIGH y casos usados en recetas, antes de cualquier consolidación. El listado completo evita que un ejemplo favorable o una coincidencia textual se convierta en decisión global.

Checkpoint recomendado, no creado: `audit: classify existing food catalog`.
