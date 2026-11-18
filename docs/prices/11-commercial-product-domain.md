# Fase 11 — Dominio comercial y contrato de publicaciones

Fase revisada el 18 de noviembre de 2026 sobre `c2759ae`. La fase 10 seleccionó una fuente abierta para el piloto. El ADR-0002 estableció la importación en backend con lectura local en Android. [Fase 10](10-source-feasibility.md) · [ADR-0002](../architecture/adr/0002-price-source-architecture.md)

## Separación del catálogo nutricional

Las recetas y la compra mantienen `IngredientEntity.id` y los gramos comestibles. Los [contratos canónicos](../architecture/03-canonical-catalog.md) preservan composición, ámbito e identidad local. `IngredientProductMatch` apunta a una clave editorial global versionada que la futura app resolverá localmente. El GTIN pertenece al envase vendible. El catálogo comercial puede describir gramos netos, mililitros o unidades, y el paso a gramos comestibles sigue requiriendo las [conversiones y evidencias existentes](../architecture/04-validated-food-quantity-conversions.md).

Los [modelos Kotlin](../../app/src/main/java/com/zame/nutrinest/domain/prices/CommercialCatalog.kt) son independientes de Room y del JSON. El [decodificador](../../app/src/main/java/com/zame/nutrinest/data/prices/SnapshotJson.kt) traduce el contrato público a dominio y valida sus campos. Los DTOs de cada futuro proveedor pertenecerán a sus adaptadores de backend.

| Modelo | Contrato |
| --- | --- |
| `Retailer`, `Store`, `PriceZone` | Identidades independientes. Tienda y zona tienen cadena propietaria. Una zona comercial de una cadena y la región de descarga son conceptos distintos. El código postal de una tienda puede faltar y se conserva como texto. |
| `CommercialProduct`, `ProductPackage` | Producto con marca, variante y atributos de texto. El envase fija la unidad de venta y admite GTIN opcional, con dígito de control y normalización a 14 caracteres para búsqueda. El índice admite varios registros con el mismo GTIN. Correlación comercial exige comprobar formato y códigos de alcance restringido. |
| Formato fijo o variable | `FIXED` se cobra por `PACKAGE`, con contenido neto total declarado y número de unidades interiores positivo. `VARIABLE` tiene precio por `KG` y carece de contenido fijo o recuento declarado. Versionar la identidad del envase si cambia el formato vendible. El contrato v1 limita qué formatos se publican y deja fuera unidades de venta mixtas. |
| `IngredientProductMatch` | Mapeo editorial de clave global a formato vendible, con versión, procedencia, confianza y tipo `EXACT` o `ALTERNATIVE`. PostgreSQL exige una clave registrada en `editorial_ingredient_key`. La resolución de la clave a una fila local queda para la fase 12. |
| `PriceObservation`, `AvailabilityObservation` | Ámbitos `STORE`, `ZONE`, `ONLINE` con zona opcional, o `UNKNOWN`. La tienda o zona debe pertenecer a la cadena informada. Fecha de observación obligatoria en el día civil de la fuente, más instante si se conoce. Precio en EUR decimal, hasta cuatro decimales sin redondear. La base del precio coincide con la unidad de venta. Promoción y elegibilidad explícitas. El stock tiene su propio registro, revisión y procedencia. La disponibilidad es desconocida en ausencia de registro. |
| `PriceSource`, `PriceSyncStatus` | Identidad de origen, tipo, licencia y atribución. El estado de sincronización pertenece al backend, por fuente y región, con fechas del último intento y éxito. Los estados operativos quedan fuera del JSON público. |

## Persistencia y versionado

- [Esquema PostgreSQL](11-commercial-schema.sql), compatible con PostgreSQL 15 o posterior. Las FK aseguran pertenencia de tiendas y zonas, envases y bases de precio. Los índices sirven la búsqueda por GTIN, el mapeo por clave editorial y el recorrido por envase, ámbito y fecha. Los importadores asignarán claves estables de registro por fuente y tipo. Si un SKU se repite por tienda o canal, el adaptador incluye el ámbito en su clave estable. Las revisiones positivas distinguen correcciones de un mismo registro; precio y stock tienen claves independientes. Para la producción, la cuenta de ingesta debe insertar nuevas revisiones y tratar un reintento con la misma clave/revisión y contenido como idempotente. Un contenido diferente para esa clave exige otra revisión. [Restricciones de PostgreSQL](https://www.postgresql.org/docs/15/ddl-constraints.html)
- El manifiesto activo y la publicación inmutable se consultan por región. La publicación incluye las referencias y licencias necesarias y una sola revisión publicada de cada registro por fuente y tipo. Los importes y cantidades se serializan como cadenas decimales. Los GTIN conservan ceros iniciales. SHA-256 y longitud cubren los bytes UTF-8 descomprimidos. El límite de 1 MiB deja margen para una fila de Room en `CursorWindow`. Un `304` conserva la caducidad. La comparación de fechas, el dígito de control, la unicidad y las referencias entre registros se validan fuera de JSON Schema. [Contrato REST](11-price-snapshots.openapi.yaml) · [Especificación OpenAPI](https://spec.openapis.org/oas/v3.1.1.html)
- El backend registra qué fuentes contiene cada publicación. El manifiesto consulta la revisión mayor y devuelve `404` si está retirada o caducada. La URL de una revisión retirada o caducada devuelve `410` antes de aplicar validadores de caché. Una corrección o una región vacía se publica con una revisión superior.
- La migración automática 10→11 añade `price_snapshot_cache`, con una fila por región y sin FK nutricionales. El payload se reemplaza en una transacción después de verificar versión, hash, longitud, campos y referencias. Repetir una revisión queda sin efecto. Las revisiones inferiores se rechazan y cambiar el contenido de la revisión instalada es un error. La caducidad bloquea su lectura. La purga y la retirada dejan la revisión y su hash para evitar que una sincronización posterior reinstale la misma revisión. La política existente de Android excluye las bases de las copias y transferencias. La eliminación es lógica. Si una licencia exige borrado físico, hay que resolver los requisitos de WAL y retención antes de habilitar el origen. [Caché Room](../../app/src/main/java/com/zame/nutrinest/data/prices/PriceSnapshotCache.kt)

## Verificación reproducible

El JSON de prueba vive en los assets del APK de instrumentación. La ejecución del DDL usa un PostgreSQL desechable en memoria. Las dependencias npm son externas al proyecto Android.

La verificación local pasó 138 pruebas JVM y 47 instrumentadas en un Pixel 5 con API 36. Con modo avión activo, Wi-Fi desactivado y sin red predeterminada, `MainActivity` completó un arranque en frío. Se verificó que el APK de la app excluye el fixture comercial. La migración conserva las 26 definiciones previas y las pruebas SQLite verifican 93 consultas DAO. Cuatro consultas con colecciones se comprueban en Room durante la compilación. PostgreSQL 18.6 en PGlite validó 22 casos de restricciones y dos planes con índice. OpenAPI pasó la validación del manifiesto, del JSON compartido y de siete casos inválidos. Esta ejecución de Android cubre API 36.

```powershell
python tools/verify_migration.py
python tools/check_room_schema.py
python tools/check_dao_sql.py
npm.cmd install --prefix build/phase11-verification --no-audit --ignore-scripts @electric-sql/pglite@0.5.16 @apidevtools/swagger-parser@13.1.0 ajv@8.20.0 ajv-formats@3.0.1
node tools/verify_commercial_contract.cjs build/phase11-verification
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline --no-configuration-cache
```

## Dependencias para la fase 12

Implementar el publicador regional, el registro de derechos de fuente y el adaptador autorizado. Confirmar cobertura antes de publicar e implementar la resolución local de claves editoriales y las conversiones compatibles con la cesta. Al añadir transporte HTTPS, aplicar `ETag`, límites antes y después de descomprimir, reintentos acotados y sincronización serializada por región. Conectar las retiradas comprobadas a la invalidación de la revisión instalada y programar la purga. Cada contrato debe cubrir retención, atribución y distribución de derivados. Esta fase deja el puerto local disponible en `AppContainer`, con la caché inicial vacía.
