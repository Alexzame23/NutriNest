# Fase 18 · Precios en la lista de compra

La lista mantiene la agrupación, el orden y la casilla de compra originales. La cantidad de cada fila sigue siendo el agregado semanal de gramos comestibles de `ShoppingListCalculator`. El botón **Actualizar precios** proyecta un estado temporal sobre las filas por ID. No escribe en Room ni regenera el menú. Excluir comprados oculta sus precios y los retira del cálculo de cobertura sin modificar sus casillas, sus cantidades ni el contenido de la lista.

## Consulta y cálculo

- La actualización pide una publicación regional al repositorio compartido con la fase 17 (manifiesto y snapshot del mismo regional, hasta dos GET). El código postal se resuelve localmente al identificador de región. El DAO lee las claves editoriales con `IN` en grupos de hasta 900 IDs distintos. El motor indexa los envases, precios y correspondencias una vez para todo el lote.
- La proyección acepta solo observaciones de tiendas verificadas, precios regulares vigentes y correspondencias exactas con evidencia de gramos comestibles. El atributo opcional `edibleMass` del [contrato v1](11-price-snapshots.openapi.yaml) declara gramos por envase o por kg facturado y su referencia editorial. Los snapshots anteriores siguen siendo legibles. Cuando falta la evidencia o la unidad es incompatible se muestra el estado sin precio comparable.
- Para envases se muestra el coste proporcional y el desembolso de los envases completos necesarios. Se ordena por menor desembolso y después por coste proporcional. La venta al peso aparece a continuación por coste proporcional y el importe final depende del peso facturado. Cada opción identifica tienda, cadena, fecha y fuente. La cabecera muestra zona, fecha de publicación y cobertura sobre las filas comparadas.
- El lote se calcula bajo demanda, fuera de la recomposición. Los resultados caducan en memoria. Cambiar de semana o CP descarta la consulta en curso y los resultados. Cambiar los gramos invalida la proyección anterior. Al recrear la pantalla con estado guardado se recuperan semana, CP y filtro. Los precios requieren una nueva actualización. La lista se sigue leyendo en local durante carga, error o pérdida de red.

## Verificación y fase 19

`./gradlew :app:testDebugUnitTest :app:lintDebug` cubre la agregación existente, precios por lote, envases frente a venta al peso, unidades sin conversión, caducidad, ausencia de cobertura, exclusión de comprados, cambio de semana y restauración en modo sin conexión. Los importes de prueba son simulados.

Para validar precios reales, publicar las claves BEDCA y la masa comestible completa revisada por envase, con procedencia, tiendas verificadas y observaciones vigentes. Sigue aplicándose la configuración HTTPS y confirmación `live` de [fase 17](17-ingredient-retail-comparison.md). Antes de optimizar una cesta en fase 19, hace falta cobertura comparable por tienda, resolver observaciones ambiguas, contrastar redondeos con tickets y definir cómo se informa de partidas sin precio y de condiciones de compra. La fase 18 muestra resultados por artículo.
