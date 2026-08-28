package com.zame.nutrinest.data.local

internal data class SpanishRecipeText(
    val name: String,
    val coreSteps: List<String>
)

/** Spanish copy for the language-neutral built-in recipe definitions. */
internal object BuiltInRecipeTranslations {
    private val translations = mapOf(
        // Mediterranean Cuisine
        "med-pan-tomate" to es(
            "Pan con tomate",
            "Tuesta el pan y frótalo suavemente con el ajo.",
            "Reparte el tomate rallado por encima y termina con el aceite de oliva."
        ),
        "med-yogurt-honey-walnut" to es(
            "Yogur griego con miel y nueces",
            "Sirve el yogur en un cuenco.",
            "Añade las nueces y un hilo fino de miel."
        ),
        "med-mushroom-frittata" to es(
            "Frittata italiana de champiñones",
            "Pocha la cebolla y los champiñones en el aceite.",
            "Añade las claras y la yema batidas y cocina a fuego suave hasta que cuajen."
        ),
        "med-overnight-oats" to es(
            "Avena mediterránea con fresas",
            "Mezcla la avena, la leche y el yogur y refrigera durante la noche.",
            "Sirve con las fresas laminadas y las almendras picadas."
        ),
        "med-tortilla" to es(
            "Tortilla española de patata",
            "Cocina lentamente las láminas finas de patata y cebolla en el aceite.",
            "Añade el huevo batido, forma una tortilla gruesa y cuájala por ambos lados."
        ),
        "med-chicken-paella" to es(
            "Paella cotidiana de pollo",
            "Dora el pollo y después sofríe el tomate y el pimiento con el azafrán.",
            "Incorpora el arroz, los guisantes y agua; cuece sin remover hasta que esté tierno."
        ),
        "med-seafood-paella" to es(
            "Paella española de marisco",
            "Sofríe el tomate y el pimiento con el azafrán y el aceite.",
            "Añade el arroz y agua, coloca los langostinos, almejas y guisantes y cuece hasta que esté hecho."
        ),
        "med-pasta-pomodoro" to es(
            "Pasta al pomodoro y albahaca",
            "Cuece la pasta hasta que quede al dente.",
            "Calienta el tomate con ajo y aceite, mezcla con la pasta y añade la albahaca fresca."
        ),
        "med-pasta-primavera" to es(
            "Pasta integral primavera",
            "Cuece la pasta y reserva un poco del agua de cocción.",
            "Saltea las verduras, añade el tomate y mezcla con la pasta y el agua reservada."
        ),
        "med-eggplant-parmigiana" to es(
            "Berenjena a la parmesana ligera",
            "Asa las láminas de berenjena hasta que estén tiernas.",
            "Forma capas con tomate, albahaca, pan rallado y mozzarella y hornea hasta que burbujee."
        ),
        "med-lemon-chicken" to es(
            "Pollo griego al limón con patatas",
            "Impregna el pollo y las patatas con limón, ajo, orégano y aceite.",
            "Hornea hasta que el pollo esté completamente hecho y las patatas doradas."
        ),
        "med-moussaka" to es(
            "Musaca griega cotidiana",
            "Dora la carne con cebolla y tomate y asa las láminas de berenjena.",
            "Monta las capas, extiende el yogur por encima y hornea hasta dorar."
        ),
        "med-salmon-traybake" to es(
            "Salmón mediterráneo al horno",
            "Coloca el salmón y las verduras en una bandeja.",
            "Aliña con limón y aceite y hornea hasta que el salmón se separe en lascas."
        ),
        "med-cod-tomato-olives" to es(
            "Bacalao con tomate y aceitunas",
            "Cuece el tomate, la cebolla, el ajo y las aceitunas hasta obtener una salsa rápida.",
            "Añade el bacalao y cocina suavemente hasta que se separe en lascas."
        ),
        "med-gazpacho" to es(
            "Gazpacho andaluz",
            "Tritura el tomate, el pepino, el pimiento, el ajo y el pan hasta que quede fino.",
            "Emulsiona con el aceite de oliva, enfría bien y sirve frío."
        ),
        "med-greek-salad" to es(
            "Ensalada griega clásica",
            "Corta las verduras en trozos de bocado.",
            "Añade las aceitunas y el queso de cabra y aliña ligeramente con aceite."
        ),
        "med-hummus" to es(
            "Hummus cremoso",
            "Tritura los garbanzos, el tahini, el limón y el ajo con un poco de agua.",
            "Termina con aceite de oliva y sirve como crema para untar o guarnición."
        ),
        "med-roast-vegetables" to es(
            "Verduras mediterráneas asadas",
            "Corta todas las verduras en trozos de tamaño parecido.",
            "Mézclalas con aceite y ásalas hasta que se caramelicen por los bordes."
        ),
        "med-bruschetta" to es(
            "Bruschetta de tomate y albahaca",
            "Tuesta el pan hasta que quede crujiente.",
            "Cubre con tomate picado, albahaca, ajo y aceite de oliva."
        ),
        "med-tzatziki-toast" to es(
            "Tzatziki con tostada integral",
            "Mezcla el pepino rallado con el yogur, el ajo, el eneldo y el aceite.",
            "Enfría unos minutos y sirve con la tostada integral."
        ),
        "med-fruit-yogurt" to es(
            "Parfait de yogur griego y fresa",
            "Alterna capas de yogur y fresas laminadas.",
            "Termina con la avena y un pequeño hilo de miel."
        ),
        "med-rice-pudding" to es(
            "Arroz con leche y canela",
            "Cuece el arroz lentamente en la leche hasta que esté tierno y cremoso.",
            "Endulza ligeramente y termina con canela y limón."
        ),
        "med-lemon-yogurt" to es(
            "Mousse de yogur, limón y miel",
            "Bate el yogur con el limón hasta que quede aireado.",
            "Incorpora la miel con movimientos suaves y enfría antes de servir."
        ),
        "med-strawberry-crumble" to es(
            "Crumble de fresa y almendra",
            "Coloca las fresas en una fuente pequeña de horno.",
            "Mezcla la avena, las almendras, el azúcar y el aceite en migas y hornea sobre la fruta."
        ),

        // Low-Calorie Recipes
        "light-spinach-scramble" to es(
            "Revuelto de claras y espinacas",
            "Saltea las espinacas y el tomate en una sartén antiadherente.",
            "Añade las claras y remueve suavemente hasta que estén recién cuajadas."
        ),
        "light-berry-oats" to es(
            "Avena con yogur y frutos rojos",
            "Mezcla la avena con el yogur y deja reposar diez minutos o toda la noche.",
            "Incorpora las frambuesas y los arándanos."
        ),
        "light-avocado-egg-toast" to es(
            "Tostada de aguacate y huevo escalfado",
            "Tuesta el pan y extiende el aguacate machacado.",
            "Coloca encima el huevo escalfado y el tomate picado."
        ),
        "light-banana-oat-pancakes" to es(
            "Tortitas de plátano y avena",
            "Tritura el plátano, la harina de avena, el huevo y la leche hasta obtener una masa.",
            "Cocina tortitas pequeñas en una sartén antiadherente hasta dorarlas por ambos lados."
        ),
        "light-chicken-quinoa" to es(
            "Bol de pollo, quinoa y verduras",
            "Cuece la quinoa y cocina el pollo a la plancha hasta que esté hecho.",
            "Sirve con las verduras picadas y aliña con limón y aceite."
        ),
        "light-turkey-courgette" to es(
            "Calabacines rellenos de pavo",
            "Vacía el calabacín y cocina la pulpa con el pavo, la cebolla y el tomate.",
            "Rellena las mitades, añade un poco de mozzarella y hornea hasta que estén tiernas."
        ),
        "light-hake-broccoli" to es(
            "Merluza con brócoli al horno",
            "Coloca la merluza, el brócoli y la patata en trozos pequeños en una bandeja.",
            "Aliña con limón y aceite y hornea hasta que el pescado se separe en lascas."
        ),
        "light-tuna-bean-salad" to es(
            "Ensalada de atún y alubias blancas",
            "Escurre el atún y las alubias y mézclalos con las verduras picadas.",
            "Aliña con limón y aceite de oliva."
        ),
        "light-lentil-soup" to es(
            "Sopa saciante de lentejas y tomate",
            "Cuece las lentejas, el tomate, la zanahoria, el apio y la cebolla en el caldo.",
            "Tritura una pequeña parte para espesar la sopa y termina la cocción."
        ),
        "light-tofu-stir-fry" to es(
            "Salteado de tofu y verduras",
            "Dora el tofu en una sartén antiadherente bien caliente.",
            "Añade las verduras y termina con salsa de soja y aceite de sésamo."
        ),
        "light-salmon-cauliflower" to es(
            "Salmón con puré de coliflor",
            "Cuece la coliflor al vapor y tritúrala con la leche.",
            "Cocina el salmón, condimenta con limón y sirve sobre el puré."
        ),
        "light-turkey-lettuce-burger" to es(
            "Hamburguesa de pavo en hojas de lechuga",
            "Forma una hamburguesa con el pavo y cocínala por completo.",
            "Envuélvela en lechuga y sirve con tomate, pepino, cebolla y mostaza."
        ),
        "light-chicken-veg-soup" to es(
            "Sopa de pollo y verduras",
            "Cuece las verduras en el caldo hasta que estén casi tiernas.",
            "Añade el pollo en trozos pequeños y cocínalo suavemente hasta que esté hecho."
        ),
        "light-chickpea-curry" to es(
            "Curry de garbanzos y coliflor",
            "Cuece la coliflor, los garbanzos, el tomate, la cebolla y el curry hasta que estén tiernos.",
            "Retira del fuego e incorpora el yogur para dar cremosidad."
        ),
        "light-carrot-pumpkin-soup" to es(
            "Crema de zanahoria y calabaza",
            "Cuece la calabaza, la zanahoria y la cebolla en el caldo hasta que estén blandas.",
            "Tritura hasta obtener una crema fina y termina con aceite."
        ),
        "light-cauliflower-mash" to es(
            "Puré cremoso de coliflor",
            "Cuece la coliflor y el ajo al vapor hasta que estén muy tiernos.",
            "Tritura con la leche y una pequeña cantidad de mantequilla ligera."
        ),
        "light-yogurt-slaw" to es(
            "Ensalada de col y pepino con yogur",
            "Corta finamente la col y la zanahoria y lamina el pepino.",
            "Mezcla con el yogur y el limón y enfría antes de servir."
        ),
        "light-broccoli-mushrooms" to es(
            "Brócoli y champiñones asados",
            "Mezcla el brócoli y los champiñones con el ajo y el aceite.",
            "Asa a temperatura alta hasta dorar los bordes."
        ),
        "light-cottage-strawberry" to es(
            "Requesón con fresas",
            "Sirve el requesón en un cuenco.",
            "Añade las fresas laminadas y la canela."
        ),
        "light-hummus-sticks" to es(
            "Hummus con bastones de verduras",
            "Tritura los garbanzos con el tahini, el limón y un poco de agua.",
            "Acompaña con bastones de zanahoria, pepino y pimiento."
        ),
        "light-banana-yogurt" to es(
            "Yogur helado de plátano",
            "Tritura el plátano y el yogur hasta obtener una crema fina.",
            "Congela hasta que se pueda servir con cuchara y espolvorea canela."
        ),
        "light-raspberry-yogurt" to es(
            "Crema de yogur y frambuesa",
            "Machaca la mitad de las frambuesas con la miel.",
            "Mézclalas parcialmente con el yogur y añade el resto por encima."
        ),
        "light-cocoa-oat-bites" to es(
            "Bocaditos de cacao y avena",
            "Machaca y mezcla todos los ingredientes hasta obtener una masa espesa.",
            "Forma bocaditos pequeños y enfría hasta que estén firmes."
        ),
        "light-mango-yogurt" to es(
            "Vaso de mango fresco y yogur",
            "Corta el mango en dados y forma capas con el yogur.",
            "Añade la avena justo antes de servir."
        ),

        // World Cuisine
        "world-huevos-rancheros" to es(
            "Huevos rancheros mexicanos",
            "Calienta las alubias y el tomate con el chile verde.",
            "Cocina el huevo y sirve todo sobre el pan de maíz caliente."
        ),
        "world-masala-oats" to es(
            "Avena masala india",
            "Pocha la cebolla y el tomate con el curry.",
            "Añade la avena, los guisantes y agua y cuece hasta obtener una crema salada."
        ),
        "world-japanese-breakfast" to es(
            "Bol japonés de arroz y salmón",
            "Cuece el arroz y cocina el salmón.",
            "Sirve con pepino, salsa de soja y semillas de sésamo."
        ),
        "world-french-toast" to es(
            "Tostada francesa con fresas",
            "Empapa el pan en el huevo batido con leche y canela.",
            "Cocina en una sartén antiadherente y sirve con las fresas."
        ),
        "world-classic-burger" to es(
            "Hamburguesa clásica de ternera y queso",
            "Forma la hamburguesa y cocínala hasta un punto seguro.",
            "Móntala con queso, lechuga, tomate, cebolla y mostaza."
        ),
        "world-turkey-avocado-burger" to es(
            "Hamburguesa de pavo y aguacate",
            "Forma una hamburguesa con el pavo y cocínala por completo.",
            "Sírvela en el pan con aguacate, lechuga, tomate y cebolla."
        ),
        "world-chickpea-burger" to es(
            "Hamburguesa vegetal de garbanzos especiados",
            "Machaca los garbanzos con zanahoria, cebolla, pan rallado y comino y forma una hamburguesa.",
            "Cocina hasta que esté crujiente y sirve en el pan con lechuga."
        ),
        "world-margherita-pizza" to es(
            "Pizza margarita integral",
            "Prepara una masa sencilla con harina y agua y estírala fina.",
            "Cubre con tomate y mozzarella, hornea fuerte y termina con albahaca."
        ),
        "world-vegetable-pizza" to es(
            "Pizza de verduras asadas",
            "Estira la masa fina y reparte el tomate.",
            "Añade las verduras y la mozzarella y hornea hasta que esté crujiente y dorada."
        ),
        "world-chicken-curry" to es(
            "Curry indio de pollo con arroz",
            "Cocina la cebolla, el tomate y el curry hasta obtener una salsa espesa.",
            "Cuece el pollo en la salsa, incorpora el yogur y sirve con arroz."
        ),
        "world-red-lentil-dal" to es(
            "Dal indio de lentejas con arroz",
            "Cuece las lentejas con tomate, cebolla, ajo y curry hasta que estén cremosas.",
            "Sirve con arroz recién cocido."
        ),
        "world-thai-tofu-curry" to es(
            "Curry tailandés de coco y tofu",
            "Cuece suavemente la leche de coco con el jengibre y la salsa de soja.",
            "Añade el tofu y las verduras y sirve con el arroz cuando estén tiernos."
        ),
        "world-chinese-stir-fry" to es(
            "Salteado chino de pollo",
            "Saltea el pollo hasta que esté casi hecho y añade las verduras.",
            "Termina con salsa de soja y aceite de sésamo y sirve sobre el arroz."
        ),
        "world-mexican-chili" to es(
            "Chili mexicano de ternera y alubias",
            "Dora la carne con la cebolla, el chile y el comino.",
            "Añade las alubias, el maíz y el tomate y cuece hasta que espese."
        ),
        "world-guacamole" to es(
            "Guacamole mexicano con pan de maíz",
            "Machaca el aguacate con tomate, cebolla, limón y cilantro.",
            "Sirve inmediatamente con trozos de pan de maíz."
        ),
        "world-miso-soup" to es(
            "Sopa japonesa de miso y tofu",
            "Disuelve el miso en agua caliente sin dejar que hierva con fuerza.",
            "Añade el tofu y los brotes de soja, calienta y termina con cebollino."
        ),
        "world-tabbouleh" to es(
            "Tabulé de Oriente Medio",
            "Remoja o cuece el trigo partido y déjalo enfriar.",
            "Mezcla con tomate, pepino, perejil, limón y aceite."
        ),
        "world-spiced-cauliflower" to es(
            "Coliflor especiada india",
            "Mezcla la coliflor y la cebolla con comino, curry y aceite.",
            "Asa hasta dorar y añade el tomate picado."
        ),
        "world-chili-popcorn" to es(
            "Palomitas con chile y lima",
            "Calienta las palomitas en una sartén seca si es necesario.",
            "Mézclalas con el chile en polvo y la lima fresca."
        ),
        "world-peanut-banana-toast" to es(
            "Tostada de cacahuete y plátano",
            "Tuesta el pan y úntalo con la crema de cacahuete.",
            "Añade las rodajas de plátano y la canela."
        ),
        "world-mango-coconut-rice" to es(
            "Arroz con leche de coco y mango",
            "Cuece el arroz con la leche de coco y agua hasta que esté cremoso.",
            "Endulza ligeramente y sirve con el mango fresco."
        ),
        "world-berry-parfait" to es(
            "Parfait francés de yogur y frutos rojos",
            "Forma capas de yogur, frambuesas y arándanos.",
            "Termina con una pequeña cucharada de granola."
        ),
        "world-mexican-cocoa-oats" to es(
            "Avena mexicana con cacao y canela",
            "Cuece la avena y la leche hasta que estén cremosas.",
            "Añade el cacao, la canela y una pizca mínima de chile rojo."
        ),
        "world-coconut-banana-bites" to es(
            "Bocaditos de coco y plátano",
            "Corta el plátano y baña un lado de cada rodaja en chocolate negro fundido.",
            "Cubre con coco rallado y enfría hasta que se endurezca."
        ),

        // Mediterranean Cuisine · additional main dishes
        "med-chicken-olive-stew" to es(
            "Guiso mediterráneo de pollo y aceitunas",
            "Corta el pollo en trozos uniformes y prepara la cebolla, el tomate y las aceitunas.",
            "Dora el pollo, añade la cebolla y deja que se ablande antes de incorporar el tomate.",
            "Agrega las aceitunas y cuece a fuego suave hasta que el pollo esté hecho y la salsa espese."
        ),
        "med-turkey-spinach-meatballs" to es(
            "Albóndigas de pavo y espinacas a la griega",
            "Pica las espinacas, mézclalas con el pavo y el pan rallado y forma albóndigas iguales.",
            "Dora las albóndigas por todos los lados en una sartén amplia.",
            "Añade el tomate y termina la cocción a fuego suave hasta que el pavo esté bien hecho."
        ),
        "med-pork-mushroom-skillet" to es(
            "Salteado ibérico de cerdo y champiñones",
            "Corta el cerdo en tiras finas y lamina los champiñones y la cebolla.",
            "Dora el cerdo a fuego vivo y resérvalo para evitar que se reseque.",
            "Saltea las verduras, devuelve el cerdo a la sartén y cocina hasta que esté en su punto."
        ),
        "med-rabbit-rosemary-stew" to es(
            "Conejo guisado al romero",
            "Trocea el conejo y prepara la cebolla, la zanahoria y el tomate.",
            "Dora el conejo, añade las verduras y cocina hasta que la cebolla esté tierna.",
            "Agrega el tomate y el romero y cuece tapado hasta que la carne esté completamente hecha."
        ),
        "med-lamb-eggplant-bake" to es(
            "Cordero al horno con berenjena",
            "Corta la berenjena en dados regulares y pica la cebolla.",
            "Dora el cordero, incorpora la cebolla, la berenjena y el tomate y reduce el relleno.",
            "Pasa la mezcla a una fuente y hornea hasta que la berenjena esté tierna."
        ),
        "med-hake-garlic-parsley" to es(
            "Merluza al ajo y perejil con patata",
            "Corta la patata en rodajas finas y cuécela hasta que esté casi tierna.",
            "Coloca la merluza sobre la patata y añade el ajo y el aceite de oliva.",
            "Hornea hasta que el pescado se desmenuce con facilidad y termina con perejil y limón."
        ),
        "med-salmon-spinach-pasta" to es(
            "Pasta italiana con salmón y espinacas",
            "Cuece la pasta al dente y reserva un poco del agua de cocción.",
            "Cocina el salmón, desmenúzalo en trozos grandes y deja que las espinacas se ablanden.",
            "Mezcla la pasta con el salmón, las espinacas y el tomate y ajusta la salsa con el agua reservada."
        ),
        "med-tuna-tomato-rice" to es(
            "Arroz mediterráneo con atún y tomate",
            "Cuece el arroz y corta el tomate y el pimiento en trozos pequeños.",
            "Sofríe el pimiento y el tomate hasta obtener una salsa concentrada.",
            "Incorpora el atún escurrido y el arroz y calienta todo sin deshacer demasiado el pescado."
        ),
        "med-prawn-courgette-pasta" to es(
            "Pasta con gambas y calabacín",
            "Cuece la pasta al dente, lamina el calabacín y pica el ajo.",
            "Saltea las gambas hasta que estén casi opacas y resérvalas; cocina después el calabacín.",
            "Añade de nuevo las gambas y mezcla con la pasta, el ajo y el aceite de oliva."
        ),
        "med-white-bean-stew" to es(
            "Guiso mediterráneo de alubias blancas y acelgas",
            "Lava y trocea las acelgas y corta la cebolla, la zanahoria y el tomate.",
            "Cocina la cebolla, la zanahoria y el tomate y añade las alubias con un poco de agua.",
            "Incorpora las acelgas y el aceite y cuece hasta que las hojas estén tiernas."
        ),
        "med-lentil-moussaka" to es(
            "Musaca vegetariana de lentejas",
            "Cuece las lentejas hasta que estén tiernas y asa la berenjena en rodajas uniformes.",
            "Cocina la cebolla, el tomate y las lentejas hasta que el relleno quede espeso.",
            "Alterna capas con la berenjena, cubre con yogur y hornea hasta dorar."
        ),
        "med-chickpea-semolina-bowl" to es(
            "Bol mediterráneo de garbanzos y sémola",
            "Remoja y cuece los garbanzos hasta que estén tiernos y prepara la sémola con agua caliente.",
            "Asa o saltea el calabacín, el pimiento y el tomate hasta que estén tiernos.",
            "Suelta la sémola con un tenedor y mézclala con los garbanzos, las verduras y el aceite."
        ),
        "med-mushroom-risotto" to es(
            "Risotto italiano de champiñones",
            "Calienta el caldo, dora los champiñones y reserva la mitad para servir.",
            "Pocha la cebolla, añade el arroz e incorpora el caldo caliente poco a poco mientras remueves.",
            "Cuando el arroz esté cremoso y tierno, agrega el queso y los champiñones reservados."
        ),
        "med-spinach-cottage-pasta" to es(
            "Pasta con espinacas y queso cottage",
            "Cuece la pasta al dente y reserva una taza pequeña del agua de cocción.",
            "Cocina las espinacas con el tomate y el ajo, baja el fuego y añade el queso cottage.",
            "Incorpora la pasta y agrega agua reservada hasta que la salsa la cubra ligeramente."
        ),
        "med-chickpea-patties" to es(
            "Tortitas griegas de garbanzos con ensalada de yogur",
            "Machaca los garbanzos y mézclalos con pan rallado, zanahoria, cebolla y comino.",
            "Forma tortitas compactas y cocínalas en una sartén antiadherente hasta dorarlas por ambos lados.",
            "Mezcla el yogur con el pepino en dados y sírvelo junto a las tortitas calientes."
        ),
        "med-turkey-meatball-pasta" to es(
            "Pasta italiana con albóndigas de pavo",
            "Mezcla el pavo con pan rallado, forma albóndigas pequeñas y dóralas de manera uniforme.",
            "Añade la cebolla y el tomate y cuece las albóndigas en la salsa hasta que estén hechas.",
            "Cuece la pasta, mézclala con la salsa y termina con albahaca fresca."
        ),
        "med-chicken-spinach-rice" to es(
            "Arroz español con pollo y espinacas",
            "Trocea y dora el pollo en una sartén amplia y cocina brevemente el ajo.",
            "Añade el tomate, el arroz y el agua medida y cuece hasta que el arroz esté casi tierno.",
            "Incorpora las espinacas y termina hasta que el pollo esté hecho y se absorba el líquido."
        ),
        "med-cod-chickpea-stew" to es(
            "Guiso de bacalao y garbanzos",
            "Pocha la cebolla en el aceite y cuécela con el tomate hasta concentrar la salsa.",
            "Añade los garbanzos y las espinacas con un poco de agua y cocina varios minutos.",
            "Coloca el bacalao y cuece suavemente hasta que se desmenuce sin romperlo en exceso."
        ),
        "med-stuffed-eggplant-beef" to es(
            "Berenjena mediterránea rellena de ternera",
            "Parte la berenjena, marca la pulpa y ásala hasta que se pueda extraer.",
            "Dora la carne y la cebolla, añade la pulpa picada y el tomate y reduce el relleno.",
            "Rellena las mitades, cubre con mozzarella y hornea hasta que estén calientes y doradas."
        ),
        "med-tofu-tomato-bake" to es(
            "Tofu mediterráneo al horno con tomate",
            "Prensa y corta el tofu en dados y trocea la berenjena y el calabacín por igual.",
            "Asa las verduras y el tofu hasta que se doren ligeramente y añade el tomate.",
            "Cubre con mozzarella y hornea de nuevo hasta que la salsa burbujee y el queso se funda."
        ),

        // Low-Calorie Recipes · additional main dishes
        "light-lemon-turkey-quinoa" to es(
            "Plato ligero de pavo al limón con quinoa",
            "Cuece la quinoa con el agua medida y corta el pavo y las verduras de forma uniforme.",
            "Cocina el pavo en una sartén antiadherente y añade el calabacín y el pimiento hasta que estén tiernos.",
            "Suelta la quinoa, añade el limón y el aceite y sírvela con la mezcla de pavo."
        ),
        "light-chicken-broccoli-rice" to es(
            "Bol de pollo, brócoli y arroz integral",
            "Cuece el arroz integral hasta que esté tierno y corta el pollo en trozos pequeños.",
            "Saltea el pollo, añade el brócoli y la zanahoria con un poco de agua y termina la cocción.",
            "Agrega la salsa de soja y el aceite de sésamo y sirve la mezcla sobre el arroz."
        ),
        "light-hake-tomato-courgette" to es(
            "Merluza ligera con tomate y calabacín",
            "Corta el calabacín y la cebolla y repártelos en una fuente baja.",
            "Añade el tomate y hornea hasta que las verduras se ablanden; coloca la merluza encima.",
            "Agrega el limón y hornea de nuevo hasta que el pescado esté opaco y se desmenuce."
        ),
        "light-prawn-cauliflower-rice" to es(
            "Salteado de gambas con arroz de coliflor",
            "Tritura o ralla la coliflor hasta obtener granos y prepara las demás verduras.",
            "Cocina las gambas y resérvalas; saltea la zanahoria, los guisantes y la coliflor.",
            "Devuelve las gambas, añade la salsa de soja y el aceite de sésamo y calienta todo."
        ),
        "light-tuna-courgette-boats" to es(
            "Barquitas de calabacín rellenas de atún",
            "Parte el calabacín, vacía el centro y hornea las mitades hasta que estén algo tiernas.",
            "Cocina la pulpa con la cebolla y el tomate e incorpora el atún escurrido.",
            "Rellena las barquitas, añade mozzarella y hornea hasta que estén calientes y doradas."
        ),
        "light-turkey-meatballs" to es(
            "Albóndigas magras de pavo con tomate",
            "Mezcla el pavo con el pan rallado y la clara y forma albóndigas del mismo tamaño.",
            "Dóralas en una sartén antiadherente, retíralas y cocina la cebolla y el calabacín.",
            "Añade el tomate, devuelve las albóndigas y cuece tapado hasta que estén bien hechas."
        ),
        "light-tofu-broccoli-curry" to es(
            "Curry ligero de tofu y brócoli",
            "Corta el tofu en dados y el brócoli en ramilletes pequeños para que se cocinen por igual.",
            "Pocha la cebolla con el curry, añade el tomate y el brócoli y cuece hasta casi tiernos.",
            "Incorpora el tofu, calienta y mezcla el yogur fuera del fuego."
        ),
        "light-white-bean-chard-soup" to es(
            "Sopa de alubias blancas y acelgas",
            "Trocea las acelgas, la zanahoria y el apio y lleva el caldo a fuego suave.",
            "Añade la zanahoria, el apio, el tomate y las alubias y cuece hasta que estén tiernos.",
            "Incorpora las acelgas en los últimos minutos y cocina hasta que se ablanden."
        ),
        "light-lentil-stuffed-pepper" to es(
            "Pimiento relleno de lentejas",
            "Cuece las lentejas hasta que estén tiernas y parte y limpia el pimiento.",
            "Cocina la cebolla, la zanahoria y el tomate y mezcla las lentejas escurridas.",
            "Rellena el pimiento, añade mozzarella y hornea hasta que esté tierno."
        ),
        "light-egg-white-frittata" to es(
            "Frittata de claras y verduras",
            "Lamina los champiñones y el pimiento y trocea las espinacas y el tomate.",
            "Cocina las verduras en una sartén antiadherente apta para horno hasta que pierdan el exceso de agua.",
            "Vierte las claras, cocina suavemente y termina bajo el grill hasta que cuajen."
        ),
        "light-pork-cabbage" to es(
            "Cerdo magro con col de Saboya",
            "Corta el cerdo en tiras finas y la col, la zanahoria y la cebolla en juliana.",
            "Marca el cerdo en una sartén antiadherente muy caliente y retíralo al dorarse.",
            "Saltea las verduras, devuelve el cerdo y termina con soja y aceite de sésamo."
        ),
        "light-chicken-mushroom-wraps" to es(
            "Rollitos de lechuga con pollo y champiñones",
            "Pica finamente el pollo y los champiñones y separa hojas firmes de lechuga.",
            "Cocina el pollo, incorpora los champiñones, la zanahoria y la cebolla y termina la cocción.",
            "Condimenta con soja y reparte el relleno caliente entre las hojas de lechuga."
        ),
        "light-salmon-cucumber-quinoa" to es(
            "Ensalada de salmón, pepino y quinoa",
            "Cuece y enfría la quinoa y cocina el salmón hasta que se desmenuce.",
            "Corta el pepino y el tomate y mezcla el limón con el yogur.",
            "Reparte el salmón sobre la quinoa y las verduras y añade el aliño."
        ),
        "light-chickpea-spinach-salad" to es(
            "Ensalada templada de garbanzos y espinacas",
            "Escurre los garbanzos y corta el tomate y el pepino en trozos uniformes.",
            "Calienta los garbanzos y cocina las espinacas solo hasta que estén tiernas.",
            "Mezcla con las verduras crudas y aliña con limón y aceite de oliva."
        ),
        "light-hake-cabbage-parcel" to es(
            "Paquetitos de merluza y col",
            "Ablanda brevemente las hojas grandes de col y corta la zanahoria y el puerro en juliana.",
            "Coloca la merluza y las verduras sobre las hojas, añade limón y forma paquetes cerrados.",
            "Hornea o cocina al vapor hasta que la merluza esté opaca y se desmenuce fácilmente."
        ),
        "light-turkey-pumpkin-stew" to es(
            "Guiso ligero de pavo y calabaza",
            "Corta el pavo y la calabaza en trozos de tamaño parecido.",
            "Dora el pavo en una cazuela antiadherente y ablanda la cebolla y la zanahoria.",
            "Añade la calabaza, el tomate y el caldo y cuece hasta que todo esté tierno."
        ),
        "light-tofu-eggplant-bowl" to es(
            "Bol de tofu, berenjena y tomate",
            "Prensa y corta el tofu en dados y trocea la berenjena y el calabacín por igual.",
            "Asa las verduras hasta que estén tiernas y dora el tofu en una sartén antiadherente.",
            "Calienta el tomate con la soja y mézclalo con las verduras y el tofu."
        ),
        "light-chicken-cauliflower-curry" to es(
            "Curry ligero de pollo y coliflor",
            "Corta el pollo y la coliflor en trozos pequeños y uniformes.",
            "Pocha la cebolla con el curry, añade el tomate y el pollo y cuece hasta que esté casi hecho.",
            "Incorpora la coliflor y, cuando esté tierna, mezcla el yogur fuera del fuego."
        ),
        "light-tuna-broccoli-pasta" to es(
            "Pasta integral con atún y brócoli",
            "Cuece la pasta y añade el brócoli en los últimos minutos para que terminen juntos.",
            "Calienta el tomate y el ajo en una sartén amplia e incorpora el atún escurrido.",
            "Escurre la pasta y el brócoli, mezcla con la salsa y termina con aceite."
        ),
        "light-beef-pepper-bowl" to es(
            "Bol ligero de ternera y pimiento",
            "Corta la ternera contra la fibra y trocea el pimiento, los champiñones y la cebolla.",
            "Marca rápidamente la ternera en una sartén antiadherente muy caliente y resérvala.",
            "Cocina las verduras, devuelve la carne con la soja y sirve sobre la lechuga."
        ),

        // World Cuisine · additional main dishes
        "world-chicken-taco-bowl" to es(
            "Bol mexicano de tacos de pollo",
            "Cuece el arroz y corta el pollo, el tomate y el pimiento en trozos pequeños.",
            "Dora el pollo con comino, añade el pimiento y cocina hasta que el pollo esté hecho.",
            "Coloca el arroz, los garbanzos, el pollo y el tomate en un bol y sirve caliente."
        ),
        "world-fish-taco-bowl" to es(
            "Bol de tacos de pescado al estilo Baja",
            "Cuece el arroz, corta la col en juliana y trocea el tomate mientras reposa el arroz.",
            "Sazona y cocina la merluza hasta que esté opaca y sepárala en lascas grandes.",
            "Forma capas de arroz, col, tomate y pescado y termina con yogur de lima."
        ),
        "world-turkey-quesadilla" to es(
            "Quesadilla de pavo y pimiento",
            "Mezcla la harina con agua hasta obtener una masa blanda, déjala reposar y estira dos tortillas.",
            "Cocina bien el pavo, la cebolla y el pimiento y coloca el relleno y el queso entre las tortillas.",
            "Tuesta la quesadilla por ambos lados, córtala en porciones y sirve con tomate."
        ),
        "world-chicken-tikka" to es(
            "Bol indio de pollo tikka con arroz",
            "Cubre el pollo con la mitad del yogur, el curry y el jengibre y déjalo marinar brevemente.",
            "Cuece el arroz, dora y cocina completamente el pollo y después reduce el tomate.",
            "Mezcla el yogur restante en la salsa fuera del fuego y sirve con el pollo y el arroz."
        ),
        "world-palak-tofu" to es(
            "Tofu indio con espinacas y arroz",
            "Cuece el arroz, prensa y corta el tofu en dados y lava bien las espinacas.",
            "Dora el tofu y pocha la cebolla y el ajo con el curry antes de añadir las espinacas.",
            "Tritura o pica finamente la salsa de espinacas, incorpora el tofu y sirve con arroz."
        ),
        "world-teriyaki-salmon" to es(
            "Salmón teriyaki al estilo japonés",
            "Cuece el arroz integral y cocina el brócoli al vapor hasta que esté tierno y verde.",
            "Mezcla la soja y el jengibre, pincela el salmón y cocínalo hasta que se desmenuce.",
            "Reduce brevemente la salsa restante y repártela sobre el salmón, el arroz y el brócoli."
        ),
        "world-tuna-sushi-bowl" to es(
            "Bol japonés de sushi de atún",
            "Cuece el arroz y deja que se temple mientras cortas las verduras de forma uniforme.",
            "Marca el atún por todos los lados, mantén el centro al punto deseado y córtalo en láminas.",
            "Coloca el atún, el pepino y la zanahoria sobre el arroz y añade soja y sésamo."
        ),
        "world-korean-beef-bowl" to es(
            "Bol coreano de ternera y arroz",
            "Cuece el arroz y corta la ternera finamente contra la fibra para una cocción rápida.",
            "Marca la ternera con el jengibre y el chile, añade la soja y deja que cubra la carne.",
            "Cocina el brócoli al vapor y sírvelo junto a la ternera sobre el arroz."
        ),
        "world-thai-prawn-noodles" to es(
            "Fideos tailandeses con gambas",
            "Cuece los fideos hasta que estén tiernos, escúrrelos y prepara el pimiento y la zanahoria.",
            "Saltea las gambas hasta que estén casi opacas y añade las verduras, dejándolas algo crujientes.",
            "Incorpora los fideos con la soja y la lima y calienta todo por igual."
        ),
        "world-tofu-satay-rice" to es(
            "Bol indonesio de tofu satay con arroz",
            "Cuece el arroz, prensa y corta el tofu y separa el brócoli en ramilletes pequeños.",
            "Dora el tofu, cocina el brócoli al vapor y mezcla el cacahuete, la soja y la lima con agua tibia.",
            "Cubre ligeramente el tofu con la salsa satay y sirve con el brócoli y el arroz."
        ),
        "world-moroccan-chicken" to es(
            "Pollo marroquí especiado con garbanzos",
            "Corta el pollo y la zanahoria por igual y escurre y enjuaga los garbanzos.",
            "Dora el pollo, añade comino y pimentón y cuécelo con el tomate y la zanahoria.",
            "Incorpora los garbanzos y cocina hasta que estén calientes y la salsa espese."
        ),
        "world-lebanese-kofta" to es(
            "Plato libanés de kofta de ternera",
            "Mezcla la ternera con comino, forma koftas pequeñas y prepara la sémola con agua caliente.",
            "Cocina las koftas por todos los lados hasta que estén doradas y bien hechas.",
            "Sirve con la sémola, el pepino y el tomate picados y una cucharada de yogur."
        ),
        "world-turkish-eggplant" to es(
            "Berenjena rellena al estilo turco",
            "Parte y marca la berenjena, pincélala con aceite y ásala hasta ablandar el centro.",
            "Cocina el pavo con la cebolla y el ajo, añade el tomate y reduce hasta obtener un relleno espeso.",
            "Rellena las mitades de berenjena y vuelve a hornear hasta que estén muy calientes."
        ),
        "world-jerk-chicken" to es(
            "Arroz caribeño con pollo al estilo jerk",
            "Cuece el arroz y sazona el pollo con pimentón y una cantidad controlada de chile.",
            "Dora y cocina el pollo por completo y saltea el pimiento hasta que esté tierno.",
            "Sirve el pollo en rodajas y el pimiento sobre el arroz con tomate fresco."
        ),
        "world-brazilian-beef-beans" to es(
            "Bol brasileño de ternera y alubias",
            "Cuece el arroz, corta la ternera en tiras y prepara el pimiento y la cebolla.",
            "Marca la ternera y resérvala; pocha la cebolla y el pimiento y añade el tomate.",
            "Calienta las alubias en la salsa, devuelve la carne y sirve la mezcla sobre el arroz."
        ),
        "world-bbq-chicken-burger" to es(
            "Hamburguesa de pollo a la barbacoa",
            "Aplana el pollo para igualar su grosor y cocina la cebolla y el tomate hasta obtener una salsa espesa.",
            "Cocina completamente el pollo, añade el queso al final y tuesta ligeramente el pan.",
            "Monta la hamburguesa con lechuga, pollo y salsa de tomate y cebolla y sirve enseguida."
        ),
        "world-mango-chicken-burger" to es(
            "Hamburguesa de pollo y mango",
            "Aplana el pollo, corta el mango y el pepino y lava la lechuga.",
            "Cocina el pollo por completo y tuesta ligeramente el interior del pan.",
            "Rellena el pan con lechuga, pollo, pepino y mango y termina con lima."
        ),
        "world-mushroom-cheese-burger" to es(
            "Hamburguesa con queso y champiñones",
            "Forma una hamburguesa de ternera y lamina los champiñones con grosor uniforme.",
            "Dora los champiñones, cocina la hamburguesa y funde el queso encima.",
            "Tuesta el pan y móntalo con lechuga, tomate, la hamburguesa y los champiñones."
        ),
        "world-white-spinach-pizza" to es(
            "Pizza blanca de espinacas y champiñones",
            "Mezcla la harina y el agua hasta formar una masa, amasa, deja reposar y estira fina.",
            "Cocina las espinacas, dora los champiñones y reparte el queso cottage sobre la masa.",
            "Añade las verduras y la mozzarella y hornea fuerte hasta que esté crujiente y dorada."
        ),
        "world-mexican-vegetable-pizza" to es(
            "Pizza mexicana de verduras",
            "Prepara una masa blanda de harina y agua, amásala, déjala reposar y estírala fina.",
            "Reparte el tomate y añade el pimiento, los guisantes y los garbanzos escurridos.",
            "Cubre con mozzarella y hornea fuerte hasta que la base esté hecha y el queso burbujee."
        )
    )

    fun forKey(key: String): SpanishRecipeText =
        checkNotNull(translations[key]) { "Missing Spanish recipe text for $key" }

    private fun es(name: String, vararg coreSteps: String) =
        SpanishRecipeText(name, coreSteps.toList())
}
