# NutriNest

**NutriNest** is a native Android nutrition-planning app built to help people make practical, more informed food choices. It brings personal nutrition targets, recipes, ingredients, weekly menus, meal portions, food intake, and shopping lists into one private, offline-first experience.

The project has two purposes: to contribute to better day-to-day nutrition planning and to demonstrate the Android engineering skills of its creator, Alejandro Zamorano Méndez, through a real product with thoughtful domain logic and maintainable architecture.

> NutriNest is an educational planning tool. It does not provide medical diagnosis, treatment, or personalised clinical nutrition advice. People with medical conditions, eating disorders, pregnancy-related requirements, or severe allergies should consult an appropriately qualified health professional.

## What users can do

- Create one or more personal profiles with body data, activity level, goals, dietary preferences, allergies, and metric or imperial units.
- Calculate estimated daily calories and macronutrients from Mifflin-St Jeor, revised Harris-Benedict, or Katch-McArdle formulas.
- Browse a bundled food reference database with macro- and micronutrients, portions, aliases, food-safety notes, and data-quality information.
- Create, organise, search, favourite, and photograph recipes; instructions and built-in recipe titles are available in English and Spanish.
- Generate a weekly menu for three, four, or five meals per day, with individual calorie portions.
- Plan for a shared household, entirely individual menus, or a mixed household where only selected meals are different.
- Keep favourite meals on chosen days, choose first course/main/dessert structures for lunch and dinner, set free meals, filter recipe books/categories, and control recipe repetition.
- Create a calculated shopping list from the generated menu and mark purchases as completed.
- Record food intake and adjust planned portions against each profile's daily target.
- Receive a Friday reminder when the following week's menu has not been created.

## How it works

1. **Set up profiles.** NutriNest derives age from date of birth, estimates basal metabolic rate (BMR), applies activity to estimate total daily energy expenditure (TDEE), then applies the selected goal. It derives a protein target from the goal and body weight, assigns approximately 27% of calories to fats, and uses carbohydrates for the remaining energy.
2. **Build the recipe collection.** Recipes use ingredients expressed in grams as the canonical calculation unit. The app totals the nutrition of every ingredient and supports display/input conversions for common portions and measures.
3. **Configure the week.** Choose meal slots, their calorie shares, menu scope, courses, free meals, recipe filters, and any meal locks.
4. **Generate the plan.** The local planning engine filters out incompatible recipes using declared dietary preferences and allergy matching. It then scores the remaining recipes by meal/course affinity, variety, recent use, repeats, and potential use of leftover ingredients. Portions scale each recipe to the relevant calorie budget.
5. **Shop, cook, and log.** Ingredient quantities are aggregated into a shopping list. Intake entries can then be recorded so the plan remains useful when real life differs from it.

The generator intentionally favours variety and avoids exceeding the configured repeat limit. If the selected filters leave too few recipes, it reports the problem instead of silently producing an incomplete plan.

## Privacy and data

NutriNest is designed as a local-first application:

- Profiles, recipes, menus, food-intake entries, and shopping lists are stored in a Room/SQLite database on the device.
- The bundled food database is imported locally from the application asset; the app does not need an app-owned account or backend service.
- The Android manifest requests only notification permission, used for the optional weekly planning reminder.
- Android's device-level backup and restore behaviour is controlled by the operating system and the user's device settings.

Do not treat allergy filtering as a clinical safety guarantee. The feature performs conservative local matching against the data recorded in the app; users remain responsible for checking ingredients, labels, cross-contamination risks, and advice from qualified professionals.

## Built-in content

The app includes a local food database derived from BEDCA data and a library of 250 one-serving recipes. Built-in recipes are original, concise adaptations; their names and ingredient ideas were checked against reputable public culinary and health sources. Source notes and content rules are available in [docs/recipe-library-sources.md](docs/recipe-library-sources.md).

## Technology and architecture

| Area | Implementation |
| --- | --- |
| Platform | Android (min SDK 26, target SDK 36) |
| Language | Kotlin |
| UI | Jetpack Compose with Material 3 |
| Navigation and state | Navigation Compose, ViewModels, Kotlin Flow |
| Local persistence | Room / SQLite with checked-in migration schemas |
| Background work | WorkManager for the weekly reminder |
| Images | Coil with local image storage |
| Build | Gradle, Android Gradle Plugin, Kotlin Symbol Processing (KSP) |
| Quality | Unit tests for nutrition, planning, text normalisation, reminders, and database behaviour |

The code is organised around clear responsibilities:

- `ui/` contains Compose screens and ViewModels.
- `domain/` contains the testable nutrition and menu-planning rules.
- `data/` contains Room entities, DAOs, database migrations, importers, seed data, and repository implementations.
- `notifications/` contains the weekly menu reminder.
- `tools/` contains project checks and food-asset utilities.

The nutrition calculator and weekly menu generator are pure Kotlin components, which keeps their rules independently testable and makes them suitable foundations for future interfaces or services.

## Getting started as a developer

### Requirements

- Android Studio with a compatible Android SDK installation.
- JDK 17 or newer (Android Gradle Plugin 9 requires JDK 17).
- An Android emulator or physical device running Android 8.0 (API 26) or newer.

### Build and run

1. Clone the repository.
2. Open the project root in Android Studio.
3. Let Gradle sync and install any requested SDK platforms.
4. Select an emulator or device, then run the `app` configuration.

From a terminal on Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

On macOS or Linux:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Run the JVM unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Connected-device instrumentation tests can be run with `connectedDebugAndroidTest`.

## Value for organisations

NutriNest is a portfolio project and a useful demonstration of how a consumer health-and-wellness product can be designed around privacy, offline resilience, and explainable rules instead of opaque recommendations. It demonstrates:

- Product thinking for households with shared and individual nutritional needs.
- Native Android development with modern declarative UI and local persistence.
- Explicit, testable calculation and recommendation logic.
- Bilingual UX for English- and Spanish-speaking users.
- Careful handling of dietary preferences, allergy warnings, ingredient metadata, and non-clinical safety boundaries.
- A foundation that could be evaluated for future integrations, professional workflows, analytics, or cloud capabilities under a separate, written agreement.

This repository is not offered for reuse, deployment, white-labelling, or commercial integration. See the licence below.

## Project status

NutriNest is an active personal portfolio project by **Alejandro Zamorano Méndez** under **Zame**. Features, food data, formulas, and interfaces may change as the project evolves.

## Licence and permitted use

This repository is **proprietary and source-available only**. All rights are reserved by Alejandro Zamorano Méndez / Zame. Viewing the repository does not grant permission to use, copy, modify, distribute, publish, sublicense, deploy, train on, or create derivative works from any part of it.

Any personal, educational, commercial, internal, or enterprise use requires prior written permission from the rights holder. See [LICENSE](LICENSE) for the complete terms.

## Author

Created by **Alejandro Zamorano Méndez** · **Zame**

---

## Versión en español

**NutriNest** es una aplicación Android nativa de planificación nutricional para ayudar a las personas a tomar decisiones alimentarias prácticas y mejor fundamentadas. Reúne objetivos nutricionales personales, recetas, ingredientes, menús semanales, raciones, registro de ingestas y listas de la compra en una experiencia privada y local.

El proyecto persigue dos fines: contribuir a una mejor planificación de la alimentación diaria y demostrar las competencias de desarrollo Android de su creador, Alejandro Zamorano Méndez, mediante un producto real con lógica de dominio meditada y arquitectura mantenible.

> NutriNest es una herramienta educativa de planificación. No ofrece diagnósticos médicos, tratamientos ni asesoramiento nutricional clínico personalizado. Las personas con patologías, trastornos de la conducta alimentaria, necesidades relacionadas con el embarazo o alergias graves deben consultar a un profesional sanitario cualificado.

### Funcionalidades para usuarios

- Crear uno o varios perfiles con datos corporales, nivel de actividad, objetivos, preferencias dietéticas, alergias y unidades métricas o imperiales.
- Calcular calorías y macronutrientes diarios estimados mediante Mifflin-St Jeor, Harris-Benedict revisada o Katch-McArdle.
- Consultar una base de datos alimentaria incluida con macro- y micronutrientes, porciones, alias, notas de seguridad alimentaria e información sobre calidad de datos.
- Crear, organizar, buscar, marcar como favoritas y fotografiar recetas; los títulos e instrucciones de las recetas incluidas están en inglés y español.
- Generar un menú semanal de tres, cuatro o cinco comidas diarias, con raciones calóricas individuales.
- Planificar un menú compartido para el hogar, menús totalmente individuales o un modelo mixto en el que solo algunas comidas sean diferentes.
- Fijar recetas para días concretos, elegir estructuras de primero/principal/postre para comidas y cenas, establecer comidas libres, filtrar recetarios y categorías, y limitar las repeticiones.
- Crear una lista de la compra calculada a partir del menú y marcar los productos ya adquiridos.
- Registrar ingestas y ajustar las raciones previstas a los objetivos diarios de cada perfil.
- Recibir un recordatorio los viernes cuando no exista un menú para la semana siguiente.

### Funcionamiento

1. **Configurar perfiles.** NutriNest calcula la edad desde la fecha de nacimiento, estima la tasa metabólica basal (TMB), aplica la actividad para estimar el gasto energético diario total (TDEE) y ajusta el resultado al objetivo elegido. La proteína se calcula según el objetivo y el peso, las grasas reciben aproximadamente el 27 % de las calorías y los carbohidratos cubren la energía restante.
2. **Crear recetas.** Las recetas usan gramos como unidad canónica de cálculo. La aplicación suma la nutrición de cada ingrediente y permite convertir porciones y medidas habituales para mostrarlas o introducirlas.
3. **Configurar la semana.** Se eligen franjas de comidas, reparto calórico, tipo de menú, platos por comida, comidas libres, filtros de recetas y comidas bloqueadas.
4. **Generar el plan.** El motor local descarta recetas incompatibles con las preferencias dietéticas y alergias declaradas. Después puntúa las candidatas por afinidad con la comida o el plato, variedad, uso reciente, repeticiones y posible aprovechamiento de ingredientes sobrantes. Las raciones escalan cada receta al presupuesto calórico correspondiente.
5. **Comprar, cocinar y registrar.** Las cantidades se agregan en una lista de la compra. Se pueden registrar ingestas para que el plan siga siendo útil cuando la realidad difiere de lo previsto.

El generador prioriza la variedad y no supera el límite de repeticiones configurado. Si los filtros dejan muy pocas recetas, informa del problema en vez de crear silenciosamente un plan incompleto.

### Privacidad y datos

NutriNest se ha diseñado con un enfoque local-first:

- Los perfiles, recetas, menús, ingestas y listas de la compra se guardan en una base de datos Room/SQLite del dispositivo.
- La base de datos alimentaria incluida se importa localmente desde los recursos de la aplicación; la app no necesita una cuenta ni un backend propio.
- El manifiesto Android solo solicita el permiso de notificaciones, destinado al recordatorio semanal opcional.
- La copia de seguridad y restauración a nivel de dispositivo dependen de Android y de los ajustes de la persona usuaria.

El filtrado de alergias no es una garantía de seguridad clínica. Usa coincidencias locales y conservadoras sobre la información registrada en la aplicación. La persona usuaria sigue siendo responsable de comprobar ingredientes, etiquetas, contaminación cruzada e indicaciones de profesionales cualificados.

### Contenido incluido

La app incluye una base de datos alimentaria local derivada de BEDCA y una biblioteca de 250 recetas de una ración. Las recetas incluidas son adaptaciones originales y concisas; sus nombres e ideas de ingredientes se contrastaron con fuentes públicas culinarias y sanitarias de confianza. Consulta las fuentes y normas de contenido en [docs/recipe-library-sources.md](docs/recipe-library-sources.md).

### Tecnología y arquitectura

| Área | Implementación |
| --- | --- |
| Plataforma | Android (SDK mínimo 26, SDK objetivo 36) |
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose con Material 3 |
| Navegación y estado | Navigation Compose, ViewModels y Kotlin Flow |
| Persistencia local | Room / SQLite con esquemas de migración versionados |
| Segundo plano | WorkManager para el recordatorio semanal |
| Imágenes | Coil con almacenamiento local de imágenes |
| Compilación | Gradle, Android Gradle Plugin y Kotlin Symbol Processing (KSP) |
| Calidad | Pruebas unitarias de nutrición, planificación, normalización de texto, recordatorios y base de datos |

El código se organiza por responsabilidades:

- `ui/`: pantallas Compose y ViewModels.
- `domain/`: reglas de nutrición y planificación independientes y comprobables.
- `data/`: entidades Room, DAOs, migraciones, importadores, datos iniciales y repositorios.
- `notifications/`: recordatorio semanal del menú.
- `tools/`: comprobaciones del proyecto y utilidades para recursos alimentarios.

La calculadora nutricional y el generador de menús son componentes Kotlin puros. Sus reglas se pueden probar independientemente y constituyen una base útil para futuras interfaces o servicios.

### Empezar como desarrollador

#### Requisitos

- Android Studio con una instalación compatible del SDK de Android.
- JDK 17 o superior (Android Gradle Plugin 9 requiere JDK 17).
- Un emulador o dispositivo con Android 8.0 (API 26) o superior.

#### Compilar y ejecutar

1. Clona el repositorio.
2. Abre la raíz del proyecto en Android Studio.
3. Espera a que Gradle se sincronice e instala las plataformas SDK solicitadas.
4. Selecciona un emulador o dispositivo y ejecuta la configuración `app`.

En Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

En macOS o Linux:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Para ejecutar las pruebas unitarias de JVM:

```powershell
.\gradlew.bat testDebugUnitTest
```

Las pruebas de instrumentación en un dispositivo conectado se ejecutan con `connectedDebugAndroidTest`.

### Valor para organizaciones

NutriNest es un proyecto de portfolio y una demostración de cómo se puede diseñar un producto de salud y bienestar de consumo con privacidad, resiliencia sin conexión y reglas explicables en lugar de recomendaciones opacas. Demuestra:

- Pensamiento de producto para hogares con necesidades nutricionales compartidas e individuales.
- Desarrollo Android nativo con una interfaz declarativa moderna y persistencia local.
- Lógica de cálculo y recomendación explícita y comprobable.
- Experiencia bilingüe para usuarios de español e inglés.
- Tratamiento cuidadoso de preferencias dietéticas, avisos de alergias, metadatos de ingredientes y límites de seguridad no clínicos.
- Una base que se podría evaluar para futuras integraciones, flujos profesionales, analítica o capacidades cloud mediante un acuerdo escrito independiente.

Este repositorio no se ofrece para reutilización, despliegue, marca blanca ni integración comercial. Consulta la licencia.

### Estado del proyecto

NutriNest es un proyecto personal de portfolio activo de **Alejandro Zamorano Méndez** bajo **Zame**. Las funciones, los datos alimentarios, las fórmulas y las interfaces pueden cambiar a medida que evolucione el proyecto.

### Licencia y uso permitido

Este repositorio es **propietario y solo está disponible para consultar el código fuente**. Todos los derechos están reservados a Alejandro Zamorano Méndez / Zame. Consultar el repositorio no concede permiso para usar, copiar, modificar, distribuir, publicar, sublicenciar, desplegar, entrenar modelos con él ni crear obras derivadas de ninguna de sus partes.

Todo uso personal, educativo, comercial, interno o empresarial requiere autorización previa y por escrito del titular de los derechos. Consulta los términos completos en [LICENSE](LICENSE).

### Autor

Creado por **Alejandro Zamorano Méndez** · **Zame**
