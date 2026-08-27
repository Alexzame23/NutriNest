package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories

/** Filling, one-serving recipes designed to stay below 600 kcal in the bundled dataset. */
internal object LowCalorieRecipeExpansion {
    val recipes = listOf(
        // Breakfasts
        expandedRecipe(
            "light-raspberry-porridge", "Raspberry Yogurt Porridge",
            "Gachas de avena con frambuesa y yogur", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1010, 45), bedca(915, 220), bedca(914, 90), bedca(2530, 60), bedca(2477, 1)),
            instruction("Put the oats and milk in a saucepan and bring them slowly to a simmer while stirring.", "Pon la avena y la leche en un cazo y llévalas lentamente a hervor suave mientras remueves."),
            instruction("Cook over low heat for 4 to 5 minutes, stirring often, until the oats are creamy.", "Cocina a fuego bajo de 4 a 5 minutos, removiendo a menudo, hasta que la avena esté cremosa."),
            instruction("Crush half the raspberries with a fork and swirl them through the porridge.", "Machaca la mitad de las frambuesas con un tenedor y mézclalas parcialmente con las gachas."),
            instruction("Pour into a bowl and add the yogurt, remaining raspberries and cinnamon.", "Sirve en un cuenco y añade el yogur, las frambuesas restantes y la canela.")
        ),
        expandedRecipe(
            "light-mango-overnight-oats", "Mango Overnight Oats",
            "Avena nocturna con mango", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1010, 45), bedca(915, 120), bedca(2530, 90), bedca(1186, 100), bedca(896, 8)),
            instruction("Mix the oats, milk and yogurt in a lidded container until no dry oats remain.", "Mezcla la avena, la leche y el yogur en un recipiente con tapa hasta que no queden copos secos."),
            instruction("Cover and refrigerate for at least 6 hours so the oats hydrate fully.", "Tapa y refrigera al menos 6 horas para que la avena se hidrate por completo."),
            instruction("Dice the mango just before serving and stir half of it through the oats.", "Corta el mango en dados justo antes de servir e incorpora la mitad a la avena."),
            instruction("Add the remaining mango and chopped almonds on top.", "Añade por encima el mango restante y la almendra picada.")
        ),
        expandedRecipe(
            "light-mushroom-breakfast-cups", "No-Pastry Mushroom Egg Cups",
            "Pastelitos de huevo y champiñón sin masa", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1173, 110), bedca(2125, 25), bedca(2384, 110), bedca(2382, 30), bedca(915, 35), bedca(1005, 15)),
            instruction("Heat the oven to 180°C and line or lightly grease two muffin-tin wells.", "Calienta el horno a 180 °C y forra o engrasa ligeramente dos huecos de un molde de magdalenas."),
            instruction("Finely chop the mushroom and onion and cook them in a non-stick pan until their moisture evaporates.", "Pica finos el champiñón y la cebolla y cocínalos en una sartén antiadherente hasta que se evapore el agua."),
            instruction("Divide the cooked vegetables between the prepared wells.", "Reparte las verduras cocinadas entre los huecos preparados."),
            instruction("Whisk the egg whites, yolk and milk and pour the mixture over the vegetables.", "Bate las claras, la yema y la leche y vierte la mezcla sobre las verduras."),
            instruction("Top with the cheese and bake for 14 to 17 minutes until risen and set in the centre.", "Cubre con el queso y hornea de 14 a 17 minutos hasta que suban y el centro esté cuajado."),
            instruction("Cool in the tin for 3 minutes, then loosen the edges before lifting them out.", "Deja templar 3 minutos en el molde y separa los bordes antes de sacarlos.")
        ),
        expandedRecipe(
            "light-tomato-cottage-toast", "Tomato Cottage Cheese Toast",
            "Tostada de tomate y requesón", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(2163, 70), bedca(2517, 90), bedca(2421, 130), bedca(2361, 2), bedca(2598, 1)),
            instruction("Toast the bread until firm enough to hold the moist topping.", "Tuesta el pan hasta que esté lo bastante firme para sostener la cobertura húmeda."),
            instruction("Cut the tomato in half, grate the flesh and discard the skin.", "Parte el tomate, ralla la pulpa y desecha la piel."),
            instruction("Rub the toast lightly with the cut garlic and spoon over the grated tomato.", "Frota ligeramente la tostada con el ajo cortado y reparte el tomate rallado."),
            instruction("Add the cottage cheese in small spoonfuls and finish with oregano.", "Añade el requesón en pequeñas cucharadas y termina con orégano.")
        ),
        expandedRecipe(
            "light-banana-raspberry-bowl", "Banana Raspberry Smoothie Bowl",
            "Bol cremoso de plátano y frambuesa", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(2245, 100), bedca(914, 100), bedca(2530, 130), bedca(915, 60), bedca(1010, 20)),
            instruction("Slice and freeze the banana for at least 2 hours so the bowl becomes thick without ice.", "Corta y congela el plátano al menos 2 horas para espesar el bol sin usar hielo."),
            instruction("Blend the frozen banana, three-quarters of the raspberries, yogurt and milk until smooth.", "Tritura el plátano congelado, tres cuartas partes de las frambuesas, el yogur y la leche hasta dejarlo liso."),
            instruction("Pour immediately into a chilled bowl before the mixture softens.", "Vierte enseguida en un cuenco frío antes de que la mezcla se ablande."),
            instruction("Scatter the oats and remaining raspberries on top and eat with a spoon.", "Reparte por encima la avena y las frambuesas restantes y sirve con cuchara.")
        ),
        expandedRecipe(
            "light-quinoa-spinach-egg", "Savoury Quinoa Spinach Egg Bowl",
            "Bol salado de quinoa, espinacas y huevo", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(bedca(1083, 45), bedca(1282, 100), bedca(1173, 80), bedca(2125, 25), bedca(2421, 100), bedca(2543, 3)),
            instruction("Rinse the quinoa thoroughly and simmer it in measured water until the grains open.", "Enjuaga bien la quinoa y cuécela con el agua medida hasta que se abran los granos."),
            instruction("Cover the cooked quinoa, remove it from the heat and leave it to steam for 5 minutes.", "Tapa la quinoa cocida, retírala del fuego y deja que repose al vapor 5 minutos."),
            instruction("Heat the oil in a non-stick pan, add the chopped tomato and cook for 3 minutes.", "Calienta el aceite en una sartén antiadherente, añade el tomate picado y cocina 3 minutos."),
            instruction("Add the spinach and stir just until wilted.", "Añade las espinacas y remueve solo hasta que se ablanden."),
            instruction("Pour in the beaten egg white and yolk and stir gently until softly set.", "Vierte la clara y la yema batidas y remueve suavemente hasta que cuajen sin secarse."),
            instruction("Fluff the quinoa and serve the spinach scramble over it.", "Suelta la quinoa con un tenedor y sirve encima el revuelto de espinacas.")
        ),

        // Main dishes
        expandedRecipe(
            "light-chicken-ratatouille", "Grilled Chicken with Ratatouille",
            "Pollo a la plancha con pisto ligero", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2370, 120), bedca(2376, 120), bedca(2409, 80), bedca(481, 140), bedca(2543, 4)),
            instruction("Cut the aubergine, courgette and pepper into even 2 cm pieces.", "Corta la berenjena, el calabacín y el pimiento en trozos iguales de 2 cm."),
            instruction("Cook the aubergine in a covered non-stick pan with half the oil and a splash of water for 5 minutes.", "Cocina la berenjena en una sartén antiadherente tapada con la mitad del aceite y un poco de agua durante 5 minutos."),
            instruction("Add the courgette and pepper, cook uncovered for 5 minutes, then add the tomato and simmer until thick.", "Añade el calabacín y el pimiento, cocina destapado 5 minutos, incorpora el tomate y cuece hasta que espese."),
            instruction("Flatten the chicken to an even thickness and brush it with the remaining oil.", "Aplana el pollo hasta igualar su grosor y pincélalo con el aceite restante."),
            instruction("Cook on a hot grill pan, turning once, until browned and safely cooked through.", "Cocina en una plancha caliente, dándole una vuelta, hasta que esté dorado y completamente hecho."),
            instruction("Rest the chicken for 3 minutes, slice it and serve it over the ratatouille.", "Deja reposar el pollo 3 minutos, córtalo y sírvelo sobre el pisto.")
        ),
        expandedRecipe(
            "light-turkey-cabbage-skillet", "Turkey and Cabbage Skillet",
            "Salteado de pavo y col", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 155), bedca(2386, 200), bedca(2424, 65), bedca(2382, 40), bedca(2575, 10), bedca(752, 2)),
            instruction("Slice the turkey thinly and shred the cabbage, carrot and onion.", "Corta el pavo en tiras finas y la col, la zanahoria y la cebolla en juliana."),
            instruction("Heat a large non-stick pan and sear the turkey in one layer until lightly browned; remove it.", "Calienta una sartén antiadherente grande y marca el pavo en una sola capa hasta dorarlo; retíralo."),
            instruction("Add the onion and carrot with a splash of water and stir-fry for 3 minutes.", "Añade la cebolla y la zanahoria con un poco de agua y saltea 3 minutos."),
            instruction("Add the cabbage and cook for 5 to 6 minutes, turning it often so it softens evenly.", "Añade la col y cocina de 5 a 6 minutos, removiendo a menudo para que se ablande por igual."),
            instruction("Return the turkey, add the soy sauce and cook until the meat is fully done.", "Devuelve el pavo, añade la salsa de soja y cocina hasta que la carne esté completamente hecha."),
            instruction("Take off the heat and stir in the sesame oil so its aroma is not lost.", "Retira del fuego e incorpora el aceite de sésamo para conservar su aroma.")
        ),
        expandedRecipe(
            "light-hake-cauliflower-tray", "Hake and Cauliflower Paprika Traybake",
            "Merluza y coliflor al pimentón", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 190), bedca(859, 230), bedca(2409, 90), bedca(2592, 3), bedca(411, 18), bedca(2543, 4)),
            instruction("Heat the oven to 210°C and separate the cauliflower into small, equal florets.", "Calienta el horno a 210 °C y separa la coliflor en ramilletes pequeños e iguales."),
            instruction("Toss the cauliflower and pepper with the oil and paprika and spread them on a tray.", "Mezcla la coliflor y el pimiento con el aceite y el pimentón y repártelos en una bandeja."),
            instruction("Roast for 18 minutes, turning once, until the cauliflower is nearly tender.", "Asa 18 minutos, dando una vuelta, hasta que la coliflor esté casi tierna."),
            instruction("Pat the hake dry, place it among the vegetables and spoon over the lemon juice.", "Seca la merluza, colócala entre las verduras y reparte el zumo de limón."),
            instruction("Bake for another 8 to 11 minutes until the fish is opaque and flakes easily.", "Hornea otros 8 u 11 minutos hasta que el pescado esté opaco y se separe en lascas."),
            instruction("Remove the fish promptly and give thicker cauliflower pieces extra time only if needed.", "Retira el pescado a tiempo y deja solo los trozos más gruesos de coliflor unos minutos más si hace falta.")
        ),
        expandedRecipe(
            "light-prawn-vegetable-soup", "Prawn and Vegetable Soup",
            "Sopa ligera de gambas y verduras", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(581, 150), bedca(2376, 120), bedca(2424, 60), bedca(2366, 45), bedca(481, 120), bedca(792, 60)),
            instruction("Cut the courgette, carrot and celery into small pieces of similar size.", "Corta el calabacín, la zanahoria y el apio en trozos pequeños de tamaño parecido."),
            instruction("Put the stock and 300 ml water in a saucepan and bring them to a simmer.", "Pon el caldo y 300 ml de agua en un cazo y llévalos a hervor suave."),
            instruction("Add the carrot and celery and simmer for 6 minutes before adding the courgette and tomato.", "Añade la zanahoria y el apio y cuece 6 minutos antes de incorporar el calabacín y el tomate."),
            instruction("Cook for another 5 minutes, until the vegetables are tender but not falling apart.", "Cuece otros 5 minutos, hasta que las verduras estén tiernas pero no se deshagan."),
            instruction("Add the prawns and simmer only until opaque and curled, usually 2 to 3 minutes.", "Añade las gambas y cuece solo hasta que estén opacas y curvadas, normalmente de 2 a 3 minutos."),
            instruction("Take the soup off the heat immediately so the prawns do not become rubbery.", "Retira la sopa del fuego inmediatamente para que las gambas no queden gomosas.")
        ),
        expandedRecipe(
            "light-tuna-bean-tomatoes", "Tuna Bean Stuffed Tomatoes",
            "Tomates rellenos de atún y alubias", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2421, 280), bedca(2133, 110), bedca(2199, 70), bedca(2407, 70), bedca(2530, 45), bedca(411, 12)),
            instruction("Slice the tops from the tomatoes, scoop out the centres and turn the shells upside down to drain.", "Corta la parte superior de los tomates, vacíalos y pon las carcasas boca abajo para que escurran."),
            instruction("Chop the firm tomato flesh and discard excess watery seeds.", "Pica la pulpa firme del tomate y desecha el exceso de semillas y líquido."),
            instruction("Drain the tuna and beans and dice the cucumber finely.", "Escurre el atún y las alubias y corta el pepino en dados pequeños."),
            instruction("Mix the tuna, beans, cucumber and tomato flesh with the yogurt and lemon.", "Mezcla el atún, las alubias, el pepino y la pulpa con el yogur y el limón."),
            instruction("Spoon the filling into the tomato shells without compressing it too tightly.", "Rellena los tomates sin prensar demasiado la mezcla."),
            instruction("Chill for 15 minutes or serve immediately at room temperature.", "Enfría 15 minutos o sirve inmediatamente a temperatura ambiente.")
        ),
        expandedRecipe(
            "light-chicken-lentil-soup", "Chicken and Lentil Vegetable Soup",
            "Sopa de pollo, lentejas y verduras", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 130), bedca(1126, 100), bedca(2424, 60), bedca(2366, 45), bedca(481, 130), bedca(792, 60)),
            instruction("Cut the chicken, carrot and celery into small, even pieces and drain the canned lentils.", "Corta el pollo, la zanahoria y el apio en trozos pequeños e iguales y escurre las lentejas."),
            instruction("Bring the stock and 300 ml water to a simmer and add the carrot and celery.", "Lleva el caldo y 300 ml de agua a hervor suave y añade la zanahoria y el apio."),
            instruction("After 5 minutes, add the chicken and keep the liquid at a gentle simmer, not a rolling boil.", "Tras 5 minutos, añade el pollo y mantén un hervor suave, no fuerte."),
            instruction("Cook for 7 to 9 minutes until the chicken is done, skimming any foam from the surface.", "Cuece de 7 a 9 minutos hasta que el pollo esté hecho y retira la espuma de la superficie."),
            instruction("Add the tomato and lentils and simmer for 4 minutes until thoroughly hot.", "Añade el tomate y las lentejas y cuece 4 minutos hasta que todo esté bien caliente."),
            instruction("Rest for 2 minutes before serving so the temperature becomes even throughout.", "Deja reposar 2 minutos antes de servir para igualar la temperatura.")
        ),
        expandedRecipe(
            "light-tofu-cabbage-stirfry", "Tofu and Cabbage Stir-Fry",
            "Salteado de tofu y col", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 170), bedca(2386, 190), bedca(2424, 60), bedca(2409, 70), bedca(2575, 11), bedca(752, 2)),
            instruction("Press the tofu for 10 minutes, cube it and pat the cubes dry.", "Prensa el tofu 10 minutos, córtalo en dados y sécalos."),
            instruction("Shred the cabbage and cut the carrot and pepper into thin matchsticks.", "Corta la col en juliana y la zanahoria y el pimiento en tiras finas."),
            instruction("Brown the tofu in a hot non-stick pan, turning it carefully, then remove it.", "Dora el tofu en una sartén antiadherente caliente, girándolo con cuidado, y retíralo."),
            instruction("Stir-fry the carrot and pepper for 2 minutes, add the cabbage and continue for 4 minutes.", "Saltea la zanahoria y el pimiento 2 minutos, añade la col y sigue 4 minutos."),
            instruction("Return the tofu, pour the soy sauce around the edge of the pan and toss until hot.", "Devuelve el tofu, vierte la soja por el borde de la sartén y mezcla hasta que esté caliente."),
            instruction("Remove from the heat and add the sesame oil at the end.", "Retira del fuego y añade el aceite de sésamo al final.")
        ),
        expandedRecipe(
            "light-salmon-broccoli-parcel", "Salmon and Broccoli Parcel",
            "Papillote de salmón y brócoli", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2626, 125), bedca(1177, 180), bedca(2376, 110), bedca(411, 18), bedca(2530, 40)),
            instruction("Heat the oven to 200°C and cut a large sheet of baking paper.", "Calienta el horno a 200 °C y corta una hoja grande de papel de horno."),
            instruction("Cut the broccoli into small florets and the courgette into thin half-moons.", "Separa el brócoli en ramilletes pequeños y corta el calabacín en medias lunas finas."),
            instruction("Place the vegetables in the centre of the paper and set the salmon on top.", "Coloca las verduras en el centro del papel y pon el salmón encima."),
            instruction("Add the lemon juice and fold the paper into a tightly sealed parcel with space for steam.", "Añade el limón y cierra el papel formando un paquete bien sellado con espacio para el vapor."),
            instruction("Bake for 14 to 18 minutes, depending on the salmon thickness, then rest the unopened parcel for 2 minutes.", "Hornea de 14 a 18 minutos, según el grosor del salmón, y deja reposar el paquete cerrado 2 minutos."),
            instruction("Open away from your face, check the fish flakes easily and serve with yogurt.", "Abre alejándolo de la cara, comprueba que el pescado se separe en lascas y sirve con yogur.")
        ),
        expandedRecipe(
            "light-turkey-cauliflower-loaf", "Turkey and Cauliflower Mini Meatloaf",
            "Mini pastel de pavo y coliflor", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 160), bedca(859, 150), bedca(2171, 15), bedca(1173, 30), bedca(481, 100), bedca(2382, 30)),
            instruction("Heat the oven to 190°C and line a small loaf tin or oven-safe dish.", "Calienta el horno a 190 °C y forra un molde pequeño o una fuente apta para horno."),
            instruction("Steam the cauliflower until tender, cool it slightly and chop it very finely.", "Cuece la coliflor al vapor hasta que esté tierna, deja que temple y pícala muy fina."),
            instruction("Mix the turkey, cauliflower, breadcrumbs, egg white and finely chopped onion only until combined.", "Mezcla el pavo, la coliflor, el pan rallado, la clara y la cebolla picada solo hasta integrar."),
            instruction("Shape the mixture in the tin without packing it down hard and spread the tomato on top.", "Da forma a la mezcla en el molde sin prensarla y reparte el tomate por encima."),
            instruction("Bake for 28 to 35 minutes until the centre is fully cooked.", "Hornea de 28 a 35 minutos hasta que el centro esté completamente hecho."),
            instruction("Rest for 5 minutes before slicing so the juices settle and the loaf holds together.", "Deja reposar 5 minutos antes de cortar para que se asienten los jugos y mantenga la forma.")
        ),
        expandedRecipe(
            "light-beef-courgette-noodles", "Beef Mushroom Courgette Noodles",
            "Tallarines de calabacín con ternera y champiñón", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(981, 140), bedca(2376, 230), bedca(2384, 130), bedca(2409, 70), bedca(2575, 10), bedca(752, 2)),
            instruction("Cut the courgette into long thin ribbons with a peeler and pat them dry.", "Corta el calabacín en tiras largas y finas con un pelador y sécalas."),
            instruction("Slice the beef thinly across the grain and slice the mushrooms and pepper.", "Corta la ternera fina contra la fibra y lamina los champiñones y el pimiento."),
            instruction("Sear the beef in a very hot non-stick pan in one layer and remove it as soon as it colours.", "Marca la ternera en una sartén antiadherente muy caliente en una sola capa y retírala al dorarse."),
            instruction("Cook the mushrooms until their moisture evaporates, then add the pepper for 2 minutes.", "Cocina los champiñones hasta que se evapore su agua y añade el pimiento 2 minutos."),
            instruction("Add the courgette ribbons and toss for only 60 to 90 seconds so they stay firm.", "Añade las tiras de calabacín y saltea solo de 60 a 90 segundos para que queden firmes."),
            instruction("Return the beef with the soy sauce, heat briefly and finish off the heat with sesame oil.", "Devuelve la ternera con la soja, calienta brevemente y termina fuera del fuego con el aceite de sésamo.")
        ),
        expandedRecipe(
            "light-chicken-chickpea-salad", "Chicken Chickpea Crunch Salad",
            "Ensalada crujiente de pollo y garbanzos", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 135), bedca(2191, 90), bedca(2399, 100), bedca(2407, 100), bedca(2421, 100), bedca(2530, 55), bedca(411, 15)),
            instruction("Flatten the chicken to an even thickness and cook it in a hot non-stick pan until safely done.", "Aplana el pollo hasta igualar su grosor y cocínalo en una sartén antiadherente caliente hasta que esté bien hecho."),
            instruction("Rest the chicken for 4 minutes before slicing it across the grain.", "Deja reposar el pollo 4 minutos antes de cortarlo contra la fibra."),
            instruction("Drain the chickpeas and chop the lettuce, cucumber and tomato into bite-sized pieces.", "Escurre los garbanzos y corta la lechuga, el pepino y el tomate en trozos cómodos."),
            instruction("Whisk the yogurt and lemon into a smooth dressing.", "Bate el yogur y el limón hasta obtener un aliño liso."),
            instruction("Toss the vegetables and chickpeas with half the dressing.", "Mezcla las verduras y los garbanzos con la mitad del aliño."),
            instruction("Lay the warm sliced chicken on top and spoon over the remaining dressing.", "Coloca encima el pollo templado en lonchas y añade el resto del aliño.")
        ),
        expandedRecipe(
            "light-cod-spinach-tomato", "Cod with Spinach Tomato Sauce",
            "Bacalao con salsa de tomate y espinacas", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(795, 180), bedca(1282, 160), bedca(481, 170), bedca(2382, 35), bedca(2361, 2), bedca(2543, 3)),
            instruction("Pat the cod dry and check it carefully for bones.", "Seca el bacalao y comprueba con cuidado que no tenga espinas."),
            instruction("Soften the onion in the oil in a lidded non-stick pan, then add the garlic for 30 seconds.", "Pocha la cebolla en el aceite en una sartén antiadherente con tapa y añade el ajo 30 segundos."),
            instruction("Add the tomato and simmer uncovered for 7 minutes.", "Añade el tomate y cuece destapado 7 minutos."),
            instruction("Add the spinach in handfuls, stirring until each addition wilts.", "Añade las espinacas por puñados y remueve hasta que cada tanda se ablande."),
            instruction("Set the cod into the sauce, cover and cook gently for 6 to 9 minutes.", "Coloca el bacalao en la salsa, tapa y cocina suavemente de 6 a 9 minutos."),
            instruction("Remove from the heat as soon as the thickest section flakes under light pressure.", "Retira del fuego en cuanto la parte más gruesa se separe en lascas al presionarla suavemente.")
        ),
        expandedRecipe(
            "light-pork-cabbage-carrot", "Lean Pork Cabbage and Carrot Braise",
            "Cerdo magro guisado con col y zanahoria", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1021, 150), bedca(2386, 200), bedca(2424, 70), bedca(2382, 40), bedca(481, 110), bedca(2543, 3)),
            instruction("Cut the pork into thin strips and shred the cabbage, carrot and onion.", "Corta el cerdo en tiras finas y la col, la zanahoria y la cebolla en juliana."),
            instruction("Heat the oil in a casserole and brown the pork quickly; transfer it to a plate.", "Calienta el aceite en una cazuela y dora rápidamente el cerdo; pásalo a un plato."),
            instruction("Add the onion and carrot with a splash of water and cook covered for 4 minutes.", "Añade la cebolla y la zanahoria con un poco de agua y cocina tapado 4 minutos."),
            instruction("Add the cabbage and tomato, cover and braise gently for 8 minutes, stirring halfway.", "Añade la col y el tomate, tapa y guisa suavemente 8 minutos, removiendo a mitad."),
            instruction("Return the pork and cook uncovered until the meat is done and excess liquid has reduced.", "Devuelve el cerdo y cocina destapado hasta que la carne esté hecha y se reduzca el exceso de líquido."),
            instruction("Rest for 2 minutes before serving to keep the lean pork tender.", "Deja reposar 2 minutos antes de servir para mantener tierno el cerdo magro.")
        ),
        expandedRecipe(
            "light-lentil-cauliflower-pie", "Lentil Cauliflower Shepherd's Pie",
            "Pastel de lentejas con puré de coliflor", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1126, 160), bedca(859, 240), bedca(481, 150), bedca(2424, 60), bedca(2382, 40), bedca(915, 40)),
            instruction("Heat the oven to 210°C and steam the cauliflower until completely tender.", "Calienta el horno a 210 °C y cuece la coliflor al vapor hasta que esté totalmente tierna."),
            instruction("Drain it very well and mash it with the milk until smooth; keep the mash warm.", "Escúrrela muy bien y tritúrala con la leche hasta dejarla lisa; mantén el puré caliente."),
            instruction("Cook the onion and carrot in a non-stick pan with a splash of water until softened.", "Cocina la cebolla y la zanahoria en una sartén antiadherente con un poco de agua hasta que estén tiernas."),
            instruction("Add the tomato and drained lentils and simmer until the filling is thick, about 8 minutes.", "Añade el tomate y las lentejas escurridas y cuece hasta que el relleno esté espeso, unos 8 minutos."),
            instruction("Transfer the filling to a baking dish and spread the cauliflower mash to every edge.", "Pasa el relleno a una fuente y extiende el puré de coliflor hasta todos los bordes."),
            instruction("Bake for 15 minutes and grill briefly until the ridges on the mash are lightly browned.", "Hornea 15 minutos y gratina brevemente hasta que los relieves del puré se doren."),
            instruction("Rest for 5 minutes before spooning out the pie.", "Deja reposar 5 minutos antes de servir el pastel.")
        ),
        expandedRecipe(
            "light-chickpea-courgette-fritters", "Chickpea Courgette Fritters",
            "Tortitas de garbanzo y calabacín", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2191, 150), bedca(2376, 150), bedca(1173, 45), bedca(2171, 15), bedca(2530, 55), bedca(411, 12)),
            instruction("Grate the courgette, wrap it in a clean cloth and squeeze out as much water as possible.", "Ralla el calabacín, envuélvelo en un paño limpio y exprime toda el agua posible."),
            instruction("Mash the drained chickpeas, leaving a few small pieces for texture.", "Machaca los garbanzos escurridos dejando algunos trozos pequeños para dar textura."),
            instruction("Mix the chickpeas, courgette, egg white and breadcrumbs and leave the mixture for 5 minutes.", "Mezcla los garbanzos, el calabacín, la clara y el pan rallado y deja reposar 5 minutos."),
            instruction("Shape small thin fritters and place them in a preheated non-stick pan.", "Forma tortitas pequeñas y finas y colócalas en una sartén antiadherente precalentada."),
            instruction("Cook over medium heat for 3 to 4 minutes per side until firm and browned.", "Cocina a fuego medio de 3 a 4 minutos por lado hasta que estén firmes y doradas."),
            instruction("Mix the yogurt and lemon and serve the sauce beside the hot fritters.", "Mezcla el yogur y el limón y sirve la salsa junto a las tortitas calientes.")
        ),
        expandedRecipe(
            "light-egg-white-shakshuka", "Egg-White Shakshuka",
            "Shakshuka ligera de claras", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1173, 180), bedca(481, 200), bedca(2409, 100), bedca(2382, 45), bedca(1282, 80), bedca(2571, 2)),
            instruction("Dice the pepper and onion and cook them in a covered non-stick pan with a splash of water for 6 minutes.", "Corta el pimiento y la cebolla y cocínalos en una sartén antiadherente tapada con un poco de agua durante 6 minutos."),
            instruction("Add the cumin and tomato and simmer uncovered for 10 minutes until the sauce thickens.", "Añade el comino y el tomate y cuece destapado 10 minutos hasta que la salsa espese."),
            instruction("Stir in the spinach and cook until it has just wilted.", "Incorpora las espinacas y cocina solo hasta que se ablanden."),
            instruction("Make three hollows in the sauce and divide the egg whites among them.", "Abre tres huecos en la salsa y reparte las claras entre ellos."),
            instruction("Cover and cook over low heat until the whites are opaque and set, 4 to 6 minutes.", "Tapa y cocina a fuego bajo hasta que las claras estén opacas y cuajadas, de 4 a 6 minutos."),
            instruction("Remove the pan promptly so the residual heat does not make the whites rubbery.", "Retira la sartén enseguida para que el calor residual no endurezca las claras.")
        ),
        expandedRecipe(
            "light-turkey-aubergine-skillet", "Turkey Aubergine Tomato Skillet",
            "Sartén de pavo, berenjena y tomate", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 150), bedca(2370, 210), bedca(481, 170), bedca(2382, 40), bedca(2409, 70), bedca(2543, 3)),
            instruction("Cut the aubergine into small cubes and the turkey and pepper into even strips.", "Corta la berenjena en dados pequeños y el pavo y el pimiento en tiras uniformes."),
            instruction("Cook the aubergine in a covered non-stick pan with the oil and a splash of water for 6 minutes.", "Cocina la berenjena en una sartén antiadherente tapada con el aceite y un poco de agua durante 6 minutos."),
            instruction("Uncover, add the onion and pepper and cook until the edges begin to colour.", "Destapa, añade la cebolla y el pimiento y cocina hasta que los bordes empiecen a dorarse."),
            instruction("Push the vegetables aside, add the turkey and cook while turning until no raw colour remains.", "Aparta las verduras, añade el pavo y cocina girándolo hasta que pierda el color crudo."),
            instruction("Add the tomato, mix everything and simmer until the turkey is safely done and the sauce thickens.", "Añade el tomate, mezcla y cuece hasta que el pavo esté bien hecho y la salsa espese."),
            instruction("Rest covered for 2 minutes before serving.", "Deja reposar tapado 2 minutos antes de servir.")
        ),
        expandedRecipe(
            "light-tofu-cauliflower-rice", "Tofu Cauliflower Fried Rice",
            "Arroz de coliflor salteado con tofu", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 170), bedca(859, 230), bedca(1175, 60), bedca(2424, 55), bedca(2575, 11), bedca(752, 2)),
            instruction("Press and cube the tofu and grate or pulse the cauliflower into rice-sized pieces.", "Prensa y corta el tofu en dados y ralla o tritura la coliflor hasta obtener granos."),
            instruction("Brown the tofu in a hot non-stick pan and transfer it to a plate.", "Dora el tofu en una sartén antiadherente caliente y pásalo a un plato."),
            instruction("Add the carrot and peas and stir-fry for 2 minutes.", "Añade la zanahoria y los guisantes y saltea 2 minutos."),
            instruction("Add the cauliflower and cook over high heat for 4 minutes, stirring so steam can escape.", "Añade la coliflor y cocina a fuego alto 4 minutos, removiendo para que salga el vapor."),
            instruction("Return the tofu, pour in the soy sauce and toss until everything is evenly hot.", "Devuelve el tofu, incorpora la soja y mezcla hasta que todo esté caliente por igual."),
            instruction("Remove from the heat and finish with the sesame oil.", "Retira del fuego y termina con el aceite de sésamo.")
        ),
        expandedRecipe(
            "light-tuna-pepper-lettuce", "Tuna Pepper Lettuce Bowl",
            "Bol de lechuga, atún y pimiento", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2133, 130), bedca(2399, 130), bedca(2409, 100), bedca(2407, 100), bedca(2421, 100), bedca(2530, 55), bedca(411, 12)),
            instruction("Drain the tuna thoroughly so it does not water down the dressing.", "Escurre bien el atún para que no agüe el aliño."),
            instruction("Shred the lettuce and cut the pepper, cucumber and tomato into small even pieces.", "Corta la lechuga en tiras y el pimiento, el pepino y el tomate en trozos pequeños e iguales."),
            instruction("Whisk the yogurt and lemon until smooth.", "Bate el yogur y el limón hasta obtener una mezcla lisa."),
            instruction("Toss the vegetables with two-thirds of the dressing just before eating.", "Mezcla las verduras con dos tercios del aliño justo antes de comer."),
            instruction("Flake the tuna over the bowl and spoon the remaining dressing across the top.", "Reparte el atún en lascas sobre el bol y añade por encima el aliño restante.")
        ),
        expandedRecipe(
            "light-chicken-cucumber-yogurt", "Chicken Cucumber Yogurt Plate",
            "Plato de pollo, pepino y yogur", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2407, 160), bedca(2530, 90), bedca(2421, 120), bedca(2399, 80), bedca(411, 15)),
            instruction("Flatten the chicken to an even thickness and pat the surface dry.", "Aplana el pollo hasta igualar su grosor y seca la superficie."),
            instruction("Cook it in a preheated non-stick pan, turning once, until browned and safely cooked.", "Cocínalo en una sartén antiadherente precalentada, dándole una vuelta, hasta que esté dorado y bien hecho."),
            instruction("Rest the chicken for 4 minutes before slicing so it retains its juices.", "Deja reposar el pollo 4 minutos antes de cortarlo para que conserve sus jugos."),
            instruction("Grate half the cucumber, squeeze it dry and mix it with the yogurt and lemon.", "Ralla la mitad del pepino, escúrrelo y mézclalo con el yogur y el limón."),
            instruction("Dice the remaining cucumber and tomato and place them over the lettuce.", "Corta el pepino restante y el tomate y colócalos sobre la lechuga."),
            instruction("Add the sliced chicken and serve the cucumber yogurt alongside.", "Añade el pollo en lonchas y sirve al lado el yogur con pepino.")
        ),
        expandedRecipe(
            "light-hake-pea-tomato", "Hake with Peas and Tomato",
            "Merluza con guisantes y tomate", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 190), bedca(1175, 110), bedca(481, 170), bedca(2382, 35), bedca(2376, 110), bedca(2543, 3)),
            instruction("Pat the hake dry and cut the courgette and onion into small pieces.", "Seca la merluza y corta el calabacín y la cebolla en trozos pequeños."),
            instruction("Soften the onion in the oil, add the courgette and cook for 4 minutes.", "Pocha la cebolla en el aceite, añade el calabacín y cocina 4 minutos."),
            instruction("Add the tomato and simmer uncovered for 6 minutes.", "Añade el tomate y cuece destapado 6 minutos."),
            instruction("Stir in the peas, place the hake on top and cover the pan.", "Incorpora los guisantes, coloca la merluza encima y tapa la sartén."),
            instruction("Cook gently for 7 to 10 minutes until the fish is opaque and the peas are hot.", "Cocina suavemente de 7 a 10 minutos hasta que el pescado esté opaco y los guisantes calientes."),
            instruction("Lift the hake out with a wide spatula so it does not break apart.", "Retira la merluza con una espátula ancha para que no se rompa.")
        ),
        expandedRecipe(
            "light-beef-cauliflower-bowl", "Lean Beef Cauliflower Bowl",
            "Bol ligero de ternera y coliflor", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(981, 140), bedca(859, 230), bedca(1177, 120), bedca(2424, 55), bedca(2575, 10), bedca(2606, 3)),
            instruction("Grate the cauliflower into rice-sized pieces and cut the broccoli and carrot small.", "Ralla la coliflor hasta obtener granos y corta pequeños el brócoli y la zanahoria."),
            instruction("Slice the beef thinly across the grain and mix it with the ginger and half the soy sauce.", "Corta la ternera fina contra la fibra y mézclala con el jengibre y la mitad de la soja."),
            instruction("Sear the beef in a hot non-stick pan in one layer and remove it as soon as it colours.", "Marca la ternera en una sartén antiadherente caliente en una sola capa y retírala al dorarse."),
            instruction("Cook the broccoli and carrot with a splash of water for 3 minutes.", "Cocina el brócoli y la zanahoria con un poco de agua durante 3 minutos."),
            instruction("Add the cauliflower and stir-fry for 4 minutes so excess moisture evaporates.", "Añade la coliflor y saltea 4 minutos para que se evapore el exceso de agua."),
            instruction("Return the beef with the remaining soy sauce and toss only until hot.", "Devuelve la ternera con la soja restante y mezcla solo hasta que esté caliente.")
        ),
        expandedRecipe(
            "light-turkey-broccoli-quinoa", "Turkey Broccoli Quinoa Bowl",
            "Bol de pavo, brócoli y quinoa", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 145), bedca(1083, 45), bedca(1177, 170), bedca(2424, 55), bedca(2575, 10), bedca(411, 12)),
            instruction("Rinse the quinoa and simmer it in measured water until the grains open; cover and rest for 5 minutes.", "Enjuaga la quinoa y cuécela con el agua medida hasta que se abran los granos; tapa y deja reposar 5 minutos."),
            instruction("Cut the turkey into strips and the broccoli into small florets.", "Corta el pavo en tiras y el brócoli en ramilletes pequeños."),
            instruction("Cook the turkey in a hot non-stick pan until lightly browned, then remove it.", "Cocina el pavo en una sartén antiadherente caliente hasta dorarlo ligeramente y retíralo."),
            instruction("Add the broccoli and carrot with a splash of water, cover and steam-fry for 4 minutes.", "Añade el brócoli y la zanahoria con un poco de agua, tapa y cocina 4 minutos."),
            instruction("Return the turkey with the soy sauce and cook until safely done.", "Devuelve el pavo con la soja y cocina hasta que esté completamente hecho."),
            instruction("Fluff the quinoa with lemon juice and serve the turkey mixture over it.", "Suelta la quinoa con el limón y sirve encima la mezcla de pavo.")
        ),
        expandedRecipe(
            "light-prawn-courgette-quinoa", "Prawn Courgette Quinoa Skillet",
            "Sartén de gambas, calabacín y quinoa", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(581, 150), bedca(1083, 45), bedca(2376, 170), bedca(2421, 120), bedca(2361, 2), bedca(411, 14)),
            instruction("Rinse and cook the quinoa in measured water, then cover and rest it for 5 minutes.", "Enjuaga y cuece la quinoa con el agua medida; tápala y déjala reposar 5 minutos."),
            instruction("Slice the courgette into half-moons, chop the tomato and slice the garlic.", "Corta el calabacín en medias lunas, pica el tomate y lamina el ajo."),
            instruction("Cook the prawns in a hot non-stick pan for about 1 minute per side and remove them.", "Cocina las gambas en una sartén antiadherente caliente alrededor de 1 minuto por lado y retíralas."),
            instruction("Cook the courgette until lightly coloured, add the garlic for 30 seconds and then the tomato.", "Cocina el calabacín hasta dorarlo ligeramente, añade el ajo 30 segundos y después el tomate."),
            instruction("Return the prawns and fold in the quinoa only until everything is hot.", "Devuelve las gambas e incorpora la quinoa solo hasta que todo esté caliente."),
            instruction("Take off the heat and add the lemon juice before serving.", "Retira del fuego y añade el zumo de limón antes de servir.")
        ),
        expandedRecipe(
            "light-tofu-spinach-tomato", "Tofu Spinach Tomato Scramble",
            "Revuelto de tofu, espinacas y tomate", RecipeBooks.LOW_CALORIE,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 190), bedca(1282, 150), bedca(2421, 140), bedca(2382, 40), bedca(2621, 3), bedca(2543, 3)),
            instruction("Press the tofu for 10 minutes, then crumble it into bite-sized pieces with your fingers.", "Prensa el tofu 10 minutos y desmenúzalo con los dedos en trozos pequeños."),
            instruction("Soften the onion in the oil in a non-stick pan.", "Pocha la cebolla en el aceite en una sartén antiadherente."),
            instruction("Add the curry and tomato and cook until the tomato releases and then loses most of its liquid.", "Añade el curry y el tomate y cocina hasta que este suelte y pierda casi todo su líquido."),
            instruction("Add the tofu and cook for 4 minutes, turning it so the seasoning coats every piece.", "Añade el tofu y cocina 4 minutos, girándolo para que el condimento cubra todos los trozos."),
            instruction("Fold in the spinach and cook only until wilted.", "Incorpora las espinacas y cocina solo hasta que se ablanden."),
            instruction("Let the scramble stand for 2 minutes so the tofu absorbs the pan juices.", "Deja reposar el revuelto 2 minutos para que el tofu absorba los jugos.")
        ),

        // Desserts
        expandedRecipe(
            "light-baked-peach-raspberry", "Baked Peach with Raspberries",
            "Melocotón asado con frambuesas", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(2239, 180), bedca(914, 80), bedca(2530, 70), bedca(2477, 1)),
            instruction("Heat the oven to 200°C, halve the peach and remove the stone.", "Calienta el horno a 200 °C, parte el melocotón y retira el hueso."),
            instruction("Place the peach cut-side up in a small dish and fill the centres with half the raspberries.", "Pon el melocotón con el corte hacia arriba en una fuente y rellena el centro con la mitad de las frambuesas."),
            instruction("Dust with cinnamon and bake for 15 to 20 minutes until tender.", "Añade canela y hornea de 15 a 20 minutos hasta que esté tierno."),
            instruction("Cool for 5 minutes and serve with the yogurt and remaining fresh raspberries.", "Deja templar 5 minutos y sirve con el yogur y las frambuesas frescas restantes.")
        ),
        expandedRecipe(
            "light-cocoa-banana-mousse", "Cocoa Banana Yogurt Mousse",
            "Mousse de yogur, plátano y cacao", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(2245, 110), bedca(2530, 140), bedca(2431, 8), bedca(914, 40)),
            instruction("Slice and freeze the banana for at least 2 hours.", "Corta y congela el plátano al menos 2 horas."),
            instruction("Blend the frozen banana, yogurt and cocoa until completely smooth and aerated.", "Tritura el plátano congelado, el yogur y el cacao hasta obtener una crema lisa y aireada."),
            instruction("Spoon immediately into a chilled glass so the mixture keeps its mousse-like texture.", "Sirve enseguida en una copa fría para mantener la textura de mousse."),
            instruction("Crush the raspberries lightly and spoon them over the top.", "Machaca ligeramente las frambuesas y repártelas por encima.")
        ),
        expandedRecipe(
            "light-mango-frozen-yogurt", "Mango Frozen Yogurt",
            "Yogur helado de mango", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(1186, 150), bedca(2530, 130), bedca(411, 8)),
            instruction("Cut the mango into small cubes and freeze them in one layer until solid.", "Corta el mango en dados pequeños y congélalos en una sola capa hasta que estén duros."),
            instruction("Blend the frozen mango, yogurt and lemon, stopping to scrape down the sides as needed.", "Tritura el mango congelado, el yogur y el limón, parando para bajar la mezcla de las paredes cuando sea necesario."),
            instruction("Serve immediately for a soft texture, or transfer to a shallow container for a firmer dessert.", "Sirve enseguida para una textura cremosa o pasa a un recipiente bajo para obtener un postre más firme."),
            instruction("If frozen again, let it stand at room temperature for 10 minutes before scooping.", "Si vuelves a congelarlo, déjalo 10 minutos a temperatura ambiente antes de servir.")
        ),
        expandedRecipe(
            "light-orange-raspberry-yogurt", "Orange Raspberry Yogurt Cup",
            "Copa de yogur, naranja y frambuesa", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(2530, 170), bedca(914, 100), bedca(2468, 35), bedca(1010, 15)),
            instruction("Toast the oats in a dry pan for 3 minutes and leave them to cool completely.", "Tuesta la avena en una sartén seca 3 minutos y deja que se enfríe por completo."),
            instruction("Crush half the raspberries with the orange juice to make a loose fruit sauce.", "Machaca la mitad de las frambuesas con el zumo de naranja para formar una salsa ligera."),
            instruction("Layer yogurt, raspberry sauce and the remaining whole berries in a glass.", "Alterna capas de yogur, salsa de frambuesa y las frutas enteras restantes en una copa."),
            instruction("Add the toasted oats immediately before serving so they stay crisp.", "Añade la avena tostada justo antes de servir para que siga crujiente.")
        ),
        expandedRecipe(
            "light-strawberry-oat-parfait", "Strawberry Oat Yogurt Parfait",
            "Parfait de yogur, fresa y avena", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(2225, 150), bedca(2530, 160), bedca(1010, 20), bedca(2643, 4)),
            instruction("Toast the oats in a dry pan until lightly golden and let them cool.", "Tuesta la avena en una sartén seca hasta que se dore ligeramente y deja que se enfríe."),
            instruction("Slice two-thirds of the strawberries and mash the remainder with the honey.", "Corta en láminas dos tercios de las fresas y machaca el resto con la miel."),
            instruction("Spoon half the yogurt into a glass and add the mashed and sliced strawberries.", "Pon la mitad del yogur en una copa y añade las fresas machacadas y en láminas."),
            instruction("Add the remaining yogurt and finish with the cooled toasted oats.", "Añade el yogur restante y termina con la avena tostada y fría.")
        ),
        expandedRecipe(
            "light-cinnamon-grape-compote", "Warm Cinnamon Grape Compote",
            "Compota templada de uvas y canela", RecipeBooks.LOW_CALORIE,
            RecipeCategories.DESSERT,
            listOf(bedca(1017, 170), bedca(2530, 90), bedca(2477, 1), bedca(411, 8)),
            instruction("Halve the grapes and place them in a small saucepan with the lemon and cinnamon.", "Parte las uvas y ponlas en un cazo pequeño con el limón y la canela."),
            instruction("Cover and cook over low heat for 5 minutes until the grapes release their juice.", "Tapa y cocina a fuego bajo 5 minutos hasta que las uvas suelten su jugo."),
            instruction("Uncover and simmer for another 2 to 3 minutes until the juice becomes lightly syrupy.", "Destapa y cuece otros 2 o 3 minutos hasta que el jugo espese ligeramente."),
            instruction("Cool until warm rather than hot and spoon the compote over the yogurt.", "Deja templar y sirve la compota sobre el yogur sin que esté demasiado caliente.")
        )
    )
}
