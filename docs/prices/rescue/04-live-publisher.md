# R4 · Publicación regional de precios reales

El 24 de noviembre de 2026, un APK debug descargó por HTTPS una observación de Open Prices en el Carrefour Market de Tirso de Molina, Madrid. El cliente de producción comprobó el manifiesto, el SHA-256, la longitud y las referencias del snapshot. Room partió vacío. Tras instalar la revisión, el test cerró la base en disco, la volvió a abrir y recuperó la misma observación.

## Resultado medido

| Campo | Resultado de la prueba integrada |
| --- | --- |
| Región, revisión, cobertura | `es-madrid-28012`, revisión 1. Una observación, una tienda y un envase del plan. |
| Observación | Open Prices `357963`, prueba `142969`, GTIN `8435257073279`, 1,69 EUR por envase de 900 g. |
| Tienda | Ubicación pública `7394`, referencia `node/6918727592`, CP 28012. Android la enlazó con su directorio. |
| Fechas | `observedOn=2026-11-02`, `observedAt=null`. La fuente solo identifica el día. `fetchedAt=2026-11-24T05:33:06.946Z`. |
| Ingesta | Una petición a la fuente, un intento correcto. Respuesta de 12.843 bytes. |
| Publicación | 1.885 bytes UTF-8. Generada el 24 de noviembre a las 05:33:06.946 UTC, válida hasta el 25 a las 17:33:06.946 UTC. |
| SHA-256 de la respuesta de Open Prices | `2cb09c8a44d18c83739a8ee0554e2a6927f46170ce9cd5616c56b598e4078f62` |
| SHA-256 del manifiesto (ETag) | `c54b1dd0eec25e5dad1e5e16c637fdd84372a64dcfe1c3fd9d73968fb2bd7a90` |
| SHA-256 del snapshot descargado y del BLOB en Room | `18412f437f92a54e84b49ea210e412b0480888ab0cf23e5c0a34a30490caffb9` |

El test compara el digest de la fila Room con el de la ejecución en curso. Una ingesta posterior tiene sus propias fechas y hashes. [Registro público del precio](https://prices.openfoodfacts.org/api/v1/prices/357963) · [Producto de Open Food Facts](https://world.openfoodfacts.org/product/8435257073279)

## Arquitectura y contrato

Se amplió el único worker Node y SQLite encontrado en `backend/prices`. La búsqueda incluyó las referencias Git locales, el proyecto vecino `nutrinest-bedca` y los repositorios NutriNest visibles del propietario en GitHub. El bucle de sincronización reserva el presupuesto de red en SQLite y una transacción publica las evidencias y los bytes inmutables de cada revisión. Un fallo de red, de identidad o de validación conserva la última publicación válida hasta su vencimiento. Se necesita conservar la base de datos entre reinicios para mantener la secuencia de revisiones. [Sincronización y retención](../13-scheduled-price-synchronization.md)

El modo `location-api` adapta al worker la consulta por ubicación de R2. Cada ciclo lee la primera página de hasta diez filas, ordenadas por fecha e ID, de una tienda revisada. Cruza país, CP, referencia OSM, marca del comercio, GTIN, nombre, marca y contenido neto del producto. Solo proyecta precios regulares EUR por envase, dentro de la ventana, y guarda la clave de la observación, prueba cuando existe, fecha, URL y digest de entrada. El plan declara una botella por unidad de venta. El nombre y los 900 g proceden de los datos abiertos del producto. La licencia de la base derivada es ODbL 1.0. Se atribuyen Open Prices, Open Food Facts y OpenStreetMap en la publicación. [Datos y licencia](https://openfoodfacts.github.io/open-prices/guides/data/) · [Ficha de la unidad de venta](https://supermediterraneo.es/supermercado/familia/postresnestle/9841-NESTLEYOGURBEBERFRESA-PLATANO900ML-8435257073279.html)

El exportador histórico continúa disponible para pilotos internos. La cabecera `X-NutriNest-Price-Data` se obtiene de la revisión servida en ambos GET, también en 304. La consulta HTTPS al host fijo con observaciones verificadas produce `live`. El proveedor de fixtures y el transporte inyectado de pruebas producen `simulated`. Las revisiones antiguas sin acreditación, los exports internos y los lotes reales sin observaciones vigentes reciben `unverified`. Superfudis se excluye de la sincronización compartida por las restricciones de redistribución recogidas en R3. [Guía de Superfudis](https://superfudis.com/developers/)

| Ruta GET | Contenido |
| --- | --- |
| `/v1/price-regions/{regionId}/manifest` | Revisión activa, ventana de validez, longitud y SHA-256 del snapshot. 404 fuera de cobertura o sin publicación vigente. |
| `/v1/price-regions/{regionId}/snapshots/{revision}` | JSON v1 con registros trazables, `dataKind`, fechas y `coverage`. La cobertura cuenta observaciones, tiendas y envases observados, y el tamaño del plan. Una revisión retirada o caducada devuelve 410. |
| `/healthz`, `/metrics` | Estado de la ingesta, cuota consumida, antigüedad y fallos. |
| `/internal/price-regions/{regionId}/quotes` | Estado por pareja tienda/envase y procedencia de la última evidencia retenida. |

La lectura regional del APK usa `HttpsRetailPriceProvider` y exige `live` en ambas respuestas. Después de validar los bytes, instala la revisión vigente en `RoomPriceSnapshotCache`. Una respuesta 410 retira esa revisión del caché. Los campos adicionales son opcionales en v1. Android conserva la procedencia completa en el BLOB y su lector sigue usando los modelos existentes. Todas las lecturas HTTP del teléfono se dirigen al publicador configurado. [Contrato OpenAPI](../11-price-snapshots.openapi.yaml)

## Despliegue local y reproducción

Desde la raíz, con PowerShell 7, Node 24.11+, OpenSSL, JDK 21, SDK Android del proyecto y un emulador iniciado. Usar un directorio privado bajo `build` para SQLite y la clave TLS. El backend resuelve las rutas relativas al JSON de configuración. El certificado de siete días solo se incorpora como ancla para `localhost` en la variante debug solicitada. El APK solo recibe el certificado público.

```powershell
$pilot = 'build/live-publisher'
New-Item -ItemType Directory -Force "$pilot/res/raw" | Out-Null
Copy-Item backend/prices/madrid-pilot.json "$pilot/config.json"
# Preparación inicial. Reutilizar el par mientras siga vigente.
openssl req -x509 -newkey rsa:2048 -nodes -days 7 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost' -addext 'basicConstraints=critical,CA:TRUE' -keyout "$pilot/localhost.key" -out "$pilot/localhost.pem"
Copy-Item "$pilot/localhost.pem" "$pilot/res/raw/publisher_ca.pem"
$env:NUTRINEST_PRICE_USER_AGENT = 'NutriNest/0.1 (https://github.com/Alexzame23/NutriNest)'
$env:ANDROID_SERIAL = 'emulator-5580' # Elegir el dispositivo de adb devices
$base = 'https://localhost:8787'
$res = (Resolve-Path "$pilot/res").Path
.\gradlew.bat "-PnutrinestRetailPriceBaseUrl=$base" "-PnutrinestLocalPublisherRes=$res" :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

La prueba integrada abre la base persistente, sincroniza Open Prices, inicia el servidor HTTPS en un puerto efímero y reserva con ADB el puerto 8787 del emulador durante la prueba. Ejecutarla antes de crear una redirección manual. La primera ejecución con base vacía debe informar `SUCCEEDED`. Las repeticiones dentro de la cadencia reutilizan la misma evidencia con `NOT_DUE`. El test exige una observación vigente, un comercio enlazado y la lectura del BLOB desde una instancia real de Room inicialmente vacía. El runner imprime el manifiesto, los hashes de entrada y la cobertura. Si ADB no está en PATH, indicar la ruta del ejecutable en `ADB`.

```powershell
$env:NUTRINEST_LIVE_PUBLISHER_CONFIG = "$pilot/config.json"
node --test backend/prices/live-publisher.test.mjs
Remove-Item Env:NUTRINEST_LIVE_PUBLISHER_CONFIG
```

Para usar el mismo APK con el servicio continuo, ejecutar lo siguiente. El worker escucha en loopback y consulta Open Prices como máximo una vez por ciclo correcto. El piloto usa la cadencia de 24 horas, 30 días de frescura, TTL de 36 horas y el presupuesto persistente de seis peticiones en 24 horas. El límite operativo elegido para este plan es el 31 de diciembre de 2026. Un despliegue posterior requiere revisar los datos y actualizar el plan. Para otro host HTTPS de confianza pública, compilar con su URL y omitir la propiedad del certificado local.

```powershell
adb reverse tcp:8787 tcp:8787
node backend/prices/run-sync.mjs "$pilot/config.json"
```

El proceso también admite `--once` para una ingesta sin servidor. Ctrl+C detiene el servicio continuo. La prueba local de contrato y el servicio deben usar la misma base si comparten el plan y el presupuesto de origen.

## Pruebas y límites

- Node ejecutó 31 pruebas deterministas sin fallos. La prueba integrada de red se ejecutó aparte con los APK compilados desde el contenido aislado del commit. Pasó con SQLite y Room inicialmente vacíos. El verificador OpenAPI validó el contrato y los casos de regresión.
- Pasaron las nueve pruebas instrumentadas existentes de transporte y caché, incluida la comprobación de cabeceras, hashes inválidos, rollback de revisión y migración. La suite JVM debug del árbol de trabajo terminó con 214 pruebas pasadas y dos pruebas de red opcionales omitidas.
- La página acotada y el catálogo de un solo GTIN limitan la cobertura. Nuevas filas pueden desplazar el producto de la página. La observación del 2 de noviembre sale de la ventana de 30 días el 2 de diciembre a las 00:00 UTC. Si faltan filas vigentes, la prueba real falla y la cobertura publicada lo refleja.
- La prueba acredita descarga y caché. Los campos `matches` y `availability` están vacíos. Comparar ingredientes o afirmar existencias requiere evidencia adicional.
