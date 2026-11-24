# R7 · Resiliencia de la caché de precios

Room instala los bytes verificados y su índice de CP en una única transacción. La clave combina la huella de la URL HTTPS del publicador y la región. Cada publicación mantiene las observaciones únicas por fuente e identificador de origen. La reinstalación de la misma revisión es idempotente. Un hash distinto para la misma revisión o una revisión antigua carecen de efecto sobre los precios guardados. La migración 11 → 12 conserva los datos del usuario y los registros de caché anteriores en el ámbito legacy. Como estos registros carecían de identidad del publicador, la primera descarga verificada después de la actualización establece la procedencia para el acceso sin conexión.

Las pantallas de ingrediente y cesta consultan Room antes del refresco y pueden mostrar el último resultado verificado mientras llega la respuesta. El comparador conserva el resultado vigente ante timeout, error de integridad, ausencia temporal de publicación o lote vacío. Un 410 de una revisión concreta invalida únicamente esa revisión y conserva el marcador que impide reinstalarla. El directorio vigente determina la región preferida. Cuando el directorio no está disponible, Room solo resuelve el CP si la última publicación verificable de ese publicador acredita una única región. El selector de supermercados puede recuperar por CP las tiendas de ese índice. El resultado de otro CP se descarta al cambiar de zona.

Cada observación tiene el TTL de su fuente, aplicado tanto a la ficha como a la cesta y su optimizador. Open Prices usa un máximo de 30 días. La vigencia efectiva tiene como límite la caducidad del snapshot, que ya incorpora la política de frescura y derechos del servidor. La pantalla identifica las lecturas de Room y retira los importes al caducar. El refresco manual consulta el directorio y el manifiesto. En Android, las revalidaciones ocurren con el uso de las pantallas. La ingesta programada y sus reintentos siguen en el publicador. Si una respuesta de Open Prices llega vacía mientras sigue vigente la publicación del mismo ámbito, la ingesta registra EMPTY_BATCH y conserva la publicación anterior con su caducidad original.

La tabla price_snapshot_sync conserva el último intento de red, el último éxito y un código de error por publicador y región. La región vacía identifica un fallo anterior a la resolución geográfica. Los errores conservan códigos de integridad, transporte y disponibilidad, sin cuerpos de respuesta ni coordenadas. La limpieza elimina como máximo 32 payloads vencidos por revalidación y conserva los marcadores de revisión. Se limita a las tablas de precios.

## Matriz de verificación

La prueba instrumentada utiliza respuestas HTTPS controladas, Room persistente y un reloj fijo. Cada región del fixture contiene dos observaciones, una elegible para la ficha del ingrediente y otra de ámbito online.

| Escenario | Observaciones vigentes en Room | Ofertas visibles para el CP consultado |
| --- | --- | --- |
| Primer sync de 08001 | 0 → 2 | 0 → 1 |
| Mismo snapshot | 2 → 2, revisión 1 | 1 → 1 |
| Revisión nueva | 2 → 2, revisión 1 → 2 | 1 → 1 |
| Hash erróneo | 2 → 2, revisión 2 | 1 → 1, desde Room |
| Timeout | 2 → 2, revisión 2 | 1 → 1, desde Room |
| Respuesta vacía | 2 → 2, revisión 2 | 1 → 1, desde Room |
| HTTP 404 | 2 → 2, revisión 2 | 1 → 1, desde Room |
| Cambio a 15001 | 2 → 4 en dos regiones | 1 → 1, tienda de la nueva región |
| Sin conexión | 4 → 4 | 1 → 1 para 08001. 0 para 41001. |
| Reinicio y reapertura de Room | 4 → 4 | 1 → 1 para cada CP con cobertura |
| Caducidad en 08001 | 2 → 0 observaciones vigentes de esa región | 1 → 0 |
| HTTP 410 de la revisión 2 | 4 → 2 tras retirar 08001 en la prueba con reloj fijo | 1 → 0 para 08001 |

La prueba del publicador con un lote vacío conserva una observación y la misma revisión. La prueba JVM de la ficha comprueba que el resultado de Room aparece con la petición pendiente y se retira al cambiar de CP.

Pasaron 220 pruebas JVM, 18 pruebas Android 16 y 36 pruebas del publicador. La instrumentación incluye la migración real desde v10 y la preservación de los registros anteriores de caché en v11 → v12. El validador de contrato verificó OpenAPI, el snapshot, el manifiesto y las restricciones de PostgreSQL. Las pruebas optativas que requieren un publicador externo quedaron omitidas en esta ejecución.
