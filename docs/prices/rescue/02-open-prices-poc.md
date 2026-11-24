# R2 · Prueba de concepto con Open Prices

R1 reprodujo `NO_BASE_URL` antes de emitir solicitudes HTTP. El proveedor actual espera un manifiesto y un snapshot regional de NutriNest con cabecera `live` y SHA-256. R2 consulta el API público mediante `OpenPriceObservationSource`, una interfaz experimental exclusiva de la variante `debug`. La prueba JVM instancia `OpenPricesSource` directamente, sin pasar por el proveedor regional, Room ni las pantallas. [Diagnóstico R1](01-runtime-diagnosis.md)

## Contrato oficial consultado el 23 de noviembre de 2026

| Lectura HTTPS | Parámetros usados |
| --- | --- |
| `GET /api/v1/locations/nearby` | `lat`, `lon`, `radius_km` positivo, `size=10`. Devuelve ubicaciones por distancia, de la más cercana a la más lejana. |
| `GET /api/v1/prices` | `location_id__in` con los IDs de la primera página de proximidad, o `location_id` con un ID configurado. `currency=EUR`, `order_by=-date,-id` y `size=10`. |

El host de datos reales es `https://prices.openfoodfacts.org`. Ambas lecturas son anónimas. Una colección contiene `items`, `page`, `pages`, `size` y `total`. La paginación publicada usa 10 filas por defecto, hasta 100 por página y un máximo de página 500. La documentación consultada de Open Prices carece de un cupo vigente publicado por minuto. Los límites publicados para búsquedas y productos de Product Opener se refieren a ese otro API. La PoC usa una sola página por endpoint, hasta dos GET secuenciales por ejecución, 512 KiB por respuesta y 5 s de espera de conexión y de lectura. Un error HTTP conserva el estado y `Retry-After` si existe, sin reintentos automáticos. [API de Open Prices](https://openfoodfacts.github.io/open-prices/guides/API/) · [Esquema de producción](https://prices.openfoodfacts.org/api/schema?format=json) · [Paginación oficial](https://github.com/openfoodfacts/open-prices/blob/main/open_prices/api/pagination.py) · [Política de Product Opener](https://openfoodfacts.github.io/openfoodfacts-server/api/#rate-limits)

Cada llamada incluye `User-Agent: NutriNest/0.1 (https://github.com/Alexzame23/NutriNest)`, con nombre, versión y enlace de contacto del proyecto. Open Food Facts pide identificar al cliente con un User-Agent propio. El esquema de Open Prices permite estas lecturas sin token. [Identificación de clientes](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v3/products/get-api-v3-product-code/) · [API de Open Prices](https://openfoodfacts.github.io/open-prices/guides/API/)

Una `location` es el lugar de la observación. Puede ser un comercio físico vinculado a OpenStreetMap o un sitio online. Un elemento OSM puede corresponder a otra clase de lugar y los datos geográficos pueden desactualizarse. Una `proof` es el registro de evidencia asociado, por ejemplo una etiqueta, un ticket de compra, una importación de datos personales o un fichero de comercio. Se conservan únicamente su ID y su tipo conocido. El adaptador trabaja en memoria y omite imágenes, rutas de archivos, usuarios, comentarios y texto libre de `source`. El identificador del precio y el `PriceSource` permiten rastrear la observación hasta Open Prices. [Modelo](https://openfoodfacts.github.io/open-prices/topics/core/) · [Ubicaciones OSM](https://openfoodfacts.github.io/open-prices/topics/openstreetmap-location-data/) · [Esquema de producción](https://prices.openfoodfacts.org/api/schema?format=json)

## Conversión

| Origen | Representación en la PoC |
| --- | --- |
| `product_code` y metadatos de `product` | `CommercialProduct` con ID estable dentro de la fuente. Si falta el código se admite el ID de producto. `Gtin` se mantiene opcional y exige longitud y dígito de control válidos. |
| `location_id`, `location` y ubicaciones de proximidad | ID externo, ciudad, país y referencia OSM cuando existen. Se crea `Store` y ámbito `AtStore` para un OSM con referencia válida, etiqueta `shop` y marca explícita. La identidad experimental del comercio queda acotada a esa ubicación. |
| `id`, `date`, `price`, `currency` y descuento | Cada fila con ID se conserva como una observación independiente del producto. Una fecha ausente o inválida y un importe ausente quedan como `null`. El cero explícito se conserva. Con producto, tienda, fecha, EUR, importe representable y descuento conocido se obtiene un candidato `PriceObservation`, con `LocalDate`, `Money`, sin hora inventada y confianza baja. |
| `type`, `category_tag` y `price_per` | Open Prices reserva `price_per` para `CATEGORY` y publica `UNIT`, `KILOGRAM` o `LITER`. Para `PRODUCT` con `price_per` vacío se usa `SaleUnit.PACKAGE`. Una tarifa por categoría se conserva con su unidad original y carece de SKU proyectado. |
| Procedencia | `PriceSource` de clase `OPEN_DATA`, `ProviderRecord` con `prices/{id}` y referencia opcional a la prueba. |

Los objetos anidados, sus IDs, el nombre y marca del producto, el código, el precio, la fecha, la moneda y los atributos OSM pueden faltar en los datos de lectura. El resultado experimental conserva filas incompletas sin convertirlas en ofertas. Un candidato usa una referencia provisional a un envase con revisión local 1. El dominio exige contenido neto y unidades para construir un `ProductPackage` fijo. Esos datos y las equivalencias BEDCA quedan pendientes de validación. Una ubicación física de Open Prices tampoco establece una `PriceZone` de un retailer. [Esquema de producción](https://prices.openfoodfacts.org/api/schema?format=json) · [Validación de precios](https://github.com/openfoodfacts/open-prices/blob/main/open_prices/prices/validators.py) · [Calidad de datos](https://openfoodfacts.github.io/open-prices/topics/data-quality/)

## Reproducción

Pruebas normales sin red. La fixture reducida conserva la forma de una fila de Madrid y su fecha e importe. Usa IDs, código y nombres sustituidos, sin imágenes ni datos de colaboradores. Cubre la lectura y conversión, respuesta vacía, distinción entre producto y observación, código sin GTIN, ausencia de ubicación o importe, cero explícito, fecha inválida, tarifa por categoría, moneda ajena al dominio, timeout, 429, 5xx y envoltorio inválido.

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

El test de red opt-in usa Puerta del Sol, Madrid, con radio de 5 km. Para otro caso público se puede configurar `NUTRINEST_OPEN_PRICES_LOCATION_ID` o el par `NUTRINEST_OPEN_PRICES_LAT` y `NUTRINEST_OPEN_PRICES_LON` con `NUTRINEST_OPEN_PRICES_RADIUS_KM` opcional. La prueba requiere al menos un precio convertido con tienda, GTIN y referencia de prueba.

```powershell
$env:NUTRINEST_OPEN_PRICES_LIVE = '1'
try {
    .\gradlew.bat :app:testDebugUnitTest --rerun --tests '*OpenPricesLiveTest'
} finally {
    Remove-Item Env:NUTRINEST_OPEN_PRICES_LIVE
}
```

## Resultado de la prueba real

El 23 de noviembre de 2026 a las 19:49 CET, `OpenPricesLiveTest.mapsAPublicObservationOverHttps` instanció el adaptador con su transporte HTTPS predeterminado y consultó el host público. Una comprobación previa desde PowerShell, con el mismo radio y filtros, también obtuvo HTTP 200 en ambas lecturas y los mismos totales.

| Endpoint | HTTP | Recibidos | Total declarado por el endpoint |
| --- | --- | --- | --- |
| `/api/v1/locations/nearby` | 200 | 10 ubicaciones | 17 dentro del radio |
| `/api/v1/prices` | 200 | 10 observaciones | 13 en EUR para los 10 IDs de ubicación consultados |

Las 10 observaciones corresponden a 7 ubicaciones en Madrid, España. La fecha más antigua de la página fue el **27 de mayo de 2025** y la más reciente el **2 de noviembre de 2026**. Se obtuvieron **8 candidatos `PriceObservation`**. El resto se conservó como observaciones de origen. La suite con red ejecutó 208 pruebas con cero fallos, incluidas las 10 pruebas deterministas de esta PoC y la prueba HTTPS. La ejecución normal completó 207 pruebas con cero fallos y omitió únicamente la prueba de red.

Registro verificable de ejemplo. El adaptador obtuvo el GTIN `8435257073279`, **1,69 EUR por envase**, el **2 de noviembre de 2026**, en la ubicación pública Open Prices **7394**, Madrid. El registro de evidencia **142969** está tipificado como `PRICE_TAG`. La aserción de red requiere una observación convertida, su GTIN y una referencia de evidencia. [Registro 357963 en la API pública](https://prices.openfoodfacts.org/api/v1/prices/357963)

## Licencia y atribución

**Datos de Open Prices, por los colaboradores de Open Food Facts, bajo ODbL 1.0.** Atribuir la fuente al reutilizar sus datos y conservar los avisos de licencia. Para cualquier publicación de una base derivada, revisar las obligaciones de compartir bajo ODbL y la compatibilidad de otras fuentes. Los metadatos geográficos físicos provienen de OpenStreetMap, con atribución a sus colaboradores. La PoC no reutiliza imágenes ni archivos de evidencia. [Guía de datos y licencia](https://openfoodfacts.github.io/open-prices/guides/data/#license) · [Procedencia geográfica](https://openfoodfacts.github.io/open-prices/topics/openstreetmap-location-data/)

## Decisión

**GO para evaluar una integración piloto opcional. NO-GO para activar Open Prices como proveedor predeterminado.** La muestra permite comprobar origen, producto, fecha, ubicación e importe. Para publicar comparaciones siguen pendientes el catálogo de envases, la correspondencia con el directorio y BEDCA, la frescura por producto y tienda y la revisión de la reutilización bajo ODbL. El muestreo de una página en un radio urbano solo mide el caso consultado.
