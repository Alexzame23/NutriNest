# R6 · Resolución geográfica nacional

El selector admite cinco dígitos ASCII, conserva los ceros iniciales y acepta prefijos provinciales del 01 al 52. La validación práctica excluye los sufijos 000. El usuario puede cambiar el CP desde supermercados, la ficha del ingrediente o la cesta. La búsqueda por municipio usa el nombre publicado. La de provincia filtra las direcciones publicadas por el prefijo provincial y admite las denominaciones bilingües. [Códigos de provincia del INE](https://www.ine.es/daco/daco42/codmun/cod_provincia.htm)

## Evidencia y ámbito

| Resultado del selector | Evidencia |
| --- | --- |
| COBERTURA | El publicador tiene una observación física vigente de la fuente en una tienda que coincide con la búsqueda. La coincidencia de producto y el stock se evalúan por separado. |
| SIN_COBERTURA | La publicación consultada carece de observaciones físicas localizadas que coincidan con la búsqueda. El resultado describe esa publicación. |
| FUENTE_NO_LOCALIZABLE | Superfudis aporta precios orientativos por cadena y carece de dirección, CP o tienda demostrable en el adaptador R3. |
| Error de consulta | El selector indica que el directorio está inaccesible si falta la configuración o falla el transporte. |

Android obtiene el directorio con GET /v1/geo/directory. El publicador lo deriva de la revisión activa marcada como live, de los derechos vigentes y de las observaciones STORE de la fuente. Entrega el identificador opaco de publicación, los comercios observados con CP y los metadatos físicos disponibles. GeoResolver filtra localmente y el comparador solicita el manifiesto de la región devuelta. El cliente comprueba región, vigencia e integridad del snapshot. Un PriceZone o una observación online conserva su ámbito comercial. Ninguna de las dos fuentes implementadas ofrece una tabla de correspondencias CP → tarifa zonal.

El directorio reside en memoria durante un máximo de cinco minutos, limitado por la caducidad de la publicación. Una ausencia se vuelve a consultar a partir de los treinta segundos. Los fallos de transporte permiten reintentar. Al reiniciar se recuperan los cinco últimos CP distintos en orden de uso. El directorio de Madrid con dos tiendas está en el conjunto de pruebas JVM.

La ubicación aproximada se solicita al pulsar su botón. Android reduce la posición a una cuadrícula de unos dos kilómetros y calcula las distancias en el dispositivo. Solo los comercios con coordenadas publicadas participan en la búsqueda por radio. Ni las preferencias ni la petición al publicador contienen la posición del usuario. Una tienda sin coordenadas sigue siendo localizable por CP si el publicador acredita ese CP.

## Resoluciones ejecutadas el 24 de noviembre de 2026

El plan de prueba R4 consultó Open Prices y el publicador devolvió HTTP 200. La revisión generada a las 14:58 UTC incluyó la ubicación 7394, Carrefour Market Tirso de Molina, CP 28012, municipio Madrid. La observación 357963 es del 2 de noviembre. La publicación consultada caducaba el 26 de noviembre a las 02:58 UTC. [Ubicación en Open Prices](https://prices.openfoodfacts.org/locations/7394)

| Consulta | Fuente o conjunto | Resultado |
| --- | --- | --- |
| 28012 | Publicación real de Open Prices del plan consultado | COBERTURA, una tienda |
| 15001 y 41001 | Mismo plan real | SIN_COBERTURA en esa publicación |
| Cualquiera de los tres CP | Superfudis R3 | FUENTE_NO_LOCALIZABLE |
| 01001 → 15001 → 41001 | Directorio de prueba con dos tiendas | COBERTURA → COBERTURA → SIN_COBERTURA |
| 5 / 10 / 25 km | Tiendas de prueba y posición aproximada | 1 / 2 / 3 tiendas. La cuarta, sin coordenadas, solo aparece en búsqueda manual. |

## Alcance operativo

El servicio existente sirve una publicación activa por instancia. La ingesta por ubicación admite entre una y tres tiendas revisadas por plan, sujeta al presupuesto de peticiones configurado. Un plan puede abarcar varios CP y provincias. Para ampliar los datos se configura la ingesta del publicador. La compilación Android requiere la URL HTTPS del publicador y este debe servir el directorio nuevo. La prueba en vivo acredita el contenido del plan consultado y la fecha indicada. La PoC de Superfudis sigue en debug.

Las pruebas reproducibles de dominio están en GeoResolverTest, las de permisos y preferencias en SupermarketDiscoveryTest y la comprobación del endpoint en price-sync.test.mjs. La prueba optativa LivePricePublisherTest utiliza el recorrido HTTPS y Room descrito en R4. [Publicador R4](04-live-publisher.md)

## Verificación

La copia aislada del commit pasó 196 pruebas JVM. En el árbol de trabajo pasaron las seis pruebas instrumentadas de descubrimiento, permisos, preferencias y configuración en Android 16. El publicador pasó 35 pruebas deterministas y la validación OpenAPI. La prueba de integración se ejecutó por separado con el APK aislado, HTTPS local y Room inicialmente vacío. Verificó los tres CP de la publicación real, la procedencia de la oferta y el SHA-256 del snapshot, 4dff8535b76cb75540f67fba2bc079901cc7d8ccd6f4f99ba8a683f09684019e.
