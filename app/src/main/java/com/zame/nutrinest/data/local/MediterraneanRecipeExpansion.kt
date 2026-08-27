package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories

/** Source-checked Spanish, Italian and Greek additions for library version 3. */
internal object MediterraneanRecipeExpansion {
    val recipes = listOf(
        // Breakfasts
        expandedRecipe(
            "med-ricotta-strawberry-toast", "Ricotta Strawberry Almond Toast",
            "Tostada de requesón, fresa y almendra", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(2163, 75), bedca(2517, 90), bedca(2225, 100), bedca(896, 12), bedca(2643, 6)),
            instruction("Toast the bread until the surface is crisp but the centre remains tender.", "Tuesta el pan hasta que la superficie esté crujiente pero el centro siga tierno."),
            instruction("Spread the cottage cheese over the warm toast in an even layer.", "Reparte el requesón sobre la tostada caliente en una capa uniforme."),
            instruction("Slice the strawberries and arrange them over the cheese.", "Corta las fresas en láminas y colócalas sobre el queso."),
            instruction("Finish with chopped almonds and a thin thread of honey.", "Termina con la almendra picada y un hilo fino de miel.")
        ),
        expandedRecipe(
            "med-spinach-tomato-eggs", "Greek-Style Spinach Tomato Eggs",
            "Huevos con espinacas y tomate al estilo griego", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1173, 90), bedca(2125, 35), bedca(1282, 100), bedca(2421, 120), bedca(2382, 35), bedca(2543, 5)),
            instruction("Dice the tomato and onion and roughly chop the washed spinach.", "Corta el tomate y la cebolla en dados y pica las espinacas lavadas."),
            instruction("Heat the oil in a non-stick pan and soften the onion for 3 to 4 minutes.", "Calienta el aceite en una sartén antiadherente y pocha la cebolla durante 3 o 4 minutos."),
            instruction("Add the tomato and cook until its released liquid has mostly evaporated.", "Añade el tomate y cocina hasta que se haya evaporado casi todo el líquido que suelte."),
            instruction("Fold in the spinach and cook just until wilted.", "Incorpora las espinacas y cocínalas solo hasta que se ablanden."),
            instruction("Beat the egg whites and yolk, pour them into the pan and stir gently until softly set.", "Bate las claras y la yema, viértelas en la sartén y remueve suavemente hasta que cuajen sin secarse.")
        ),
        expandedRecipe(
            "med-orange-semolina-porridge", "Orange Cinnamon Semolina Porridge",
            "Gachas de sémola con naranja y canela", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1084, 45), bedca(915, 220), bedca(2468, 45), bedca(2477, 1), bedca(896, 10)),
            instruction("Warm the milk in a small saucepan until steaming but not boiling.", "Calienta la leche en un cazo hasta que desprenda vapor, sin que llegue a hervir."),
            instruction("Rain in the semolina while whisking continuously to prevent lumps.", "Añade la sémola en forma de lluvia mientras bates sin parar para evitar grumos."),
            instruction("Lower the heat and cook for 4 to 5 minutes, stirring, until thick and smooth.", "Baja el fuego y cocina de 4 a 5 minutos, removiendo, hasta que quede espesa y lisa."),
            instruction("Remove from the heat and mix in the orange juice and cinnamon.", "Retira del fuego y mezcla el zumo de naranja y la canela."),
            instruction("Pour into a bowl and scatter the chopped almonds over the top.", "Sirve en un cuenco y reparte por encima la almendra picada.")
        ),
        expandedRecipe(
            "med-chickpea-egg-breakfast", "Chickpea Spinach Breakfast Skillet",
            "Sartén de garbanzos, espinacas y huevo", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(2191, 100), bedca(1282, 90), bedca(2421, 110), bedca(1173, 70), bedca(2125, 30), bedca(2543, 4)),
            instruction("Drain and rinse the chickpeas, dice the tomato and chop the spinach.", "Escurre y enjuaga los garbanzos, corta el tomate y pica las espinacas."),
            instruction("Warm the oil in a small skillet and cook the tomato until it begins to collapse.", "Calienta el aceite en una sartén pequeña y cocina el tomate hasta que empiece a deshacerse."),
            instruction("Add the chickpeas and spinach and cook until the spinach has wilted and the chickpeas are hot.", "Añade los garbanzos y las espinacas y cocina hasta que estas se ablanden y los garbanzos estén calientes."),
            instruction("Make a hollow in the mixture and add the egg white and yolk without breaking the yolk.", "Abre un hueco en la mezcla y añade la clara y la yema sin romper esta última."),
            instruction("Cover and cook gently until the white is set and the yolk reaches the preferred doneness.", "Tapa y cocina a fuego suave hasta que la clara cuaje y la yema quede al punto deseado.")
        ),
        expandedRecipe(
            "med-courgette-cottage-omelette", "Courgette Cottage Cheese Omelette",
            "Tortilla de calabacín y requesón", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1173, 100), bedca(2125, 35), bedca(2376, 120), bedca(2517, 60), bedca(2382, 30), bedca(2543, 4)),
            instruction("Grate the courgette, squeeze out excess moisture and finely chop the onion.", "Ralla el calabacín, escurre el exceso de agua y pica fina la cebolla."),
            instruction("Soften the onion in the oil, add the courgette and cook until no liquid remains in the pan.", "Pocha la cebolla en el aceite, añade el calabacín y cocina hasta que no quede líquido en la sartén."),
            instruction("Beat the egg whites and yolk and pour them evenly around the vegetables.", "Bate las claras y la yema y viértelas de forma uniforme sobre las verduras."),
            instruction("Dot the cottage cheese over the surface and cook over low heat until almost set.", "Reparte el requesón por la superficie y cocina a fuego bajo hasta que esté casi cuajada."),
            instruction("Fold the omelette in half and rest it for 1 minute before serving.", "Dobla la tortilla por la mitad y déjala reposar 1 minuto antes de servir.")
        ),
        expandedRecipe(
            "med-grape-walnut-muesli", "Grape Walnut Yogurt Muesli",
            "Muesli de yogur, uvas y nueces", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1010, 45), bedca(2530, 170), bedca(1017, 100), bedca(2201, 14), bedca(2643, 5)),
            instruction("Toast the oats in a dry pan for 3 to 4 minutes, stirring until they smell nutty, then cool them.", "Tuesta la avena en una sartén seca durante 3 o 4 minutos, removiendo hasta que desprenda aroma, y deja que se enfríe."),
            instruction("Halve the grapes and roughly chop the walnuts.", "Parte las uvas por la mitad y trocea las nueces."),
            instruction("Spoon the yogurt into a bowl and cover it with the toasted oats and grapes.", "Pon el yogur en un cuenco y cúbrelo con la avena tostada y las uvas."),
            instruction("Add the walnuts and honey immediately before eating so the oats remain crisp.", "Añade las nueces y la miel justo antes de comer para que la avena conserve su textura.")
        ),

        // Main dishes
        expandedRecipe(
            "med-spanish-meatballs", "Spanish Beef Meatballs in Tomato Sauce",
            "Albóndigas de ternera en salsa de tomate", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2680, 150), bedca(2171, 18), bedca(1173, 25), bedca(481, 180), bedca(2382, 45), bedca(2543, 6)),
            instruction("Mix the beef, breadcrumbs and egg white only until combined; overworking makes firm meatballs.", "Mezcla la ternera, el pan rallado y la clara solo hasta integrar; amasar de más endurece las albóndigas."),
            instruction("With damp hands, shape equal walnut-sized meatballs and chill them for 10 minutes.", "Con las manos húmedas, forma albóndigas iguales del tamaño de una nuez y enfríalas 10 minutos."),
            instruction("Heat half the oil in a wide pan and brown the meatballs in batches; transfer them to a plate.", "Calienta la mitad del aceite en una sartén amplia y dora las albóndigas por tandas; pásalas a un plato."),
            instruction("Add the remaining oil and onion and cook over medium-low heat until soft and translucent.", "Añade el resto del aceite y la cebolla y cocina a fuego medio-bajo hasta que esté tierna y transparente."),
            instruction("Pour in the tomato, simmer for 8 minutes, then return the meatballs and cover.", "Incorpora el tomate, cuece 8 minutos y devuelve las albóndigas a la sartén; tapa."),
            instruction("Simmer gently for 12 to 15 minutes, turning once, until the centres are fully cooked.", "Cuece suavemente de 12 a 15 minutos, dándoles una vuelta, hasta que el centro esté bien hecho.")
        ),
        expandedRecipe(
            "med-pollo-al-ajillo-rice", "Spanish Garlic Chicken with Rice",
            "Pollo al ajillo con arroz", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 160), bedca(2660, 65), bedca(2361, 5), bedca(411, 18), bedca(2592, 2), bedca(2543, 7)),
            instruction("Cook the rice in measured simmering water until tender; cover and leave it to rest off the heat.", "Cuece el arroz con el agua medida a fuego suave hasta que esté tierno; tápalo y déjalo reposar fuera del fuego."),
            instruction("Cut the chicken into even pieces and pat them dry so they brown rather than steam.", "Corta el pollo en trozos iguales y sécalos para que se doren en lugar de cocerse al vapor."),
            instruction("Heat the oil in a wide pan and brown the chicken on all sides over medium-high heat.", "Calienta el aceite en una sartén amplia y dora el pollo por todos los lados a fuego medio-alto."),
            instruction("Lower the heat, add sliced garlic and paprika and stir for 30 seconds without letting the garlic burn.", "Baja el fuego, añade el ajo laminado y el pimentón y remueve 30 segundos sin dejar que el ajo se queme."),
            instruction("Add a splash of water, cover and cook until the chicken reaches a safe doneness.", "Añade un poco de agua, tapa y cocina hasta que el pollo esté completamente hecho."),
            instruction("Stir in the lemon juice and serve the garlicky pan juices over the rested rice.", "Incorpora el zumo de limón y sirve los jugos de ajo de la sartén sobre el arroz reposado.")
        ),
        expandedRecipe(
            "med-basque-hake", "Basque-Style Hake with Peppers",
            "Merluza con pimientos al estilo vasco", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 190), bedca(2409, 110), bedca(481, 150), bedca(2382, 45), bedca(2361, 3), bedca(2543, 6)),
            instruction("Thaw the hake if necessary, pat it dry and cut the pepper and onion into thin strips.", "Descongela la merluza si es necesario, sécala y corta el pimiento y la cebolla en tiras finas."),
            instruction("Soften the onion and pepper in the oil over medium-low heat for 8 to 10 minutes.", "Pocha la cebolla y el pimiento en el aceite a fuego medio-bajo durante 8 o 10 minutos."),
            instruction("Add the garlic, stir for 30 seconds, then add the tomato and simmer until slightly reduced.", "Añade el ajo, remueve 30 segundos, incorpora el tomate y cuece hasta que reduzca ligeramente."),
            instruction("Place the hake on the sauce, cover the pan and cook gently for 6 to 8 minutes.", "Coloca la merluza sobre la salsa, tapa la sartén y cocina suavemente de 6 a 8 minutos."),
            instruction("Remove the pan when the thickest part is opaque and flakes under light pressure.", "Retira la sartén cuando la parte más gruesa esté opaca y se separe en lascas al presionarla suavemente.")
        ),
        expandedRecipe(
            "med-spanish-chickpea-spinach", "Spanish Chickpeas with Spinach",
            "Garbanzos con espinacas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2191, 180), bedca(1282, 160), bedca(2163, 25), bedca(2361, 4), bedca(2571, 2), bedca(2543, 7)),
            instruction("Toast the bread in half the oil until golden, then remove it from the pan.", "Tuesta el pan en la mitad del aceite hasta que esté dorado y retíralo de la sartén."),
            instruction("Cook the sliced garlic briefly in the same pan without browning it deeply.", "Cocina brevemente el ajo laminado en la misma sartén sin dorarlo en exceso."),
            instruction("Crush the toast, garlic and cumin with a spoonful of water to make a coarse paste.", "Machaca el pan, el ajo y el comino con una cucharada de agua hasta obtener una pasta gruesa."),
            instruction("Wilt the spinach in the remaining oil, working in batches if necessary.", "Saltea las espinacas en el aceite restante, por tandas si es necesario, hasta que se ablanden."),
            instruction("Add the drained chickpeas and the bread paste and simmer with a splash of water for 8 minutes.", "Añade los garbanzos escurridos y el majado de pan y cuece con un poco de agua durante 8 minutos."),
            instruction("Stir carefully so the chickpeas stay whole and serve when the sauce clings to them.", "Remueve con cuidado para mantener enteros los garbanzos y sirve cuando la salsa quede adherida a ellos.")
        ),
        expandedRecipe(
            "med-chicken-cacciatore", "Italian Chicken Cacciatore",
            "Pollo italiano a la cazadora", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 165), bedca(481, 190), bedca(2384, 130), bedca(2382, 45), bedca(2214, 25), bedca(2543, 6)),
            instruction("Cut the chicken evenly and slice the mushrooms and onion.", "Corta el pollo de forma uniforme y lamina los champiñones y la cebolla."),
            instruction("Brown the chicken in the oil over medium-high heat and transfer it to a plate.", "Dora el pollo en el aceite a fuego medio-alto y pásalo a un plato."),
            instruction("Cook the onion until soft, add the mushrooms and continue until their moisture evaporates.", "Pocha la cebolla, añade los champiñones y sigue cocinando hasta que se evapore su agua."),
            instruction("Add the tomato and olives and scrape the browned flavour from the bottom of the pan.", "Añade el tomate y las aceitunas y despega del fondo de la sartén los jugos dorados."),
            instruction("Return the chicken, cover and simmer gently for 15 minutes or until safely cooked through.", "Devuelve el pollo, tapa y cuece suavemente 15 minutos o hasta que esté completamente hecho."),
            instruction("Uncover for the final 2 minutes if the sauce needs to thicken.", "Destapa durante los últimos 2 minutos si la salsa necesita espesar.")
        ),
        expandedRecipe(
            "med-turkey-bolognese", "Turkey Bolognese Whole-Wheat Pasta",
            "Pasta integral con boloñesa de pavo", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 150), bedca(1074, 75), bedca(481, 190), bedca(2382, 45), bedca(2424, 55), bedca(2543, 5)),
            instruction("Finely chop the onion and carrot so they melt into the sauce.", "Pica muy finas la cebolla y la zanahoria para que se integren en la salsa."),
            instruction("Heat the oil and cook the vegetables over medium-low heat for 7 minutes.", "Calienta el aceite y cocina las verduras a fuego medio-bajo durante 7 minutos."),
            instruction("Add the turkey, break it into small pieces and cook until no raw colour remains.", "Añade el pavo, sepáralo en trozos pequeños y cocina hasta que pierda el color crudo."),
            instruction("Add the tomato and simmer uncovered for 15 to 20 minutes, stirring occasionally.", "Incorpora el tomate y cuece destapado de 15 a 20 minutos, removiendo de vez en cuando."),
            instruction("Meanwhile cook the pasta in boiling water until al dente and reserve a little cooking water.", "Mientras tanto, cuece la pasta en agua hirviendo hasta que esté al dente y reserva un poco del agua."),
            instruction("Toss the drained pasta with the sauce, loosening it with reserved water if needed.", "Mezcla la pasta escurrida con la salsa y aligérala con el agua reservada si hace falta.")
        ),
        expandedRecipe(
            "med-pasta-puttanesca", "Whole-Wheat Pasta Puttanesca",
            "Pasta integral a la puttanesca", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1074, 80), bedca(481, 190), bedca(2129, 15), bedca(2214, 30), bedca(2361, 3), bedca(2543, 5)),
            instruction("Bring a pan of water to the boil and cook the pasta until al dente.", "Lleva agua a ebullición y cuece la pasta hasta que esté al dente."),
            instruction("While it cooks, warm the oil and gently dissolve the chopped anchovy with the sliced garlic.", "Mientras se cuece, calienta el aceite y deshaz suavemente la anchoa picada con el ajo laminado."),
            instruction("Add the tomato and simmer briskly for 8 to 10 minutes until concentrated.", "Añade el tomate y cuece a fuego vivo de 8 a 10 minutos hasta que se concentre."),
            instruction("Slice the olives, add them to the sauce and cook for 2 minutes.", "Corta las aceitunas, incorpóralas a la salsa y cocina 2 minutos."),
            instruction("Transfer the pasta directly into the sauce and toss until every strand is coated.", "Pasa la pasta directamente a la salsa y mézclala hasta que quede bien cubierta."),
            instruction("Add a spoonful of pasta water only if the sauce is too tight, then serve at once.", "Añade una cucharada del agua de la pasta solo si la salsa está demasiado espesa y sirve enseguida.")
        ),
        expandedRecipe(
            "med-cod-livornese", "Cod Livornese with Tomato and Olives",
            "Bacalao a la livornesa con tomate y aceitunas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(795, 180), bedca(481, 190), bedca(2214, 30), bedca(2382, 40), bedca(2361, 3), bedca(2543, 6)),
            instruction("Pat the cod dry and check carefully for any remaining bones.", "Seca el bacalao y comprueba con cuidado que no queden espinas."),
            instruction("Soften the onion in the oil for 6 minutes, then add the garlic for 30 seconds.", "Pocha la cebolla en el aceite durante 6 minutos y añade el ajo durante 30 segundos."),
            instruction("Add the tomato and simmer uncovered for 10 minutes to build the sauce.", "Añade el tomate y cuece destapado durante 10 minutos para formar la salsa."),
            instruction("Stir in the olives and place the cod in the pan in a single layer.", "Incorpora las aceitunas y coloca el bacalao en una sola capa."),
            instruction("Cover and cook gently for 6 to 8 minutes, spooning sauce over the fish once.", "Tapa y cocina suavemente de 6 a 8 minutos, cubriendo el pescado con salsa una vez."),
            instruction("Take the pan off the heat as soon as the cod separates into moist flakes.", "Retira la sartén del fuego en cuanto el bacalao se separe en lascas jugosas.")
        ),
        expandedRecipe(
            "med-pasta-fagioli", "Tuscan Pasta and White Bean Soup",
            "Sopa toscana de pasta y alubias", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1117, 55), bedca(2199, 100), bedca(481, 140), bedca(2382, 40), bedca(2366, 45), bedca(792, 90)),
            instruction("Finely dice the onion and celery and rinse the cooked beans.", "Pica finos la cebolla y el apio y enjuaga las alubias cocidas."),
            instruction("Cook the onion and celery in a saucepan with a splash of the stock until softened.", "Cocina la cebolla y el apio en un cazo con un poco de caldo hasta que estén tiernos."),
            instruction("Add the tomato, beans, remaining stock and enough water to make a loose soup; simmer for 10 minutes.", "Añade el tomate, las alubias, el resto del caldo y agua suficiente para obtener una sopa ligera; cuece 10 minutos."),
            instruction("Mash a spoonful of beans against the side of the pan to give the broth body.", "Machaca una cucharada de alubias contra la pared del cazo para dar cuerpo al caldo."),
            instruction("Add the pasta and cook at a steady simmer until al dente, stirring often to prevent sticking.", "Añade la pasta y cuece a fuego constante hasta que esté al dente, removiendo a menudo para que no se pegue."),
            instruction("Rest the soup for 2 minutes and add hot water if it has thickened beyond a spoonable consistency.", "Deja reposar la sopa 2 minutos y añade agua caliente si ha quedado demasiado espesa.")
        ),
        expandedRecipe(
            "med-gnocchi-sorrentina", "Gnocchi-Style Potato Dumplings alla Sorrentina",
            "Ñoquis de patata a la sorrentina", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2403, 230), bedca(1113, 55), bedca(481, 160), bedca(2510, 55), bedca(2473, 6), bedca(2543, 4)),
            instruction("Boil the potatoes whole until a knife enters easily, then peel and mash them while hot.", "Cuece las patatas enteras hasta que un cuchillo entre con facilidad; pélalas y machácalas en caliente."),
            instruction("Let the steam escape for 5 minutes, add the flour and mix lightly into a soft dough without over-kneading.", "Deja salir el vapor 5 minutos, añade la harina y mezcla suavemente hasta formar una masa blanda sin amasar de más."),
            instruction("Roll the dough into ropes on a floured surface, cut small dumplings and dust them lightly with flour.", "Forma cilindros sobre una superficie enharinada, corta ñoquis pequeños y espolvoréalos ligeramente con harina."),
            instruction("Drop the dumplings into gently boiling water in batches and lift them out when they float.", "Cuece los ñoquis por tandas en agua con hervor suave y retíralos cuando suban a la superficie."),
            instruction("Warm the tomato with the oil and basil and fold in the drained gnocchi.", "Calienta el tomate con el aceite y la albahaca e incorpora los ñoquis escurridos."),
            instruction("Transfer to a baking dish, cover with mozzarella and bake at 220°C until bubbling and browned.", "Pasa a una fuente, cubre con mozzarella y hornea a 220 °C hasta que burbujee y se dore.")
        ),
        expandedRecipe(
            "med-greek-souvlaki-bowl", "Greek Chicken Souvlaki Bowl",
            "Bol griego de souvlaki de pollo", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 160), bedca(1084, 60), bedca(2407, 90), bedca(2421, 100), bedca(2520, 55), bedca(411, 18)),
            instruction("Cut the chicken into equal cubes and coat it with half the lemon juice; marinate for 15 minutes.", "Corta el pollo en dados iguales, mézclalo con la mitad del limón y déjalo marinar 15 minutos."),
            instruction("Cover the semolina with its measured amount of boiling water, cover and leave for 5 minutes.", "Cubre la sémola con la cantidad medida de agua hirviendo, tapa y deja reposar 5 minutos."),
            instruction("Thread the chicken onto skewers or arrange it in one layer in a hot grill pan.", "Ensarta el pollo o colócalo en una sola capa en una plancha caliente."),
            instruction("Cook, turning regularly, until browned on every side and safely cooked in the centre.", "Cocina, girándolo con frecuencia, hasta que esté dorado por todos los lados y hecho en el centro."),
            instruction("Dice the cucumber and tomato and mix the yogurt with the remaining lemon.", "Corta el pepino y el tomate y mezcla el yogur con el limón restante."),
            instruction("Fluff the semolina and assemble it with the vegetables, chicken and lemon yogurt.", "Suelta la sémola con un tenedor y monta el bol con las verduras, el pollo y el yogur al limón.")
        ),
        expandedRecipe(
            "med-soutzoukakia", "Greek Cumin Meatballs with Rice",
            "Soutzoukakia griegas con arroz", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2680, 150), bedca(2171, 18), bedca(2571, 3), bedca(481, 180), bedca(2382, 35), bedca(2660, 60)),
            instruction("Cook the rice in measured simmering water, cover it and leave it to rest.", "Cuece el arroz con el agua medida, tápalo y déjalo reposar."),
            instruction("Mix the beef, breadcrumbs and cumin and shape short oval meatballs with damp hands.", "Mezcla la ternera, el pan rallado y el comino y forma albóndigas ovaladas con las manos húmedas."),
            instruction("Brown the meatballs in a non-stick pan on all sides and transfer them to a plate.", "Dora las albóndigas por todos los lados en una sartén antiadherente y resérvalas."),
            instruction("Soften the onion in the same pan, add the tomato and simmer for 8 minutes.", "Pocha la cebolla en la misma sartén, añade el tomate y cuece 8 minutos."),
            instruction("Return the meatballs, cover and simmer for 12 minutes until the centres are cooked.", "Devuelve las albóndigas, tapa y cuece 12 minutos hasta que el centro esté hecho."),
            instruction("Spoon the sauce and meatballs over the rested rice.", "Sirve la salsa y las albóndigas sobre el arroz reposado.")
        ),
        expandedRecipe(
            "med-greek-baked-beans", "Greek-Style Baked White Beans",
            "Alubias blancas al horno al estilo griego", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2199, 150), bedca(481, 180), bedca(2382, 50), bedca(2424, 65), bedca(2366, 45), bedca(2543, 6)),
            instruction("Heat the oven to 190°C and rinse and drain the cooked beans.", "Calienta el horno a 190 °C y enjuaga y escurre las alubias cocidas."),
            instruction("Finely dice the onion, carrot and celery so they cook at the same rate.", "Pica finos la cebolla, la zanahoria y el apio para que se cocinen al mismo ritmo."),
            instruction("Cook the vegetables in the oil for 8 minutes until softened but not browned.", "Cocina las verduras en el aceite durante 8 minutos hasta que estén tiernas sin dorarse."),
            instruction("Add the tomato and simmer for 5 minutes, then fold in the beans without crushing them.", "Añade el tomate y cuece 5 minutos; incorpora las alubias sin aplastarlas."),
            instruction("Transfer to a shallow dish and add enough hot water for the sauce to sit level with the beans.", "Pasa a una fuente baja y añade agua caliente hasta que la salsa llegue al nivel de las alubias."),
            instruction("Bake uncovered for 25 to 30 minutes until the top is lightly caramelised and the sauce is thick.", "Hornea destapado de 25 a 30 minutos hasta que la superficie se caramelice ligeramente y la salsa espese.")
        ),
        expandedRecipe(
            "med-spanakorizo", "Greek Spinach Lemon Rice",
            "Spanakorizo griego de espinacas y limón", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2660, 70), bedca(1282, 200), bedca(2382, 45), bedca(411, 20), bedca(2543, 7), bedca(2520, 45)),
            instruction("Wash the spinach thoroughly, drain it and chop any large leaves.", "Lava bien las espinacas, escúrrelas y corta las hojas grandes."),
            instruction("Soften the onion in the oil over medium-low heat for 6 minutes.", "Pocha la cebolla en el aceite a fuego medio-bajo durante 6 minutos."),
            instruction("Add the spinach in batches and stir until all of it has wilted.", "Añade las espinacas por tandas y remueve hasta que todas se hayan ablandado."),
            instruction("Stir in the rice and measured hot water, cover and simmer gently until the rice is tender.", "Incorpora el arroz y el agua caliente medida, tapa y cuece suavemente hasta que el arroz esté tierno."),
            instruction("Rest off the heat for 5 minutes, then loosen the grains with a fork and add lemon juice.", "Deja reposar fuera del fuego 5 minutos, suelta los granos con un tenedor y añade el limón."),
            instruction("Serve with the yogurt spooned on top rather than stirred into the hot rice.", "Sirve con el yogur por encima, sin mezclarlo con el arroz caliente.")
        ),
        expandedRecipe(
            "med-salmon-lemon-potato", "Greek Lemon Oregano Salmon and Potatoes",
            "Salmón griego al limón con patatas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2626, 150), bedca(2403, 220), bedca(411, 22), bedca(2598, 2), bedca(2361, 3), bedca(2543, 7)),
            instruction("Heat the oven to 210°C and cut the potatoes into 2 cm pieces.", "Calienta el horno a 210 °C y corta las patatas en trozos de 2 cm."),
            instruction("Toss the potatoes with two-thirds of the oil, half the lemon, oregano and sliced garlic.", "Mezcla las patatas con dos tercios del aceite, la mitad del limón, el orégano y el ajo laminado."),
            instruction("Roast for 25 minutes, turning once, until the potatoes are nearly tender.", "Asa 25 minutos, dándoles una vuelta, hasta que las patatas estén casi tiernas."),
            instruction("Move the potatoes aside, place the salmon skin-side down and brush it with the remaining oil and lemon.", "Aparta las patatas, coloca el salmón con la piel hacia abajo y úntalo con el aceite y el limón restantes."),
            instruction("Roast for another 10 to 13 minutes, depending on thickness, until the salmon flakes but stays moist.", "Asa otros 10 o 13 minutos, según el grosor, hasta que el salmón se separe en lascas pero siga jugoso."),
            instruction("Rest the salmon for 2 minutes before serving with the crisp-edged potatoes.", "Deja reposar el salmón 2 minutos antes de servirlo con las patatas doradas.")
        ),
        expandedRecipe(
            "med-spanish-lentil-stew", "Spanish Vegetable Lentil Stew",
            "Lentejas guisadas con verduras", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1065, 75), bedca(2403, 120), bedca(2424, 65), bedca(481, 150), bedca(2382, 45), bedca(2592, 2)),
            instruction("Rinse the lentils and cut the potato and carrot into equal small pieces.", "Enjuaga las lentejas y corta la patata y la zanahoria en trozos pequeños del mismo tamaño."),
            instruction("Put the lentils, onion, carrot and tomato in a saucepan and cover with plenty of cold water.", "Pon las lentejas, la cebolla, la zanahoria y el tomate en una cazuela y cubre con abundante agua fría."),
            instruction("Bring slowly to a simmer, skim any foam and cook uncovered for 20 minutes.", "Lleva lentamente a hervor suave, retira la espuma y cuece destapado 20 minutos."),
            instruction("Add the potato and paprika and continue simmering until both lentils and potato are tender.", "Añade la patata y el pimentón y sigue cociendo hasta que las lentejas y la patata estén tiernas."),
            instruction("Add hot water during cooking if needed; the finished stew should be spoonable, not dry.", "Añade agua caliente durante la cocción si hace falta; el guiso debe quedar caldoso, no seco."),
            instruction("Rest for 5 minutes so the broth thickens naturally before serving.", "Deja reposar 5 minutos para que el caldo espese de forma natural antes de servir.")
        ),
        expandedRecipe(
            "med-courgette-lasagna", "Courgette and Cottage Cheese Lasagna",
            "Lasaña de calabacín y requesón", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1117, 70), bedca(2376, 180), bedca(481, 180), bedca(2517, 90), bedca(2510, 40), bedca(2543, 5)),
            instruction("Heat the oven to 200°C and slice the courgette into thin, even rounds.", "Calienta el horno a 200 °C y corta el calabacín en rodajas finas y uniformes."),
            instruction("Brush the courgette with the oil and roast it for 12 minutes to remove excess moisture.", "Pincela el calabacín con el aceite y ásalo 12 minutos para eliminar el exceso de agua."),
            instruction("Boil the pasta only until flexible, then drain and separate the sheets so they do not stick.", "Cuece la pasta solo hasta que sea flexible, escúrrela y separa las láminas para que no se peguen."),
            instruction("Spread a little tomato in a baking dish and layer pasta, courgette, cottage cheese and more tomato.", "Reparte un poco de tomate en una fuente y alterna pasta, calabacín, requesón y más tomate."),
            instruction("Repeat the layers, finish with tomato and mozzarella and cover the dish.", "Repite las capas, termina con tomate y mozzarella y cubre la fuente."),
            instruction("Bake for 20 minutes, uncover and bake for 8 to 10 minutes more until browned.", "Hornea 20 minutos, destapa y hornea otros 8 o 10 minutos hasta dorar."),
            instruction("Rest the lasagna for 8 minutes before cutting so the layers stay together.", "Deja reposar la lasaña 8 minutos antes de cortarla para que las capas se mantengan unidas.")
        ),
        expandedRecipe(
            "med-prawn-saganaki", "Greek Prawns with Tomato and Cheese",
            "Gambas griegas con tomate y queso", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(581, 170), bedca(481, 180), bedca(2382, 40), bedca(2517, 55), bedca(2598, 2), bedca(2543, 6)),
            instruction("Thaw the prawns if needed, drain them well and pat them dry.", "Descongela las gambas si hace falta, escúrrelas bien y sécalas."),
            instruction("Soften the onion in the oil for 6 minutes, add the tomato and oregano and simmer for 10 minutes.", "Pocha la cebolla en el aceite 6 minutos, añade el tomate y el orégano y cuece 10 minutos."),
            instruction("Increase the heat, add the prawns in one layer and cook for 1 minute.", "Sube el fuego, añade las gambas en una sola capa y cocina 1 minuto."),
            instruction("Turn the prawns and cook only until they are opaque and curled, about 1 to 2 minutes more.", "Da la vuelta a las gambas y cocina solo hasta que estén opacas y curvadas, 1 o 2 minutos más."),
            instruction("Remove from the heat, dot the cottage cheese over the sauce and cover for 2 minutes.", "Retira del fuego, reparte el requesón sobre la salsa y tapa 2 minutos."),
            instruction("Serve directly from the pan while the prawns remain tender.", "Sirve directamente de la sartén mientras las gambas siguen tiernas.")
        ),
        expandedRecipe(
            "med-beef-rice-stuffed-pepper", "Mediterranean Beef and Rice Stuffed Pepper",
            "Pimiento mediterráneo relleno de ternera y arroz", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2409, 220), bedca(2680, 125), bedca(2660, 45), bedca(481, 140), bedca(2382, 40), bedca(2510, 25)),
            instruction("Heat the oven to 190°C, halve the pepper lengthwise and remove the seeds and white membrane.", "Calienta el horno a 190 °C, parte el pimiento a lo largo y retira las semillas y la parte blanca."),
            instruction("Bake the empty pepper halves cut-side up for 12 minutes while preparing the filling.", "Hornea las mitades vacías con el corte hacia arriba durante 12 minutos mientras preparas el relleno."),
            instruction("Cook the rice until just tender and drain it well.", "Cuece el arroz hasta que esté justo tierno y escúrrelo bien."),
            instruction("Brown the beef with the onion, breaking up the meat, then add the tomato and simmer for 6 minutes.", "Dora la ternera con la cebolla, separando la carne, añade el tomate y cuece 6 minutos."),
            instruction("Mix in the rice, fill the pepper halves firmly and add the mozzarella.", "Mezcla el arroz, rellena bien las mitades de pimiento y añade la mozzarella."),
            instruction("Bake for 18 to 22 minutes until the pepper is tender and the filling is piping hot.", "Hornea de 18 a 22 minutos hasta que el pimiento esté tierno y el relleno muy caliente."),
            instruction("Rest for 3 minutes before serving because the filling retains heat.", "Deja reposar 3 minutos antes de servir porque el relleno conserva mucho calor.")
        ),
        expandedRecipe(
            "med-tuna-white-bean-plate", "Tuscan Tuna and White Bean Plate",
            "Plato toscano de atún y alubias blancas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2133, 120), bedca(2199, 120), bedca(2421, 120), bedca(2382, 35), bedca(411, 18), bedca(2543, 5)),
            instruction("Drain the tuna and beans separately and rinse the beans under cool water.", "Escurre por separado el atún y las alubias y enjuaga estas con agua fría."),
            instruction("Slice the onion very thinly and soak it in cold water for 5 minutes to soften its bite.", "Corta la cebolla muy fina y déjala 5 minutos en agua fría para suavizar su sabor."),
            instruction("Dice the tomato and combine it with the drained beans and onion.", "Corta el tomate y mézclalo con las alubias y la cebolla escurridas."),
            instruction("Whisk the lemon juice and olive oil and fold the dressing through the bean mixture.", "Bate el zumo de limón con el aceite de oliva e incorpora el aliño a las alubias."),
            instruction("Flake the tuna into large pieces over the salad and turn it once or twice without crushing it.", "Separa el atún en trozos grandes sobre la ensalada y mezcla una o dos veces sin deshacerlo."),
            instruction("Let the plate stand for 5 minutes at room temperature before serving.", "Deja reposar el plato 5 minutos a temperatura ambiente antes de servir.")
        ),
        expandedRecipe(
            "med-turkey-aubergine-ragout", "Italian Turkey and Aubergine Ragout",
            "Ragú italiano de pavo y berenjena", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 150), bedca(2370, 190), bedca(481, 180), bedca(2382, 45), bedca(2473, 6), bedca(2543, 6)),
            instruction("Cut the aubergine into 2 cm cubes, spread them out and pat the surfaces dry.", "Corta la berenjena en dados de 2 cm, extiéndelos y seca la superficie."),
            instruction("Heat half the oil and brown the aubergine in a single layer; remove it when golden.", "Calienta la mitad del aceite y dora la berenjena en una sola capa; retírala cuando esté dorada."),
            instruction("Add the remaining oil and onion, cook until soft, then add the turkey and break it up.", "Añade el aceite restante y la cebolla, póchala y agrega el pavo separándolo en trozos."),
            instruction("When no raw colour remains, add the tomato and simmer for 10 minutes.", "Cuando no quede color crudo, añade el tomate y cuece 10 minutos."),
            instruction("Return the aubergine and simmer uncovered until tender and the sauce is thick.", "Devuelve la berenjena y cuece destapado hasta que esté tierna y la salsa espese."),
            instruction("Fold in the basil off the heat so its aroma stays fresh.", "Incorpora la albahaca fuera del fuego para conservar su aroma fresco.")
        ),
        expandedRecipe(
            "med-chicken-artichoke-style", "Mediterranean Chicken, Pea and Lemon Braise",
            "Pollo mediterráneo guisado con guisantes y limón", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 160), bedca(1175, 100), bedca(2376, 130), bedca(2382, 40), bedca(411, 20), bedca(2543, 6)),
            instruction("Cut the chicken and courgette into similar-sized pieces and thaw the peas.", "Corta el pollo y el calabacín en trozos de tamaño parecido y descongela los guisantes."),
            instruction("Brown the chicken in the oil over medium-high heat and transfer it to a plate.", "Dora el pollo en el aceite a fuego medio-alto y pásalo a un plato."),
            instruction("Lower the heat and soften the onion, then add the courgette and cook for 4 minutes.", "Baja el fuego y pocha la cebolla; añade el calabacín y cocina 4 minutos."),
            instruction("Return the chicken with a small splash of water, cover and simmer for 8 minutes.", "Devuelve el pollo con un poco de agua, tapa y cuece 8 minutos."),
            instruction("Add the peas and cook uncovered until the chicken is done and the liquid lightly coats the ingredients.", "Añade los guisantes y cocina destapado hasta que el pollo esté hecho y el líquido cubra ligeramente los ingredientes."),
            instruction("Remove from the heat and stir in the lemon juice just before serving.", "Retira del fuego e incorpora el zumo de limón justo antes de servir.")
        ),
        expandedRecipe(
            "med-mushroom-semolina-bake", "Italian Mushroom Semolina Bake",
            "Sémola italiana al horno con champiñones", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1084, 70), bedca(2384, 160), bedca(915, 240), bedca(2510, 45), bedca(2382, 35), bedca(2543, 5)),
            instruction("Heat the oven to 210°C and line a small baking dish with baking paper.", "Calienta el horno a 210 °C y forra una fuente pequeña con papel de horno."),
            instruction("Brown the sliced mushrooms in the oil, add the onion and cook until all released moisture evaporates.", "Dora los champiñones laminados en el aceite, añade la cebolla y cocina hasta que se evapore toda el agua."),
            instruction("Warm the milk to a simmer and whisk in the semolina in a steady stream.", "Calienta la leche hasta un hervor suave e incorpora la sémola en forma de hilo mientras bates."),
            instruction("Cook for 4 to 5 minutes, stirring constantly, until the mixture pulls from the side of the pan.", "Cocina de 4 a 5 minutos, removiendo sin parar, hasta que la mezcla se separe de la pared del cazo."),
            instruction("Fold in the mushrooms, spread the mixture in the dish and cover with mozzarella.", "Incorpora los champiñones, extiende la mezcla en la fuente y cubre con mozzarella."),
            instruction("Bake for 15 minutes and grill briefly only if the top needs more colour; rest for 5 minutes before cutting.", "Hornea 15 minutos y gratina brevemente solo si falta color; deja reposar 5 minutos antes de cortar.")
        ),
        expandedRecipe(
            "med-pork-tomato-olive", "Spanish Pork with Tomato and Olives",
            "Cerdo con tomate y aceitunas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1021, 165), bedca(481, 180), bedca(2214, 30), bedca(2382, 45), bedca(2409, 80), bedca(2543, 6)),
            instruction("Cut the pork into thin medallions and the pepper and onion into strips.", "Corta el cerdo en medallones finos y el pimiento y la cebolla en tiras."),
            instruction("Heat half the oil and sear the pork for about 1 minute per side; transfer it to a plate.", "Calienta la mitad del aceite y marca el cerdo alrededor de 1 minuto por lado; pásalo a un plato."),
            instruction("Add the remaining oil, onion and pepper and cook over medium heat until softened.", "Añade el aceite restante, la cebolla y el pimiento y cocina a fuego medio hasta que estén tiernos."),
            instruction("Add the tomato and olives and simmer for 8 minutes until the sauce loses its raw taste.", "Añade el tomate y las aceitunas y cuece 8 minutos hasta que la salsa pierda el sabor a crudo."),
            instruction("Return the pork and its juices and cook gently for 3 to 5 minutes, just until done.", "Devuelve el cerdo con sus jugos y cocina suavemente de 3 a 5 minutos, solo hasta que esté hecho."),
            instruction("Rest off the heat for 2 minutes so the lean meat stays juicy.", "Deja reposar fuera del fuego 2 minutos para que la carne magra quede jugosa.")
        ),
        expandedRecipe(
            "med-cod-potato-spinach", "Cod, Potato and Spinach Casserole",
            "Cazuela de bacalao, patata y espinacas", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(795, 170), bedca(2403, 190), bedca(1282, 120), bedca(481, 140), bedca(2382, 40), bedca(2543, 6)),
            instruction("Heat the oven to 200°C and slice the potato into rounds no thicker than 5 mm.", "Calienta el horno a 200 °C y corta la patata en rodajas de no más de 5 mm."),
            instruction("Simmer the potato for 6 minutes, drain it and arrange it in a shallow baking dish.", "Cuece la patata a fuego suave 6 minutos, escúrrela y colócala en una fuente baja."),
            instruction("Soften the onion in the oil, add the tomato and simmer for 6 minutes.", "Pocha la cebolla en el aceite, añade el tomate y cuece 6 minutos."),
            instruction("Wilt the spinach into the sauce and spread half of it over the potatoes.", "Incorpora las espinacas a la salsa hasta que se ablanden y reparte la mitad sobre las patatas."),
            instruction("Place the cod on top, cover with the remaining sauce and loosely cover the dish.", "Coloca el bacalao encima, cúbrelo con la salsa restante y tapa la fuente sin cerrar del todo."),
            instruction("Bake for 12 to 16 minutes until the potato is tender and the cod separates into flakes.", "Hornea de 12 a 16 minutos hasta que la patata esté tierna y el bacalao se separe en lascas.")
        ),
        expandedRecipe(
            "med-tofu-chickpea-ragout", "Mediterranean Tofu Chickpea Ragout",
            "Ragú mediterráneo de tofu y garbanzos", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 150), bedca(2191, 110), bedca(481, 180), bedca(2376, 120), bedca(2382, 40), bedca(2543, 5)),
            instruction("Press the tofu for 10 minutes, cut it into 2 cm cubes and drain the chickpeas.", "Prensa el tofu 10 minutos, córtalo en dados de 2 cm y escurre los garbanzos."),
            instruction("Brown the tofu in half the oil, turning it carefully, then transfer it to a plate.", "Dora el tofu en la mitad del aceite, girándolo con cuidado, y pásalo a un plato."),
            instruction("Add the remaining oil and onion and cook until soft; add the courgette for 4 minutes.", "Añade el aceite restante y pocha la cebolla; incorpora el calabacín durante 4 minutos."),
            instruction("Pour in the tomato and simmer for 8 minutes before adding the chickpeas.", "Incorpora el tomate y cuece 8 minutos antes de añadir los garbanzos."),
            instruction("Return the tofu and simmer uncovered until the sauce is thick enough to cling to the cubes.", "Devuelve el tofu y cuece destapado hasta que la salsa quede adherida a los dados."),
            instruction("Turn the mixture gently with a spatula rather than stirring vigorously, so the tofu stays intact.", "Mezcla suavemente con una espátula, sin remover con fuerza, para mantener entero el tofu.")
        ),

        // Desserts
        expandedRecipe(
            "med-baked-peach-yogurt", "Baked Peach with Greek Yogurt and Almonds",
            "Melocotón asado con yogur griego y almendras", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(2239, 180), bedca(2520, 90), bedca(896, 12), bedca(2643, 6), bedca(2477, 1)),
            instruction("Heat the oven to 200°C, halve the peach and remove the stone.", "Calienta el horno a 200 °C, parte el melocotón y retira el hueso."),
            instruction("Place the halves cut-side up in a small dish and dust them with cinnamon.", "Coloca las mitades con el corte hacia arriba en una fuente pequeña y añade canela."),
            instruction("Bake for 15 to 20 minutes until the flesh is tender but still holds its shape.", "Hornea de 15 a 20 minutos hasta que la pulpa esté tierna pero mantenga la forma."),
            instruction("Cool for 5 minutes, spoon yogurt into the centres and add chopped almonds and honey.", "Deja templar 5 minutos, pon yogur en el centro y añade almendra picada y miel.")
        ),
        expandedRecipe(
            "med-strawberry-ricotta-cream", "Italian Strawberry Ricotta Cream",
            "Crema italiana de requesón y fresa", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(2517, 130), bedca(2225, 130), bedca(2643, 8), bedca(411, 8), bedca(896, 10)),
            instruction("Reserve two strawberries for the top and chop the rest into small pieces.", "Reserva dos fresas para decorar y corta el resto en trozos pequeños."),
            instruction("Beat the cottage cheese with the honey and lemon juice until creamy but not completely smooth.", "Bate el requesón con la miel y el limón hasta obtener una crema, sin dejarla totalmente lisa."),
            instruction("Fold in the chopped strawberries and chill the mixture for at least 20 minutes.", "Incorpora las fresas troceadas y enfría la mezcla al menos 20 minutos."),
            instruction("Serve with the reserved sliced strawberries and toasted chopped almonds.", "Sirve con las fresas reservadas en láminas y la almendra picada y tostada.")
        ),
        expandedRecipe(
            "med-orange-semolina-cake", "Greek Orange Semolina Yogurt Cake",
            "Bizcocho griego de naranja, sémola y yogur", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(1084, 55), bedca(1113, 35), bedca(1173, 55), bedca(2125, 25), bedca(2530, 70), bedca(2468, 45), bedca(2643, 10)),
            instruction("Heat the oven to 180°C and line a small baking tin.", "Calienta el horno a 180 °C y forra un molde pequeño."),
            instruction("Whisk the egg white and yolk with the honey until lightly foamy.", "Bate la clara y la yema con la miel hasta que formen una espuma ligera."),
            instruction("Mix in the yogurt and half the orange juice.", "Incorpora el yogur y la mitad del zumo de naranja."),
            instruction("Fold in the semolina and flour only until no dry pockets remain, then rest the batter for 5 minutes.", "Añade la sémola y la harina solo hasta que no queden zonas secas y deja reposar la masa 5 minutos."),
            instruction("Bake for 22 to 28 minutes until the centre springs back when lightly pressed.", "Hornea de 22 a 28 minutos hasta que el centro recupere la forma al presionarlo suavemente."),
            instruction("Pierce the warm cake and spoon over the remaining orange juice; cool before cutting.", "Pincha el bizcocho caliente y reparte el zumo restante; deja enfriar antes de cortar.")
        ),
        expandedRecipe(
            "med-banana-cinnamon-custard", "Spanish Banana Cinnamon Custard",
            "Natillas de plátano y canela", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(2245, 110), bedca(915, 200), bedca(2125, 25), bedca(2477, 1), bedca(2643, 6)),
            instruction("Mash half the banana until smooth and slice the remainder for serving.", "Machaca la mitad del plátano hasta dejarlo liso y corta el resto para servir."),
            instruction("Warm the milk with the cinnamon until steaming, then remove it from the heat.", "Calienta la leche con la canela hasta que desprenda vapor y retírala del fuego."),
            instruction("Whisk the yolk with the honey and slowly pour in the warm milk while whisking.", "Bate la yema con la miel y vierte poco a poco la leche caliente sin dejar de batir."),
            instruction("Return the mixture to low heat and stir constantly until it coats the back of a spoon; do not boil.", "Devuelve la mezcla a fuego bajo y remueve hasta que cubra el dorso de una cuchara; no dejes que hierva."),
            instruction("Remove from the heat, mix in the mashed banana and pour into a serving cup.", "Retira del fuego, incorpora el plátano machacado y vierte en una copa."),
            instruction("Chill for at least 1 hour and add the banana slices immediately before serving.", "Enfría al menos 1 hora y añade las rodajas de plátano justo antes de servir.")
        ),
        expandedRecipe(
            "med-raspberry-yogurt-granita", "Raspberry Yogurt Granita",
            "Granizado de frambuesa y yogur", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(914, 140), bedca(2530, 100), bedca(2643, 7), bedca(411, 8)),
            instruction("Crush the raspberries with the honey and lemon juice, leaving a little texture.", "Tritura las frambuesas con la miel y el limón, dejando algo de textura."),
            instruction("Fold in the yogurt and spread the mixture in a shallow freezer-safe dish.", "Incorpora el yogur y extiende la mezcla en una fuente baja apta para congelador."),
            instruction("Freeze for 45 minutes, then scrape the frozen edges toward the centre with a fork.", "Congela 45 minutos y raspa los bordes helados hacia el centro con un tenedor."),
            instruction("Repeat the freezing and scraping every 30 minutes until loose ice crystals form throughout.", "Repite la congelación y el raspado cada 30 minutos hasta obtener cristales sueltos en toda la mezcla."),
            instruction("Scrape once more immediately before serving in a chilled glass.", "Raspa una vez más justo antes de servir en una copa fría.")
        ),
        expandedRecipe(
            "med-roasted-grapes-yogurt", "Roasted Grapes with Yogurt and Walnuts",
            "Uvas asadas con yogur y nueces", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(bedca(1017, 150), bedca(2520, 100), bedca(2201, 12), bedca(2643, 5), bedca(2477, 1)),
            instruction("Heat the oven to 210°C and spread the washed grapes in one layer on a small tray.", "Calienta el horno a 210 °C y reparte las uvas lavadas en una sola capa sobre una bandeja pequeña."),
            instruction("Roast for 10 to 14 minutes until some skins split and the juices begin to caramelise.", "Asa de 10 a 14 minutos hasta que algunas pieles se abran y el jugo empiece a caramelizarse."),
            instruction("Let the grapes cool for 5 minutes so their hot juice does not thin the yogurt.", "Deja templar las uvas 5 minutos para que su jugo caliente no licúe el yogur."),
            instruction("Spoon the yogurt into a bowl, add the grapes and their juice, then finish with walnuts, honey and cinnamon.", "Pon el yogur en un cuenco, añade las uvas y su jugo y termina con nueces, miel y canela.")
        )
    )
}
