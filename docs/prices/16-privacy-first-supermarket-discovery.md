# Fase 16 · Localización de supermercados

Desde Inicio → Supermercados se puede buscar por código postal español o por nombre completo del municipio. La búsqueda devuelve los establecimientos del directorio que coinciden con el área escrita y permite elegir uno. La ficha enlaza al directorio del comercio y muestra la fecha de comprobación.

## Permisos y datos

- Android declara `ACCESS_COARSE_LOCATION` desde API 26 hasta el objetivo API 36. Se solicita tras pulsar «Usar ubicación aproximada». La llamada a `LocationManagerCompat` obtiene una lectura del proveedor de red, con un máximo de diez segundos. Se cancela al salir de la pantalla o pasar a segundo plano.
- La coordenada aproximada se redondea a una cuadrícula de 0,02° en memoria. El repositorio calcula la distancia en línea recta en un radio de 5 km. La interfaz conserva temporalmente las coincidencias y sus distancias. «Dejar de usar la ubicación» las descarta. «Gestionar permiso de ubicación» abre los ajustes de NutriNest. Si Android permite reanudar el mismo proceso tras un cambio de permiso, se vuelve a comprobar el permiso y se eliminan los resultados derivados al detectar la retirada.
- La búsqueda manual, el cálculo de proximidad y la lectura de precios previamente instalados son locales. El buscador envía cero coordenadas, códigos postales, municipios, selecciones o identificadores al backend. Pulsar la ficha oficial abre la URL pública del comercio. Las pantallas de ingredientes y recetas siguen utilizando sus repositorios locales.

## Cobertura y procedencia

El piloto cubre dos establecimientos para el CP 28012 de Madrid. Direcciones comprobadas el 18 de noviembre de 2026 en las fichas oficiales de [Lavapiés](https://www.carrefour.es/tiendas-carrefour/supermercados/carrefour-market/lavapies_-_s.aspx) y [Tirso de Molina](https://www.carrefour.es/tiendas-carrefour/supermercados/carrefour-market/tirso-de-molina-madrid_-_s.aspx). Coordenadas © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright), ODbL 1.0, objetos [622204638](https://www.openstreetmap.org/node/622204638) y [6918727592](https://www.openstreetmap.org/node/6918727592). El catálogo es parcial y su revisión se distribuye con la app.

La región de publicación reservada para este piloto es `es-madrid-28012`. La pantalla consume solamente snapshots vigentes del caché Room para esa región. Las observaciones conservan fecha, fuente y ámbito. Se asocia un precio a una tienda cuando su ámbito es `STORE` y la tienda publicada comparte un ID comercial o una referencia OSM verificada. `ZONE` y `ONLINE` con zona se muestran con el nombre del área declarada. `UNKNOWN` y `ONLINE` sin zona se excluyen de la cobertura geográfica. El proveedor Open Prices propaga la referencia que ya contrasta con su configuración del piloto. Los snapshots v1 anteriores siguen siendo legibles. Las publicaciones que carecen de identidad común y las tiendas sin observaciones propias muestran ausencia de cobertura local. La proximidad y los precios nunca se usan para afirmar stock.

## Verificación

Ejecutar `./gradlew :app:testDebugUnitTest :app:lintDebug`, `./gradlew :app:connectedDebugAndroidTest` y `node --test backend/prices/*.test.mjs`. La suite cubre coincidencia manual, dos tiendas en el radio, zona sin servicio, identidad exacta de tienda, observaciones futuras y snapshots vencidos o de otra región. La prueba de interfaz comprueba el manifiesto combinado y el recorrido desde Inicio a una tienda por CP. Pasó en emuladores con API 26 y 36.

En API 36 se comprobó la denegación, la concesión con una ubicación de red simulada en Madrid y la retirada del permiso. En modo avión se comprobó la consulta manual, la selección, el mensaje de ausencia de precios propios y el acceso a recetas e ingredientes.

Para comprobar el flujo del sistema, repetir en API 26–29, 30, 31 y 36 sobre dispositivos de prueba. Desde una instalación limpia, abrir el buscador, denegar y buscar 28012. Volver a pulsar «Usar ubicación aproximada», conceder y probar con un proveedor de red operativo en Madrid. Retirar el permiso desde los ajustes de la app y comprobar que la búsqueda manual funciona al regresar. En API 30 y posteriores, repetir con el permiso de un solo uso si el dispositivo lo ofrece. Repetir la búsqueda manual y abrir ingredientes y recetas en modo avión.

## Entrada en fase 17

El catálogo aporta identidad física y región de publicación. La proyección `coverageFor` conserva las observaciones y su procedencia para alimentar el comparador de fase 15. Antes de publicar precios piloto se necesitan IDs de Open Prices revisados para los objetos OSM, derechos vigentes, distribución autenticada y carga del snapshot regional. Para la cesta, usar observaciones locales del mismo establecimiento al agregar un total por tienda y comunicar cobertura, partidas sin observación y términos de oferta por separado.
