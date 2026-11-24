# R3 · Evaluación de Superfudis

R1 diagnosticó el proveedor regional de NutriNest y R2 verificó observaciones públicas en Open Prices. R3 evalúa el endpoint público de una fuente multicadena española mediante una cesta pequeña y consultas de solo lectura comercial. [R1](01-runtime-diagnosis.md) · [R2](02-open-prices-poc.md)

## Contrato y condiciones

| Aspecto | Evidencia consultada el 23 de noviembre de 2026 |
| --- | --- |
| Operación pública | `POST https://supercomparador-i9tw.onrender.com/api/v1/compare-basket`. La guía de integradores la publica sin credenciales. |
| Entrada | OpenAPI 3.1, versión de API 0.2.0. `items` contiene de 1 a 10 objetos con `query` y `quantity`. `retailers` selecciona cadenas e `include_delivery` es opcional. La prueba lo fija a `false`. |
| Salida | El esquema declara HTTP 200 con JSON genérico y 422 para validación. Los campos concretos de éxito se evalúan con la respuesta real. |
| Cuota | 3 peticiones por minuto e IP, con hasta 10 productos por petición. |
| Localización | La operación pública carece de filtros documentados de CP, tienda o coordenadas. La funcionalidad de localización de la web tiene un contrato distinto. |
| Cadenas anunciadas | Mercadona, Carrefour, Dia, Consum, Eroski, El Corte Inglés, Ahorramás, BM y Alcampo. La guía envía `ahorramas` para Ahorramás. |

[Guía para integradores](https://superfudis.com/developers/) · [OpenAPI público](https://supercomparador-i9tw.onrender.com/openapi.json)

La guía destina la API a consultas de consumidores y asistentes que actúan por ellos. Excluye la extracción masiva y la redistribución comercial de sus datos como usos previstos. Los términos generales del 17 de septiembre de 2026 dan carácter informativo a las ofertas y piden verificar precio, disponibilidad y condiciones con el comercio. La PoC usa términos genéricos, conserva el resultado en memoria y registra únicamente evidencia acotada. Para un fallback comercial, una publicación periódica o una caché compartida de precios, la documentación pública consultada no aporta una autorización de reutilización suficiente. [Guía para integradores](https://superfudis.com/developers/) · [Términos de uso, apartados 3, 9–12](https://superfudis.com/terminos/)

## Adaptador y reproducción

`SuperfudisPriceSource` implementa `SpanishBasketPriceSource` en la variante `debug`. El adaptador limita cada operación a una cesta, con un presupuesto de 3 solicitudes por ventana móvil de 60 s compartido en el proceso. El servidor aplica la cuota a toda la IP, incluida la actividad de otros procesos. El cliente cuenta fallos de transporte, rechaza redirecciones, limita el cuerpo de respuesta a 1 MiB y devuelve el estado HTTP de error y `Retry-After` si existe. Un 429 activa una pausa local de al menos 60 s, ampliada con un `Retry-After` válido. Cada intento se inicia de forma explícita.

Cada oferta conserva la cadena, el nombre de la referencia y su importe `price_eur` como decimal. Cuando llegan, conserva formato, marca, EAN, número de unidades, medidas, precio de comparación y tipo de coincidencia. La lectura distingue el producto de referencia de la oferta devuelta por cada cadena. Un resultado de resolución parcial puede traer `resolved_items` y `unresolved_items` sin precios. Un importe omitido queda como `null`. La fecha de precio queda ausente cuando el servicio no la devuelve. Toda muestra lleva una advertencia de precio orientativo sin tienda ni CP, por lo que permanece fuera del modelo de ofertas locales y del proveedor productivo.

La suite normal usa respuestas sintéticas con las estructuras observadas y verifica el contrato enviado, respuestas parciales, precio y fecha ausentes, entradas inválidas, timeout, 429 con pausa, 503 y presupuesto compartido.

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

La prueba real hace una sola petición de un alimento. Puede elegirse otro término con `NUTRINEST_SUPERFUDIS_QUERY`.

```powershell
$env:NUTRINEST_SUPERFUDIS_LIVE = '1'
try {
    .\gradlew.bat :app:testDebugUnitTest --rerun --tests '*SuperfudisLiveTest'
} finally {
    Remove-Item Env:NUTRINEST_SUPERFUDIS_LIVE
}
```

## Resultados reales

El 23 de noviembre de 2026 se enviaron cinco solicitudes puntuales al mismo endpoint público, repartidas en varios minutos, con cantidad 1 y las nueve cadenas anunciadas. Se mantuvo `include_delivery=false`.

| Consulta y cliente | Resultado HTTP | Qué devolvió |
| --- | --- | --- |
| Leche semidesnatada 1 l, arroz redondo 1 kg y huevos frescos 12 unidades, PowerShell | Sin respuesta HTTP tras 40 s | Timeout del cliente. |
| La misma cesta con mayor tiempo de espera, PowerShell | 200 | Una entrada resuelta (leche) y dos pendientes (arroz y huevos). El cuerpo carecía de ofertas y precios. |
| Leche semidesnatada 1 l, PowerShell, 19:55 CET | 200, `ok` | Una oferta, de Mercadona. |
| Leche semidesnatada 1 l, `SuperfudisLiveTest`, 19:57 CET | 200, `ok` | Una oferta con importe, de Mercadona. Carrefour, El Corte Inglés, Eroski, Consum, Dia, Ahorramás, BM y Alcampo aparecieron en `unavailable_retailers` para esta referencia. |
| Arroz redondo SOS 1 kg, PowerShell, 19:58 CET | 200, `needs_clarification` | Una entrada pendiente con motivo `search_timeout`, sin ofertas. |

La prueba Kotlin leyó **Leche semidesnatada Hacendado**, EAN `8402001002106`, brik de 1.000 ml, **0,83 EUR** y precio de comparación de **0,83 EUR/l**. La coincidencia fue `exact_ean`. La oferta traía nombre, marca, EAN, formato, medidas, importes, tipo de coincidencia y una URL de origen. La respuesta también incluía un total, los comercios del mejor total y una URL de cesta. El resultado carecía de fecha de precio, CP y tienda. La hora de la prueba acredita cuándo se consultó, sin indicar cuándo se observó o actualizó el precio.

La única cadena verificada con un importe en esta muestra fue Mercadona. La lista de cadenas sin oferta se refiere a la referencia elegida en esa consulta. El timeout interno de búsqueda de arroz y la falta de resolución de huevos dejan esos productos sin evidencia de precio. El contrato deja indeterminadas la cobertura geográfica, la disponibilidad local y las diferencias de precio entre sucursales.

La suite con `SuperfudisLiveTest` completó 215 pruebas sin fallos, incluidas las siete deterministas de R3 y la consulta HTTPS. Omitió únicamente la prueba en vivo de R2. La ejecución normal completó 214 pruebas sin fallos y omitió las dos pruebas de red opt-in.

## Decisión de uso

| Uso | Decisión |
| --- | --- |
| A. PoC | **GO** para una consulta puntual y orientativa de consumidor por la API pública. |
| B. Fallback | **NO-GO** para el comparador local de NutriNest. Para un futuro flujo orientativo bajo demanda habría que confirmar con Superfudis los derechos de presentación de los datos y el presupuesto de llamadas. |
| C. Producción | **NO-GO**. El contrato carece de tienda o CP, la frecuencia por IP limita un backend compartido y los términos publicados no respaldan la redistribución comercial de un feed de precios. |
| D. Ninguno | Hay un uso de PoC demostrado. |
