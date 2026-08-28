package com.zame.nutrinest.data.local

/**
 * Full methods for the more technique-sensitive recipes from library v1.
 * Simpler assembly recipes retain their concise, dish-specific original steps.
 */
internal object BuiltInRecipeInstructionOverrides {
    private val instructions = mapOf(
        "med-tortilla" to listOf(
            instruction("Peel the potato and slice it and the onion very thinly so both cook through without browning hard.", "Pela la patata y córtala, junto con la cebolla, en láminas muy finas para que ambas se hagan sin dorarse en exceso."),
            instruction("Heat the olive oil in a small non-stick pan and add the potato and onion.", "Calienta el aceite de oliva en una sartén antiadherente pequeña y añade la patata y la cebolla."),
            instruction("Cover and cook over medium-low heat for 15 to 20 minutes, turning gently, until the potato breaks under a spatula.", "Tapa y cocina a fuego medio-bajo de 15 a 20 minutos, moviendo con cuidado, hasta que la patata se rompa con la espátula."),
            instruction("Drain off any unabsorbed oil and let the potato mixture cool for 2 minutes.", "Escurre el aceite que no se haya absorbido y deja templar la mezcla de patata 2 minutos."),
            instruction("Beat the egg whites and yolks, fold in the potato and leave the mixture for 3 minutes.", "Bate las claras y las yemas, incorpora la patata y deja reposar la mezcla 3 minutos."),
            instruction("Return it to the pan and cook over medium-low heat until the base and edges are set.", "Devuelve la mezcla a la sartén y cocina a fuego medio-bajo hasta que la base y los bordes estén cuajados."),
            instruction("Invert onto a plate, slide back into the pan and cook the second side to the preferred centre doneness; rest before cutting.", "Da la vuelta con un plato, desliza de nuevo a la sartén y cocina el segundo lado hasta dejar el centro al punto deseado; reposa antes de cortar.")
        ),
        "med-chicken-paella" to listOf(
            instruction("Cut the chicken and pepper into even pieces and grate or finely chop the tomato.", "Corta el pollo y el pimiento en trozos iguales y ralla o pica muy fino el tomate."),
            instruction("Heat the oil in a wide shallow pan and brown the chicken thoroughly on all sides.", "Calienta el aceite en una sartén amplia y baja y dora bien el pollo por todos los lados."),
            instruction("Add the pepper and cook for 3 minutes, then add the tomato and reduce it until thick.", "Añade el pimiento y cocina 3 minutos; incorpora el tomate y redúcelo hasta que espese."),
            instruction("Stir in the saffron, peas and rice and turn the grains through the concentrated base.", "Incorpora el azafrán, los guisantes y el arroz y mezcla los granos con el sofrito concentrado."),
            instruction("Add the measured hot water, distribute the rice evenly and bring to a strong simmer.", "Añade el agua caliente medida, reparte el arroz de forma uniforme y lleva a hervor vivo."),
            instruction("Cook for 5 minutes over medium-high heat, then 12 to 14 minutes over low heat without stirring.", "Cuece 5 minutos a fuego medio-alto y después de 12 a 14 minutos a fuego bajo sin remover."),
            instruction("Remove when the rice is just tender and the liquid is absorbed, cover loosely and rest for 5 minutes.", "Retira cuando el arroz esté justo tierno y se haya absorbido el líquido, cubre sin cerrar del todo y reposa 5 minutos.")
        ),
        "med-seafood-paella" to listOf(
            instruction("Thaw and drain the seafood if needed, pat it dry and finely chop the pepper and tomato.", "Descongela y escurre el marisco si hace falta, sécalo y pica finos el pimiento y el tomate."),
            instruction("Sear the prawns and mussels briefly in half the oil and remove them before they cook through.", "Marca brevemente las gambas y los mejillones en la mitad del aceite y retíralos antes de que se hagan por completo."),
            instruction("Add the remaining oil and pepper, cook for 3 minutes, then reduce the tomato with the saffron.", "Añade el aceite restante y el pimiento, cocina 3 minutos y reduce el tomate con el azafrán."),
            instruction("Stir in the rice, add the measured hot water and spread the grains evenly across the pan.", "Incorpora el arroz, añade el agua caliente medida y reparte los granos por la sartén."),
            instruction("Cook without stirring for 15 minutes, starting at a lively simmer and lowering the heat after 5 minutes.", "Cuece sin remover 15 minutos, empezando con hervor vivo y bajando el fuego tras 5 minutos."),
            instruction("Arrange the seafood on top for the final 4 to 5 minutes, then remove the pan and rest it for 5 minutes before serving.", "Coloca el marisco encima durante los últimos 4 o 5 minutos, retira la sartén y deja reposar 5 minutos antes de servir.")
        ),
        "med-pasta-pomodoro" to listOf(
            instruction("Bring a large pan of water to the boil and cook the pasta until al dente.", "Lleva abundante agua a ebullición y cuece la pasta hasta que esté al dente."),
            instruction("Meanwhile warm the oil over low heat and cook the sliced garlic for 30 seconds without browning it.", "Mientras tanto, calienta el aceite a fuego bajo y cocina el ajo laminado 30 segundos sin dorarlo."),
            instruction("Add the tomato and simmer uncovered for 10 to 12 minutes until concentrated.", "Añade el tomate y cuece destapado de 10 a 12 minutos hasta que se concentre."),
            instruction("Reserve a little pasta water, drain the pasta and toss it directly in the tomato sauce.", "Reserva un poco del agua, escurre la pasta y mézclala directamente con la salsa de tomate."),
            instruction("Add basil off the heat and use only enough reserved water to make the sauce cling to the pasta.", "Añade la albahaca fuera del fuego y usa solo el agua reservada necesaria para que la salsa se adhiera a la pasta.")
        ),
        "med-pasta-primavera" to listOf(
            instruction("Cut the courgette, pepper and carrot into similarly thin pieces.", "Corta el calabacín, el pimiento y la zanahoria en trozos finos de tamaño parecido."),
            instruction("Cook the pasta in boiling water until al dente and reserve a small cup of its water.", "Cuece la pasta en agua hirviendo hasta que esté al dente y reserva una taza pequeña del agua."),
            instruction("Heat the oil in a wide pan and cook the carrot for 2 minutes before adding the pepper.", "Calienta el aceite en una sartén amplia y cocina la zanahoria 2 minutos antes de añadir el pimiento."),
            instruction("Add the courgette and cook over medium-high heat until all vegetables are tender-crisp, not soft.", "Añade el calabacín y cocina a fuego medio-alto hasta que las verduras estén tiernas pero firmes."),
            instruction("Add the drained pasta and a spoonful of cooking water and toss vigorously to coat it.", "Añade la pasta escurrida y una cucharada del agua de cocción y mezcla con energía."),
            instruction("Fold in the cheese off the heat and serve while the vegetables retain their colour and bite.", "Incorpora el queso fuera del fuego y sirve mientras las verduras conservan su color y textura.")
        ),
        "med-eggplant-parmigiana" to listOf(
            instruction("Heat the oven to 220°C and slice the aubergine lengthwise into even 7 mm slices.", "Calienta el horno a 220 °C y corta la berenjena a lo largo en láminas iguales de 7 mm."),
            instruction("Brush both sides lightly with oil, arrange in one layer and roast for 15 minutes, turning once.", "Pincela ambos lados con aceite, coloca en una capa y asa 15 minutos, dando una vuelta."),
            instruction("Simmer the tomato with the garlic and basil for 10 minutes until it is no longer watery.", "Cuece el tomate con el ajo y la albahaca 10 minutos hasta que deje de estar aguado."),
            instruction("Lower the oven to 200°C and spread a thin layer of tomato in a baking dish.", "Baja el horno a 200 °C y extiende una capa fina de tomate en una fuente."),
            instruction("Layer aubergine, tomato and mozzarella, repeating until the ingredients are used.", "Alterna capas de berenjena, tomate y mozzarella hasta terminar los ingredientes."),
            instruction("Bake uncovered for 18 to 22 minutes until bubbling and browned at the edges.", "Hornea destapado de 18 a 22 minutos hasta que burbujee y los bordes se doren."),
            instruction("Rest for 8 minutes before serving so the layers set and the cheese does not run out.", "Deja reposar 8 minutos antes de servir para que las capas se asienten y el queso no se salga.")
        ),
        "med-lemon-chicken" to listOf(
            instruction("Heat the oven to 210°C and cut the potatoes into equal wedges.", "Calienta el horno a 210 °C y corta las patatas en gajos iguales."),
            instruction("Mix the potatoes with two-thirds of the oil, half the lemon and oregano and roast for 20 minutes.", "Mezcla las patatas con dos tercios del aceite, la mitad del limón y el orégano y asa 20 minutos."),
            instruction("Pat the chicken dry and brown it in the remaining oil in an oven-safe pan.", "Seca el pollo y dóralo con el aceite restante en una sartén apta para horno."),
            instruction("Turn the potatoes, nestle in the chicken and pour the remaining lemon around, not over, the browned surface.", "Da la vuelta a las patatas, coloca el pollo y vierte el limón restante alrededor, no sobre la superficie dorada."),
            instruction("Roast for 15 to 20 minutes until the potatoes are tender and the chicken is safely cooked.", "Asa de 15 a 20 minutos hasta que las patatas estén tiernas y el pollo bien hecho."),
            instruction("Rest the chicken for 4 minutes and spoon the lemon pan juices over it before serving.", "Deja reposar el pollo 4 minutos y añade los jugos de limón de la fuente antes de servir.")
        ),
        "med-moussaka" to listOf(
            instruction("Heat the oven to 220°C and cut the aubergine and potato into even thin slices.", "Calienta el horno a 220 °C y corta la berenjena y la patata en rodajas finas y uniformes."),
            instruction("Brush the vegetables with half the oil and roast in separate layers until the potato is tender and aubergine browned.", "Pincela las verduras con la mitad del aceite y ásalas por separado hasta que la patata esté tierna y la berenjena dorada."),
            instruction("Cook the onion in the remaining oil, add the beef and break it up until browned.", "Pocha la cebolla en el aceite restante, añade la ternera y sepárala mientras se dora."),
            instruction("Add the tomato and simmer uncovered for 12 minutes until the meat sauce is thick.", "Añade el tomate y cuece destapado 12 minutos hasta que la salsa de carne esté espesa."),
            instruction("Lower the oven to 190°C and layer potato, half the aubergine, the meat sauce and remaining aubergine.", "Baja el horno a 190 °C y forma capas de patata, la mitad de la berenjena, la carne y la berenjena restante."),
            instruction("Beat the yogurt with the egg white until smooth and spread it to every edge of the top layer.", "Bate el yogur con la clara hasta dejarlo liso y extiéndelo hasta todos los bordes de la capa superior."),
            instruction("Bake for 25 to 30 minutes until the topping is set and golden.", "Hornea de 25 a 30 minutos hasta que la cobertura esté cuajada y dorada."),
            instruction("Rest for 10 minutes before cutting so the layers stay intact.", "Deja reposar 10 minutos antes de cortar para que las capas se mantengan enteras.")
        ),
        "med-salmon-traybake" to listOf(
            instruction("Heat the oven to 210°C and cut the courgette, pepper and tomato into pieces that will cook evenly.", "Calienta el horno a 210 °C y corta el calabacín, el pimiento y el tomate en trozos que se cocinen por igual."),
            instruction("Toss the courgette and pepper with the oil and roast for 15 minutes.", "Mezcla el calabacín y el pimiento con el aceite y asa 15 minutos."),
            instruction("Turn the vegetables, add the tomato and make a space in the centre for the salmon.", "Da la vuelta a las verduras, añade el tomate y abre un espacio en el centro para el salmón."),
            instruction("Place the salmon skin-side down and roast for 10 to 13 minutes depending on its thickness.", "Coloca el salmón con la piel hacia abajo y asa de 10 a 13 minutos según su grosor."),
            instruction("Remove the tray when the salmon flakes under light pressure but remains moist in the centre.", "Retira la bandeja cuando el salmón se separe en lascas al presionarlo pero siga jugoso en el centro."),
            instruction("Rest the fish for 2 minutes and serve it with the vegetables and their roasting juices.", "Deja reposar el pescado 2 minutos y sírvelo con las verduras y sus jugos.")
        ),
        "med-cod-tomato-olives" to listOf(
            instruction("Pat the cod dry and thinly slice the onion and garlic.", "Seca el bacalao y corta finos la cebolla y el ajo."),
            instruction("Soften the onion in the oil for 6 minutes, add the garlic and cook for 30 seconds.", "Pocha la cebolla en el aceite 6 minutos, añade el ajo y cocina 30 segundos."),
            instruction("Add the tomato and simmer uncovered for 10 minutes, then stir in the olives.", "Añade el tomate y cuece destapado 10 minutos; incorpora las aceitunas."),
            instruction("Set the cod into the sauce, cover and cook over low heat for 6 to 9 minutes.", "Coloca el bacalao en la salsa, tapa y cocina a fuego bajo de 6 a 9 minutos."),
            instruction("Remove from the heat as soon as the thickest part separates into moist flakes.", "Retira del fuego en cuanto la parte más gruesa se separe en lascas jugosas.")
        ),

        "light-chicken-quinoa" to listOf(
            instruction("Rinse the quinoa and simmer it in measured water until the grains open; cover and rest for 5 minutes.", "Enjuaga la quinoa y cuécela con el agua medida hasta que se abran los granos; tapa y reposa 5 minutos."),
            instruction("Cut the chicken, pepper, carrot and courgette into bite-sized pieces.", "Corta el pollo, el pimiento, la zanahoria y el calabacín en trozos pequeños."),
            instruction("Cook the chicken in a hot non-stick pan until lightly browned and transfer it to a plate.", "Cocina el pollo en una sartén antiadherente caliente hasta que se dore y pásalo a un plato."),
            instruction("Cook the carrot for 2 minutes, add the pepper and courgette and continue until tender-crisp.", "Cocina la zanahoria 2 minutos, añade el pimiento y el calabacín y sigue hasta que estén tiernos pero firmes."),
            instruction("Return the chicken and cook until safely done, adding a splash of water if the pan is dry.", "Devuelve el pollo y cocina hasta que esté bien hecho, añadiendo un poco de agua si la sartén está seca."),
            instruction("Fluff the quinoa and assemble the bowl with the chicken and vegetables arranged over it.", "Suelta la quinoa con un tenedor y monta el bol con el pollo y las verduras por encima.")
        ),
        "light-turkey-courgette" to listOf(
            instruction("Heat the oven to 200°C, halve the courgette lengthwise and scoop out the centre without piercing the shell.", "Calienta el horno a 200 °C, parte el calabacín a lo largo y vacía el centro sin perforar la piel."),
            instruction("Bake the empty shells cut-side up for 12 minutes.", "Hornea las carcasas vacías con el corte hacia arriba durante 12 minutos."),
            instruction("Finely chop the courgette flesh and onion and cook them in a non-stick pan until their moisture evaporates.", "Pica finas la pulpa y la cebolla y cocínalas en una sartén antiadherente hasta que se evapore su agua."),
            instruction("Add the turkey, break it into small pieces and cook until no raw colour remains.", "Añade el pavo, sepáralo en trozos pequeños y cocina hasta que pierda el color crudo."),
            instruction("Add the tomato, simmer until thick and spoon the filling into the courgette shells.", "Añade el tomate, cuece hasta que espese y rellena las carcasas de calabacín."),
            instruction("Top with cheese and bake for 15 minutes until the filling is piping hot and the courgette tender.", "Cubre con queso y hornea 15 minutos hasta que el relleno esté muy caliente y el calabacín tierno.")
        ),
        "light-hake-broccoli" to listOf(
            instruction("Heat the oven to 210°C and cut the broccoli into small even florets.", "Calienta el horno a 210 °C y separa el brócoli en ramilletes pequeños e iguales."),
            instruction("Toss the broccoli and carrot with the oil and roast for 12 minutes.", "Mezcla el brócoli y la zanahoria con el aceite y asa 12 minutos."),
            instruction("Turn the vegetables, place the dried hake on the tray and add the lemon.", "Da la vuelta a las verduras, coloca la merluza seca en la bandeja y añade el limón."),
            instruction("Bake for another 8 to 11 minutes until the hake is opaque and flakes easily.", "Hornea otros 8 u 11 minutos hasta que la merluza esté opaca y se separe en lascas."),
            instruction("Remove the fish promptly and roast any thick broccoli pieces longer only if necessary.", "Retira el pescado a tiempo y asa solo los trozos gruesos de brócoli algo más si es necesario.")
        ),
        "light-tuna-bean-salad" to listOf(
            instruction("Drain the tuna and beans separately and rinse the beans under cool water.", "Escurre por separado el atún y las alubias y enjuaga estas con agua fría."),
            instruction("Dice the tomato and cucumber and thinly slice the onion.", "Corta el tomate y el pepino en dados y la cebolla en láminas finas."),
            instruction("Whisk the lemon and oil and toss the dressing with the beans and vegetables.", "Bate el limón y el aceite y mezcla el aliño con las alubias y las verduras."),
            instruction("Flake the tuna over the salad and turn it gently once or twice without crushing it.", "Reparte el atún en lascas y mezcla suavemente una o dos veces sin deshacerlo."),
            instruction("Stand for 5 minutes at room temperature before serving so the flavours combine.", "Deja reposar 5 minutos a temperatura ambiente antes de servir para integrar los sabores.")
        ),
        "light-lentil-soup" to listOf(
            instruction("Rinse the lentils and finely dice the onion, carrot and celery.", "Enjuaga las lentejas y pica finos la cebolla, la zanahoria y el apio."),
            instruction("Cook the vegetables in a saucepan with a splash of water for 5 minutes.", "Cocina las verduras en un cazo con un poco de agua durante 5 minutos."),
            instruction("Add the tomato, lentils, stock and 300 ml water and bring slowly to a simmer.", "Añade el tomate, las lentejas, el caldo y 300 ml de agua y lleva lentamente a hervor suave."),
            instruction("Skim any foam and cook uncovered for 25 to 30 minutes, stirring occasionally.", "Retira la espuma y cuece destapado de 25 a 30 minutos, removiendo de vez en cuando."),
            instruction("Add hot water if necessary; the lentils should be tender and surrounded by a spoonable broth.", "Añade agua caliente si hace falta; las lentejas deben estar tiernas y rodeadas de un caldo que se pueda servir con cuchara."),
            instruction("Rest for 5 minutes before serving so the soup thickens naturally.", "Deja reposar 5 minutos antes de servir para que la sopa espese de forma natural.")
        ),
        "light-tofu-stir-fry" to listOf(
            instruction("Press the tofu for 10 minutes, cut it into cubes and pat them dry.", "Prensa el tofu 10 minutos, córtalo en dados y sécalos."),
            instruction("Cut the broccoli, pepper and carrot into small pieces that will cook quickly.", "Corta el brócoli, el pimiento y la zanahoria en trozos pequeños que se cocinen rápido."),
            instruction("Brown the tofu in a hot non-stick pan, turning carefully, then transfer it to a plate.", "Dora el tofu en una sartén antiadherente caliente, girándolo con cuidado, y pásalo a un plato."),
            instruction("Stir-fry the carrot for 2 minutes, add the broccoli and pepper and cook for 3 minutes more.", "Saltea la zanahoria 2 minutos, añade el brócoli y el pimiento y cocina 3 minutos más."),
            instruction("Return the tofu, pour the soy sauce around the hot edge of the pan and toss until evenly hot.", "Devuelve el tofu, vierte la soja por el borde caliente de la sartén y mezcla hasta que todo esté caliente."),
            instruction("Remove from the heat and add the sesame oil only at the end.", "Retira del fuego y añade el aceite de sésamo solo al final.")
        ),
        "light-salmon-cauliflower" to listOf(
            instruction("Steam the cauliflower until completely tender, then leave it uncovered for 2 minutes so excess steam escapes.", "Cuece la coliflor al vapor hasta que esté totalmente tierna y déjala destapada 2 minutos para que salga el vapor."),
            instruction("Blend or mash it with the milk until smooth and keep the mash warm over very low heat.", "Tritúrala o machácala con la leche hasta dejarla lisa y mantén el puré caliente a fuego muy bajo."),
            instruction("Pat the salmon dry and place it skin-side down in a preheated non-stick pan.", "Seca el salmón y colócalo con la piel hacia abajo en una sartén antiadherente precalentada."),
            instruction("Cook without moving for 4 to 5 minutes, turn and cook the second side according to thickness.", "Cocina sin mover de 4 a 5 minutos, da la vuelta y cocina el segundo lado según el grosor."),
            instruction("Remove when the centre flakes but remains moist and rest for 2 minutes.", "Retira cuando el centro se separe en lascas pero siga jugoso y deja reposar 2 minutos."),
            instruction("Add lemon to the mash and serve the salmon on top so the skin stays crisp.", "Añade limón al puré y sirve el salmón encima para que la piel se mantenga crujiente.")
        ),
        "light-turkey-lettuce-burger" to listOf(
            instruction("Mix the turkey and breadcrumbs briefly and shape a patty slightly wider than the lettuce stack.", "Mezcla brevemente el pavo y el pan rallado y forma una hamburguesa algo más ancha que la base de lechuga."),
            instruction("Chill the patty for 10 minutes so it firms without adding more breadcrumbs.", "Enfría la hamburguesa 10 minutos para que se afirme sin añadir más pan rallado."),
            instruction("Cook in a preheated non-stick pan, turning once, until browned and fully cooked in the centre.", "Cocina en una sartén antiadherente precalentada, dando una vuelta, hasta que esté dorada y bien hecha en el centro."),
            instruction("Rest the burger for 3 minutes while slicing the tomato, cucumber and avocado.", "Deja reposar la hamburguesa 3 minutos mientras cortas el tomate, el pepino y el aguacate."),
            instruction("Layer sturdy lettuce leaves to make a base and add the warm patty and vegetables.", "Superpón hojas firmes de lechuga para formar una base y añade la hamburguesa caliente y las verduras."),
            instruction("Fold the outer leaves around the filling or eat the plate with a knife and fork.", "Dobla las hojas exteriores alrededor del relleno o come el plato con cuchillo y tenedor.")
        ),
        "light-chicken-veg-soup" to listOf(
            instruction("Cut the chicken, carrot, celery and courgette into small even pieces.", "Corta el pollo, la zanahoria, el apio y el calabacín en trozos pequeños e iguales."),
            instruction("Bring the stock and 300 ml water to a gentle simmer and add the carrot and celery.", "Lleva el caldo y 300 ml de agua a hervor suave y añade la zanahoria y el apio."),
            instruction("After 5 minutes add the chicken, keeping the liquid below a rolling boil.", "Tras 5 minutos, añade el pollo y mantén el líquido por debajo de un hervor fuerte."),
            instruction("Simmer for 7 minutes, skimming any foam, then add the courgette and tomato.", "Cuece 7 minutos, retirando la espuma, y añade el calabacín y el tomate."),
            instruction("Continue until the chicken is safely cooked and every vegetable is tender, about 5 minutes.", "Continúa hasta que el pollo esté bien hecho y todas las verduras tiernas, unos 5 minutos."),
            instruction("Rest the soup for 2 minutes before serving so the temperature evens out.", "Deja reposar la sopa 2 minutos antes de servir para igualar la temperatura.")
        ),
        "light-chickpea-curry" to listOf(
            instruction("Drain the chickpeas and separate the cauliflower into small florets.", "Escurre los garbanzos y separa la coliflor en ramilletes pequeños."),
            instruction("Cook the onion with a splash of water until soft, add the curry and stir for 30 seconds.", "Cocina la cebolla con un poco de agua hasta que esté tierna, añade el curry y remueve 30 segundos."),
            instruction("Add the tomato and simmer for 6 minutes until the spice sauce thickens.", "Añade el tomate y cuece 6 minutos hasta que la salsa especiada espese."),
            instruction("Add the cauliflower and 100 ml water, cover and simmer until nearly tender.", "Añade la coliflor y 100 ml de agua, tapa y cuece hasta que esté casi tierna."),
            instruction("Fold in the chickpeas and cook uncovered for 5 minutes until hot and the sauce is reduced.", "Incorpora los garbanzos y cocina destapado 5 minutos hasta que estén calientes y la salsa reduzca."),
            instruction("Remove from the heat and stir in the yogurt so it stays smooth rather than splitting.", "Retira del fuego e incorpora el yogur para que quede liso y no se corte.")
        ),

        "world-classic-burger" to listOf(
            instruction("Shape the beef into a loose patty slightly wider than the bun, pressing only enough for it to hold.", "Forma una hamburguesa de ternera algo más ancha que el pan, presionando solo lo necesario para unirla."),
            instruction("Cook it in a preheated pan, turning once, until browned and cooked to a safe centre doneness.", "Cocínala en una sartén precalentada, dando una vuelta, hasta que esté dorada y el centro bien hecho."),
            instruction("Place the cheese on top for the final minute and cover the pan so it melts.", "Coloca el queso encima durante el último minuto y tapa la sartén para que se funda."),
            instruction("Split and toast the bun cut-side down while the burger rests for 2 minutes.", "Abre y tuesta el pan por la cara cortada mientras la hamburguesa reposa 2 minutos."),
            instruction("Assemble with lettuce, tomato and onion and serve before the toasted bun softens.", "Monta con lechuga, tomate y cebolla y sirve antes de que el pan tostado se ablande.")
        ),
        "world-turkey-avocado-burger" to listOf(
            instruction("Mix the turkey with breadcrumbs briefly and shape a patty without compacting it.", "Mezcla brevemente el pavo con el pan rallado y forma una hamburguesa sin prensarla."),
            instruction("Chill for 10 minutes, then cook in a preheated non-stick pan until browned and fully done.", "Enfría 10 minutos y cocina en una sartén antiadherente precalentada hasta que esté dorada y bien hecha."),
            instruction("Rest the patty for 3 minutes and toast the cut sides of the bun.", "Deja reposar la hamburguesa 3 minutos y tuesta el interior del pan."),
            instruction("Mash the avocado with the lime and slice the tomato and onion thinly.", "Machaca el aguacate con la lima y corta finos el tomate y la cebolla."),
            instruction("Spread the avocado on the bun and add the turkey patty, lettuce, tomato and onion.", "Unta el aguacate en el pan y añade la hamburguesa de pavo, la lechuga, el tomate y la cebolla.")
        ),
        "world-chickpea-burger" to listOf(
            instruction("Drain the chickpeas thoroughly and mash them, leaving a few small pieces for texture.", "Escurre bien los garbanzos y machácalos dejando algunos trozos pequeños para dar textura."),
            instruction("Finely grate the carrot and onion and squeeze away any released liquid.", "Ralla finas la zanahoria y la cebolla y elimina el líquido que suelten."),
            instruction("Mix the vegetables, chickpeas, breadcrumbs and cumin and leave for 5 minutes.", "Mezcla las verduras, los garbanzos, el pan rallado y el comino y deja reposar 5 minutos."),
            instruction("Shape a firm patty with damp hands and chill it for 10 minutes.", "Forma una hamburguesa firme con las manos húmedas y enfríala 10 minutos."),
            instruction("Cook in a non-stick pan over medium heat for 4 to 5 minutes per side until browned and hot through.", "Cocina en una sartén antiadherente a fuego medio de 4 a 5 minutos por lado hasta que esté dorada y caliente."),
            instruction("Toast the bun and assemble with lettuce, handling the patty gently so it does not split.", "Tuesta el pan y monta con lechuga, manipulando la hamburguesa con cuidado para que no se rompa.")
        ),
        "world-margherita-pizza" to listOf(
            instruction("Mix the flour with enough warm water to make a soft dough and knead for 5 minutes.", "Mezcla la harina con agua templada suficiente para formar una masa blanda y amasa 5 minutos."),
            instruction("Cover and rest the dough for at least 30 minutes while heating the oven and tray to its highest setting.", "Tapa y deja reposar la masa al menos 30 minutos mientras calientas el horno y la bandeja a máxima temperatura."),
            instruction("Simmer the tomato for 8 minutes so it will not make the pizza base wet, then cool it slightly.", "Cuece el tomate 8 minutos para que no humedezca la base y deja que se temple."),
            instruction("Stretch the dough by hand into a thin round, leaving a slightly thicker rim.", "Estira la masa a mano en un disco fino dejando el borde algo más grueso."),
            instruction("Spread a thin layer of tomato, leaving the rim bare, and distribute the mozzarella.", "Extiende una capa fina de tomate dejando libre el borde y reparte la mozzarella."),
            instruction("Slide onto the hot tray and bake until the base is crisp and the edge browned, about 8 to 12 minutes.", "Pasa a la bandeja caliente y hornea hasta que la base esté crujiente y el borde dorado, de 8 a 12 minutos."),
            instruction("Add the basil after baking so it stays aromatic and does not scorch.", "Añade la albahaca después de hornear para conservar su aroma y evitar que se queme.")
        ),
        "world-vegetable-pizza" to listOf(
            instruction("Mix the flour and warm water into a soft dough, knead for 5 minutes, cover and rest for 30 minutes.", "Mezcla la harina y el agua templada hasta formar una masa blanda, amasa 5 minutos, tapa y reposa 30 minutos."),
            instruction("Heat the oven and a baking tray to the highest setting.", "Calienta el horno y una bandeja a la máxima temperatura."),
            instruction("Cut the vegetables thinly and roast or sauté them first until their excess moisture evaporates.", "Corta las verduras finas y ásalas o saltéalas primero hasta que pierdan el exceso de agua."),
            instruction("Stretch the dough by hand into a thin base with a slightly raised rim.", "Estira la masa a mano en una base fina con el borde ligeramente elevado."),
            instruction("Spread the reduced tomato, then add the cooked vegetables and mozzarella without overloading the centre.", "Reparte el tomate reducido y añade las verduras cocinadas y la mozzarella sin cargar demasiado el centro."),
            instruction("Bake on the hot tray for 8 to 12 minutes until the underside is crisp and the cheese bubbles.", "Hornea sobre la bandeja caliente de 8 a 12 minutos hasta que la base esté crujiente y el queso burbujee."),
            instruction("Rest for 2 minutes before cutting so the toppings do not slide off.", "Deja reposar 2 minutos antes de cortar para que la cobertura no se deslice.")
        ),
        "world-chicken-curry" to listOf(
            instruction("Rinse the rice and cook it in measured water; cover and rest off the heat.", "Enjuaga el arroz y cuécelo con el agua medida; tapa y deja reposar fuera del fuego."),
            instruction("Cut the chicken evenly and finely chop the onion.", "Corta el pollo de forma uniforme y pica fina la cebolla."),
            instruction("Cook the onion until soft, add the curry and stir for 30 seconds to release its aroma.", "Pocha la cebolla, añade el curry y remueve 30 segundos para liberar su aroma."),
            instruction("Add the tomato and simmer for 7 minutes until the spice base is thick.", "Añade el tomate y cuece 7 minutos hasta que la base especiada esté espesa."),
            instruction("Add the chicken with a splash of water, cover and simmer until safely cooked through.", "Añade el pollo con un poco de agua, tapa y cuece hasta que esté completamente hecho."),
            instruction("Remove from the heat and stir in the yogurt so the sauce stays smooth.", "Retira del fuego e incorpora el yogur para que la salsa quede lisa."),
            instruction("Fluff the rice and serve the curry beside it rather than mixing it in the pot.", "Suelta el arroz con un tenedor y sirve el curry al lado sin mezclarlo en la cazuela.")
        ),
        "world-red-lentil-dal" to listOf(
            instruction("Rinse the lentils until the water runs mostly clear and rinse the rice separately.", "Enjuaga las lentejas hasta que el agua salga casi limpia y lava el arroz por separado."),
            instruction("Cook the rice in measured water and leave it covered to steam.", "Cuece el arroz con el agua medida y déjalo tapado para que repose al vapor."),
            instruction("Soften the onion with a splash of water, add the garlic and curry and stir for 30 seconds.", "Pocha la cebolla con un poco de agua, añade el ajo y el curry y remueve 30 segundos."),
            instruction("Add the tomato, lentils and 250 ml water and bring slowly to a simmer.", "Añade el tomate, las lentejas y 250 ml de agua y lleva lentamente a hervor suave."),
            instruction("Cook uncovered for 20 to 25 minutes, stirring more often near the end, until creamy.", "Cuece destapado de 20 a 25 minutos, removiendo más al final, hasta que quede cremoso."),
            instruction("Add hot water if the dal thickens before the lentils are tender, then serve with rice.", "Añade agua caliente si el dal espesa antes de que las lentejas estén tiernas y sirve con arroz.")
        ),
        "world-thai-tofu-curry" to listOf(
            instruction("Cook the rice and keep it covered while preparing the curry.", "Cuece el arroz y mantenlo tapado mientras preparas el curry."),
            instruction("Press and cube the tofu and cut the pepper and courgette into thin pieces.", "Prensa y corta el tofu en dados y el pimiento y el calabacín en trozos finos."),
            instruction("Warm the curry and ginger in a saucepan for 20 seconds and gradually stir in the coconut milk.", "Calienta el curry y el jengibre en un cazo 20 segundos e incorpora poco a poco la leche de coco."),
            instruction("Add 80 ml water and the vegetables and simmer for 5 minutes.", "Añade 80 ml de agua y las verduras y cuece 5 minutos."),
            instruction("Add the tofu and soy sauce and simmer gently until the vegetables are tender and tofu hot.", "Añade el tofu y la soja y cuece suavemente hasta que las verduras estén tiernas y el tofu caliente."),
            instruction("Serve the curry as soon as it is ready so the vegetables retain their shape, with rice alongside.", "Sirve el curry en cuanto esté listo para que las verduras mantengan su forma, con el arroz al lado.")
        ),
        "world-chinese-stir-fry" to listOf(
            instruction("Cook the rice and spread it with a fork so it does not clump.", "Cuece el arroz y suéltalo con un tenedor para que no se apelmace."),
            instruction("Slice the chicken thinly and cut the broccoli, pepper and carrot into small pieces.", "Corta el pollo fino y el brócoli, el pimiento y la zanahoria en trozos pequeños."),
            instruction("Cook the chicken in a very hot non-stick pan in batches and remove it when lightly browned.", "Cocina el pollo por tandas en una sartén antiadherente muy caliente y retíralo al dorarse."),
            instruction("Stir-fry the carrot for 2 minutes, then add the broccoli and pepper for 3 minutes.", "Saltea la zanahoria 2 minutos y añade el brócoli y el pimiento 3 minutos."),
            instruction("Return the chicken, add soy sauce and cook until the meat is safely done.", "Devuelve el pollo, añade la soja y cocina hasta que esté completamente hecho."),
            instruction("Remove from the heat, add sesame oil and serve over the rice immediately.", "Retira del fuego, añade el aceite de sésamo y sirve enseguida sobre el arroz.")
        ),
        "world-mexican-chili" to listOf(
            instruction("Finely chop the onion and drain the beans and corn.", "Pica fina la cebolla y escurre las alubias y el maíz."),
            instruction("Brown the beef in a wide pan, breaking it into small pieces as it cooks.", "Dora la ternera en una sartén amplia y sepárala en trozos pequeños mientras se cocina."),
            instruction("Add the onion, cumin and chilli and cook until the onion is soft.", "Añade la cebolla, el comino y el chile y cocina hasta que la cebolla esté tierna."),
            instruction("Add the tomato with a small splash of water and simmer uncovered for 12 minutes.", "Añade el tomate con un poco de agua y cuece destapado 12 minutos."),
            instruction("Fold in the beans and corn and simmer for another 6 minutes until thoroughly hot.", "Incorpora las alubias y el maíz y cuece otros 6 minutos hasta que estén bien calientes."),
            instruction("Rest off the heat for 3 minutes; the beans will thicken the chilli further.", "Deja reposar fuera del fuego 3 minutos; las alubias espesarán aún más el chili.")
        )
    )

    fun forKey(key: String): List<BilingualRecipeStep>? = instructions[key]
}
