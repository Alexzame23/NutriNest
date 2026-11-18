# Fase 12 — Adaptadores de fuentes de precios

[Decisión de arquitectura](../architecture/adr/0002-price-source-architecture.md) · [Viabilidad y permisos](10-source-feasibility.md) · [Contrato comercial](11-commercial-product-domain.md)

## Entorno y fuentes

El repositorio contiene la app Android, la caché Room, el contrato OpenAPI y el DDL comercial de fase 11. La revisión del repositorio no encontró un servicio desplegable de precios ni una configuración aprobada de alojamiento. Gradle confirmó OkHttp 4.12.0 como dependencia transitiva de Coil 2.7.0 para imágenes. El transporte de esta fase usa `fetch` nativo de Node.js 24 en un módulo separado de Android. La ejecución local y CI prescinden de dependencias npm para el módulo.

| Adaptador | Uso | Datos |
| --- | --- | --- |
| [`FakePriceProvider`](../../backend/prices/fake-price-provider.mjs) | Pruebas y desarrollo local | Fixture marcado como simulado en origen y metadatos. Dos formatos y dos precios. |
| [`OpenPricesProvider`](../../backend/prices/open-prices-provider.mjs) | Única fuente real del piloto interno, habilitada mediante un plan explícito | Exportaciones oficiales diarias `locations.jsonl.gz` y `prices.jsonl.gz`, lectura pública con ODbL 1.0. El plan aporta la selección revisada de tiendas y formatos. |

Open Food Facts figura en la fase 10 como complemento de metadatos. El piloto actual usa metadatos de envase revisados en el plan y un único origen externo. Los CSV de establecimientos y los servicios comerciales dependen de un permiso y un contrato específicos. [Guía oficial de exportación y licencia](https://openfoodfacts.github.io/open-prices/guides/data/) · [Dataset ODbL](https://huggingface.co/datasets/openfoodfacts/open-prices)

## Contrato de lectura

[`PriceProvider`](../../backend/prices/price-provider.mjs) representa un lote cargado y validado. Sus consultas se resuelven en memoria.

| Operación | Resultado |
| --- | --- |
| `getMetadata()` | Fuente, licencia, atribución, capacidades, restricción de uso, tiendas y cadenas. Open Prices añade ventana de cobertura, hora de recuperación, URL, SHA-256, longitud y `Last-Modified` de cada archivo. |
| `listCatalog({limit, offset})` | Pares de producto y envase en el orden del catálogo del lote. En Open Prices el catálogo es la selección editorial. |
| `getProduct(externalId)` | Par de producto y envase por clave externa del catálogo dentro de la fuente. |
| `listPrices(storeId, {limit, offset})` | Observaciones de un establecimiento. Un establecimiento conocido con cobertura vacía devuelve una página vacía. |

Cada página devuelve `items` y `nextOffset`. El valor por defecto es 25, el máximo 100. Se continúa con el mismo lote y consulta hasta obtener `null`. Los registros usan el vocabulario comercial de fase 11, con importes como cadenas decimales, claves internas con prefijo de fuente y `origin.sourceId` / `origin.recordKey`. El futuro persistidor asignará `record.revision` y resolverá el mapeo editorial de ingredientes. Un alcance de tienda requiere referencias coherentes a tienda, cadena y envase.

## Piloto Open Prices

El plan de backend selecciona 1–3 ubicaciones físicas de España y 1–20 GTIN con envase fijo validado, en una ventana inclusiva de hasta 90 días. Cada tienda debe incluir `locationId`, `osmId`, `osmType`, `name`, `retailerKey` y `retailerName`. Se comprueba país, clase de ubicación y referencia OSM en la exportación. Cada producto aporta `externalId`, `gtin`, `name`, `netContent`, `unitsPerPackage` y, opcionalmente, `brand`. La clave de producto viene del plan. Cambiar un formato vendible exige una nueva clave. El [fixture de contrato](../../backend/prices/fixtures/open-prices-export.simulated.json) contiene un ejemplo sintético de la estructura.

Se conservan observaciones ordinarias en EUR de esos GTIN, con fecha de fuente y precio por envase. Se excluyen duplicados de Open Prices, ofertas promocionales, precios por peso y ubicaciones fuera del plan. En cada combinación tienda–envase se toma la fecha más reciente y, en caso de empate, el ID de origen mayor. El GTIN se compara por dígito de control y representación de 14 dígitos, conservando el original del plan. Los DTO de origen quedan dentro del adaptador. La salida omite autores, comentarios, tickets, fotos y disponibilidad.

El proceso acepta como máximo dos GET de los archivos oficiales por carga, en secuencia, con plazo de 60 segundos por descarga, 64 MiB comprimidos por archivo, 512 MiB descomprimidos por archivo y 64 Ki caracteres por línea. El proceso rechaza lotes con más de 10.000 observaciones candidatas. Una carga fallida no devuelve proveedor parcial. No hay reintentos internos ni circuit breaker. El trabajo diario y exclusivo que ejecute la fase 13 debe espaciar fallos, aplicar `Retry-After` y conservar la última publicación utilizable según vigencia y derechos. No hay componente HTTP de usuario que pueda iniciar descargas.

El transporte convierte 401, 403 y 404 en estados definitivos para ese intento. 429, 503, otros 5xx, timeout y desconexión admiten reprogramación. En 429 y 503, `retryAt` conserva el plazo válido de `Retry-After`. `MALFORMED_RESPONSE`, `LIMIT_EXCEEDED` e `INVALID_CONFIGURATION` requieren revisión. Los errores no contienen el cuerpo del proveedor. Open Prices usa acceso anónimo, URL HTTPS fija y redirecciones desactivadas. `NUTRINEST_PRICE_USER_AGENT` identifica el proceso desde el entorno de backend.

## Ejecución y verificación

```powershell
node --test backend/prices/provider.test.mjs
node backend/prices/run-pilot.mjs fake
$env:NUTRINEST_PRICE_USER_AGENT = 'NutriNest-Pilot/1.0 (contacto operativo)'
node backend/prices/run-pilot.mjs open-prices ruta-al-plan-revisado.json
```

El último comando se reserva a un entorno de ingesta con el plan revisado. Imprime metadatos y conteos de cobertura. La suite sustituye las respuestas de ambos archivos por fixtures comprimidos y verifica las URL, la traducción, la paginación, la privacidad de la salida y los fallos de contrato, transporte y límites. La verificación comercial de fase 11 comprueba también que el lote simulado con sus revisiones asignadas se ajusta a OpenAPI.

El 18 de noviembre de 2026 se verificaron HEAD y GET con HTTP 200 en ambas URL oficiales. El adaptador recuperó la observación histórica `344675` de la tienda `7581` (Veritas, Barcelona), con fecha `2026-10-12` e importe `2.490 EUR`. El plan de esta prueba utilizó un envase sintético para comprobar el transporte y la normalización. El resultado queda fuera de la cobertura comercial y la publicación. [Exportador oficial](https://github.com/openfoodfacts/open-prices/blob/main/open_prices/common/export_data.py) · [Serializador de precios](https://github.com/openfoodfacts/open-prices/blob/main/open_prices/api/prices/serializers.py) · [Serializador de ubicaciones](https://github.com/openfoodfacts/open-prices/blob/main/open_prices/api/locations/serializers.py)

| Archivo | Bytes de la descarga | SHA-256 |
| --- | ---: | --- |
| `locations.jsonl.gz` | 1.259.384 | `b22e58b87288fc5c80712e781683fbf0ab11007b41c0c107a44309d03c3b05b9` |
| `prices.jsonl.gz` | 22.980.050 | `f2e8079dedd281e2bd230dd5d9d4f36b367002573eeeb6c75249681c73ea25f2` |

## Incorporar otra fuente

1. Registrar responsable, permiso y alcance de los endpoints, campos, cobertura, autenticación, frecuencia, cuotas, redistribución, atribución, retención y retirada. Obtener una muestra y verificar el esquema y los errores en un entorno expresamente permitido.
2. Crear un adaptador independiente que implemente el contrato de lectura. Traducir y validar los DTO dentro de ese adaptador. Conservar las claves externas con su ámbito, los decimales exactos, la fecha de observación y el linaje del lote. Declarar solo las capacidades disponibles.
3. Configurar URL permitidas, tamaños y plazos por fuente. Inyectar credenciales desde el gestor de secretos del backend, limitar permisos y evitar que el transporte reenvíe autenticación a destinos redirigidos. Instalar la fuente en el proceso de ingesta tras validar sus fixtures y fallos. Aplicar límites y circuit breaker si su volumen o latencia lo justifican.
4. Verificar la licencia y el umbral de cobertura antes de activar publicación. Android sigue leyendo el contrato regional normalizado. Probar la retirada del origen y el tratamiento de publicaciones y cachés afectados.

## Costes y fase 13

Open Prices tiene coste de licencia de 0 €. El trabajo de esta fase generó 0 € de nuevas contrataciones. Con una carga diaria, se prevén unos 60 GET de archivos por cada 30 días antes de reprogramaciones, con cero consultas de origen impulsadas por el uso de la app. Los 24,24 MB de la descarga observada equivaldrían a unos 0,73 GB de entrada cada 30 días a tamaño constante. La fase 10 estimó 8–25 €/mes de infraestructura, sin contar operación ni datos comerciales. [Supuestos de costes](10-mvp-and-costs.md)

La fase 13 debe elegir el alojamiento y revisar el plan de tiendas, envases y correspondencias editoriales. También debe programar ingestas exclusivas, persistir revisiones idempotentes, validar cobertura y publicar de forma atómica el manifiesto y los snapshots regionales con ETag, caducidad, atribución y revocación. El lanzamiento público requiere la revisión ODbL y el umbral de 12 de 20 productos en una tienda con observación reciente. La comparación de la selección piloto requiere al menos 10 de 20 GTIN recientes en común. [Puertas del piloto](10-mvp-and-costs.md) · [Contrato de publicación](11-price-snapshots.openapi.yaml)
