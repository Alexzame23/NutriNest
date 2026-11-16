# 04 — Conversiones alimentarias validadas

Fecha: 2026-11-17. Motor de dominio implementado; no modifica el esquema Room v8.
Las fases 01–03 son el contexto de diseño, no una migración ya instalada.

## Contrato y API

`FoodQuantity.kt` define `Quantity(amount, unit, massBasis)`, `UnitDefinition`,
`ConversionResult.Success/Failure`, evidencia y factores tipados. Los números son
`Double`; no hay redondeos intermedios ni pesos alimentarios predeterminados.

```kotlin
val result = FoodQuantityConversions.toGrams(
    Quantity(1.25, UnitDefinition.KILOGRAM)
)
// Success: 1250 g comestibles; no necesita evidencia alimentaria.

val volume = FoodQuantityConversions.convert(
    Quantity(250.0, UnitDefinition.MILLILITRE), UnitDefinition.LITRE
)
// Success: 0.25 L; tampoco necesita densidad.
```

`toGrams` siempre solicita **gramos comestibles**, la entrada de los cálculos por
100 g. `convert` permite otras unidades y bases; por defecto conserva la base de
la cantidad de origen. La base por defecto de `Quantity` es EDIBLE: introducir
peso bruto exige indicar GROSS expresamente, no inferirlo del nombre del alimento.

`IngredientConversionContext(ingredientId, factors)` contiene únicamente factores
seleccionados para esa fila nutricional. No se comparten por categoría, nombre,
`sourceKey` o identidad canónica. Dos candidatos para el mismo paso producen
`AMBIGUOUS_FACTOR`; el llamador debe resolver la revisión vigente.

| Conversión | Factor requerido |
| --- | --- |
| g↔kg, oz, lb | Definiciones dimensionales integradas; constantes anteriores conservadas |
| ml↔L | Definiciones dimensionales integradas, sin densidad |
| volumen↔masa | `Density`: g/ml, misma base y evidencia aceptada |
| piezas↔masa | `UnitWeight` para una clave inequívoca `UNIT:…` |
| porciones↔masa | `UnitWeight` para `PORTION:…`, específico del ingrediente |
| cucharas, vasos, tazas | `UnitWeight`, o `HouseholdVolume` explícito más densidad al pasar a masa |
| medida doméstica↔volumen | `HouseholdVolume`; no necesita densidad |
| bruto/neto/escurrido↔comestible | `EdibleYield` específico, fracción en (0,1] |

No se fija una taza, cucharada, huevo, lata o ración universal. `CUP`, `TABLESPOON`
y `TEASPOON` conservan sus claves, pero el motor nuevo exige evidencia para su
factor. Las claves nuevas se construyen con `count`, `portion` y `household`;
una clave debe identificar tamaño, preparación y alcance cuando sea relevante.
NET es contenido sin envase, no una afirmación de comestibilidad. DRAINED tampoco
implica EDIBLE. UNKNOWN no permite conversiones. No se convierten cantidades de
una composición a otra: entero, clara, yema y desecado necesitan filas/evidencias
propias. El rendimiento no altera las cifras por 100 g.

## Evidencia y trazabilidad

Cada factor contiene `ingredientId` y `ConversionEvidence`: identificador,
localizador de fuente, versión, condiciones, confianza HIGH/MEDIUM/LOW/UNVERIFIED
y estado ACCEPTED/UNVERIFIED/REJECTED. Se exigen campos no vacíos, ACCEPTED y
confianza distinta de UNVERIFIED. Confianza baja aceptada sigue siendo explícita;
no se eleva a alta automáticamente. La API verifica estructura y compatibilidad,
no la veracidad científica: la aceptación corresponde al proceso de revisión.

`Success.appliedFactors` devuelve las instantáneas completas de todos los factores
usados, sin duplicados, y `engineVersion` identifica las reglas. Las conversiones
dimensionales devuelven una lista vacía. Este registro está en el resultado de
dominio: **todavía no existe almacenamiento Room del historial de evidencia**.
No se atribuye versión BEDCA a una fila local que no la contiene.

Se rechazan NaN, infinitos, cero, negativos, desbordamiento/subdesbordamiento a
cero, factores inválidos o ausentes, claves desconocidas, unidades incompatibles,
bases desconocidas, evidencia incompleta/no aceptada, ambigüedad y factores de
otro ingrediente. El resultado es un error tipado, nunca un factor 1 de reserva.
Los factores no usados no bloquean una conversión dimensional independiente.

## Adaptación y uso en recetas

`IngredientQuantityConversions.legacyContext` conserva el valor de `densityGml`
como evidencia UNVERIFIED, base UNKNOWN, con localizador y versión de la fila
local. No lo atribuye a una medición científica. `portionFactor` adapta
`IngredientPortionEntity` conservando ingrediente y gramos; exige que el llamador
aporte clave, base y evidencia. No interpreta `portionType=UNIT` como huevo
comestible ni usa `isDefault` para acreditar factores. El ID mutable de una
porción no se convierte en una clave histórica estable por sí solo.

El formulario de creación y su ViewModel usan la API estricta para todas las
nuevas cantidades. Una conversión no disponible muestra un mensaje ES/EN, impide
añadir la cantidad y solicita peso comestible en gramos. Editar una cantidad con
texto inválido marca la fila e impide guardar los gramos anteriores por accidente.
Se mantiene el límite de producto de 100 000 g y se valida también la suma al
añadir dos veces el mismo ingrediente. El motor de dominio no impone ese límite.
El texto editable generado desde una conversión conserva la precisión de Double.

El selector actual sigue ofreciendo g/L para líquidos. No incorpora todavía el
selector canónico ni controles de porciones. La API soporta los factores revisados,
pero el adaptador v8 no dispone de un repositorio de evidencia aceptada: las
conversiones alimentarias heredadas quedan bloqueadas para entradas nuevas hasta
que se conecte dicho repositorio. Esto incluye densidades numéricamente presentes,
no solo líquidos de densidad desconocida.

## Conservación histórica

`MeasureConversions` mantiene exactamente las operaciones y formato heredados,
incluido el antiguo 1 g/ml, exclusivamente por compatibilidad. Sus métodos de
conversión están deprecados para impedir que se confundan con la API nueva.
Los tests anteriores se mantienen sin cambiar sus expectativas. No quedan
llamadas a sus métodos de conversión desde las pantallas de entrada nuevas.

No se cambia `NutritionValues.forGrams`, `IngredientWithQuantity.nutrition`, los
gramos o claves persistidas, ingredientes, porciones, importador, seeder, consultas,
migraciones Room ni cálculos de perfiles corporales. No se reinterpreta una receta
guardada usando factores nuevos. El adaptador `fromLegacyKey` conserva los nueve
nombres del enum; una clave desconocida en una entrada nueva produce INVALID_UNIT.
La presentación histórica sigue utilizando su ruta anterior.

`IngredientValidator` ya rechaza números no finitos y porciones/densidades inválidas;
se conserva sin cambios. Sus intervalos de plausibilidad de edición no acreditan
una fuente ni sustituyen la validación de evidencia del motor.

Asset BEDCA sin modificaciones, SHA-256:
`66682591bb5ae18bd53f7b0d9f720718e3dd42abe81fc08a2d90e2141da90359`.

## Validación

`FoodQuantityConversionsTest` utiliza únicamente **fixtures alimentarios ficticios**,
identificados como tales en fuente y versión. Cubre masa, volumen, piezas,
porciones, medidas domésticas, huevo comestible y con cáscara, fracciones de bruto,
neto y escurrido, trazabilidad, reversibilidad con tolerancia relativa 1e-12,
valores inválidos, incompatibilidades, ambigüedad y conservación de cálculos de
recetas históricas incluso con unidades desconocidas o densidad cambiada.

Comandos de verificación (JDK 21 y caché local del proyecto):

```powershell
$env:JAVA_HOME = Join-Path (Get-Location) '.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle'
.\gradlew.bat testDebugUnitTest assembleDebug --offline --console=plain
python tools/check_strings.py
git diff --check
```

Resultado: `testDebugUnitTest` y `assembleDebug` completados correctamente.
125 tests en 17 suites, cero fallos, errores u omitidos: 21 tests nuevos y los
104 anteriores (incluidos los 17 de `MeasureConversionsTest`). La comprobación
de strings ES/EN y `git diff --check` también pasan. El aislamiento inicial
bloqueó el socket local del daemon; la ejecución se completó fuera de ese
aislamiento con JDK 21 y dependencias offline, sin cambiar Gradle ni dependencias.
No se afirma validación visual en dispositivo ni una migración 8→9: no se
implementa aquí.

## Preparación para fase 05

1. Revisar las densidades y porciones del catálogo: fuente individual, versión,
   preparación, talla, temperatura cuando aplique y base de masa. La auditoría de
   fase 02 identifica 115 densidades y 1309 porciones sin esa acreditación completa.
   No se ha aprobado ningún peso o densidad alimentaria de producción en esta fase.
2. Implementar el repositorio de evidencias versionadas de fase 03, su selección
   inequívoca y las revisiones de aceptación. En v8 `ingredientId` puede conservarse
   al editar una fila; la futura capa de instantáneas debe garantizar su inmutabilidad.
3. Persistir cantidad original, unidad/bases, gramos calculados, factores completos
   y versión del motor para nuevas operaciones, sin recalcular recetas anteriores.
4. Conectar el selector canónico y las opciones de piezas/porciones al contexto
   aceptado. Mantener g disponible y mostrar causas de conversión no habilitada.
5. Probar las migraciones y escritores de fase 03 por separado; este motor no
   sustituye la validación Room, importador/seeder ni pruebas en API 26.
