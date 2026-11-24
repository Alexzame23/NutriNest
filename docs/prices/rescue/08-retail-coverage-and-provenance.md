# Cobertura y procedencia de los precios

La ficha del ingrediente y la comparación nutricional enlazan a los precios de cada ingrediente. La lista de la compra muestra la cobertura de la selección y las ofertas de cada artículo. El selector de supermercados distingue el directorio de tiendas de la cobertura de precios. El selector geográfico admite búsquedas manuales por municipio o código postal y pide permiso de ubicación aproximada únicamente al pulsar el botón correspondiente.

| Estado | Qué comunica la interfaz |
| --- | --- |
| `Loading` | Consulta en curso con indicador y anuncio accesible. Si tarda, cambia el mensaje de progreso. |
| `LiveData` | Hay observaciones vigentes para la consulta. La fecha individual indica cuándo se observó cada precio. |
| `StaleData` | La publicación o las observaciones han caducado. Los importes vencidos dejan de mostrarse. |
| `NoProviderCoverage` | Falta cobertura del proveedor para la zona consultada. |
| `NoProductMatch` | Falta una equivalencia comercial para el ingrediente. |
| `NoPricesInArea` | La consulta no produjo precios comparables para la zona y las tiendas seleccionadas. |
| `NetworkError` | Falló la conexión durante la actualización. Se puede reintentar. |
| `ProviderError` | La fuente falló o su respuesta no pudo usarse. Se puede reintentar. |
| `LocationRequired` | La consulta local pide un código postal escrito manualmente. |
| `UserProvidedPrice` | La observación fue aportada por un colaborador de Open Prices. La ficha indica el nivel de evidencia de esa observación. |

Cada oferta incluye supermercado, producto, marca conocida, formato, precio de venta, precio normalizado por kg, litro o unidad, tienda y zona conocida, fecha de observación, fuente y nivel de evidencia. Los importes de cesta son cálculos a partir de las observaciones que se detallan por producto. En los subtotales de una tienda se muestra la fuente y el intervalo de observación por fuente. La ausencia de importes se expresa mediante texto.

El buscador geográfico muestra las observaciones de tienda solo para una identidad física comprobada. Las zonas declaradas se muestran con su propio nombre. Un precio en línea sin zona declarada se identifica como tal. Las comparaciones locales y las cestas usan observaciones de tiendas físicas. La indicación de Open Prices describe el origen del dato publicado y su nivel de evidencia se muestra por separado. La compilación release descarta publicaciones de prueba.

Tras un fallo de actualización, una copia vigente puede seguir visible con su aviso de caché y el motivo del fallo. Si falta una copia utilizable, aparece el estado de red o proveedor que corresponda. Con la caché regional del flujo de resiliencia, un CP ya indexado también puede resolverse tras reiniciar sin conexión.

## Verificación

- `RetailPriceExperienceTest` recorre los estados de la ficha, comprueba el reintento, los anuncios accesibles, el texto al 200 %, las etiquetas de oferta en inglés y la procedencia visible cuando falla la conexión.
- `ShoppingCoverageExperienceTest` comprueba el código postal pendiente, la ausencia de precios y el fallo de actualización con copia guardada en español.
- `SupermarketDiscoveryTest` valida la búsqueda manual sin solicitar ubicación y el permiso aproximado en el manifiesto.
- `RetailPriceViewModelTest`, `ShoppingPriceRepositoryTest` y `StoreDiscoveryTest` cubren transiciones, filtrado, caducidad, copia vigente y alcance geográfico.
- Gradle genera los resultados en `app/build/reports/tests/testDebugUnitTest` y `app/build/reports/androidTests/connected/debug`. Se pueden reproducir con `./gradlew :app:testDebugUnitTest` y `./gradlew :app:connectedDebugAndroidTest`.
