# Fase 17 · Comparación comercial de ingredientes

Desde la ficha del ingrediente, abre **Comparar precios de supermercado** e introduce un código postal español. El piloto utiliza las tiendas del directorio de fase 16 para 28012. Cada observación debe referirse a una tienda del directorio por ID comercial o referencia OSM verificada. Se excluyen los ámbitos zonales y online mientras no exista una asociación explícita entre la zona del comercio y el directorio.

Los resultados muestran el precio del envase o de venta al peso, la tarifa normalizada y la fecha y fuente de la observación. Se ordenan dentro de su unidad (kg, litro o pieza) por la tarifa exacta antes de redondear para mostrarla. El contenido neto corresponde al envase completo. La pantalla indica las alternativas editoriales, excluye las promociones y los precios futuros o de al menos 30 días y no afirma disponibilidad de stock.

## Publicación autorizada

Configurar la URL HTTPS del publicador propio con la propiedad Gradle `nutrinestRetailPriceBaseUrl`. La configuración activa el cliente y el manifiesto de permisos de precios. Sin esa propiedad la aplicación muestra ausencia de cobertura y no realiza solicitudes de precios. El cliente lee el contrato v1 de [publicaciones regionales](11-price-snapshots.openapi.yaml) y valida la longitud, el SHA-256 y la cabecera de la publicación. La red recibe el identificador de región. El ingrediente, el código postal escrito y la selección de tiendas se procesan localmente.

El publicador de producción debe adjuntar `X-NutriNest-Price-Data: live` tanto al manifiesto como al snapshot, exclusivamente para publicaciones respaldadas por fuentes autorizadas y libres de fixtures. La falta de esta confirmación deja la publicación sin cobertura en la app. Las publicaciones del proveedor `fake-prices` también se rechazan explícitamente. Los proveedores de prueba están en los tests y el estado simulado tiene etiquetas ES/EN.

Las correspondencias deben usar la convención editorial `bedca.<id-de-registro>` para registros principales BEDCA visibles o secundarios del catálogo global. El backend existente publica `matches: []`. Para habilitar precios de extremo a extremo, integrar correspondencias aprobadas y retiradas al generar el snapshot, desplegar el publicador autorizado con TLS y comprobar la distribución con observaciones reales vigentes.

## Verificación

Ejecutar `./gradlew :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`. La prueba JVM usa un proveedor en memoria. La prueba instrumental abre el comparador desde la ficha y vuelve al panel nutricional.
