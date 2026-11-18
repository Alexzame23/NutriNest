# Fase 15 · Precios comparables y desembolso de compra

El motor Kotlin compara las observaciones de un snapshot regional para una demanda y una clave editorial. Devuelve opciones por envase y observación, su procedencia y las exclusiones. La reducción a un único ganador por cesta, el transporte de evidencias adicionales y la presentación al usuario corresponden a la fase 16.

## Entrada y salida

```kotlin
val snapshot = reader.read(regionId, policy.now)
val comparison = PriceComparisonEngine.compare(demand, snapshot, policy, massContents, promotions)
val first = comparison.options.firstOrNull()
val proportionalEur = first?.proportional?.decimal(2)
val checkoutEur = first?.checkoutEur
```

PriceSnapshotReader aporta una publicación verificada. El motor comprueba también su vigencia. Solo compara IngredientProductMatch EXACT para la clave solicitada. Varios mapeos del mismo envase a la clave se consideran ambiguos. Las alternativas aprobadas por el editor conservan la categoría de sustituto. Cada resultado apunta a la observación, su revisión, tienda o ámbito, el envase y el mapeo. La disponibilidad de stock se consulta aparte.

Quantity expresa cantidades como Double. El motor valida que sean positivas y finitas, toma su representación decimal con BigDecimal.valueOf y aplica el multiplicador dimensional en decimal. Money contiene EUR con hasta cuatro decimales. Ningún cálculo monetario pasa por coma flotante binaria. Una cantidad ya aproximada por el llamador conserva esa aproximación, lo cual importa en el límite entre uno y dos envases.

## Fórmulas

Para un formato fijo, q es la demanda en g, ml o unidades y c es el contenido comparable total del envase. El número de envases n = ceil(q/c). Si c ya incluye las porciones interiores, se utiliza una sola vez. P es el precio observado de un envase pagado.

| Magnitud | Cálculo |
| --- | --- |
| Envases pagados, venta ordinaria | b = n |
| Envases pagados, promoción repetible de t por p | b = floor(n/t) × p + (n mod t) |
| Importe de línea antes de redondear | A = P × b |
| Desembolso para cubrir la demanda | A redondeado al céntimo, HALF_UP |
| Coste proporcional de la cantidad utilizada | A × q / (n × c) |
| Precio efectivo por kg, L o pieza | A × k / (n × c), con k = 1000 para gramos y ml, o 1 para piezas |

EuroRatio conserva numerador y denominador exactos. La comparación usa productos cruzados. La presentación elige los decimales, por ejemplo cuatro para tarifas y dos para importes. El motor redondea el desembolso una vez por línea. Esta política debe contrastarse con los tickets de cada distribuidor antes de presentar un total de caja multirreferencia.

Para peso variable, P es EUR/kg facturado y c son los gramos comparables acreditados por kg facturado. La tarifa comparable es P × 1000 / c y el proporcional P × q / c. Si se conocen los gramos efectivamente pesados w y cubren la demanda, el desembolso es P × w / 1000 redondeado al céntimo. Sin pesada, checkoutEur queda ausente. packageCount y packagePrice solo se informan en formatos fijos.

## Bases, condiciones y cobertura

- Un envase fijo declara su contenido neto. Para peso escurrido, bruto o comestible, VerifiedMassContent vincula gramos, base, fuente, envase y revisión del match. En venta por kg también debe acreditarse la relación entre masa facturada y masa solicitada. fromNetYield usa la validación de fase 04 para el rendimiento neto–comestible de un match exacto, conserva el factor aplicado y multiplica en decimal. Corresponde al adaptador resolver la clave al ingrediente local correcto y verificar que el factor se aplica al producto. [Conversiones](../architecture/04-validated-food-quantity-conversions.md)
- Las cantidades admitidas son masa, ml/L y recuentos del ingrediente (UnitDefinition.count con la clave editorial). Volumen y masa son dimensiones distintas. Un producto sin evidencia de base o con declaraciones múltiples para la misma base queda excluido.
- Una promoción necesita fecha final inclusiva en la zona de venta y términos verificados para esa observación exacta. En un 3×2, P debe ser el importe de un envase pagado, antes de la bonificación. Las unidades restantes se cobran a P. Si el precio recibido ya es prorrateado o los multiplicadores, topes o condiciones no pueden comprobarse, el adaptador debe omitir los términos. Un precio regular elegible se evalúa como otra observación. La fidelización requiere pertenencia confirmada a la cadena y una oferta condicional requiere confirmación explícita. El modo de peso variable admite promociones de tarifa por kg sin agrupación de envases.
- Se excluyen observaciones futuras, con ámbito desconocido o de edad mayor o igual al plazo configurado (30 días por defecto). Se usa observedAt o, en su ausencia, el inicio de observedOn en UTC, como en fase 13. Un snapshot vencido queda fuera incluso si contiene precios recientes.
- comparablePackages y evaluatedPackages cuentan envases distintos. excluded identifica el error por observación o envase. Las ausencias de snapshot o de match se comunican en el resultado de la demanda. Un importe cero necesita una observación válida de cero euros.
- Se ordena por desembolso conocido ascendente, seguido del coste proporcional exacto, ID del envase, fuente y clave externa de la observación. Las opciones pendientes de pesada quedan al final. Cada observación conserva su ámbito para que el consumidor filtre las tiendas accesibles.

## Fixtures numéricos

Importes simulados de prueba. El desembolso corresponde a adquirir el formato de la fila hasta cubrir la demanda.

| Demanda y formato | Precio observado | Tarifa comparable | Proporcional EUR | Caja EUR |
| --- | ---: | ---: | ---: | ---: |
| 300 g de arroz, bolsa de 1 kg | 2,40 | 2,40/kg | 0,72 | 2,40 |
| 800 g de arroz, dos bolsas de 500 g | 1,30 | 2,60/kg | 2,08 | 2,60 |
| Seis huevos, docena | 3,00 | 0,25/ud | 1,50 | 3,00 |
| 1,5 L de leche, botellas de 1 L | 1,10 | 1,10/L | 1,65 | 2,20 |
| 1,5 L de leche, botella de 1,5 L | 1,50 | 1,00/L | 1,50 | 1,50 |
| 75 ml de aceite, botella de 750 ml | 5,00 | 6,6667/L | 0,50 | 5,00 |
| 1001 g, dos packs de 2 × 500 g | 1,9950 | 1,9950/kg | 2,00 | 3,99 |
| 1250 g, tres bolsas de 500 g en 3×2 | 1,00 | 1,3333/kg | 1,67 | 2,00 |
| 300 g escurridos, lata con 200 g escurridos y 500 g netos | 1,00 | 5,00/kg escurrido | 1,50 | 2,00 |
| 300 g comestibles, rendimiento de 800 g/kg facturado | 7,90/kg | 9,875/kg comestible | 2,96 | 3,16 con pesada de 400 g |

Ejecutar la suite JVM desde la raíz con `./gradlew :app:testDebugUnitTest`. Las pruebas incluyen el borde de caducidad, un precio observado a cero, HALF_UP de 1,0050 a 1,01, grupos parciales y repetidos en 3×2, restricciones de fidelización y desempates independientes del orden de entrada.

## Entrada en fase 16

Conectar la publicación de matches aprobados y sus retiradas, resolver claves locales y suministrar evidencias versionadas de base, condiciones de oferta y pesada cuando proceda. La interfaz debe mostrar por separado tarifa, proporcional y desembolso, ámbito y fecha de la observación. Para totales parciales de cesta, contar demandas cubiertas y señalar las pendientes. Agrupar compras compatibles antes del ceil para aprovechar sobrantes y promociones dentro de una misma tienda. La selección de combinaciones de formatos y el stock requieren una decisión posterior de cesta. [Contrato comercial](11-commercial-product-domain.md) · [Matching](14-verified-ingredient-product-matching.md)
