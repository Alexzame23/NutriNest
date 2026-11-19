# Fase 24 · Actualización y recuperación de instalaciones

NutriNest registra todas las migraciones consecutivas desde la versión 1 hasta la 11. Los tramos 1→2 a 9→10 son explícitos. Room genera 10→11 para añadir la caché de snapshots de precios. Los exports conservados en Git empiezan en la versión 4. Para 1–3, el fixture reproduce el DDL v1 de `verify_migration.py` y los dos siguientes cambios históricos. Esta distinción limita las conclusiones sobre instalaciones antiguas con modificaciones ajenas a esos esquemas.

## Qué se compara

`ProductionUpgradeTest` construye un origen aislado por versión. Usa SQLite de Android para 1–3 y `MigrationTestHelper` con el export original para 4–10. Cierra la base, comprueba que no queda un journal o WAL con datos pendientes, conserva el archivo inicial y verifica su SHA-256 antes de migrar una copia. Abre dos veces la copia mediante `NutriNestDatabase.build`, con las migraciones, el importador, la curación y el seeder registrados en producción. En cada apertura exige además que una llamada explícita al importador devuelva éxito. Los nombres de trabajo pertenecen al prefijo `qa24-`.

| Origen | Datos distintivos del fixture, además de los heredados |
| --- | --- |
| 1 | Ingredientes personalizados, referencias de imagen de ingrediente, portada y paso, receta favorita y gramos fraccionarios. |
| 2 | Perfil con preferencias dietéticas, alergia sintética y entrada de peso relacionada. |
| 3 | Preferencia de ritmo de pérdida de peso y reconstrucción posterior de perfiles. |
| 4 | Menú, plato bloqueado, factor por perfil y compra marcada. |
| 5–6 | Favoritos y uso del ingrediente, nutrientes y porciones. Una receta y su lista utilizan un panel histórico editado de BEDCA:42 y un registro obsoleto BEDCA:2173. |
| 7 | Ingesta, configuración de comidas por perfil, plato individual, factores servido y planificado distintos y unidad original de receta. |
| 8 | Nombres e instrucciones bilingües. |
| 9 | Correspondencias canónicas preexistentes creadas por el backfill, con su identidad y revisiones. |
| 10 | Cantidad de entrada y referencia de evidencia históricas. La migración automática crea la caché vacía. |

El oráculo compara los campos existentes en el origen, incluidos los tipos SQLite, los nulos y los bits de los valores reales. Relee las relaciones mediante sus IDs y recalcula kcal, carbohidratos, grasas y proteína por ingrediente. El digest crítico abarca las proyecciones históricas seleccionadas. Los hashes de archivo identifican las copias concretas de esta ejecución. Una nueva generación puede crear UUID y fechas diferentes en los metadatos canónicos.

Cada apertura comprueba `PRAGMA user_version`, `foreign_keys`, `foreign_key_check` e `integrity_check`, así como los índices frente a una v11 de referencia. Room valida el esquema durante la actualización. Dos escrituras inválidas comprueban la FK de ingredientes de receta y el trigger de correspondencias. Se exige una correspondencia por ingrediente, la receta personal y 250 recetas incorporadas con identificadores distintos. La segunda apertura debe conservar todos los recuentos de tablas y el contenido de todas las correspondencias.

## Reproducir

Desde la raíz del proyecto, con Python 3, el SDK de Android y un emulador de pruebas conectado:

```sh
python tools/verify_migration.py
python tools/check_room_schema.py
python tools/verify_upgrade_interruption.py
./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.zame.nutrinest.data.local.ProductionUpgradeTest"
```

En Windows se usa `.\gradlew.bat`. Las pruebas instrumentadas completas se ejecutan omitiendo el filtro de clase. Los XML y los logs por caso se generan en `app/build/outputs/androidTest-results/connected/debug/`. La etiqueta `UpgradeQA` contiene el hash crítico y el hash del origen. La batería de fase 23 y `CatalogReleaseGateTest` aportan las comprobaciones complementarias del catálogo completo y de instalaciones v8 parcialmente inicializadas.

## Recuperación y límites

El ensayo de interrupción trabaja en un directorio temporal. Un proceso SQLite inicia 3→4 sobre una copia de v3 y termina abruptamente justo después de eliminar la tabla antigua de perfiles, antes del commit. Otro proceso abre el archivo con su journal, compara el volcado lógico con el original y reintenta la migración. Esto ejercita la recuperación del journal SQLite de escritorio. Quedan fuera la pérdida de energía física, fallos del almacenamiento y terminación forzada de la aplicación en Android.

Reinstalar el APK anterior conserva el esquema ya actualizado cuando el instalador permite mantener los datos. NutriNest registra rutas ascendentes. Una vuelta a una versión anterior requiere una migración inversa diseñada para ese esquema o restaurar una copia consistente previa, junto con una versión de la aplicación que pueda leerla. Una copia SQLite tomada en caliente debe incluir el estado confirmado en WAL mediante un mecanismo consistente. La restauración de una copia anterior recupera el estado de su fecha y requiere reconciliar por separado los cambios posteriores.

Los fixtures verifican las rutas de fotos guardadas en SQLite. Una restauración de instalación completa requiere también los archivos de imagen y las preferencias externas a Room, incluidas las de supermercados. Las reglas del repositorio excluyen las bases y `files/images/` de la copia automática y de la transferencia declarada entre dispositivos. Para recuperar ese histórico hace falta disponer de una copia independiente tomada previamente. [Reglas de backup](../../app/src/main/res/xml/backup_rules.xml) y [reglas de extracción](../../app/src/main/res/xml/data_extraction_rules.xml).

Las pruebas usan únicamente datos sintéticos y un emulador API 36. La cadena validada corresponde al upgrade de la base y a sus callbacks. El instalador Android, el almacenamiento físico de fotografías, la base de equivalencias comerciales del servicio backend y las variantes OEM quedan fuera del ensayo.

## Resultado por origen

| Ruta | Filas históricas comparadas | Ingredientes finales | Tiempo en el emulador | Resultado |
| --- | ---: | ---: | ---: | --- |
| 1→11 | 5 | 925 | 11.57 s | Correcto |
| 2→11 | 7 | 925 | 10.26 s | Correcto |
| 3→11 | 7 | 925 | 11.32 s | Correcto |
| 4→11 | 11 | 925 | 10.67 s | Correcto |
| 5→11 | 22 | 926 | 10.47 s | Correcto |
| 6→11 | 22 | 926 | 11.14 s | Correcto |
| 7→11 | 24 | 926 | 10.13 s | Correcto |
| 8→11 | 24 | 926 | 10.26 s | Correcto |
| 9→11 | 28 | 926 | 10.29 s | Correcto |
| 10→11 | 28 | 926 | 10.54 s | Correcto |

Pasaron las diez rutas y las trece regresiones de migración y catálogo en 149.687 s de instrumentación, con cero errores u omisiones. Las filas de la tabla corresponden a las proyecciones históricas del fixture. Cada ruta verificó también las 251 recetas finales y los recuentos de todas las tablas tras reabrir. [Hashes completos y tiempos](24-upgrade-results.json) · [Diez originales SQLite sintéticos](fixtures/24-upgrade-originals.zip).

La primera comparación de v1–v4 incluía un ID BEDCA incorporado durante la importación. Se corrigió el filtro extrayendo los IDs del origen antes de migrar y se repitió la matriz completa. El informe recoge la ejecución corregida. Las verificaciones Python de migraciones y del esquema Room v11 también pasaron.

El ensayo final de interrupción verificó los diez SHA-256 archivados, recuperó el volcado lógico exacto de v3 y completó el reintento a v4 con todas las filas previas. La fase 25 puede partir de la versión 11 con las diez rutas cubiertas y sus originales disponibles para futuras regresiones.
