package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories

/** Familiar dishes from several food cultures, adapted only where BEDCA requires it. */
internal object WorldRecipeExpansion {
    val recipes = listOf(
        // Breakfasts
        expandedRecipe(
            "world-turkish-menemen", "Turkish Menemen-Style Eggs",
            "Huevos menemen al estilo turco", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(1173, 100), bedca(2125, 35), bedca(2421, 150), bedca(2409, 90), bedca(2382, 35), bedca(2543, 5)),
            instruction("Dice the tomato, pepper and onion into small pieces so they soften quickly.", "Corta el tomate, el pimiento y la cebolla en trozos pequeños para que se ablanden rápido."),
            instruction("Heat the oil in a wide pan and cook the onion and pepper over medium heat for 6 minutes.", "Calienta el aceite en una sartén amplia y cocina la cebolla y el pimiento a fuego medio 6 minutos."),
            instruction("Add the tomato and cook uncovered until the mixture is thick and no longer watery.", "Añade el tomate y cocina destapado hasta que la mezcla esté espesa y sin exceso de agua."),
            instruction("Beat the egg whites and yolk lightly and pour them across the vegetables.", "Bate ligeramente las claras y la yema y viértelas sobre las verduras."),
            instruction("Stir slowly over low heat and remove the pan while the eggs are still soft and glossy.", "Remueve despacio a fuego bajo y retira la sartén cuando el huevo aún esté suave y brillante.")
        ),
        expandedRecipe(
            "world-indian-upma", "Indian Vegetable Semolina Upma",
            "Upma indio de sémola y verduras", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(1084, 55), bedca(1175, 55), bedca(2424, 55), bedca(2382, 35), bedca(2621, 3), bedca(2606, 2)),
            instruction("Toast the semolina in a dry pan over medium-low heat for 4 minutes, stirring, then set it aside.", "Tuesta la sémola en una sartén seca a fuego medio-bajo durante 4 minutos, removiendo, y resérvala."),
            instruction("Cook the onion, carrot, curry and ginger with a splash of water until the onion softens.", "Cocina la cebolla, la zanahoria, el curry y el jengibre con un poco de agua hasta que la cebolla se ablande."),
            instruction("Add the peas and 180 ml water and bring the mixture to a steady simmer.", "Añade los guisantes y 180 ml de agua y lleva la mezcla a hervor suave."),
            instruction("Pour in the toasted semolina gradually while stirring continuously.", "Incorpora poco a poco la sémola tostada mientras remueves sin parar."),
            instruction("Cook over low heat until the water is absorbed and the grains are tender, about 4 minutes.", "Cocina a fuego bajo hasta que se absorba el agua y el grano esté tierno, unos 4 minutos."),
            instruction("Cover, rest for 2 minutes and fluff with a fork before serving.", "Tapa, deja reposar 2 minutos y suelta con un tenedor antes de servir.")
        ),
        expandedRecipe(
            "world-japanese-tamago-rice", "Japanese-Style Tamago Rice Bowl",
            "Bol japonés de arroz y huevo tamago", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(2660, 60), bedca(1173, 100), bedca(2125, 30), bedca(2575, 8), bedca(2407, 80), bedca(752, 2)),
            instruction("Cook the rice in measured water, cover it and let it steam off the heat for 5 minutes.", "Cuece el arroz con el agua medida, tápalo y deja que repose al vapor fuera del fuego 5 minutos."),
            instruction("Beat the egg whites and yolk with half the soy sauce until evenly combined.", "Bate las claras y la yema con la mitad de la soja hasta integrar."),
            instruction("Pour into a warm non-stick pan and draw the cooked edges inward until softly set.", "Vierte en una sartén antiadherente templada y lleva los bordes cuajados hacia el centro hasta que quede suave."),
            instruction("Roll or fold the omelette in the pan and cut it into thick slices.", "Enrolla o dobla la tortilla en la sartén y córtala en rodajas gruesas."),
            instruction("Fluff the rice, add sliced cucumber and omelette and finish with the remaining soy and sesame oil.", "Suelta el arroz, añade el pepino y la tortilla y termina con la soja restante y el aceite de sésamo.")
        ),
        expandedRecipe(
            "world-mexican-breakfast-quesadilla", "Mexican Breakfast Quesadilla",
            "Quesadilla mexicana de desayuno", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(1113, 65), bedca(1173, 80), bedca(2125, 25), bedca(1005, 30), bedca(2421, 100), bedca(2409, 55)),
            instruction("Mix the flour with about 40 ml warm water into a soft dough and rest it covered for 10 minutes.", "Mezcla la harina con unos 40 ml de agua templada hasta formar una masa blanda y déjala reposar tapada 10 minutos."),
            instruction("Roll the dough into two thin rounds and cook each in a dry hot pan until lightly spotted.", "Estira la masa en dos discos finos y cocina cada uno en una sartén seca y caliente hasta que tenga manchas doradas."),
            instruction("Dice and cook the pepper until tender, then add the beaten egg white and yolk and scramble softly.", "Corta y cocina el pimiento hasta que esté tierno; añade la clara y la yema batidas y cuaja suavemente."),
            instruction("Place the egg, cheese and diced tomato on one tortilla and cover with the second.", "Coloca el huevo, el queso y el tomate en dados sobre una tortilla y cubre con la segunda."),
            instruction("Toast for 2 minutes per side over medium heat until the cheese melts.", "Tuesta 2 minutos por lado a fuego medio hasta que el queso se funda."),
            instruction("Rest for 1 minute and cut into wedges so the filling does not spill out.", "Deja reposar 1 minuto y corta en porciones para que no se salga el relleno.")
        ),
        expandedRecipe(
            "world-american-raspberry-oatmeal", "American-Style Raspberry Oatmeal",
            "Avena americana con frambuesas", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(1010, 50), bedca(915, 230), bedca(914, 100), bedca(979, 12), bedca(2477, 1)),
            instruction("Bring the milk to a gentle simmer in a small saucepan.", "Lleva la leche a hervor suave en un cazo pequeño."),
            instruction("Stir in the oats, lower the heat and cook for 5 minutes, stirring frequently.", "Incorpora la avena, baja el fuego y cocina 5 minutos, removiendo con frecuencia."),
            instruction("Add half the raspberries and press them lightly so their juice streaks the oatmeal.", "Añade la mitad de las frambuesas y presiónalas ligeramente para que su jugo tiña la avena."),
            instruction("Pour into a bowl and top with the remaining berries, peanut butter and cinnamon.", "Sirve en un cuenco y termina con las frambuesas restantes, la crema de cacahuete y la canela.")
        ),
        expandedRecipe(
            "world-middle-east-chickpea-shakshuka", "Middle Eastern Chickpea Shakshuka",
            "Shakshuka de garbanzos", RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(bedca(2191, 100), bedca(1173, 80), bedca(2125, 25), bedca(481, 170), bedca(2409, 70), bedca(2571, 2)),
            instruction("Cook the diced pepper in a covered non-stick pan with a splash of water for 5 minutes.", "Cocina el pimiento en dados en una sartén antiadherente tapada con un poco de agua durante 5 minutos."),
            instruction("Add the cumin and tomato and simmer uncovered for 8 minutes until thick.", "Añade el comino y el tomate y cuece destapado 8 minutos hasta que espese."),
            instruction("Stir in the drained chickpeas and heat them through for 3 minutes.", "Incorpora los garbanzos escurridos y caliéntalos 3 minutos."),
            instruction("Make a hollow in the sauce and add the egg white and yolk.", "Abre un hueco en la salsa y añade la clara y la yema."),
            instruction("Cover and cook gently until the white is set and the yolk reaches the preferred doneness.", "Tapa y cocina suavemente hasta que la clara cuaje y la yema quede al punto deseado.")
        ),

        // Main dishes
        expandedRecipe(
            "world-vietnamese-ginger-chicken", "Vietnamese-Style Ginger Lime Chicken Rice",
            "Arroz con pollo, jengibre y lima al estilo vietnamita", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2660, 65), bedca(2606, 5), bedca(1001, 20), bedca(2575, 11), bedca(2407, 90)),
            instruction("Cook the rice in measured water, cover it and rest it for 5 minutes off the heat.", "Cuece el arroz con el agua medida, tápalo y déjalo reposar 5 minutos fuera del fuego."),
            instruction("Cut the chicken into thin pieces and mix it with the ginger, half the lime and half the soy sauce.", "Corta el pollo fino y mézclalo con el jengibre, la mitad de la lima y la mitad de la soja."),
            instruction("Leave the chicken for 10 minutes while slicing the cucumber.", "Deja marinar el pollo 10 minutos mientras cortas el pepino."),
            instruction("Cook the chicken in one layer in a hot non-stick pan, turning until browned and safely done.", "Cocina el pollo en una sola capa en una sartén antiadherente caliente hasta que esté dorado y bien hecho."),
            instruction("Add the remaining soy and lime to the pan and let them glaze the chicken for 30 seconds.", "Añade la soja y la lima restantes y deja que glaseen el pollo durante 30 segundos."),
            instruction("Serve over the rice with the cool cucumber alongside.", "Sirve sobre el arroz con el pepino fresco al lado.")
        ),
        expandedRecipe(
            "world-thai-basil-chicken", "Thai-Style Basil Chicken",
            "Pollo con albahaca al estilo tailandés", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 160), bedca(904, 60), bedca(2409, 90), bedca(2382, 35), bedca(2575, 12), bedca(2473, 8)),
            instruction("Cook the brown rice until tender, drain if necessary and keep it covered.", "Cuece el arroz integral hasta que esté tierno, escúrrelo si hace falta y mantenlo tapado."),
            instruction("Cut the chicken and pepper into small pieces and finely chop the onion.", "Corta el pollo y el pimiento en trozos pequeños y pica fina la cebolla."),
            instruction("Cook the chicken in a hot non-stick pan until lightly browned.", "Cocina el pollo en una sartén antiadherente caliente hasta que se dore ligeramente."),
            instruction("Add the onion and pepper and stir-fry until the chicken is done and the pepper is tender-crisp.", "Añade la cebolla y el pimiento y saltea hasta que el pollo esté hecho y el pimiento siga algo crujiente."),
            instruction("Add the soy sauce and toss for 30 seconds, then remove the pan from the heat.", "Añade la soja y mezcla 30 segundos; retira la sartén del fuego."),
            instruction("Fold in the basil after cooking and serve immediately over the rice.", "Incorpora la albahaca después de cocinar y sirve enseguida sobre el arroz.")
        ),
        expandedRecipe(
            "world-chinese-beef-broccoli", "Chinese-Style Beef and Broccoli",
            "Ternera con brócoli al estilo chino", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(981, 150), bedca(1177, 190), bedca(2660, 60), bedca(2575, 13), bedca(2606, 4), bedca(752, 2)),
            instruction("Cook the rice and keep it covered while preparing the stir-fry.", "Cuece el arroz y mantenlo tapado mientras preparas el salteado."),
            instruction("Slice the beef very thinly across the grain and mix it with ginger and half the soy sauce.", "Corta la ternera muy fina contra la fibra y mézclala con el jengibre y la mitad de la soja."),
            instruction("Steam or blanch the broccoli for 2 minutes, then drain it thoroughly.", "Cocina el brócoli al vapor o escáldalo 2 minutos y escúrrelo bien."),
            instruction("Sear the beef in a very hot pan in batches so the pieces brown instead of steaming.", "Marca la ternera por tandas en una sartén muy caliente para que se dore y no se cueza."),
            instruction("Return all the beef, add the broccoli and remaining soy sauce and toss for 1 to 2 minutes.", "Junta toda la ternera, añade el brócoli y la soja restante y saltea 1 o 2 minutos."),
            instruction("Finish off the heat with sesame oil and serve over rice.", "Termina fuera del fuego con el aceite de sésamo y sirve sobre el arroz.")
        ),
        expandedRecipe(
            "world-japanese-oyakodon", "Japanese-Style Chicken Egg Rice Bowl",
            "Oyakodon de pollo y huevo al estilo japonés", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 140), bedca(2660, 65), bedca(1173, 80), bedca(2125, 25), bedca(2382, 45), bedca(2575, 10)),
            instruction("Cook the rice in measured water and leave it covered to steam.", "Cuece el arroz con el agua medida y déjalo tapado para que repose al vapor."),
            instruction("Slice the chicken thinly and the onion into fine arcs.", "Corta el pollo fino y la cebolla en tiras curvas."),
            instruction("Put the onion, soy sauce and 80 ml water in a small pan and simmer for 3 minutes.", "Pon la cebolla, la soja y 80 ml de agua en una sartén pequeña y cuece 3 minutos."),
            instruction("Add the chicken in one layer, cover and simmer until almost cooked, about 5 minutes.", "Añade el pollo en una sola capa, tapa y cuece hasta que esté casi hecho, unos 5 minutos."),
            instruction("Beat the egg white and yolk lightly, pour them around the chicken and cover again.", "Bate ligeramente la clara y la yema, viértelas alrededor del pollo y vuelve a tapar."),
            instruction("Cook only until softly set, then slide the mixture and its broth over the hot rice.", "Cocina solo hasta que el huevo cuaje suavemente y desliza la mezcla con su caldo sobre el arroz caliente.")
        ),
        expandedRecipe(
            "world-korean-tofu-bibimbap", "Korean-Style Tofu Bibimbap",
            "Bibimbap coreano con tofu", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 160), bedca(904, 60), bedca(1282, 100), bedca(2424, 60), bedca(2384, 100), bedca(2575, 11), bedca(752, 2)),
            instruction("Cook the brown rice until tender and keep it warm.", "Cuece el arroz integral hasta que esté tierno y mantenlo caliente."),
            instruction("Press and cube the tofu, then brown it in a non-stick pan and set it aside.", "Prensa y corta el tofu en dados, dóralo en una sartén antiadherente y resérvalo."),
            instruction("Cook the mushrooms until their moisture evaporates and transfer them to a plate.", "Cocina los champiñones hasta que se evapore su agua y pásalos a un plato."),
            instruction("Stir-fry the carrot for 2 minutes, then wilt the spinach separately so each vegetable keeps its texture.", "Saltea la zanahoria 2 minutos y cocina aparte las espinacas para que cada verdura conserve su textura."),
            instruction("Arrange the tofu, mushrooms, carrot and spinach in separate sections over the rice.", "Coloca el tofu, los champiñones, la zanahoria y las espinacas en secciones separadas sobre el arroz."),
            instruction("Mix the soy and sesame oil, spoon it over the bowl and combine everything at the table.", "Mezcla la soja y el aceite de sésamo, repártelo sobre el bol y mezcla todo en la mesa.")
        ),
        expandedRecipe(
            "world-indian-bean-masala", "Indian White Bean Masala with Rice",
            "Masala indio de alubias blancas con arroz", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2199, 130), bedca(2660, 60), bedca(481, 170), bedca(2382, 45), bedca(2621, 4), bedca(2606, 3)),
            instruction("Cook the rice in measured water and keep it covered.", "Cuece el arroz con el agua medida y mantenlo tapado."),
            instruction("Finely chop the onion and cook it with a splash of water until soft and lightly coloured.", "Pica fina la cebolla y cocínala con un poco de agua hasta que esté tierna y ligeramente dorada."),
            instruction("Add the curry and ginger and stir for 30 seconds to release their aroma.", "Añade el curry y el jengibre y remueve 30 segundos para liberar su aroma."),
            instruction("Add the tomato and simmer for 8 minutes until the sauce darkens and thickens.", "Añade el tomate y cuece 8 minutos hasta que la salsa se oscurezca y espese."),
            instruction("Fold in the drained beans with a splash of water and simmer for 6 minutes.", "Incorpora las alubias escurridas con un poco de agua y cuece 6 minutos."),
            instruction("Mash a spoonful of beans into the sauce and serve the masala over rice.", "Machaca una cucharada de alubias dentro de la salsa y sirve el masala sobre el arroz.")
        ),
        expandedRecipe(
            "world-chana-masala", "Indian Chana Masala",
            "Chana masala de garbanzos", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2191, 180), bedca(481, 180), bedca(2382, 45), bedca(2361, 3), bedca(2571, 3), bedca(2606, 3), bedca(1071, 55)),
            instruction("Drain and rinse the chickpeas and finely chop the onion and garlic.", "Escurre y enjuaga los garbanzos y pica finos la cebolla y el ajo."),
            instruction("Cook the onion with a splash of water until deeply soft, adding more water if it catches.", "Cocina la cebolla con un poco de agua hasta que esté muy tierna y añade más agua si se pega."),
            instruction("Add the garlic, cumin and ginger and stir for 45 seconds.", "Añade el ajo, el comino y el jengibre y remueve 45 segundos."),
            instruction("Add the tomato and simmer until the spice paste is thick, about 8 minutes.", "Añade el tomate y cuece hasta obtener una pasta especiada espesa, unos 8 minutos."),
            instruction("Add the chickpeas and 100 ml water and simmer uncovered for 10 minutes.", "Añade los garbanzos y 100 ml de agua y cuece destapado 10 minutos."),
            instruction("Crush a few chickpeas to thicken the sauce and serve with warmed corn bread.", "Machaca algunos garbanzos para espesar la salsa y sirve con el pan de maíz caliente.")
        ),
        expandedRecipe(
            "world-turkey-keema", "Indian-Style Turkey Keema with Peas",
            "Keema de pavo con guisantes", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 160), bedca(1175, 90), bedca(481, 160), bedca(2382, 45), bedca(2621, 4), bedca(2660, 55)),
            instruction("Cook the rice and keep it covered while making the keema.", "Cuece el arroz y mantenlo tapado mientras preparas el keema."),
            instruction("Finely chop the onion and cook it until soft, then add the curry and stir for 30 seconds.", "Pica fina la cebolla y cocínala hasta que esté tierna; añade el curry y remueve 30 segundos."),
            instruction("Add the turkey and break it into very small pieces while it browns.", "Añade el pavo y sepáralo en trozos muy pequeños mientras se dora."),
            instruction("Add the tomato and simmer for 10 minutes until the meat is fully cooked.", "Añade el tomate y cuece 10 minutos hasta que la carne esté completamente hecha."),
            instruction("Fold in the peas and cook for another 3 minutes without overcooking them.", "Incorpora los guisantes y cocina otros 3 minutos sin pasarlos."),
            instruction("Serve the loose, moist keema over the rice.", "Sirve el keema suelto y jugoso sobre el arroz.")
        ),
        expandedRecipe(
            "world-mexican-beef-tacos", "Mexican-Style Beef Tacos",
            "Tacos de ternera al estilo mexicano", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1113, 70), bedca(2680, 140), bedca(481, 100), bedca(2399, 60), bedca(2421, 80), bedca(2571, 3)),
            instruction("Mix the flour with about 45 ml warm water into a soft dough, cover and rest for 10 minutes.", "Mezcla la harina con unos 45 ml de agua templada hasta formar una masa blanda, tapa y deja reposar 10 minutos."),
            instruction("Divide and roll the dough into two thin tortillas and cook them in a dry hot pan until spotted.", "Divide y estira la masa en dos tortillas finas y cocínalas en una sartén seca y caliente hasta que se doren por puntos."),
            instruction("Brown the beef in the pan, breaking it into small pieces, then add cumin.", "Dora la ternera en la sartén, separándola en trozos pequeños, y añade el comino."),
            instruction("Add the ground tomato and cook until it coats the beef rather than pooling in the pan.", "Añade el tomate triturado y cocina hasta que cubra la carne sin quedar líquido en la sartén."),
            instruction("Warm the tortillas briefly so they bend without cracking.", "Calienta brevemente las tortillas para que se doblen sin romperse."),
            instruction("Fill with lettuce, fresh tomato and beef and serve immediately.", "Rellena con lechuga, tomate fresco y ternera y sirve enseguida.")
        ),
        expandedRecipe(
            "world-chicken-enchilada-skillet", "Chicken Enchilada-Style Skillet",
            "Sartén de pollo al estilo enchilada", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(1071, 65), bedca(481, 180), bedca(2409, 90), bedca(1005, 30), bedca(2592, 3)),
            instruction("Cut the chicken and pepper into small pieces and the corn bread into 2 cm cubes.", "Corta el pollo y el pimiento en trozos pequeños y el pan de maíz en dados de 2 cm."),
            instruction("Cook the chicken in a non-stick skillet until lightly browned.", "Cocina el pollo en una sartén antiadherente hasta que se dore ligeramente."),
            instruction("Add the pepper and paprika and cook for 3 minutes.", "Añade el pimiento y el pimentón y cocina 3 minutos."),
            instruction("Pour in the tomato, cover and simmer until the chicken is safely cooked.", "Incorpora el tomate, tapa y cuece hasta que el pollo esté completamente hecho."),
            instruction("Fold in the corn bread cubes, leaving some exposed so they do not all become soft.", "Incorpora los dados de pan de maíz dejando algunos en la superficie para que no se ablanden todos."),
            instruction("Scatter over the cheese, cover for 2 minutes to melt it and serve from the skillet.", "Reparte el queso, tapa 2 minutos para fundirlo y sirve desde la sartén.")
        ),
        expandedRecipe(
            "world-bean-quesadilla", "Bean and Pepper Quesadilla",
            "Quesadilla de alubias y pimiento", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1113, 70), bedca(2199, 100), bedca(2409, 80), bedca(481, 80), bedca(1005, 35), bedca(2571, 2)),
            instruction("Make a soft dough with the flour and about 45 ml warm water, cover and rest for 10 minutes.", "Forma una masa blanda con la harina y unos 45 ml de agua templada, tapa y deja reposar 10 minutos."),
            instruction("Roll two thin tortillas and cook them separately in a dry hot pan.", "Estira dos tortillas finas y cocínalas por separado en una sartén seca y caliente."),
            instruction("Dice and soften the pepper, then add the cumin, tomato and drained beans.", "Corta y cocina el pimiento; añade el comino, el tomate y las alubias escurridas."),
            instruction("Mash about half the beans so the filling holds together and cook until thick.", "Machaca aproximadamente la mitad de las alubias para unir el relleno y cocina hasta que espese."),
            instruction("Spread the filling and cheese on one tortilla, cover with the other and return to the pan.", "Reparte el relleno y el queso sobre una tortilla, cubre con la otra y devuelve a la sartén."),
            instruction("Toast for 2 minutes per side, rest for 1 minute and cut into wedges.", "Tuesta 2 minutos por lado, deja reposar 1 minuto y corta en porciones.")
        ),
        expandedRecipe(
            "world-fish-fajita", "Mexican-Style Fish Fajita Plate",
            "Fajitas de pescado al estilo mexicano", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 180), bedca(1113, 65), bedca(2409, 100), bedca(2382, 40), bedca(1001, 20), bedca(2592, 3)),
            instruction("Mix the flour with about 40 ml water, knead briefly, rest covered and roll into two tortillas.", "Mezcla la harina con unos 40 ml de agua, amasa brevemente, deja reposar tapada y estira dos tortillas."),
            instruction("Cook the tortillas in a dry hot pan and wrap them in a clean cloth to keep them flexible.", "Cocina las tortillas en una sartén seca y caliente y envuélvelas en un paño limpio para mantenerlas flexibles."),
            instruction("Cut the pepper and onion into strips and cook them until tender at the edges.", "Corta el pimiento y la cebolla en tiras y cocínalos hasta que los bordes estén tiernos."),
            instruction("Pat the hake dry, dust it with paprika and cook it until opaque and flaky.", "Seca la merluza, añade el pimentón y cocínala hasta que esté opaca y se separe en lascas."),
            instruction("Break the fish into large pieces and toss it once with the peppers and lime.", "Separa el pescado en trozos grandes y mézclalo una vez con los pimientos y la lima."),
            instruction("Fill the warm tortillas immediately so the fish stays moist.", "Rellena las tortillas calientes enseguida para que el pescado siga jugoso.")
        ),
        expandedRecipe(
            "world-caribbean-prawn-rice", "Caribbean-Style Prawn Rice",
            "Arroz caribeño con gambas", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(581, 160), bedca(2660, 65), bedca(2409, 80), bedca(1175, 65), bedca(481, 110), bedca(793, 2)),
            instruction("Cook the rice until tender and spread it briefly on a plate so excess steam escapes.", "Cuece el arroz hasta que esté tierno y extiéndelo brevemente en un plato para que salga el exceso de vapor."),
            instruction("Dice the pepper and cook it in a non-stick pan for 3 minutes.", "Corta el pimiento en dados y cocínalo en una sartén antiadherente 3 minutos."),
            instruction("Add the tomato, peas and chilli and cook until the tomato is no longer watery.", "Añade el tomate, los guisantes y el chile y cocina hasta que el tomate deje de estar aguado."),
            instruction("Add the prawns in one layer and cook until nearly opaque, turning once.", "Añade las gambas en una sola capa y cocina hasta que estén casi opacas, dándoles una vuelta."),
            instruction("Fold in the rice and heat for 1 to 2 minutes until the prawns are just cooked.", "Incorpora el arroz y calienta 1 o 2 minutos hasta que las gambas estén justo hechas."),
            instruction("Serve at once; holding the pan over heat will toughen the prawns.", "Sirve enseguida; mantener la sartén al fuego endurecería las gambas.")
        ),
        expandedRecipe(
            "world-jamaican-bean-stew", "Jamaican-Style Bean and Pumpkin Stew",
            "Guiso jamaicano de alubias y calabaza", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2199, 130), bedca(2378, 200), bedca(911, 60), bedca(481, 130), bedca(2382, 40), bedca(2606, 3)),
            instruction("Cut the pumpkin into equal 2 cm cubes and drain the beans.", "Corta la calabaza en dados iguales de 2 cm y escurre las alubias."),
            instruction("Cook the onion and ginger with a splash of water until the onion is soft.", "Cocina la cebolla y el jengibre con un poco de agua hasta que la cebolla esté tierna."),
            instruction("Add the pumpkin and tomato with 100 ml water, cover and simmer for 10 minutes.", "Añade la calabaza y el tomate con 100 ml de agua, tapa y cuece 10 minutos."),
            instruction("Add the coconut milk and beans and simmer uncovered until the pumpkin is tender.", "Añade la leche de coco y las alubias y cuece destapado hasta que la calabaza esté tierna."),
            instruction("Mash two pumpkin cubes into the sauce to thicken it without flour.", "Machaca dos dados de calabaza dentro de la salsa para espesarla sin harina."),
            instruction("Rest for 3 minutes before serving because the thick sauce holds heat.", "Deja reposar 3 minutos antes de servir porque la salsa espesa conserva el calor.")
        ),
        expandedRecipe(
            "world-moroccan-fish-tagine", "Moroccan-Style Fish and Vegetable Tagine",
            "Tajín de pescado y verduras al estilo marroquí", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 180), bedca(2409, 90), bedca(2376, 120), bedca(481, 160), bedca(2571, 3), bedca(2592, 3)),
            instruction("Cut the pepper and courgette into even pieces and pat the hake dry.", "Corta el pimiento y el calabacín en trozos iguales y seca la merluza."),
            instruction("Cook the pepper and courgette in a covered pan with a splash of water for 5 minutes.", "Cocina el pimiento y el calabacín en una sartén tapada con un poco de agua durante 5 minutos."),
            instruction("Add the cumin, paprika and tomato and simmer for 7 minutes.", "Añade el comino, el pimentón y el tomate y cuece 7 minutos."),
            instruction("Place the hake over the vegetables in one layer and spoon sauce over the top.", "Coloca la merluza sobre las verduras en una sola capa y cúbrela con un poco de salsa."),
            instruction("Cover and cook gently for 7 to 10 minutes until the fish flakes easily.", "Tapa y cocina suavemente de 7 a 10 minutos hasta que el pescado se separe en lascas."),
            instruction("Lift the fish out carefully and spoon the thick vegetable sauce around it.", "Retira el pescado con cuidado y sirve la salsa espesa de verduras alrededor.")
        ),
        expandedRecipe(
            "world-lebanese-shawarma-chicken", "Lebanese-Style Chicken Shawarma Bowl",
            "Bol de pollo shawarma al estilo libanés", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 160), bedca(1084, 60), bedca(2407, 90), bedca(2421, 100), bedca(2530, 55), bedca(2571, 2), bedca(2592, 2)),
            instruction("Cut the chicken into thin strips and coat it with cumin, paprika and half the yogurt.", "Corta el pollo en tiras finas y mézclalo con el comino, el pimentón y la mitad del yogur."),
            instruction("Marinate for 15 minutes while covering the semolina with measured boiling water.", "Deja marinar 15 minutos mientras cubres la sémola con el agua hirviendo medida."),
            instruction("Cover the semolina for 5 minutes, then fluff it thoroughly with a fork.", "Tapa la sémola 5 minutos y suéltala bien con un tenedor."),
            instruction("Cook the chicken in one layer in a hot pan, turning until browned and safely done.", "Cocina el pollo en una sola capa en una sartén caliente, girándolo hasta que esté dorado y bien hecho."),
            instruction("Dice the cucumber and tomato and mix them with the remaining yogurt.", "Corta el pepino y el tomate y mézclalos con el yogur restante."),
            instruction("Assemble the semolina, hot chicken and cool yogurt salad in separate sections of a bowl.", "Monta la sémola, el pollo caliente y la ensalada de yogur fría en secciones separadas del bol.")
        ),
        expandedRecipe(
            "world-turkish-lentil-kofte", "Turkish-Style Lentil Köfte",
            "Köfte turco de lentejas", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1065, 65), bedca(1084, 45), bedca(2382, 40), bedca(481, 100), bedca(2571, 2), bedca(2399, 80)),
            instruction("Rinse the lentils and simmer them in 220 ml water until very soft and most liquid is absorbed.", "Enjuaga las lentejas y cuécelas en 220 ml de agua hasta que estén muy tiernas y se absorba casi todo el líquido."),
            instruction("Remove from the heat, stir in the semolina, cover and leave for 15 minutes to hydrate.", "Retira del fuego, incorpora la sémola, tapa y deja 15 minutos para que se hidrate."),
            instruction("Finely chop and soften the onion, then add the tomato and cumin and cook until thick.", "Pica fina y pocha la cebolla; añade el tomate y el comino y cocina hasta que espese."),
            instruction("Mix the onion paste into the warm lentils and knead with a spoon until the mixture binds.", "Mezcla la pasta de cebolla con las lentejas templadas y trabaja con una cuchara hasta que ligue."),
            instruction("Cool enough to handle, then shape small oval köfte with damp hands.", "Deja templar y forma köfte ovalados pequeños con las manos húmedas."),
            instruction("Serve at room temperature wrapped in crisp lettuce leaves.", "Sirve a temperatura ambiente envueltos en hojas crujientes de lechuga.")
        ),
        expandedRecipe(
            "world-ethiopian-lentil-stew", "Ethiopian-Style Spiced Lentil Stew",
            "Guiso etíope especiado de lentejas", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1065, 75), bedca(481, 170), bedca(2382, 55), bedca(2424, 65), bedca(2592, 3), bedca(793, 1), bedca(1071, 55)),
            instruction("Rinse the lentils until the water runs mostly clear and finely chop the onion and carrot.", "Enjuaga las lentejas hasta que el agua salga casi limpia y pica finas la cebolla y la zanahoria."),
            instruction("Cook the onion and carrot with small splashes of water until deeply soft.", "Cocina la cebolla y la zanahoria añadiendo pequeñas cantidades de agua hasta que estén muy tiernas."),
            instruction("Add the paprika and chilli and stir for 20 seconds without scorching the spices.", "Añade el pimentón y el chile y remueve 20 segundos sin quemar las especias."),
            instruction("Add the tomato, lentils and 300 ml water and bring slowly to a simmer.", "Añade el tomate, las lentejas y 300 ml de agua y lleva lentamente a hervor suave."),
            instruction("Cook uncovered for 25 to 30 minutes, stirring near the end, until the lentils break down into a thick stew.", "Cuece destapado de 25 a 30 minutos, removiendo al final, hasta que las lentejas formen un guiso espeso."),
            instruction("Warm the corn bread and use it to scoop up the stew.", "Calienta el pan de maíz y úsalo para acompañar el guiso.")
        ),
        expandedRecipe(
            "world-french-chicken-ratatouille", "French Chicken Ratatouille",
            "Ratatouille francesa con pollo", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2370, 120), bedca(2376, 120), bedca(2409, 80), bedca(481, 150), bedca(2382, 40)),
            instruction("Cut the aubergine, courgette and pepper into separate bowls of equal-sized pieces.", "Corta la berenjena, el calabacín y el pimiento en trozos iguales y mantenlos separados."),
            instruction("Brown the chicken in a wide non-stick casserole and transfer it to a plate.", "Dora el pollo en una cazuela antiadherente amplia y pásalo a un plato."),
            instruction("Cook the aubergine first for 5 minutes, then add the onion and pepper for 4 minutes.", "Cocina primero la berenjena 5 minutos y después añade la cebolla y el pimiento 4 minutos."),
            instruction("Add the courgette and tomato and simmer uncovered for 6 minutes.", "Añade el calabacín y el tomate y cuece destapado 6 minutos."),
            instruction("Return the chicken, cover and cook gently until it is safely done and the vegetables are tender.", "Devuelve el pollo, tapa y cocina suavemente hasta que esté bien hecho y las verduras tiernas."),
            instruction("Uncover for the final minutes if the ratatouille is watery, then rest for 3 minutes.", "Destapa los últimos minutos si la ratatouille está aguada y deja reposar 3 minutos.")
        ),
        expandedRecipe(
            "world-american-turkey-meatloaf", "American-Style Turkey Meatloaf",
            "Pastel de carne de pavo al estilo americano", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 170), bedca(2171, 20), bedca(1173, 30), bedca(481, 120), bedca(2382, 40), bedca(2424, 50)),
            instruction("Heat the oven to 190°C and line a small loaf tin or compact baking dish.", "Calienta el horno a 190 °C y forra un molde pequeño o una fuente compacta."),
            instruction("Finely grate the carrot and chop the onion so no large pieces split the loaf.", "Ralla fina la zanahoria y pica la cebolla para que no haya trozos grandes que rompan el pastel."),
            instruction("Mix the turkey, breadcrumbs, egg white, carrot, onion and half the tomato only until combined.", "Mezcla el pavo, el pan rallado, la clara, la zanahoria, la cebolla y la mitad del tomate solo hasta integrar."),
            instruction("Shape the mixture in the tin without pressing it hard and spread the remaining tomato on top.", "Da forma a la mezcla en el molde sin prensarla y reparte el tomate restante por encima."),
            instruction("Bake for 30 to 38 minutes until the centre is fully cooked.", "Hornea de 30 a 38 minutos hasta que el centro esté completamente hecho."),
            instruction("Rest for 7 minutes before slicing so the loaf stays moist and holds together.", "Deja reposar 7 minutos antes de cortar para que quede jugoso y mantenga la forma.")
        ),
        expandedRecipe(
            "world-vietnamese-tofu-noodle", "Vietnamese-Style Tofu Noodle Bowl",
            "Bol vietnamita de fideos y tofu", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1121, 160), bedca(1074, 70), bedca(2407, 100), bedca(2424, 60), bedca(1001, 20), bedca(2575, 10)),
            instruction("Press and cube the tofu and cut the cucumber and carrot into thin matchsticks.", "Prensa y corta el tofu en dados y el pepino y la zanahoria en tiras finas."),
            instruction("Cook the noodles until just tender, rinse them briefly in cool water and drain thoroughly.", "Cuece los fideos hasta que estén justo tiernos, enjuágalos brevemente con agua fría y escúrrelos bien."),
            instruction("Brown the tofu in a non-stick pan, turning until several sides are crisp.", "Dora el tofu en una sartén antiadherente hasta que varios lados estén crujientes."),
            instruction("Whisk the lime and soy sauce with one spoonful of water.", "Bate la lima y la soja con una cucharada de agua."),
            instruction("Toss the noodles with half the dressing and arrange the vegetables and tofu on top.", "Mezcla los fideos con la mitad del aliño y coloca encima las verduras y el tofu."),
            instruction("Spoon over the remaining dressing immediately before eating.", "Añade el aliño restante justo antes de comer.")
        ),
        expandedRecipe(
            "world-thai-salmon-curry", "Thai-Style Salmon Coconut Curry",
            "Curry tailandés de salmón y coco", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2626, 140), bedca(911, 70), bedca(2376, 130), bedca(2409, 80), bedca(2621, 4), bedca(2660, 55)),
            instruction("Cook the rice and keep it covered while preparing the curry.", "Cuece el arroz y mantenlo tapado mientras preparas el curry."),
            instruction("Cut the salmon into large cubes and the courgette and pepper into thin pieces.", "Corta el salmón en dados grandes y el calabacín y el pimiento en trozos finos."),
            instruction("Warm the curry spice in a saucepan for 20 seconds, then gradually stir in the coconut milk.", "Calienta el curry en un cazo 20 segundos e incorpora poco a poco la leche de coco."),
            instruction("Add 80 ml water and the vegetables and simmer for 5 minutes.", "Añade 80 ml de agua y las verduras y cuece 5 minutos."),
            instruction("Add the salmon in one layer and simmer very gently for 5 to 7 minutes without vigorous stirring.", "Añade el salmón en una sola capa y cuece muy suavemente de 5 a 7 minutos sin remover con fuerza."),
            instruction("Serve as soon as the salmon flakes, spooning the curry beside the rice.", "Sirve en cuanto el salmón se separe en lascas, con el curry junto al arroz.")
        ),
        expandedRecipe(
            "world-korean-chicken-cabbage", "Korean-Style Chicken Cabbage Bowl",
            "Bol coreano de pollo y col", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(904, 60), bedca(2386, 170), bedca(2424, 55), bedca(2575, 12), bedca(2606, 3), bedca(793, 1)),
            instruction("Cook the brown rice until tender and keep it covered.", "Cuece el arroz integral hasta que esté tierno y mantenlo tapado."),
            instruction("Slice the chicken thinly and mix it with ginger, chilli and half the soy sauce.", "Corta el pollo fino y mézclalo con el jengibre, el chile y la mitad de la soja."),
            instruction("Shred the cabbage and carrot while the chicken marinates for 10 minutes.", "Corta la col y la zanahoria en juliana mientras el pollo marina 10 minutos."),
            instruction("Cook the chicken in a hot non-stick pan until browned and safely done, then remove it.", "Cocina el pollo en una sartén antiadherente caliente hasta que esté dorado y bien hecho y retíralo."),
            instruction("Stir-fry the cabbage and carrot for 4 minutes so they soften but retain bite.", "Saltea la col y la zanahoria 4 minutos para que se ablanden pero mantengan textura."),
            instruction("Return the chicken with the remaining soy and serve over rice.", "Devuelve el pollo con la soja restante y sirve sobre el arroz.")
        ),
        expandedRecipe(
            "world-brazilian-chicken-rice", "Brazilian-Style Chicken and Rice",
            "Arroz con pollo al estilo brasileño", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2660, 65), bedca(2409, 80), bedca(1175, 60), bedca(481, 120), bedca(2382, 40)),
            instruction("Cut the chicken into small pieces and finely chop the onion and pepper.", "Corta el pollo en trozos pequeños y pica finos la cebolla y el pimiento."),
            instruction("Brown the chicken in a wide pan and transfer it to a plate.", "Dora el pollo en una sartén amplia y pásalo a un plato."),
            instruction("Cook the onion and pepper until soft, add the tomato and reduce for 4 minutes.", "Cocina la cebolla y el pimiento hasta que estén tiernos, añade el tomate y reduce 4 minutos."),
            instruction("Stir in the rice, return the chicken and add the measured hot water.", "Incorpora el arroz, devuelve el pollo y añade el agua caliente medida."),
            instruction("Cover and simmer gently until the rice is almost tender, then scatter in the peas.", "Tapa y cuece suavemente hasta que el arroz esté casi tierno y añade los guisantes."),
            instruction("Cook for the final 3 minutes, remove from the heat and rest covered for 5 minutes.", "Cocina los últimos 3 minutos, retira del fuego y deja reposar tapado 5 minutos.")
        ),
        expandedRecipe(
            "world-south-american-beef-rice", "South American-Style Beef Pepper Rice",
            "Arroz sudamericano con ternera y pimiento", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(981, 145), bedca(2660, 60), bedca(2409, 100), bedca(2421, 100), bedca(2382, 40), bedca(1001, 18)),
            instruction("Cook the rice until tender, cover it and leave it to rest.", "Cuece el arroz hasta que esté tierno, tápalo y déjalo reposar."),
            instruction("Slice the beef thinly across the grain and cut the pepper and onion into strips.", "Corta la ternera fina contra la fibra y el pimiento y la cebolla en tiras."),
            instruction("Sear the beef in a very hot non-stick pan in batches and transfer it to a plate.", "Marca la ternera por tandas en una sartén antiadherente muy caliente y pásala a un plato."),
            instruction("Cook the pepper and onion until tender at the edges, then add the chopped tomato.", "Cocina el pimiento y la cebolla hasta que los bordes estén tiernos y añade el tomate picado."),
            instruction("Return the beef and its juices and toss only until hot so it remains tender.", "Devuelve la ternera y sus jugos y mezcla solo hasta que esté caliente para mantenerla tierna."),
            instruction("Add lime off the heat and serve the mixture over the rested rice.", "Añade la lima fuera del fuego y sirve la mezcla sobre el arroz reposado.")
        ),
        expandedRecipe(
            "world-mexican-tuna-tostada", "Mexican-Style Tuna Tostada Plate",
            "Tostadas mexicanas de atún", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1113, 65), bedca(2133, 120), bedca(2421, 100), bedca(2399, 60), bedca(1186, 60), bedca(1001, 18)),
            instruction("Mix the flour with about 40 ml warm water into a soft dough and rest it covered for 10 minutes.", "Mezcla la harina con unos 40 ml de agua templada hasta formar una masa blanda y déjala reposar tapada 10 minutos."),
            instruction("Roll two thin rounds, prick them with a fork and cook in a dry pan until crisp on both sides.", "Estira dos discos finos, pínchalos con un tenedor y cocínalos en una sartén seca hasta que estén crujientes por ambos lados."),
            instruction("Drain the tuna and dice the tomato and mango into small pieces.", "Escurre el atún y corta el tomate y el mango en dados pequeños."),
            instruction("Mix the tuna, tomato, mango and lime carefully so the tuna remains in flakes.", "Mezcla con cuidado el atún, el tomate, el mango y la lima para mantener el atún en lascas."),
            instruction("Place shredded lettuce on each crisp tostada and spoon over the tuna mixture.", "Coloca lechuga en tiras sobre cada tostada crujiente y reparte la mezcla de atún."),
            instruction("Serve immediately before the moist topping softens the bases.", "Sirve enseguida antes de que la cobertura húmeda ablande las bases.")
        ),
        expandedRecipe(
            "world-indonesian-peanut-chicken", "Indonesian-Style Peanut Chicken",
            "Pollo con cacahuete al estilo indonesio", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(994, 155), bedca(2660, 60), bedca(979, 18), bedca(2575, 10), bedca(1001, 18), bedca(1177, 140)),
            instruction("Cook the rice and steam the broccoli until tender-crisp.", "Cuece el arroz y cocina el brócoli al vapor hasta que esté tierno pero firme."),
            instruction("Cut the chicken into even strips and cook it in a hot non-stick pan until browned.", "Corta el pollo en tiras iguales y cocínalo en una sartén antiadherente caliente hasta dorarlo."),
            instruction("Whisk the peanut butter, soy sauce and lime with 40 ml warm water until smooth.", "Bate la crema de cacahuete, la soja y la lima con 40 ml de agua templada hasta dejar una salsa lisa."),
            instruction("Lower the heat, pour the sauce around the chicken and simmer gently until the chicken is safely done.", "Baja el fuego, vierte la salsa alrededor del pollo y cuece suavemente hasta que esté completamente hecho."),
            instruction("Add warm water a spoonful at a time if the sauce becomes too thick.", "Añade agua templada cucharada a cucharada si la salsa queda demasiado espesa."),
            instruction("Serve the chicken and sauce with the rice and broccoli, keeping the components distinct.", "Sirve el pollo y la salsa con el arroz y el brócoli, manteniendo los componentes separados.")
        ),
        expandedRecipe(
            "world-moroccan-turkey-couscous", "Moroccan-Style Turkey Semolina Bowl",
            "Bol marroquí de pavo y sémola", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(2285, 150), bedca(1084, 60), bedca(2424, 60), bedca(2376, 110), bedca(2571, 3), bedca(2592, 3)),
            instruction("Cut the turkey, carrot and courgette into small even pieces.", "Corta el pavo, la zanahoria y el calabacín en trozos pequeños e iguales."),
            instruction("Brown the turkey in a wide non-stick pan and transfer it to a plate.", "Dora el pavo en una sartén antiadherente amplia y pásalo a un plato."),
            instruction("Cook the carrot for 3 minutes, add the courgette, cumin and paprika and cook for 3 minutes more.", "Cocina la zanahoria 3 minutos, añade el calabacín, el comino y el pimentón y cocina otros 3 minutos."),
            instruction("Return the turkey with 80 ml water, cover and simmer until safely done.", "Devuelve el pavo con 80 ml de agua, tapa y cuece hasta que esté completamente hecho."),
            instruction("Cover the semolina with its measured amount of boiling water and leave it for 5 minutes.", "Cubre la sémola con la cantidad medida de agua hirviendo y deja reposar 5 minutos."),
            instruction("Fluff the semolina and spoon the turkey and vegetable juices over it.", "Suelta la sémola con un tenedor y sirve encima el pavo y los jugos de las verduras.")
        ),
        expandedRecipe(
            "world-chinese-prawn-rice", "Chinese-Style Prawn Vegetable Rice",
            "Arroz chino con gambas y verduras", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(581, 155), bedca(904, 60), bedca(1175, 60), bedca(2424, 55), bedca(2409, 70), bedca(2575, 11), bedca(752, 2)),
            instruction("Cook the brown rice, spread it on a plate and let the steam escape for 10 minutes.", "Cuece el arroz integral, extiéndelo en un plato y deja salir el vapor 10 minutos."),
            instruction("Cut the carrot and pepper into small dice and thaw the peas.", "Corta la zanahoria y el pimiento en dados pequeños y descongela los guisantes."),
            instruction("Cook the prawns in a hot non-stick pan until nearly opaque and transfer them to a plate.", "Cocina las gambas en una sartén antiadherente caliente hasta que estén casi opacas y pásalas a un plato."),
            instruction("Stir-fry the vegetables for 3 minutes, then add the rice and break up any clumps.", "Saltea las verduras 3 minutos, añade el arroz y separa los grumos."),
            instruction("Return the prawns with the soy sauce and toss until just cooked and evenly hot.", "Devuelve las gambas con la soja y mezcla hasta que estén justo hechas y todo esté caliente."),
            instruction("Remove from the heat and stir in the sesame oil before serving.", "Retira del fuego e incorpora el aceite de sésamo antes de servir.")
        ),
        expandedRecipe(
            "world-greek-turkey-pita-plate", "Greek-Style Turkey Flatbread Plate",
            "Plato griego de pavo y pan plano", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(1113, 65), bedca(2285, 150), bedca(2407, 90), bedca(2421, 100), bedca(2520, 55), bedca(411, 15)),
            instruction("Mix the flour with about 40 ml warm water into a soft dough and rest it covered for 10 minutes.", "Mezcla la harina con unos 40 ml de agua templada hasta formar una masa blanda y déjala reposar tapada 10 minutos."),
            instruction("Roll one thin flatbread and cook it in a dry hot pan until puffed and spotted on both sides.", "Estira un pan plano fino y cocínalo en una sartén seca y caliente hasta que se infle y dore por ambos lados."),
            instruction("Cut the turkey into strips and cook in the hot pan until browned and safely done.", "Corta el pavo en tiras y cocínalo en la sartén caliente hasta que esté dorado y bien hecho."),
            instruction("Dice the cucumber and tomato and mix them with the yogurt and lemon.", "Corta el pepino y el tomate y mézclalos con el yogur y el limón."),
            instruction("Rest the turkey for 2 minutes, then place it beside the cool salad and warm flatbread.", "Deja reposar el pavo 2 minutos y colócalo junto a la ensalada fría y el pan caliente."),
            instruction("Tear the flatbread at the table and use it to scoop the turkey and salad.", "Trocea el pan en la mesa y úsalo para acompañar el pavo y la ensalada.")
        ),
        expandedRecipe(
            "world-indian-fish-curry", "Indian-Style Tomato Fish Curry",
            "Curry indio de pescado y tomate", RecipeBooks.WORLD,
            RecipeCategories.MAIN_DISH,
            listOf(bedca(825, 180), bedca(2660, 60), bedca(481, 180), bedca(2382, 40), bedca(2621, 4), bedca(2606, 3), bedca(2530, 45)),
            instruction("Cook the rice and keep it covered.", "Cuece el arroz y mantenlo tapado."),
            instruction("Pat the hake dry and cut it into large pieces so it does not disintegrate in the sauce.", "Seca la merluza y córtala en trozos grandes para que no se deshaga en la salsa."),
            instruction("Soften the onion, add the curry and ginger and stir for 30 seconds.", "Pocha la cebolla, añade el curry y el jengibre y remueve 30 segundos."),
            instruction("Add the tomato and simmer for 8 minutes until thick and aromatic.", "Añade el tomate y cuece 8 minutos hasta que esté espeso y aromático."),
            instruction("Add the fish in one layer, cover and simmer gently for 6 to 8 minutes without stirring.", "Añade el pescado en una sola capa, tapa y cuece suavemente de 6 a 8 minutos sin remover."),
            instruction("Take off the heat, stir yogurt into the sauce around the fish and serve with rice.", "Retira del fuego, mezcla el yogur en la salsa alrededor del pescado y sirve con arroz.")
        ),

        // Desserts
        expandedRecipe(
            "world-indian-mango-shrikhand", "Mango Shrikhand-Style Yogurt",
            "Yogur de mango al estilo shrikhand", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(2520, 150), bedca(1186, 120), bedca(2643, 6), bedca(896, 10)),
            instruction("Line a sieve with a clean cloth, add the yogurt and drain it over a bowl in the fridge for 1 hour.", "Forra un colador con un paño limpio, añade el yogur y déjalo escurrir sobre un cuenco en la nevera 1 hora."),
            instruction("Purée three-quarters of the mango until smooth and dice the remainder.", "Tritura tres cuartas partes del mango hasta dejarlo liso y corta el resto en dados."),
            instruction("Beat the thickened yogurt with the mango purée and honey until evenly coloured.", "Bate el yogur espeso con el puré de mango y la miel hasta obtener un color uniforme."),
            instruction("Chill for 30 minutes and serve with the diced mango and chopped almonds.", "Enfría 30 minutos y sirve con los dados de mango y la almendra picada.")
        ),
        expandedRecipe(
            "world-japanese-strawberry-rice", "Japanese-Style Strawberry Milk Rice",
            "Arroz japonés con leche y fresas", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(2660, 45), bedca(915, 230), bedca(2225, 120), bedca(2643, 6)),
            instruction("Rinse the rice and simmer it with 100 ml water until the water is almost absorbed.", "Enjuaga el arroz y cuécelo con 100 ml de agua hasta que se absorba casi por completo."),
            instruction("Add the milk in three additions, stirring often and waiting for each addition to absorb.", "Añade la leche en tres tandas, removiendo a menudo y esperando a que se absorba cada una."),
            instruction("Cook over low heat until the rice is very tender and the mixture is creamy.", "Cocina a fuego bajo hasta que el arroz esté muy tierno y la mezcla cremosa."),
            instruction("Remove from the heat, stir in the honey and cool for 15 minutes.", "Retira del fuego, incorpora la miel y deja templar 15 minutos."),
            instruction("Fold in half the sliced strawberries and serve with the remainder on top.", "Incorpora la mitad de las fresas en láminas y sirve con el resto por encima.")
        ),
        expandedRecipe(
            "world-mexican-cinnamon-banana", "Mexican-Style Baked Cinnamon Banana",
            "Plátano asado con canela al estilo mexicano", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(2245, 150), bedca(2477, 1), bedca(2431, 6), bedca(2530, 80)),
            instruction("Heat the oven to 200°C and split the banana lengthwise while leaving the peel underneath.", "Calienta el horno a 200 °C y abre el plátano a lo largo dejando la piel debajo."),
            instruction("Place it in a small dish and dust the cut surface with cinnamon and cocoa.", "Colócalo en una fuente pequeña y añade canela y cacao sobre el corte."),
            instruction("Bake for 10 to 14 minutes until the flesh is hot, soft and lightly caramelised.", "Hornea de 10 a 14 minutos hasta que la pulpa esté caliente, tierna y algo caramelizada."),
            instruction("Cool for 3 minutes, lift the flesh from the peel and serve with yogurt.", "Deja templar 3 minutos, separa la pulpa de la piel y sirve con yogur.")
        ),
        expandedRecipe(
            "world-moroccan-grape-salad", "Moroccan-Style Cinnamon Grape Salad",
            "Ensalada marroquí de uvas y canela", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(1017, 170), bedca(1001, 18), bedca(2477, 1), bedca(2530, 80), bedca(896, 10)),
            instruction("Wash, dry and halve the grapes, removing any seeds if present.", "Lava, seca y parte las uvas y retira las semillas si las hubiera."),
            instruction("Toss them with the lime and cinnamon and leave for 10 minutes so the juice develops.", "Mézclalas con la lima y la canela y deja reposar 10 minutos para que se forme jugo."),
            instruction("Toast the almonds in a dry pan until fragrant and let them cool.", "Tuesta las almendras en una sartén seca hasta que desprendan aroma y deja que se enfríen."),
            instruction("Spoon the grapes and their juice over the yogurt and finish with the almonds.", "Sirve las uvas y su jugo sobre el yogur y termina con las almendras.")
        ),
        expandedRecipe(
            "world-french-raspberry-clafoutis", "French-Style Raspberry Clafoutis",
            "Clafoutis francés de frambuesa", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(914, 130), bedca(1113, 35), bedca(1173, 55), bedca(2125, 25), bedca(915, 100), bedca(2643, 8)),
            instruction("Heat the oven to 180°C and line a small shallow baking dish.", "Calienta el horno a 180 °C y forra una fuente pequeña y baja."),
            instruction("Spread the raspberries in one layer in the dish.", "Reparte las frambuesas en una sola capa en la fuente."),
            instruction("Whisk the egg white, yolk and honey, then whisk in the milk.", "Bate la clara, la yema y la miel y después incorpora la leche."),
            instruction("Sift in the flour and whisk only until the batter is smooth.", "Tamiza la harina y bate solo hasta que la masa quede lisa."),
            instruction("Pour the batter around the fruit and bake for 22 to 28 minutes until puffed and just set.", "Vierte la masa alrededor de la fruta y hornea de 22 a 28 minutos hasta que suba y esté justo cuajada."),
            instruction("Rest for 10 minutes; the centre will finish setting as it cools.", "Deja reposar 10 minutos; el centro terminará de cuajar al enfriarse.")
        ),
        expandedRecipe(
            "world-brazilian-coconut-rice", "Brazilian-Style Coconut Rice Pudding",
            "Arroz con leche de coco al estilo brasileño", RecipeBooks.WORLD,
            RecipeCategories.DESSERT,
            listOf(bedca(2660, 45), bedca(911, 70), bedca(915, 160), bedca(908, 8), bedca(2643, 7), bedca(2477, 1)),
            instruction("Rinse the rice and simmer it with 100 ml water until most of the water is absorbed.", "Enjuaga el arroz y cuécelo con 100 ml de agua hasta que se absorba casi toda."),
            instruction("Add the milk gradually and cook over low heat, stirring often, until the rice is tender.", "Añade la leche poco a poco y cocina a fuego bajo, removiendo a menudo, hasta que el arroz esté tierno."),
            instruction("Stir in the coconut milk and continue cooking until the pudding coats the spoon.", "Incorpora la leche de coco y sigue cocinando hasta que el arroz cubra la cuchara."),
            instruction("Remove from the heat and mix in the honey and cinnamon.", "Retira del fuego y mezcla la miel y la canela."),
            instruction("Toast the dried coconut in a dry pan until pale gold.", "Tuesta el coco seco en una sartén seca hasta que esté ligeramente dorado."),
            instruction("Serve the pudding warm or chilled with the toasted coconut on top.", "Sirve el arroz templado o frío con el coco tostado por encima.")
        )
    )
}
