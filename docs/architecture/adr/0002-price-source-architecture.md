# ADR-0002 — Backend de precios con caché y compra local

- Fecha: 2026-11-18.
- Estado: decisión de diseño para el MVP; despliegue condicionado a la validación de cobertura y licencia de cada fuente.
- Base: `Development` / `2234727`, Room v10.
- Evidencias: [viabilidad de fuentes](../../prices/10-source-feasibility.md).
- Operación: [costes y piloto](../../prices/10-mvp-and-costs.md).

## Contexto

NutriNest calcula y guarda la compra localmente en gramos. El catálogo distingue identidades canónicas y composiciones históricas. Los precios son observaciones de productos vendibles, con ámbito geográfico, condiciones comerciales y derechos propios. Open Prices ofrece una importación pública reutilizable con cadencia diaria y cobertura comunitaria.

## Evaluación

| Opción | Impacto técnico y operativo | Decisión |
| --- | --- | --- |
| Android consulta directamente | Poco coste fijo y mayor dependencia de red en el dispositivo. Las llamadas crecen con usuarios y cestas; los cambios de fuente requieren desplegar clientes. Las claves comerciales deben mantenerse fuera del APK y las cuotas pueden compartirse por IP. Requiere red, caché, límites y tratamiento de licencias en cada cliente. | Reservar para una prueba puntual de una API pública de bajo volumen. |
| Backend independiente con caché | Una ingesta por fuente, normalización y controles de licencia centralizados. Publica únicamente el subconjunto necesario y Android lo conserva offline. Requiere alojamiento, monitorización y mantenimiento. | **Elegido**. Empezar con un único proceso programado y snapshots pequeños servidos por HTTPS. |
| Servicio de terceros autorizado | Puede aumentar establecimientos y frescura con SLA acordado. Introduce cuota, sobrecoste, dependencia y reglas de retención. | Incorporarlo detrás del backend cuando pase la revisión contractual y la prueba de datos. |
| Importación manual o catálogo parcial | Permite un arranque acotado y sin llamadas externas en Android. La calidad depende de revisión, fechas, catálogo reducido y permiso para publicar. | Usar como formato alternativo de entrada autorizada y ofrecer anotaciones privadas en local. |

## Decisión y flujo

1. Un trabajo diario importa el snapshot oficial de Open Prices o un CSV con licencia documentada. Verifica formato, versión, hash, país, moneda, cantidades, unicidad y variaciones anómalas. Conserva procedencia, fecha de observación y fecha de ingesta. La repetición de un lote es idempotente. Construye el nuevo snapshot antes de activar su manifiesto de forma atómica. Un lote fallido conserva el anterior mientras siga cubierto por el permiso.
2. El backend publica manifiesto versionado y snapshots JSON por zona piloto vía HTTPS. Incluir versión de contrato, timestamp de generación, cobertura, licencias, checksum, referencias a observaciones y fechas originales. Usar `ETag`/`If-None-Match`, compresión y límite de tamaño. Android descarga por zona seleccionada, sin enviar cesta, perfiles, recetas, gastos ni posición GPS. Las claves de una fuente comercial viven en el servidor.
3. Una futura capa de precios en Android valida y guarda el snapshot en una transacción con almacenamiento separado del catálogo nutricional. La pantalla de compra lee el último snapshot persistido. Si falta conexión, usa la copia con sus fechas; si está vencida, la presenta como histórica o como sin precio reciente. `WorkManager` puede sincronizar cuando haya red, con espera incremental y límite de reintentos, respetando `Retry-After`. La lista de compra sigue operando sin datos de precio.
4. Cada origen queda identificado en el manifiesto con licencia, usos permitidos, plazo, atribución, responsable y regla de retirada. El servidor desactiva una fuente o lote revocado. Android aplica vencimiento local y elimina datos afectados al recuperar conexión, con una retención offline máxima compatible con cada futuro contrato. Las anotaciones privadas del usuario quedan fuera del snapshot.

## Identidad, formatos y comparación

| Dato | Contrato de diseño |
| --- | --- |
| Producto vendible y envase | ID interno con GTIN validado por dígito de control cuando exista. Guardar GTIN como texto y canonicalizar a 14 dígitos para correlación sin perder el valor original. Alternativa: SKU con namespace de proveedor, cadena o tienda según su alcance. Guardar marca, variedad, pack, unidad y cantidad neta/bruta/escurrida cuando consten. Separar granel; PLU o categoría tienen alcance propio. |
| Relación con ingrediente | Mapeo explícito y revisado entre producto y semántica del ingrediente. No enviar IDs locales ni suponer que un `canonicalId` creado localmente es estable entre instalaciones. Usar claves revisadas/versionadas para el subconjunto global piloto y resolverlas localmente. Nombre similar, alias o macronutrientes cercanos requieren revisión. Mantener el vínculo nutricional existente por `ingredientId`. |
| Oferta observada | ID de fuente y registro, SKU/GTIN si existe, tienda/ámbito y canal, moneda EUR, importe decimal, unidad de venta, base del precio, fecha y precisión de la fecha, promoción elegible y periodo si consta. Mantener la última observación válida por tienda, producto, formato y condición. Precio desconocido, caducado y promesa de disponibilidad son estados distintos. |
| Comparación | Mismo GTIN, jurisdicción/área, canal y condición comparables. Los equivalentes de marca blanca aparecen como alternativas revisadas. Comparar €/kg, €/L o €/unidad dentro de la misma dimensión. Para calcular la compra de 750 g a partir de un pack de 500 g, estimar dos envases. Para granel, usar masa de venta solo cuando se conoce la fracción correspondiente. Si falta una conversión compatible (p. ej. gramos de receta a volumen sin evidencia), dejar la línea sin estimar. |
| Presupuesto parcial | Mostrar fecha, tienda, cobertura y subtotal de líneas estimadas. Mostrar total de cesta solo con todas las líneas compatibles, dejando identificados gastos de envío no incluidos. Mantener descuentos condicionados, lotes/promociones y fidelización fuera del cálculo base del MVP. |

## Recuperación y evolución

- Desacoplar el calendario de ingesta del uso de la app. Si una fuente cambia esquema, caduca o sirve un lote anómalo, detener su publicación y conservar el último lote utilizable. Alertar por ausencia de publicación, bajada de cobertura y registros rechazados. Registrar cambios de términos y de API antes de reactivar.
- Versionar política de correspondencia y contrato de datos. Conservar el último manifiesto válido para volver a publicarlo mientras derechos y frescura lo permitan. Verificar restauración de backup y compatibilidad de snapshots entre versiones Android.
- Incorporar una segunda fuente mediante un adaptador tras aprobar sus derechos y una muestra de la misma zona. Conservar datasets abiertos y bajo contrato como orígenes independientes y aplicar atribución y redistribución por licencia. La aplicación puede presentar resultados juntos si los contratos lo permiten.

## Pruebas de aceptación de la implementación futura

- Casos de ingestión repetida, caída durante publicación, esquema incompatible, pérdida de red, HTTP 304/429/503 y retirada de proveedor.
- Precios caducados, falta de coincidencias, tienda o canal incorrectos, duplicados, GTIN inválido, envase múltiple, peso variable y conversión sin evidencia.
- Lista y cálculos nutricionales idénticos con y sin módulo de precios. Pruebas de migración de Room desde esquemas soportados y reapertura de la app con snapshot válido, vencido o ausente.
- Atribución visible, dataset derivado accesible cuando corresponda y borrado/retención según contrato. Revisar que los paquetes publicados y la telemetría carezcan de tickets, autores y datos de la compra personal.
