# Diagnóstico de ejecución de precios

**Checkout del 23 de noviembre de 2026.** Rama `Development`, base `4be9a458229ce74a60e9757a04ad79af45ebefff`. El árbol estaba limpio antes de instrumentar. `origin/Development` coincide con HEAD. En las referencias locales, `master` está en `593a490` y `origin/master` en `586529b`. Hay 25 commits exclusivos de Development y uno exclusivo de origin/master. Los comparadores y la optimización más recientes están en Development. El remoto fetch/push es `https://github.com/Alexzame23/NutriNest.git`.

## Primer corte por recorrido

| Recorrido | Primera condición que decide el resultado | Consecuencia observable |
|---|---|---|
| Ingrediente con clave BEDCA válida en el área piloto, debug predeterminado | Flag activo, URL vacía, ningún proveedor instanciado | Cero solicitudes HTTP. El ViewModel y Compose distinguen `NO_BASE_URL`. |
| Compra con líneas pendientes en esa área | La carga regional se ejecuta antes de resolver las claves BEDCA | El mismo corte de configuración llega al estado de compra. |
| Comparador fuera del área piloto | La búsqueda local debe devolver exactamente una región | `NO_REGION` antes del proveedor, incluso con URL configurada. |
| Ingrediente sin clave comercial | La comparación individual resuelve la clave antes de la carga regional | `NO_PRODUCT_MATCH` antes de HTTP. |
| Cobertura al seleccionar una tienda | La pantalla lee la región desde Room | Una lectura sin payload vigente deja la cobertura vacía. No hay una instalación de publicaciones en el recorrido de la app. |

El `BuildConfig.java` generado contiene `RETAIL_PRICES_ENABLED = true`. El manifest combinado de debug contiene el metadato con `android:value=""`. Carece de `INTERNET`, aunque el manifest de WorkManager añade `ACCESS_NETWORK_STATE`. Con otra configuración, el primer GET sería `/v1/price-regions/es-madrid-28012/manifest`, seguido únicamente tras validación por `/v1/price-regions/es-madrid-28012/snapshots/{revision}`.

## Medición en el emulador

Se inició el AVD API 36 en modo de solo lectura. La reproducción usó la configuración instalada, una clave obtenida del catálogo Room del APK y los repositorios de `AppContainer`. El test de Compose introdujo el CP del piloto y pulsó buscar. El log del caso de instrumento `UnconfiguredRetailPriceTest` registró estos eventos del recorrido de ingrediente y de la lectura de cobertura:

```text
stage=config enabled=true hasBaseUrl=false provider=none state=NO_BASE_URL
stage=room.read region=es-madrid-28012 reason=miss observations=0
stage=area region=es-madrid-28012 directory=2 local=2
stage=ingredient.key requested=bedca.1000
stage=provider state=NO_BASE_URL httpRequests=0
stage=ingredient.ui state=NoBaseUrl offers=0
```

En el APK final se verificó también la pantalla de compra con una línea de prueba en memoria, sin escribirla en la lista del usuario. Compose encontró `NO_BASE_URL`. La traza del estado que recibió la pantalla fue:

```text
stage=shopping.ui state=NoBaseUrl items=1 eligible=1 optionsBefore=0 afterPurchased=0 retailers=all restrictedChains=0 afterRetailer=0 optionsAfterSelection=0 covered=0
```

| Magnitud en esa consulta | Resultado |
|---|---|
| Provider HTTP efectivo | Ninguno |
| Solicitudes HTTP / observaciones recibidas | 0 / 0 |
| Revisión, cabecera live, SHA-256, código HTTP y latencia del publisher | Sin medición. No hubo respuesta HTTP. |
| Observaciones añadidas a Room por la consulta | 0. El comparador carece de camino de escritura. |
| Observaciones recuperadas por el lector de cobertura | 0, con miss para la región. |
| Claves locales resueltas | 1, `bedca.1000` |
| Observaciones a la salida del matching | 0. La comparación de envases no llegó a ejecutarse. |
| Ofertas entregadas al ViewModel / precios mostrados en Compose | 0 / 0. La aserción de la pantalla encontró `NO_BASE_URL`. |

La cantidad de observaciones en un eventual servidor externo, la validez de sus datos y el porcentaje de matches con una publicación real siguen sin medirse. Los casos de éxito, datos caducados, hash inválido y exclusiones se ejercitan con pruebas aisladas del proyecto.

## Causa → evidencia → impacto

| Causa o condición | Evidencia en este checkout | Impacto |
|---|---|---|
| El proveedor depende de la configuración compilada | `app/build.gradle.kts` usa `nutrinestRetailPricesEnabled=true` y `nutrinestRetailPriceBaseUrl=""` como valores predeterminados. El manifest principal recibe la URL como metadato. El overlay `src/retail/AndroidManifest.xml` se activa solo con URL. | El flag permite entrar en la experiencia aunque no exista publisher. `AppContainer.retailPrices` solo crea `HttpsRetailPriceProvider` con flag y URL. La comparación se detiene antes de consultar conectividad. |
| El servidor incluido y el cliente difieren en la cabecera | `backend/prices/sync-http.mjs::priceResponse` devuelve ETag, Cache-Control y Content-Type para el 200. `HttpsRetailPriceProvider` exige `X-NutriNest-Price-Data: live` tanto en manifest como en snapshot. | Si un despliegue entrega exactamente esa respuesta, se detiene en `NOT_MARKED_LIVE` antes de leer el cuerpo. Se desconoce qué cabeceras serviría un despliegue externo. |
| Las publicaciones del sincronizador incluido carecen de mappings | `backend/prices/sync-publication.mjs::updatePublication` construye `matches: []`. El servicio de matching y sus fixtures se prueban por separado. | Incluso si pasan los controles de transporte, ese contenido ofrece cero equivalencias de ingrediente. Es una condición posterior al fallo observado. |
| El directorio solo contiene dos tiendas de Carrefour | `AppContainer.storeRepository` apunta a `PilotStoreDirectory.repository`. Las dos entradas comparten `DiscoveryArea("es-madrid-28012", "Madrid", "28012", …)`. | El descubrimiento manual admite Madrid o 28012. Los comparadores piden un CP de cinco cifras. El GPS aproximado aplica un radio de 5 km sobre esas mismas dos entradas. |
| Los compradores y el directorio siguen rutas de datos distintas | `RegionalRetailPriceRepository.load` devuelve una publicación en memoria. `SupermarketScreen` llama a `priceSnapshots.read`. Los únicos llamadores de `RoomPriceSnapshotCache.install` están en androidTest. | Una respuesta HTTP válida podría alimentar ambas comparaciones sin poblar la pantalla de tiendas. Una fila de Room tampoco habilita por sí sola la comparación sin conexión. |
| La clave comercial depende del registro BEDCA | `IngredientDao.retailBedcaRecordKeys` exige BEDCA, ámbito global, cabecera activa y estado VISIBLE o SECONDARY. `AppContainer.retailKeys` acepta de 1 a 32 dígitos y antepone `bedca.`. | La igualdad se comprueba contra `IngredientProductMatch.ingredientKey`, después contra el envase. El ID interno de Room y el nombre del ingrediente no sustituyen esta cadena. |
| La geografía del snapshot y la de la tienda tienen identidades distintas | La publicación debe coincidir con la región. Cada tienda publicada necesita una coincidencia única por ID físico u OSM. `PriceZone`, online y ámbito desconocido no prueban una oferta de esa tienda. | Un snapshot con observaciones puede producir cero ofertas locales. La pantalla de cobertura conserva por separado los ámbitos declarados. |

La región de publicación se representa con `CommercialId` en `DiscoveryArea.regionId` y `PriceSnapshot.regionId`. No hay una entidad independiente llamada `PriceRegion`. `PriceZone` representa el ámbito comercial declarado por un retailer. La pantalla de tiendas muestra cobertura, fuente y fechas por ámbito. Los importes monetarios se presentan en las pantallas de comparación.

## Trazas de debug

En Logcat, filtrar por `NutriNestPrices`. En una sesión de reproducción aislada se puede usar `adb logcat -s NutriNestPrices:D "*:S"`. Comparar primero una búsqueda en 28012 con otra fuera de cobertura. El identificador público de la región del directorio es el que aparece en la ruta. El CP introducido por el usuario, sus coordenadas, la URL base, cabeceras arbitrarias, contenidos HTTP y mensajes de excepción quedan fuera del log.

| Etapa | Qué leer |
|---|---|
| Configuración y área | `config` muestra flag, presencia de URL, provider y estado. `area` cuenta tiendas del directorio y tiendas locales. `provider` registra el corte antes de HTTP. |
| HTTP y parseo | `http.start/end` muestra la ruta relativa, código (o ausencia de respuesta), tiempo total de lectura en ms y marcador acotado a live/simulated/absent/other/unread. `manifest` identifica revisión y región. Los pasos de parseo, tamaño y coherencia identifican el rechazo. `hash` registra el resultado de verificar el SHA-256 original. |
| Snapshot y Room | `snapshot` cuenta observaciones de precio, separadas del stock. `cache=bypassed` identifica el recorrido en memoria. `room.install` cuenta observaciones contenidas en la publicación que logró commit. Room guarda un blob por región. `room.read` informa de miss, payload retirado/purgado, caducidad o cantidad recuperada. |
| Ingredientes | `ingredient.key/matches` informa de la clave comercial solicitada y los mappings que la igualan. `store.identity` compara tiendas publicadas y locales. `ingredient.filters` da las cantidades sucesivas tras ámbito físico, tienda local, preferencias de retailer y tienda, mapping único por envase, precio regular, fecha y frescura. `ingredient.ui` cuenta las ofertas que acepta el ViewModel, incluso al expirar sin otra búsqueda. |
| Compra | `shopping.filters` compara demandas con claves resueltas, mappings, precios físicos, locales y vinculados. `shopping.result` cuenta opciones por línea y exclusiones del motor por motivo. Una observación puede servir a varias líneas. `shopping.ui` separa exclusión de comprados y preferencias de cadena/tienda, líneas cubiertas y opciones entregadas. Compose muestra inicialmente la mejor opción de cada línea y permite expandir las restantes. |

Los eventos `discovery.area` y `discovery.radius` cuentan tiendas antes y después de la búsqueda. `discovery.ui` cuenta observaciones de tienda física y de ámbitos declarados en la cobertura mostrada.

Los códigos de diagnóstico incluyen `DISABLED`, `NO_BASE_URL`, `NO_NETWORK`, `NO_REGION`, `MANIFEST_404`, `SNAPSHOT_UNAVAILABLE`, `NOT_MARKED_LIVE`, `HASH_MISMATCH`, `EMPTY_SNAPSHOT`, `NO_PRODUCT_MATCH`, `FILTERED_OUT`, `STALE_DATA` y `SUCCESS`. Se interpretan junto a la etapa. Un HTTP 503 conserva su código y una excepción de transporte con red activa conserva su clase. Un snapshot vacío ya parseado es distinto de una consulta que jamás descargó uno.

## Hipótesis descartadas en la reproducción sin URL

- El primer vacío no es un 404, un timeout, un rechazo `live`, un error JSON ni un hash incorrecto. Ninguna de esas etapas puede ejecutarse sin un proveedor.
- Room no elimina las ofertas del comparador de ingredientes o compra. Esa ruta omite Room y la lectura de cobertura constituye otra operación.
- La limitación geográfica se reproduce con la búsqueda manual, incluso sin permiso de ubicación. El GPS solo ordena y filtra las entradas cargadas.
- `fake-prices` es un ID que la app reconoce como simulado. Las fixtures pertenecen a pruebas y herramientas del backend. No se ha configurado ni medido una fuente real de supermercados.
- Las ofertas que llegan a Compose ya han pasado por el repositorio y el ViewModel. Una interfaz a cero por falta de URL no demuestra ningún defecto de renderizado.

## Archivos inspeccionados

- Build y cableado: `app/build.gradle.kts`, manifests principal y retail, `NutriNestApp.kt`, `AppNavigation.kt`, BuildConfig y manifest combinados de debug.
- HTTP y caché: `HttpsRetailPriceProvider.kt`, `SnapshotJson.kt`, `PriceSnapshot.kt`, `PriceSnapshotCache.kt` (lector, entidad y transacción), `PriceSnapshotDao.kt`, `NutriNestDatabase.kt`.
- Identidad, filtros y repositorios: `IngredientDao.kt`, `CommercialCatalog.kt`, `PriceComparisonEngine.kt`, `PilotStoreDirectory.kt`, `ApproximateLocation.kt`, `StoreRepository.kt`, `StorePriceCoverage.kt`, `RetailerSelectionPreferences.kt`, `RetailPriceRepository.kt`, `ShoppingPriceRepository.kt`.
- UI: `IngredientDetailScreen.kt`, `RetailPriceViewModel.kt`, `RetailPriceScreen.kt`, `ShoppingListScreen.kt` (ViewModel de compra), `ShoppingComparison.kt`, `ShoppingPriceContent.kt`, `SupermarketScreen.kt`.
- Publisher y pruebas: `sync-http.mjs`, `sync-publication.mjs`, `sync-policy.mjs`, suites backend, `HttpsRetailPriceProviderTest`, `PriceSnapshotCacheTest`, `StoreDiscoveryTest`, `ShoppingPriceRepositoryTest`, `ShoppingComparisonTest`, `RetailPriceViewModelTest`, pruebas Compose de navegación, experiencia y descubrimiento.

## Comandos de investigación y reproducción

```powershell
git status
git branch -vv
git log -10 --oneline --decorate
git remote -v
git log -6 --all --oneline --decorate
git rev-list --left-right --count origin/master...HEAD
rg -l --hidden -g '!.git' 'RETAIL_PRICE_BASE_URL|RETAIL_PRICES_ENABLED|PilotStoreDirectory|fake-prices|simulated|bedca\.|price-regions|X-NutriNest-Price-Data|28012|Madrid|latitude|longitude|radius'
rg -n 'priceSnapshots|priceSnapshotDao\(|RoomPriceSnapshotCache|\.install\(' app/src
node --test backend/prices/*.test.mjs
adb devices
emulator -avd Medium_Phone_API_36.0 -read-only -no-window -no-audio -no-snapshot -port 5580
./gradlew.bat --max-workers=3 --no-configuration-cache testDebugUnitTest assembleDebug connectedDebugAndroidTest
./gradlew.bat --offline --no-configuration-cache --max-workers=2 testDebugUnitTest assembleDebug assembleDebugAndroidTest
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml
adb -s emulator-5580 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e class com.zame.nutrinest.ui.ingredients.UnconfiguredRetailPriceTest com.zame.nutrinest.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5580 shell am instrument -w -e class 'com.zame.nutrinest.data.prices.HttpsRetailPriceProviderTest,com.zame.nutrinest.data.prices.PriceSnapshotCacheTest,com.zame.nutrinest.ui.stores.SupermarketDiscoveryTest,com.zame.nutrinest.ui.ingredients.RetailPriceExperienceTest,com.zame.nutrinest.ui.ingredients.RetailPriceNavigationTest,com.zame.nutrinest.ui.menu.shopping.ShoppingItemTextTest' com.zame.nutrinest.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

Se usaron rutas absolutas del SDK local para ADB/emulator y el JDK 21 para Gradle. El primer intento de Node bloqueó la conexión de loopback en el sandbox. Con acceso a loopback pasaron las 30 pruebas. El primer intento offline de la suite conectada carecía de un artefacto UTP 32.2.1. La ejecución con resolución de dependencias pudo iniciar el runner del emulador.

## Resultados de verificación

- `node --test backend/prices/*.test.mjs` completó 30 pruebas, todas correctas.
- `testDebugUnitTest` completó 197 pruebas, sin fallos, errores ni omisiones en los XML JUnit. Incluye precios, tiendas, geografía y los estados nuevos.
- `assembleDebug` y `assembleDebugAndroidTest` generaron los APK de la revisión final. `aapt dump xmltree` confirmó la URL vacía dentro del APK. `aapt dump permissions` confirmó la ausencia de `INTERNET`.
- `connectedDebugAndroidTest` completo pasó en el AVD Android 16. El runner inició 74 métodos, terminó 70 correctamente y omitió los 4 benchmarks que exigen `nutrinestPerf=true`. Cero fallos. El resumen de Gradle cuenta 78 eventos de fin. El desglose anterior procede de `test-results.log` del runner, con códigos finales 0 y −4.

- Tras compilar el APK final, un segundo lanzamiento dirigido de Gradle/UTP sufrió `Adb connection Error: Connection reset` y un error de classloader en ddmlib. Se interrumpió ese reintento, se recuperó la conexión, se instalaron los dos APK y se ejecutó directamente el mismo `AndroidJUnitRunner`. La clase nueva pasó sus dos pruebas de Compose (ingrediente y compra).

En el mismo APK final se repitieron 16 pruebas de HTTPS, persistencia Room, descubrimiento de tiendas, navegación y presentación de precios. Resultado del runner: `OK (16 tests)`. Junto con las dos pruebas nuevas, pasaron las 18 dirigidas.

## Alcance de los cambios

Se añadieron `PriceDiagnostics.kt`, `PriceDiagnosticsTest.kt`, `UnconfiguredRetailPriceTest.kt` y este informe. Se instrumentaron `NutriNestApp.kt`, `HttpsRetailPriceProvider.kt`, `PriceSnapshotCache.kt`, `RetailPriceRepository.kt`, `ShoppingPriceRepository.kt`, `RetailPriceViewModel.kt`, `ShoppingComparison.kt` y `SupermarketScreen.kt`. `RetailPriceScreen.kt`, `ShoppingPriceContent.kt` y `res/values/strings.xml` presentan el diagnóstico de configuración de debug. Se extendió `RetailPriceViewModelTest.kt` usando su fixture existente. Datos reales modificados: cero.

Commit recomendado: `debug: instrument retail price pipeline`.

