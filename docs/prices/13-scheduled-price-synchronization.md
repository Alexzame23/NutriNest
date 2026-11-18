# Fase 13 — Sincronización programada de precios

[ADR-0002](../architecture/adr/0002-price-source-architecture.md) · [Contrato REST](11-price-snapshots.openapi.yaml) · [Adaptadores y permisos](12-price-provider-integration.md)

## Ejecución

El repositorio dispone de CI para compilar y probar y carece de alojamiento, servicio de precios, scheduler o PostgreSQL operativo configurados. El piloto usa un proceso Node.js 24.11+ con `node:sqlite` (experimental en 24.11), un volumen local persistente y un programador interno que comprueba vencimientos cada 60 segundos. El comando admite `--once` para diagnóstico y comparte los bloqueos y límites del servicio continuo. El estado comercial vive en un archivo separado de Room y BEDCA. La implementación requiere un único host con almacenamiento local y el mismo archivo de base para cada proceso. El futuro supervisor del host debe reiniciar el servicio tras una caída. [API SQLite de Node.js 24.11](https://nodejs.org/download/release/v24.11.0/docs/api/sqlite.html) · [Transacciones SQLite](https://sqlite.org/lang_transaction.html)

Ejemplo reproducible en PowerShell con la fuente simulada y un directorio ya existente. El fixture conserva sus fechas fijas, de modo que su estado dependerá de la fecha de ejecución.

```powershell
@'
{
  "source": "fake",
  "regionId": "pilot-test",
  "dbPath": "prices-pilot.sqlite",
  "port": 8787
}
'@ | Set-Content -Encoding utf8NoBOM build/prices-pilot.json
node backend/prices/run-sync.mjs build/prices-pilot.json --once
node backend/prices/run-sync.mjs build/prices-pilot.json
```

El servicio escucha en `127.0.0.1`. La demo usa el fixture de NutriNest. Open Prices se habilita con `source: "open-prices"`, `approvedPilot: true`, `rightsUntil` como fecha ISO con zona, `plan.stores`, `plan.products` y `NUTRINEST_PRICE_USER_AGENT`. Los elementos del plan siguen el formato revisado de fase 12. El calendario calcula la ventana en UTC cada ciclo. La configuración admite `enabled: false` para detener la ingesta y el servicio de datos de la fuente. Open Prices queda disponible para el piloto interno y requiere un plan comercial revisado para activarse. Este checkpoint carece de despliegue operativo. [Exportaciones oficiales](https://openfoodfacts.github.io/open-prices/guides/data/) · [Revisión de fuentes](10-source-feasibility.md)

## Calendario, cuotas y caché

| Política | Valor |
| --- | --- |
| Cadencia | 24 horas por defecto para ambas fuentes. Configurable entre 24 y 168 horas para Open Prices y entre 1 y 168 horas para el simulado. El primer ciclo elegible empieza al iniciar el servicio. |
| Exclusión y recuperación | SQLite mantiene un bloqueo interproceso en un archivo auxiliar durante cada intento. La reserva persistente precede a la red y evita repetir al reiniciar. Se recupera una ejecución interrumpida desde el próximo vencimiento y se omiten ciclos atrasados. |
| Reintentos | Hasta tres intentos por ciclo. Backoff de 5 y 10 minutos para fallos transitorios, subordinado a un `Retry-After` posterior. Si la espera alcanza el siguiente ciclo, se abre un ciclo nuevo al vencer el plazo. Los fallos permanentes esperan al ciclo siguiente. |
| Peticiones | Dos GET oficiales como máximo por intento. Presupuesto persistente de 2–6 peticiones en cualquier ventana de 24 h, 6 por defecto. Cada reserva se contabiliza antes de la llamada, incluidos HTTP 304 y conexiones interrumpidas. |
| Detección incremental | ETag por archivo, con `If-Modified-Since` si falta ETag. Un 304 o un hash igual evita analizar de nuevo el archivo cuando la proyección sigue siendo válida. Un cambio de plan invalida la proyección. El caché conserva solo filas normalizadas del piloto. |
| Vigente | Observación dentro de `freshDays`, 30 días por defecto, con cobertura confirmada por el último lote válido. `observedAt` conserva el instante si el origen lo conoce. Para una fecha civil sin hora se aplica el inicio UTC de `observedOn`, sin inventar un instante de observación. |
| Antiguo | La última copia válida se mantiene como `STALE` hasta `retainDays`, 90 días por defecto, si supera el umbral vigente o desaparece de un lote válido. Un fallo de la fuente conserva la copia anterior y la edad sigue avanzando. |
| No verificado | Sin observación utilizable o una vez agotado el plazo de retención. El endpoint de diagnóstico devuelve `UNVERIFIED` con `quote: null`. |
| Histórico | Valor inicial y cambios reales en importe o condiciones por tienda y envase. Una repetición conserva la revisión y el `fetchedAt` original. Una observación nueva con el mismo precio actualiza su evidencia y fecha sin crear un cambio de precio. `historyDays` tiene un máximo y valor por defecto de 365 días. La limpieza lógica se ejecuta diariamente. |

Cada evidencia registra el ámbito de tienda, región, fuente, clave externa, fecha de observación y momento de recuperación, además de los hashes del lote. El identificador y digest de revisión se conservan para mantener revisiones estables. Los lotes válidos actualizan evidencias, historial, caché y publicación en una única transacción. La copia válida permanece disponible durante fallos, sujeta a caducidad y autorización.

## Lecturas y operación

- `GET /healthz` informa `ok`, `degraded` o `disabled`. El proceso responde 503 si falla el almacenamiento. Una fuente caída permite consultar la caché elegible.
- `GET /metrics` devuelve JSON con intentos, éxitos, fallos, reservas, HTTP 304, cambios, estados de precios, último resultado y próximo intento. No contiene identificadores de usuarios ni datos de cestas.
- `GET /internal/price-regions/{regionId}/quotes` muestra las tres categorías de frescura para el plan interno.
- Los GET del [contrato v1](11-price-snapshots.openapi.yaml) sirven el manifiesto y los bytes exactos del snapshot con ETag y SHA-256. El snapshot incluye solo precios vigentes y su validez acaba en el primero de estos plazos, 36 h desde la última ingesta válida (`snapshotHours`), caducidad de cualquiera de sus precios o fecha autorizada de uso. Si vence un precio antes de la próxima ingesta, el worker puede reconstruir la publicación con las observaciones que sigan vigentes, dentro del plazo del lote. Una publicación vencida o retirada no puede generar HTTP 304. Un nuevo contenido retira las revisiones anteriores. La app puede seguir usando su copia hasta el límite incluido en ella.

## Coste y fase 14

La suite local y CI usa `node --test backend/prices/provider.test.mjs backend/prices/price-sync.test.mjs`. El [verificador comercial](11-commercial-product-domain.md#verificación-reproducible) valida también los bytes publicados por el worker contra OpenAPI. La prueba HTTP local comprobó salud, métricas y manifiesto 200/304 con una sola ejecución persistida y cero llamadas de origen.

El piloto simulado tiene coste externo de 0 €. Open Prices tiene licencia abierta y 0 € de cuota de datos. Una ejecución al día supone hasta 60 GET en 30 días y aproximadamente 0,73 GB entrantes al tamaño de los archivos medidos en fase 12, antes de aplicar 304. El máximo local por defecto es 180 GET por 30 días y 384 MiB entrantes por 24 h con el límite de 64 MiB por descarga. La estimación previa de infraestructura es 8–25 €/mes. [Supuestos de costes](10-mvp-and-costs.md) · [Medición de los archivos](12-price-provider-integration.md)

Fase 14 requiere un host y supervisor aprobados, backups consistentes de SQLite, control de acceso y HTTPS antes de conectar clientes. También requiere un plan de tiendas, formatos y claves editoriales revisado, comprobar una semana de ingesta y verificar cobertura, obligaciones ODbL y retirada. La comparación comercial y la presentación Android podrán usar el contrato v1 y la fecha de observación para distinguir vigencia, antigüedad y ausencia. Una topología multihost necesita un almacén compartido y bloqueo distribuido. [Puertas del piloto](10-mvp-and-costs.md)
