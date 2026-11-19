# Fase 20 · Reparto de la cesta entre establecimientos

En la lista de compra, actualiza los precios del código postal, selecciona las cadenas y abre **Repartir la cesta entre tiendas**. Selecciona las sucursales y el máximo de visitas. El valor inicial es dos. La aplicación evalúa la publicación regional ya descargada. Las casillas de artículos comprados y el filtro semanal siguen determinando qué filas entran en el cálculo.

## Modelo

Sea S el conjunto de sucursales verificadas de la zona que satisfacen ambas selecciones, y K el límite de visitas. Para cada grupo g de demandas canónicas compatibles, q[g] es la suma de cantidades en la misma unidad y base. Cada opción j representa un SKU en una sucursal, con contenido comparable c[j], número entero de envases x[j] ≥ 0 y coste de caja C[j](x[j]). El coste aplica una sola tarifa admisible a la cantidad completa de ese SKU en esa tienda, con sus promociones verificadas y redondeo de línea al céntimo. La cobertura del grupo exige Σ c[j]·x[j] ≥ q[g]. Los excedentes sirven a todas las filas del grupo. Las reglas editoriales de la fase 19 excluyen correspondencias ambiguas y exigen evidencia para conversiones y sustitutos.

Una tienda cuenta como visita si se compra algún envase en ella. La restricción es |V| ≤ K. El objetivo lexicográfico maximiza las filas cubiertas y minimiza Σ C[j](x[j]) + Σ f[s] para s en V cuando están activos los costes de visita. En caso de empate prioriza menos visitas, menor desembolso en productos e identificadores estables. En una cesta parcial el importe cubre exclusivamente las líneas resueltas. El stock limitado o desconocido se conserva en el desglose. Se supone que los envases admitidos se pueden repetir, ya que las observaciones comerciales no especifican cantidades disponibles.

## Desplazamiento y referencia

Por defecto se optimizan únicamente los productos. Para activar los costes fijos, introduce por tienda un importe en EUR, la referencia de su tarifa o ticket y confirma los datos. Un cero explícito con fuente es válido. La referencia documenta el dato aportado por el usuario. NutriNest no verifica el emisor del justificante. El dominio devuelve MISSING_COSTS y optimiza solo la compra si un llamador solicita incorporar visitas sin aportar todos los costes. Se puede dejar una tienda sin importe ni fuente. Si ambos planes usan únicamente visitas documentadas, todavía se puede calcular la diferencia neta entre esos planes concretos. El cálculo no consulta GPS, distancias ni rutas.

La referencia es la mejor cesta completa encontrada en una sola de las mismas tiendas bajo el mismo objetivo. El ahorro bruto es la diferencia de importes de compra. El neto es la diferencia incluyendo los costes documentados de ambas opciones. Con desplazamientos desactivados, el neto queda indeterminado. Las comparaciones de ahorro requieren dos cestas completas. Cuando interviene una heurística, las diferencias describen los planes encontrados.

## Algoritmo y límites

El motor prepara una vez la elegibilidad comercial. Para un subconjunto de tiendas, combina sus opciones por grupo mediante la programación dinámica de envases de la fase 19. Un formato a la venta en dos sucursales tiene dos líneas de caja independientes. Los grupos independientes minimizan su desembolso y las visitas se obtienen de las compras efectivamente elegidas.

La búsqueda exacta considera todos los subconjuntos de tamaño 1 a min(K, |S|). En particular considera el conjunto V utilizado por cualquier solución óptima. Para ese conjunto, el mínimo en productos alcanza como mínimo su cobertura y cuesta a lo sumo su desembolso. Las tiendas efectivamente usadas son un subconjunto de V y sus costes fijos son no negativos. Por tanto, comparar todos los candidatos recupera el óptimo para los datos y restricciones de entrada.

Con n sucursales se evalúan B = Σ binomial(n,i) subconjuntos. Por subconjunto se recorren las opciones de los grupos. Para un grupo con T transiciones, P tarifas por SKU y U cantidades parciales alcanzables, la búsqueda de envases cuesta O(T·(P + log U)), además de agrupar y ordenar ofertas. La enumeración usa una pila de profundidad máxima K. El motor conserva los planes individuales para la referencia y el mejor plan combinado.

Por encima de 256 subconjuntos se prueban todas las tiendas individuales y hasta 256 ampliaciones voraces desde la mejor individual. Se detiene al agotar el límite o dejar de mejorar. Con K = 1 basta evaluar todas las individuales. Si un grupo agota las 100.000 transiciones de envases, se conserva una compra factible del mejor formato individual, incluidos los grupos promocionales completos. Cualquier recorte activa la etiqueta heurística. Ningún resultado heurístico certifica optimalidad.

## Validación y fase 21

El fixture de tres tiendas cuesta 9,00 EUR con una visita, 6,00 EUR con dos y 3,00 EUR con tres. Con visitas de 0,00, 1,00 y 5,00 EUR se eligen las dos primeras. La compra asciende a 6,00 EUR y el total a 7,00 EUR, con ahorros bruto y neto de 3,00 y 2,00 EUR. La suite compara también 80 instancias reproducibles con una enumeración independiente de cantidades enteras por tienda y SKU.

Para fase 21 quedan la validación con tickets y tarifas de origen conocido, y la medición de calidad de la heurística en catálogos representativos. Una estimación de rutas requeriría consentimiento y un modelo de trayecto que contemple costes compartidos entre paradas.
