package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories

/** One exact link to the BEDCA-derived food database. */
internal data class BuiltInIngredient(
    val bedcaFoodId: Long,
    val grams: Double,
    val quantityUnit: String = "GRAM"
)

/** Pure recipe data; persistence is handled by [BuiltInRecipeSeeder]. */
internal data class BuiltInRecipe(
    val key: String,
    val name: String,
    val nameEs: String,
    val book: String,
    val category: String,
    val ingredients: List<BuiltInIngredient>,
    val steps: List<String>,
    val stepsEs: List<String>
)

/**
 * The offline starter library: 44 recipes per book and 132 in total.
 *
 * Quantities describe one practical serving. The menu planner's existing
 * portion factor scales that serving for each household member and the
 * shopping-list calculation scales the same canonical gram values.
 *
 * Recipe ideas were adapted rather than copied from Spain.info, Italia.it,
 * Visit Greece and NHS Healthier Families. No external text or images ship in
 * the app. BEDCA ids are deliberately explicit and validated by the seeder.
 */
internal object BuiltInRecipeLibrary {
    val recipes: List<BuiltInRecipe> = listOf(
        // Mediterranean Cuisine · breakfasts
        r(
            "med-pan-tomate", "Pan con Tomate", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.BREAKFAST,
            listOf(i(2163, 80), i(2421, 120), i(2543, 8), i(2361, 2)),
            "Toast the bread and rub it lightly with garlic.",
            "Spoon over grated tomato and finish with olive oil."
        ),
        r(
            "med-yogurt-honey-walnut", "Greek Yogurt with Honey and Walnuts",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.BREAKFAST,
            listOf(i(2520, 200), i(2643, 12), i(2201, 18)),
            "Spoon the yogurt into a bowl.",
            "Top with walnuts and a thin drizzle of honey."
        ),
        r(
            "med-mushroom-frittata", "Italian Mushroom Frittata",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.BREAKFAST,
            listOf(i(1173, 90), i(2125, 35), i(2384, 100), i(2382, 35), i(2543, 5)),
            "Soften the onion and mushrooms in the oil.",
            "Add beaten egg whites and yolk, then cook gently until set."
        ),
        r(
            "med-overnight-oats", "Mediterranean Strawberry Overnight Oats",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.BREAKFAST,
            listOf(i(1010, 45), i(1319, 120), i(2530, 80), i(2225, 100), i(896, 12)),
            "Mix the oats, milk and yogurt and refrigerate overnight.",
            "Serve with sliced strawberries and chopped almonds."
        ),

        // Mediterranean Cuisine · main dishes
        r(
            "med-tortilla", "Spanish Potato Tortilla", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(i(2403, 220), i(1173, 100), i(2125, 40), i(2382, 60), i(2543, 12)),
            "Cook thin potato and onion slices slowly in the olive oil.",
            "Add beaten egg, shape into a thick omelette and cook on both sides."
        ),
        r(
            "med-chicken-paella", "Everyday Chicken Paella", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(i(2660, 75), i(994, 140), i(2421, 100), i(2409, 60), i(1175, 45), i(2475, 1), i(2543, 8)),
            "Brown the chicken, then soften the tomato and pepper with saffron.",
            "Stir in rice and peas, add water and simmer without stirring until tender."
        ),
        r(
            "med-seafood-paella", "Spanish Seafood Paella", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(i(2660, 75), i(581, 100), i(2640, 90), i(2421, 100), i(2409, 50), i(1175, 40), i(2475, 1), i(2543, 8)),
            "Soften the tomato and pepper with the saffron and olive oil.",
            "Add rice and water; nestle in prawns, clams and peas and simmer until done."
        ),
        r(
            "med-pasta-pomodoro", "Pasta al Pomodoro and Basil",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1117, 85), i(481, 180), i(2361, 3), i(2473, 8), i(2543, 8)),
            "Boil the pasta until al dente.",
            "Warm tomato with garlic and olive oil, toss with pasta and fresh basil."
        ),
        r(
            "med-pasta-primavera", "Whole-Wheat Pasta Primavera",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1074, 80), i(2376, 90), i(2409, 60), i(2384, 80), i(2421, 100), i(2543, 7)),
            "Boil the pasta and reserve a splash of its water.",
            "Sauté the vegetables, add tomato, then toss with pasta and reserved water."
        ),
        r(
            "med-eggplant-parmigiana", "Light Eggplant Parmigiana",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2370, 250), i(481, 180), i(2510, 70), i(2171, 18), i(2543, 7), i(2473, 6)),
            "Roast eggplant slices until soft.",
            "Layer with tomato, basil, breadcrumbs and mozzarella, then bake until bubbling."
        ),
        r(
            "med-lemon-chicken", "Greek Lemon Chicken and Potatoes",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(994, 160), i(2403, 220), i(411, 25), i(2598, 2), i(2361, 3), i(2543, 10)),
            "Coat the chicken and potato wedges with lemon, garlic, oregano and oil.",
            "Roast until the chicken is cooked through and the potatoes are golden."
        ),
        r(
            "med-moussaka", "Everyday Greek Moussaka", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.MAIN_DISH,
            listOf(i(2370, 220), i(2680, 130), i(481, 150), i(2382, 50), i(2520, 70), i(2543, 7)),
            "Brown the mince with onion and tomato; roast the eggplant slices.",
            "Layer everything, spread yogurt on top and bake until browned."
        ),
        r(
            "med-salmon-traybake", "Mediterranean Salmon Traybake",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2626, 160), i(2376, 120), i(2421, 120), i(2409, 70), i(411, 15), i(2543, 8)),
            "Arrange the salmon and vegetables on a tray.",
            "Season with lemon and olive oil and roast until the salmon flakes."
        ),
        r(
            "med-cod-tomato-olives", "Cod with Tomato and Olives",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(795, 170), i(481, 180), i(2214, 35), i(2382, 45), i(2361, 3), i(2543, 7)),
            "Simmer tomato, onion, garlic and olives into a quick sauce.",
            "Add the cod and cook gently until it separates into flakes."
        ),

        // Mediterranean Cuisine · sides, snacks and desserts
        r(
            "med-gazpacho", "Andalusian Gazpacho", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.SIDE_DISH,
            listOf(i(2421, 260), i(2407, 90), i(2409, 60), i(2361, 2), i(2160, 25), i(2543, 8)),
            "Blend the tomato, cucumber, pepper, garlic and bread until smooth.",
            "Blend in olive oil, chill well and serve cold."
        ),
        r(
            "med-greek-salad", "Classic Greek Salad", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.SIDE_DISH,
            listOf(i(2421, 150), i(2407, 100), i(2409, 60), i(2214, 35), i(934, 55), i(2543, 7)),
            "Cut the vegetables into bite-sized pieces.",
            "Add olives and goat cheese, then dress lightly with olive oil."
        ),
        r(
            "med-hummus", "Creamy Hummus", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.SIDE_DISH,
            listOf(i(2191, 150), i(2567, 18), i(411, 18), i(2361, 3), i(2543, 5)),
            "Blend chickpeas, tahini, lemon and garlic with a splash of water.",
            "Finish with olive oil and serve as a dip or side."
        ),
        r(
            "med-roast-vegetables", "Roasted Mediterranean Vegetables",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.SIDE_DISH,
            listOf(i(2370, 100), i(2376, 100), i(2409, 80), i(2382, 60), i(2421, 100), i(2543, 9)),
            "Cut all vegetables into similar-sized pieces.",
            "Toss with olive oil and roast until caramelised at the edges."
        ),
        r(
            "med-bruschetta", "Tomato Basil Bruschetta", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.SNACK,
            listOf(i(2160, 70), i(2421, 110), i(2473, 6), i(2361, 2), i(2543, 6)),
            "Toast the bread until crisp.",
            "Top with chopped tomato, basil, garlic and olive oil."
        ),
        r(
            "med-tzatziki-toast", "Tzatziki with Whole-Wheat Toast",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.SNACK,
            listOf(i(2520, 130), i(2407, 80), i(2361, 2), i(1835, 2), i(2163, 55), i(2543, 3)),
            "Mix grated cucumber, yogurt, garlic, dill and olive oil.",
            "Chill briefly and serve with whole-wheat toast."
        ),
        r(
            "med-fruit-yogurt", "Greek Yogurt Strawberry Parfait",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.DESSERT,
            listOf(i(2520, 150), i(2225, 120), i(1010, 18), i(2643, 7)),
            "Layer yogurt with sliced strawberries.",
            "Top with oats and a small drizzle of honey."
        ),
        r(
            "med-rice-pudding", "Cinnamon Rice Pudding", RecipeBooks.MEDITERRANEAN,
            RecipeCategories.DESSERT,
            listOf(i(2660, 45), i(1319, 250), i(2642, 15), i(2477, 1), i(411, 5)),
            "Simmer rice gently in milk until creamy and tender.",
            "Sweeten lightly and finish with cinnamon and lemon."
        ),
        r(
            "med-lemon-yogurt", "Lemon Honey Yogurt Mousse",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.DESSERT,
            listOf(i(2520, 180), i(411, 18), i(2643, 12)),
            "Whisk yogurt with lemon until airy.",
            "Fold in honey and chill before serving."
        ),
        r(
            "med-strawberry-crumble", "Strawberry Almond Crumble",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.DESSERT,
            listOf(i(2225, 180), i(1054, 30), i(896, 15), i(780, 10), i(2543, 4)),
            "Place strawberries in a small baking dish.",
            "Rub oats, almonds, sugar and oil into crumbs and bake over the fruit."
        ),

        // Low-Calorie Recipes · breakfasts
        r(
            "light-spinach-scramble", "Spinach Egg-White Scramble",
            RecipeBooks.LOW_CALORIE, RecipeCategories.BREAKFAST,
            listOf(i(1173, 150), i(1282, 100), i(2421, 100), i(2543, 4)),
            "Wilt the spinach and tomato in a non-stick pan.",
            "Add egg whites and stir gently until just set."
        ),
        r(
            "light-berry-oats", "Berry Yogurt Oats", RecipeBooks.LOW_CALORIE,
            RecipeCategories.BREAKFAST,
            listOf(i(1010, 35), i(2530, 170), i(914, 80), i(2368, 60)),
            "Stir oats into yogurt and leave for ten minutes or overnight.",
            "Fold through the raspberries and bilberries."
        ),
        r(
            "light-avocado-egg-toast", "Avocado and Poached Egg Toast",
            RecipeBooks.LOW_CALORIE, RecipeCategories.BREAKFAST,
            listOf(i(2163, 55), i(2216, 55), i(758, 55), i(2421, 60)),
            "Toast the bread and mash avocado over it.",
            "Top with the poached egg and chopped tomato."
        ),
        r(
            "light-banana-oat-pancakes", "Banana Oat Pancakes",
            RecipeBooks.LOW_CALORIE, RecipeCategories.BREAKFAST,
            listOf(i(2245, 100), i(1054, 35), i(1173, 70), i(2125, 18), i(1319, 35)),
            "Blend banana, oat flour, egg and milk into a batter.",
            "Cook small pancakes in a non-stick pan until golden on both sides."
        ),

        // Low-Calorie Recipes · main dishes
        r(
            "light-chicken-quinoa", "Chicken Quinoa Rainbow Bowl",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(994, 140), i(1083, 55), i(2409, 70), i(2407, 80), i(2424, 70), i(411, 15), i(2543, 5)),
            "Cook the quinoa and grill the chicken until cooked through.",
            "Arrange with chopped vegetables and dress with lemon and olive oil."
        ),
        r(
            "light-turkey-courgette", "Turkey-Stuffed Courgettes",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 140), i(2376, 260), i(481, 120), i(2382, 40), i(2510, 25)),
            "Scoop out the courgette and cook the flesh with turkey, onion and tomato.",
            "Refill the shells, add a little mozzarella and bake until tender."
        ),
        r(
            "light-hake-broccoli", "Hake and Broccoli Traybake",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(825, 180), i(1177, 180), i(2403, 130), i(411, 20), i(2543, 5)),
            "Arrange hake, broccoli and small potato pieces on a tray.",
            "Dress with lemon and oil, then bake until the fish flakes."
        ),
        r(
            "light-tuna-bean-salad", "Tuna and White Bean Salad",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2133, 120), i(2199, 100), i(2421, 120), i(2407, 80), i(2382, 35), i(411, 15), i(2543, 5)),
            "Drain the tuna and beans and combine with chopped vegetables.",
            "Dress with lemon and olive oil."
        ),
        r(
            "light-lentil-soup", "Filling Lentil Tomato Soup",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1065, 60), i(481, 150), i(2424, 80), i(2366, 50), i(2382, 40), i(792, 250)),
            "Simmer lentils, tomato, carrot, celery and onion in vegetable stock.",
            "Cook until tender, blending a small portion to thicken the soup."
        ),
        r(
            "light-tofu-stir-fry", "Tofu Vegetable Stir-Fry",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 160), i(1177, 120), i(2409, 70), i(2384, 100), i(2575, 12), i(752, 3)),
            "Brown the tofu in a hot non-stick pan.",
            "Add the vegetables, then finish with soy sauce and sesame oil."
        ),
        r(
            "light-salmon-cauliflower", "Salmon with Cauliflower Mash",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2626, 130), i(859, 250), i(1319, 35), i(411, 15), i(2543, 3)),
            "Steam the cauliflower and mash it with milk.",
            "Cook the salmon, season with lemon and serve over the mash."
        ),
        r(
            "light-turkey-lettuce-burger", "Turkey Lettuce Burger Plate",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 150), i(2399, 90), i(2421, 100), i(2407, 70), i(2382, 35), i(2600, 8)),
            "Shape the turkey into a patty and cook it through.",
            "Wrap in lettuce and serve with tomato, cucumber, onion and mustard."
        ),
        r(
            "light-chicken-veg-soup", "Chicken Vegetable Soup",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(994, 130), i(2424, 80), i(2376, 100), i(2366, 50), i(2411, 50), i(792, 300)),
            "Simmer the vegetables in stock until nearly tender.",
            "Add small pieces of chicken and cook through gently."
        ),
        r(
            "light-chickpea-curry", "Chickpea Cauliflower Curry",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2191, 150), i(859, 180), i(481, 150), i(2382, 45), i(2621, 5), i(2530, 50)),
            "Simmer cauliflower, chickpeas, tomato, onion and curry until tender.",
            "Stir through yogurt off the heat for a creamy finish."
        ),

        // Low-Calorie Recipes · sides, snacks and desserts
        r(
            "light-carrot-pumpkin-soup", "Carrot Pumpkin Soup",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SIDE_DISH,
            listOf(i(2378, 180), i(2424, 100), i(2382, 35), i(792, 250), i(2543, 3)),
            "Simmer pumpkin, carrot and onion in stock until soft.",
            "Blend until smooth and finish with olive oil."
        ),
        r(
            "light-cauliflower-mash", "Creamy Cauliflower Mash",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SIDE_DISH,
            listOf(i(859, 280), i(1319, 45), i(2361, 2), i(2550, 5)),
            "Steam cauliflower and garlic until very soft.",
            "Blend with milk and a small amount of light butter."
        ),
        r(
            "light-yogurt-slaw", "Cabbage Cucumber Yogurt Slaw",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SIDE_DISH,
            listOf(i(2386, 140), i(2407, 100), i(2424, 50), i(2530, 70), i(411, 10)),
            "Shred the cabbage and carrot and slice the cucumber.",
            "Toss with yogurt and lemon and chill before serving."
        ),
        r(
            "light-broccoli-mushrooms", "Roasted Broccoli and Mushrooms",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SIDE_DISH,
            listOf(i(1177, 180), i(2384, 140), i(2361, 2), i(2543, 5)),
            "Toss broccoli and mushrooms with garlic and oil.",
            "Roast at high heat until browned at the edges."
        ),
        r(
            "light-cottage-strawberry", "Cottage Cheese and Strawberries",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SNACK,
            listOf(i(2517, 150), i(2225, 130), i(2477, 1)),
            "Spoon cottage cheese into a bowl.",
            "Top with sliced strawberries and cinnamon."
        ),
        r(
            "light-hummus-sticks", "Hummus Vegetable Snack Box",
            RecipeBooks.LOW_CALORIE, RecipeCategories.SNACK,
            listOf(i(2191, 80), i(2567, 8), i(411, 10), i(2424, 90), i(2407, 100), i(2409, 70)),
            "Blend the chickpeas with tahini, lemon and a splash of water.",
            "Pack with carrot, cucumber and pepper sticks."
        ),
        r(
            "light-banana-yogurt", "Frozen Banana Yogurt",
            RecipeBooks.LOW_CALORIE, RecipeCategories.DESSERT,
            listOf(i(2245, 120), i(2530, 160), i(2477, 1)),
            "Blend banana and yogurt until smooth.",
            "Freeze until scoopable and dust with cinnamon."
        ),
        r(
            "light-raspberry-yogurt", "Raspberry Yogurt Fool",
            RecipeBooks.LOW_CALORIE, RecipeCategories.DESSERT,
            listOf(i(914, 130), i(2530, 170), i(2643, 6)),
            "Crush half the raspberries with the honey.",
            "Swirl through yogurt and top with the remaining berries."
        ),
        r(
            "light-cocoa-oat-bites", "Cocoa Oat Energy Bites",
            RecipeBooks.LOW_CALORIE, RecipeCategories.DESSERT,
            listOf(i(1010, 30), i(2245, 55), i(2648, 8), i(979, 8)),
            "Mash everything into a thick mixture.",
            "Shape into small bites and chill until firm."
        ),
        r(
            "light-mango-yogurt", "Fresh Mango Yogurt Cup",
            RecipeBooks.LOW_CALORIE, RecipeCategories.DESSERT,
            listOf(i(1186, 140), i(2530, 170), i(1010, 12)),
            "Dice the mango and layer it with yogurt.",
            "Scatter oats over the top just before serving."
        ),

        // World Cuisine · breakfasts
        r(
            "world-huevos-rancheros", "Mexican Huevos Rancheros",
            RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(i(1173, 90), i(2125, 35), i(481, 140), i(2199, 90), i(1071, 60), i(794, 8)),
            "Warm the beans and tomato with green chilli.",
            "Cook the egg, then serve everything over warmed corn bread."
        ),
        r(
            "world-masala-oats", "Indian Masala Oats", RecipeBooks.WORLD,
            RecipeCategories.BREAKFAST,
            listOf(i(1010, 45), i(2421, 100), i(1175, 50), i(2382, 40), i(2621, 4)),
            "Soften onion and tomato with curry spice.",
            "Add oats, peas and water and simmer to a savoury porridge."
        ),
        r(
            "world-japanese-breakfast", "Japanese-Style Salmon Rice Bowl",
            RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(i(2660, 60), i(2626, 90), i(2407, 70), i(2575, 10), i(1127, 4)),
            "Cook the rice and salmon.",
            "Serve with cucumber, soy sauce and sesame seeds."
        ),
        r(
            "world-french-toast", "French Toast with Strawberries",
            RecipeBooks.WORLD, RecipeCategories.BREAKFAST,
            listOf(i(2160, 80), i(1173, 70), i(2125, 20), i(1319, 60), i(2225, 120), i(2477, 1)),
            "Dip the bread in beaten egg, milk and cinnamon.",
            "Cook in a non-stick pan and serve with strawberries."
        ),

        // World Cuisine · burgers, pizzas and international mains
        r(
            "world-classic-burger", "Classic Beef Cheeseburger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2174, 75), i(2680, 150), i(2399, 30), i(2421, 60), i(2382, 30), i(1005, 25), i(2600, 8)),
            "Shape and cook the beef patty until safely done.",
            "Build the burger with cheese, lettuce, tomato, onion and mustard."
        ),
        r(
            "world-turkey-avocado-burger", "Turkey Avocado Burger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2174, 75), i(2285, 150), i(2216, 60), i(2399, 30), i(2421, 60), i(2382, 25)),
            "Shape the turkey into a patty and cook it through.",
            "Serve in the bun with avocado, lettuce, tomato and onion."
        ),
        r(
            "world-chickpea-burger", "Spiced Chickpea Veggie Burger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2174, 75), i(2191, 150), i(2424, 45), i(2382, 35), i(2171, 15), i(2571, 2), i(2399, 30)),
            "Mash chickpeas with carrot, onion, breadcrumbs and cumin; shape a patty.",
            "Cook until crisp and serve in the bun with lettuce."
        ),
        r(
            "world-margherita-pizza", "Whole-Wheat Margherita Pizza",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1113, 100), i(481, 130), i(2510, 70), i(2473, 8), i(2543, 5)),
            "Make a simple dough with the flour and water and stretch it thin.",
            "Top with tomato and mozzarella, bake hot and finish with basil."
        ),
        r(
            "world-vegetable-pizza", "Roasted Vegetable Pizza",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1113, 95), i(481, 120), i(2510, 55), i(2384, 60), i(2409, 50), i(2376, 60), i(2543, 5)),
            "Stretch the dough thin and spread over the tomato.",
            "Add vegetables and mozzarella and bake until crisp and browned."
        ),
        r(
            "world-chicken-curry", "Indian Chicken Curry with Rice",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2660, 65), i(481, 140), i(2382, 50), i(2530, 60), i(2621, 5)),
            "Cook onion, tomato and curry spice into a thick sauce.",
            "Simmer the chicken until done, stir in yogurt and serve with rice."
        ),
        r(
            "world-red-lentil-dal", "Indian Lentil Dal and Rice",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1065, 70), i(2660, 55), i(481, 130), i(2382, 45), i(2361, 3), i(2621, 5)),
            "Simmer lentils with tomato, onion, garlic and curry until creamy.",
            "Serve with freshly cooked rice."
        ),
        r(
            "world-thai-tofu-curry", "Thai-Style Coconut Tofu Curry",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 150), i(911, 100), i(2660, 60), i(2376, 90), i(2409, 60), i(2606, 4), i(2575, 8)),
            "Simmer coconut milk with ginger and soy sauce.",
            "Add tofu and vegetables, cook until tender and serve with rice."
        ),
        r(
            "world-chinese-stir-fry", "Chinese-Style Chicken Stir-Fry",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2660, 60), i(1177, 100), i(2409, 60), i(2384, 80), i(2575, 12), i(752, 3)),
            "Stir-fry the chicken until nearly cooked, then add the vegetables.",
            "Finish with soy sauce and sesame oil and serve over rice."
        ),
        r(
            "world-mexican-chili", "Mexican Beef and Bean Chilli",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2680, 130), i(2199, 130), i(481, 160), i(1330, 60), i(2382, 45), i(793, 8), i(2571, 2)),
            "Brown the beef with onion, chilli and cumin.",
            "Add beans, corn and tomato and simmer until thick."
        ),

        // World Cuisine · sides, snacks and desserts
        r(
            "world-guacamole", "Mexican Guacamole with Corn Bread",
            RecipeBooks.WORLD, RecipeCategories.SIDE_DISH,
            listOf(i(2216, 110), i(2421, 70), i(2382, 20), i(411, 12), i(1836, 5), i(1071, 55)),
            "Mash avocado with tomato, onion, lemon and coriander.",
            "Serve immediately with pieces of corn bread."
        ),
        r(
            "world-miso-soup", "Japanese Miso Tofu Soup",
            RecipeBooks.WORLD, RecipeCategories.SIDE_DISH,
            listOf(i(2157, 22), i(1121, 90), i(849, 60), i(850, 12)),
            "Dissolve miso into hot water without boiling it hard.",
            "Add tofu and soybean sprouts, warm through and finish with chives."
        ),
        r(
            "world-tabbouleh", "Middle Eastern Tabbouleh",
            RecipeBooks.WORLD, RecipeCategories.SIDE_DISH,
            listOf(i(1084, 55), i(2421, 110), i(2407, 90), i(2593, 20), i(411, 18), i(2543, 6)),
            "Soak or cook the cracked wheat and cool it.",
            "Toss with tomato, cucumber, parsley, lemon and olive oil."
        ),
        r(
            "world-spiced-cauliflower", "Indian Spiced Cauliflower",
            RecipeBooks.WORLD, RecipeCategories.SIDE_DISH,
            listOf(i(859, 250), i(2421, 100), i(2382, 40), i(2571, 3), i(2621, 3), i(2543, 5)),
            "Coat cauliflower and onion with cumin, curry and oil.",
            "Roast until browned, then fold through the chopped tomato."
        ),
        r(
            "world-chili-popcorn", "Chilli Lime Popcorn", RecipeBooks.WORLD,
            RecipeCategories.SNACK,
            listOf(i(871, 25), i(2611, 1), i(1001, 15)),
            "Warm the popcorn in a dry pan if needed.",
            "Toss with chilli powder and fresh lime."
        ),
        r(
            "world-peanut-banana-toast", "Peanut Banana Toast",
            RecipeBooks.WORLD, RecipeCategories.SNACK,
            listOf(i(2163, 55), i(979, 18), i(2245, 90), i(2477, 1)),
            "Toast the bread and spread with peanut butter.",
            "Top with banana slices and cinnamon."
        ),
        r(
            "world-mango-coconut-rice", "Mango Coconut Rice Pudding",
            RecipeBooks.WORLD, RecipeCategories.DESSERT,
            listOf(i(2660, 45), i(911, 120), i(1186, 120), i(2642, 8)),
            "Simmer rice in coconut milk and water until creamy.",
            "Sweeten lightly and serve with fresh mango."
        ),
        r(
            "world-berry-parfait", "French-Style Berry Yogurt Parfait",
            RecipeBooks.WORLD, RecipeCategories.DESSERT,
            listOf(i(2530, 170), i(914, 80), i(2368, 70), i(2155, 20)),
            "Layer yogurt with raspberries and bilberries.",
            "Top with a small spoonful of granola."
        ),
        r(
            "world-mexican-cocoa-oats", "Mexican Cinnamon Cocoa Oats",
            RecipeBooks.WORLD, RecipeCategories.DESSERT,
            listOf(i(1010, 40), i(1319, 180), i(2648, 10), i(2477, 2), i(793, 1)),
            "Simmer oats and milk until creamy.",
            "Stir in cocoa, cinnamon and the smallest pinch of red chilli."
        ),
        r(
            "world-coconut-banana-bites", "Coconut Banana Bites",
            RecipeBooks.WORLD, RecipeCategories.DESSERT,
            listOf(i(2245, 110), i(908, 18), i(788, 12)),
            "Slice the banana and dip one side in melted dark chocolate.",
            "Coat with dried coconut and chill until set."
        ),

        // Mediterranean Cuisine · additional main dishes
        r(
            "med-chicken-olive-stew", "Mediterranean Chicken and Olive Stew",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(994, 160), i(481, 180), i(2214, 35), i(2424, 70), i(2382, 50), i(2543, 7)),
            "Cut the chicken and vegetables into even pieces and warm the olive oil in a deep pan.",
            "Brown the chicken, then soften the onion and carrot before adding tomato and olives.",
            "Cover and simmer gently until the chicken is cooked through and the sauce has thickened."
        ),
        r(
            "med-turkey-spinach-meatballs", "Turkey Spinach Meatballs in Tomato Sauce",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 150), i(1282, 80), i(2171, 18), i(1173, 35), i(481, 180), i(2382, 40)),
            "Chop the spinach finely and combine it with turkey, breadcrumbs and egg white.",
            "Shape small meatballs and brown them in a non-stick pan while the onion softens.",
            "Add tomato, cover and simmer until the meatballs are cooked to the centre."
        ),
        r(
            "med-pork-mushroom-skillet", "Pork Tenderloin with Mushrooms",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1021, 160), i(2384, 140), i(2382, 50), i(2421, 100), i(2361, 3), i(2543, 7)),
            "Slice the pork across the grain and season it lightly.",
            "Sear the pork, remove it briefly, and cook the mushrooms, onion and garlic in the same pan.",
            "Return the pork with the tomato and cook gently until just done and juicy."
        ),
        r(
            "med-rabbit-rosemary-stew", "Spanish Rabbit and Rosemary Stew",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1162, 170), i(2403, 160), i(481, 150), i(2424, 60), i(2382, 45), i(2586, 2), i(2543, 7)),
            "Cut the rabbit and vegetables into serving pieces and heat the oil in a casserole.",
            "Brown the rabbit, then add onion, carrot, tomato and rosemary and stir well.",
            "Add the potatoes and water, cover and simmer until the rabbit and potatoes are tender."
        ),
        r(
            "med-lamb-eggplant-bake", "Greek Lamb and Eggplant Bake",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2693, 150), i(2370, 220), i(481, 160), i(2382, 45), i(2520, 60), i(2543, 6)),
            "Roast the eggplant slices until flexible and lightly coloured.",
            "Brown the lamb with onion, add tomato and simmer until the mixture is no longer watery.",
            "Layer the eggplant and lamb, spread yogurt over the top and bake until browned."
        ),
        r(
            "med-hake-garlic-parsley", "Hake with Garlic, Parsley and Potatoes",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(825, 180), i(2403, 190), i(2361, 3), i(2593, 10), i(411, 18), i(2543, 7)),
            "Cut the potatoes into thin slices and start roasting them with half the oil.",
            "Place the hake over the nearly tender potatoes and add garlic, parsley and lemon.",
            "Bake until the fish is opaque and flakes easily, then spoon over the remaining oil."
        ),
        r(
            "med-salmon-spinach-pasta", "Salmon and Spinach Whole-Wheat Pasta",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2626, 130), i(1074, 75), i(1282, 100), i(2421, 100), i(2361, 2), i(2543, 5)),
            "Cook the pasta until al dente and reserve a little cooking water.",
            "Cook the salmon separately, then wilt the spinach, tomato and garlic in the pan.",
            "Flake in the salmon and toss with pasta and enough reserved water to coat."
        ),
        r(
            "med-tuna-tomato-rice", "Tuna Tomato Rice Skillet",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2134, 150), i(2660, 70), i(481, 160), i(2409, 60), i(2382, 40), i(2543, 6)),
            "Cut the tuna into large cubes and briefly sear it in the oil; set it aside.",
            "Soften the onion and pepper, add tomato and rice, then pour in measured water.",
            "Simmer until the rice is almost tender, return the tuna and finish without overcooking it."
        ),
        r(
            "med-prawn-courgette-pasta", "Prawn and Courgette Pasta",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(581, 130), i(1117, 75), i(2376, 130), i(2421, 100), i(2361, 3), i(2543, 6)),
            "Cook the pasta until al dente and slice the courgette into thin half-moons.",
            "Sauté courgette and garlic, then add the prawns and cook until they turn opaque.",
            "Add tomato and pasta, toss thoroughly and loosen with a little pasta water."
        ),
        r(
            "med-white-bean-stew", "Tuscan-Style White Bean and Chard Stew",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(899, 65), i(2212, 120), i(481, 160), i(2424, 70), i(2382, 45), i(2543, 6)),
            "Soak the dried beans as needed, drain them and start cooking them in fresh water.",
            "Add onion, carrot and tomato and simmer until the beans are nearly tender.",
            "Stir in the chopped chard and olive oil and cook until the greens are soft."
        ),
        r(
            "med-lentil-moussaka", "Vegetarian Lentil Moussaka",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1065, 65), i(2370, 220), i(481, 170), i(2382, 45), i(2520, 65), i(2543, 6)),
            "Cook the lentils until tender and roast the eggplant in even slices.",
            "Simmer onion, tomato and lentils until the filling is thick enough to layer.",
            "Layer with eggplant, spread yogurt over the surface and bake until golden."
        ),
        r(
            "med-chickpea-semolina-bowl", "Mediterranean Chickpea Semolina Bowl",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1020, 60), i(1084, 55), i(2376, 90), i(2409, 60), i(2421, 100), i(2543, 6)),
            "Soak and cook the chickpeas until tender and prepare the semolina with hot water.",
            "Roast or sauté the courgette, pepper and tomato until soft at the edges.",
            "Fluff the semolina and combine it with chickpeas, vegetables and olive oil."
        ),
        r(
            "med-mushroom-risotto", "Italian Mushroom Risotto",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2660, 75), i(2384, 150), i(2382, 45), i(792, 280), i(1011, 18), i(2543, 6)),
            "Warm the stock and sauté the mushrooms until browned; reserve half for serving.",
            "Soften the onion, add rice and incorporate the hot stock gradually while stirring.",
            "When the rice is creamy and tender, fold in cheese and the reserved mushrooms."
        ),
        r(
            "med-spinach-cottage-pasta", "Spinach and Cottage Cheese Pasta",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1074, 75), i(1282, 120), i(2517, 80), i(2421, 100), i(2361, 2), i(2543, 5)),
            "Cook the pasta until al dente and keep a small cup of cooking water.",
            "Wilt spinach with tomato and garlic, then lower the heat and add cottage cheese.",
            "Toss in the pasta and add reserved water until the sauce lightly coats it."
        ),
        r(
            "med-chickpea-patties", "Greek Chickpea Patties with Yogurt Salad",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2191, 160), i(2171, 20), i(2424, 45), i(2382, 35), i(2571, 2), i(2520, 60), i(2407, 70)),
            "Mash the chickpeas and mix with breadcrumbs, grated carrot, onion and cumin.",
            "Shape compact patties and cook them in a non-stick pan until browned on both sides.",
            "Mix yogurt with diced cucumber and serve it alongside the hot patties."
        ),
        r(
            "med-turkey-meatball-pasta", "Italian Turkey Meatball Pasta",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 145), i(1117, 70), i(481, 180), i(2382, 40), i(2171, 15), i(2473, 6)),
            "Mix turkey with breadcrumbs, shape small meatballs and brown them evenly.",
            "Add onion and tomato to the pan and simmer the meatballs until cooked through.",
            "Cook the pasta, combine it with the sauce and finish with fresh basil."
        ),
        r(
            "med-chicken-spinach-rice", "Spanish Chicken and Spinach Rice",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2660, 70), i(1282, 100), i(481, 140), i(2361, 3), i(2543, 6)),
            "Cut and brown the chicken in a wide pan, then briefly cook the garlic.",
            "Add tomato, rice and measured water and simmer until the rice is nearly tender.",
            "Fold in the spinach and finish cooking until the chicken is done and the liquid absorbed."
        ),
        r(
            "med-cod-chickpea-stew", "Cod and Chickpea Stew",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(795, 160), i(2191, 130), i(481, 170), i(1282, 90), i(2382, 40), i(2543, 5)),
            "Soften the onion in oil and simmer it with tomato until the sauce concentrates.",
            "Add chickpeas and spinach with a small splash of water and cook for several minutes.",
            "Nestle in the cod and heat gently until the fish flakes without breaking it apart."
        ),
        r(
            "med-stuffed-eggplant-beef", "Beef-Stuffed Mediterranean Eggplant",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(2370, 260), i(2680, 130), i(481, 150), i(2382, 40), i(2510, 30), i(2543, 5)),
            "Halve the eggplant, score the flesh and roast until it can be scooped out.",
            "Brown beef and onion, add chopped eggplant flesh and tomato, and reduce the filling.",
            "Fill the shells, top with mozzarella and bake until thoroughly hot and golden."
        ),
        r(
            "med-tofu-tomato-bake", "Mediterranean Tofu and Tomato Bake",
            RecipeBooks.MEDITERRANEAN, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 170), i(481, 180), i(2370, 110), i(2376, 100), i(2510, 30), i(2543, 5)),
            "Press and cube the tofu, then cut the eggplant and courgette into even pieces.",
            "Roast the vegetables and tofu until lightly coloured, then stir through the tomato.",
            "Top with mozzarella and bake again until the sauce bubbles and the cheese melts."
        ),

        // Low-Calorie Recipes · additional main dishes
        r(
            "light-lemon-turkey-quinoa", "Lemon Turkey Quinoa Plate",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 150), i(1083, 50), i(2376, 100), i(2409, 60), i(411, 18), i(2543, 4)),
            "Cook the quinoa in measured water and cut the turkey and vegetables evenly.",
            "Cook the turkey in a non-stick pan, then add courgette and pepper until just tender.",
            "Fluff the quinoa, add lemon and olive oil and serve it with the turkey mixture."
        ),
        r(
            "light-chicken-broccoli-rice", "Chicken Broccoli Brown Rice Bowl",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(994, 145), i(904, 50), i(1177, 160), i(2424, 60), i(2575, 10), i(752, 2)),
            "Cook the brown rice until tender and cut the chicken into bite-sized pieces.",
            "Stir-fry the chicken until almost done, then add broccoli and carrot with a splash of water.",
            "Finish with soy sauce and sesame oil and spoon the mixture over the rice."
        ),
        r(
            "light-hake-tomato-courgette", "Hake with Tomato and Courgette",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(825, 190), i(2376, 180), i(481, 150), i(2382, 35), i(411, 15), i(2543, 4)),
            "Slice the courgette and onion and spread them in a shallow baking dish.",
            "Add tomato and bake until the vegetables begin to soften, then place the hake on top.",
            "Add lemon and bake again until the fish is opaque and flakes easily."
        ),
        r(
            "light-prawn-cauliflower-rice", "Prawn Cauliflower Rice Skillet",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(581, 150), i(859, 230), i(1175, 50), i(2424, 55), i(2575, 10), i(752, 2)),
            "Pulse or grate the cauliflower into rice-sized pieces and prepare the remaining vegetables.",
            "Cook the prawns until just opaque and set them aside; stir-fry carrot, peas and cauliflower.",
            "Return the prawns, add soy sauce and sesame oil and heat everything through."
        ),
        r(
            "light-tuna-courgette-boats", "Tuna-Stuffed Courgette Boats",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2133, 120), i(2376, 280), i(481, 120), i(2382, 35), i(2510, 25)),
            "Halve the courgette, scoop out the centre and bake the shells until partly tender.",
            "Cook the chopped courgette centre with onion and tomato, then fold in drained tuna.",
            "Fill the shells, sprinkle with mozzarella and bake until hot and lightly browned."
        ),
        r(
            "light-turkey-meatballs", "Lean Turkey Meatballs with Tomato",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 160), i(2171, 15), i(1173, 30), i(481, 180), i(2382, 40), i(2376, 120)),
            "Mix turkey with breadcrumbs and egg white and shape evenly sized meatballs.",
            "Brown the meatballs in a non-stick pan, remove them and soften the onion and courgette.",
            "Add tomato and return the meatballs; cover and simmer until cooked through."
        ),
        r(
            "light-tofu-broccoli-curry", "Light Tofu Broccoli Curry",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 160), i(1177, 170), i(481, 150), i(2382, 40), i(2621, 5), i(2530, 50)),
            "Cube the tofu and cut the broccoli into small florets so they cook evenly.",
            "Soften the onion with curry spice, add tomato and broccoli and simmer until nearly tender.",
            "Add tofu, warm through and stir in yogurt away from the heat."
        ),
        r(
            "light-white-bean-chard-soup", "White Bean and Chard Soup",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2199, 110), i(2212, 140), i(481, 140), i(2424, 65), i(2366, 45), i(792, 100)),
            "Chop the chard, carrot and celery and bring the vegetable stock to a simmer.",
            "Add carrot, celery, tomato and beans and cook until the vegetables are tender.",
            "Stir in the chard for the final minutes and simmer until soft but still bright."
        ),
        r(
            "light-lentil-stuffed-pepper", "Lentil-Stuffed Pepper",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1065, 55), i(2409, 190), i(481, 130), i(2382, 35), i(2424, 45), i(2510, 20)),
            "Cook the lentils until tender and halve and seed the pepper.",
            "Cook onion, carrot and tomato, then mix in the drained lentils to make the filling.",
            "Fill the pepper, add mozzarella and bake until the pepper is tender."
        ),
        r(
            "light-egg-white-frittata", "Egg-White Vegetable Frittata",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1173, 200), i(1282, 100), i(2384, 100), i(2409, 60), i(2421, 100), i(2543, 3)),
            "Slice the mushrooms and pepper and chop the spinach and tomato.",
            "Cook the vegetables in an oven-safe non-stick pan until their excess moisture evaporates.",
            "Pour in the egg whites and cook gently, then finish under the grill until set."
        ),
        r(
            "light-pork-cabbage", "Lean Pork with Savoy Cabbage",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1021, 155), i(852, 200), i(2424, 60), i(2382, 40), i(2575, 10), i(752, 2)),
            "Slice the pork thinly and shred the cabbage, carrot and onion.",
            "Sear the pork in a hot non-stick pan and remove it as soon as it colours.",
            "Stir-fry the vegetables, return the pork and finish with soy sauce and sesame oil."
        ),
        r(
            "light-chicken-mushroom-wraps", "Chicken Mushroom Lettuce Wraps",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2384, 140), i(2424, 55), i(2382, 35), i(2399, 100), i(2575, 10)),
            "Dice the chicken and mushrooms finely and separate sturdy lettuce leaves.",
            "Cook the chicken until nearly done, then add mushrooms, carrot and onion.",
            "Season with soy sauce and spoon the hot filling into the lettuce leaves."
        ),
        r(
            "light-salmon-cucumber-quinoa", "Salmon Cucumber Quinoa Salad",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2626, 120), i(1083, 45), i(2407, 120), i(2421, 100), i(411, 18), i(2530, 45)),
            "Cook and cool the quinoa, then cook the salmon until it flakes.",
            "Dice the cucumber and tomato and whisk the lemon into the yogurt.",
            "Flake the salmon over the quinoa and vegetables and spoon over the dressing."
        ),
        r(
            "light-chickpea-spinach-salad", "Warm Chickpea Spinach Salad",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2191, 145), i(1282, 130), i(2421, 120), i(2407, 80), i(411, 16), i(2543, 4)),
            "Drain the chickpeas and chop the tomato and cucumber into even pieces.",
            "Warm the chickpeas in a pan and wilt the spinach only until tender.",
            "Combine with the raw vegetables and dress with lemon and olive oil."
        ),
        r(
            "light-hake-cabbage-parcel", "Hake and Cabbage Parcels",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(825, 190), i(2386, 180), i(2424, 60), i(2411, 45), i(411, 18), i(2543, 3)),
            "Soften large cabbage leaves briefly and shred the carrot and leek.",
            "Place hake and vegetables inside the leaves, add lemon and fold into secure parcels.",
            "Bake or steam the parcels until the hake is fully opaque and flakes easily."
        ),
        r(
            "light-turkey-pumpkin-stew", "Turkey Pumpkin Stew",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2285, 150), i(2378, 220), i(481, 140), i(2424, 60), i(2382, 40), i(792, 180)),
            "Cut the turkey and pumpkin into similar bite-sized pieces.",
            "Brown the turkey in a non-stick casserole and soften the onion and carrot.",
            "Add pumpkin, tomato and stock and simmer until the turkey is done and pumpkin tender."
        ),
        r(
            "light-tofu-eggplant-bowl", "Tofu Eggplant Tomato Bowl",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 160), i(2370, 200), i(481, 160), i(2376, 100), i(2575, 10), i(2543, 3)),
            "Press and cube the tofu and cut the eggplant and courgette evenly.",
            "Roast the vegetables until tender and brown the tofu in a non-stick pan.",
            "Warm the tomato with soy sauce and combine it with the vegetables and tofu."
        ),
        r(
            "light-chicken-cauliflower-curry", "Chicken Cauliflower Curry",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(994, 145), i(859, 200), i(481, 150), i(2382, 40), i(2621, 5), i(2530, 50)),
            "Cut the chicken and cauliflower into small, even pieces.",
            "Soften onion with curry spice, add tomato and chicken and simmer until almost done.",
            "Add cauliflower, cook until tender and stir in yogurt after removing the pan from heat."
        ),
        r(
            "light-tuna-broccoli-pasta", "Tuna Broccoli Whole-Wheat Pasta",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(2133, 110), i(1074, 55), i(1177, 160), i(2421, 100), i(2361, 2), i(2543, 3)),
            "Cook the pasta and add the broccoli for the final minutes so both finish together.",
            "Warm tomato and garlic in a wide pan and fold in the drained tuna.",
            "Drain the pasta and broccoli, toss with the sauce and finish with olive oil."
        ),
        r(
            "light-beef-pepper-bowl", "Lean Beef and Pepper Bowl",
            RecipeBooks.LOW_CALORIE, RecipeCategories.MAIN_DISH,
            listOf(i(981, 145), i(2409, 120), i(2384, 100), i(2382, 40), i(2399, 80), i(2575, 10)),
            "Slice the beef thinly across the grain and cut the pepper, mushrooms and onion evenly.",
            "Sear the beef quickly in a very hot non-stick pan and set it aside.",
            "Cook the vegetables, return the beef with soy sauce and serve over shredded lettuce."
        ),

        // World Cuisine · additional main dishes
        r(
            "world-chicken-taco-bowl", "Mexican Chicken Taco Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2660, 65), i(2191, 80), i(2421, 100), i(2409, 60), i(2571, 3)),
            "Cook the rice and cut the chicken, tomato and pepper into bite-sized pieces.",
            "Brown the chicken with cumin, add the pepper and cook until the chicken is done.",
            "Arrange rice, chickpeas, chicken and tomato in a bowl and serve while warm."
        ),
        r(
            "world-fish-taco-bowl", "Baja-Style Fish Taco Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(825, 180), i(2660, 60), i(2386, 100), i(2421, 90), i(1001, 25), i(2530, 45)),
            "Cook the rice, shred the cabbage and dice the tomato while the rice rests.",
            "Season and cook the hake in a non-stick pan until opaque, then break it into large flakes.",
            "Layer the rice, cabbage, tomato and fish and finish with lime yogurt."
        ),
        r(
            "world-turkey-quesadilla", "Turkey and Pepper Quesadilla",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1113, 70), i(2285, 140), i(2409, 80), i(2382, 35), i(1005, 35), i(2421, 80)),
            "Mix the flour with enough water to make a soft dough, rest it and roll two thin tortillas.",
            "Cook the turkey, onion and pepper thoroughly and place the filling and cheese between the tortillas.",
            "Toast the quesadilla on both sides until crisp, cut into wedges and serve with tomato."
        ),
        r(
            "world-chicken-tikka", "Indian Chicken Tikka Rice Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 155), i(2660, 65), i(2530, 70), i(481, 140), i(2621, 5), i(2606, 3)),
            "Coat the chicken with half the yogurt, curry and ginger and leave it to marinate briefly.",
            "Cook the rice and sear the chicken until browned and fully cooked, then simmer the tomato.",
            "Stir the remaining yogurt into the sauce away from the heat and serve with chicken and rice."
        ),
        r(
            "world-palak-tofu", "Indian Spinach Tofu with Rice",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 170), i(1282, 180), i(2660, 60), i(2382, 40), i(2361, 3), i(2621, 4)),
            "Cook the rice, press and cube the tofu and wash the spinach thoroughly.",
            "Brown the tofu, then soften the onion and garlic with curry before wilting the spinach.",
            "Blend or finely chop the spinach sauce, return the tofu and serve with rice."
        ),
        r(
            "world-teriyaki-salmon", "Japanese-Style Teriyaki Salmon",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2626, 150), i(904, 60), i(1177, 140), i(2575, 14), i(2606, 3), i(752, 2)),
            "Cook the brown rice and steam the broccoli until tender but still bright.",
            "Mix soy sauce and ginger, brush it over the salmon and cook the fish until it flakes.",
            "Reduce the remaining sauce briefly and spoon it over the salmon, rice and broccoli."
        ),
        r(
            "world-tuna-sushi-bowl", "Japanese Tuna Sushi Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2134, 140), i(2660, 65), i(2407, 100), i(2424, 60), i(2575, 12), i(752, 2)),
            "Cook the rice until tender and cool it slightly while cutting the vegetables evenly.",
            "Sear the tuna on every side while keeping the centre at the preferred doneness, then slice it.",
            "Arrange tuna, cucumber and carrot over the rice and season with soy and sesame oil."
        ),
        r(
            "world-korean-beef-bowl", "Korean-Style Beef Rice Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(981, 145), i(2660, 65), i(1177, 120), i(2575, 13), i(2606, 3), i(793, 2)),
            "Cook the rice and slice the beef thinly across the grain for quick, even cooking.",
            "Sear the beef with ginger and chilli, then add soy sauce and let it coat the meat.",
            "Steam the broccoli and serve it beside the beef over the hot rice."
        ),
        r(
            "world-thai-prawn-noodles", "Thai-Style Prawn Noodles",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(581, 150), i(1074, 70), i(2409, 70), i(2424, 55), i(2575, 12), i(1001, 20)),
            "Cook the noodles until just tender, drain them and prepare the pepper and carrot.",
            "Stir-fry the prawns until almost opaque, add the vegetables and keep them slightly crisp.",
            "Toss in the noodles with soy sauce and lime and heat everything through."
        ),
        r(
            "world-tofu-satay-rice", "Indonesian-Style Tofu Satay Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1121, 170), i(2660, 60), i(1177, 120), i(979, 18), i(2575, 10), i(1001, 18)),
            "Cook the rice, press and cube the tofu and cut the broccoli into small florets.",
            "Brown the tofu and steam the broccoli while whisking peanut butter, soy and lime with warm water.",
            "Coat the tofu lightly with the satay sauce and serve it with the broccoli and rice."
        ),
        r(
            "world-moroccan-chicken", "Moroccan-Spiced Chicken with Chickpeas",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2191, 110), i(481, 160), i(2424, 65), i(2571, 3), i(2592, 3)),
            "Cut the chicken and carrot evenly and drain and rinse the chickpeas.",
            "Brown the chicken, add cumin and paprika, then simmer it with tomato and carrot.",
            "Fold in the chickpeas and cook until hot and the sauce has thickened."
        ),
        r(
            "world-lebanese-kofta", "Lebanese-Style Beef Kofta Plate",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2680, 150), i(1084, 55), i(2407, 100), i(2421, 100), i(2520, 55), i(2571, 3)),
            "Mix the beef with cumin, shape it into small kofta and prepare the semolina with hot water.",
            "Cook the kofta on every side until browned and safely cooked through.",
            "Serve with semolina, chopped cucumber and tomato and a spoonful of yogurt."
        ),
        r(
            "world-turkish-eggplant", "Turkish-Style Stuffed Eggplant",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2370, 260), i(2285, 130), i(481, 150), i(2382, 40), i(2361, 3), i(2543, 5)),
            "Halve and score the eggplant, brush it with oil and roast until the centre softens.",
            "Cook the turkey with onion and garlic, add tomato and reduce to a thick filling.",
            "Spoon the filling into the eggplant halves and bake again until piping hot."
        ),
        r(
            "world-jerk-chicken", "Caribbean Jerk-Style Chicken Rice",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 155), i(2660, 65), i(2409, 80), i(2421, 90), i(793, 2), i(2592, 3)),
            "Cook the rice and season the chicken with paprika and a controlled amount of chilli.",
            "Brown and cook the chicken through, then sauté the pepper until tender at the edges.",
            "Serve the sliced chicken and pepper over rice with fresh chopped tomato."
        ),
        r(
            "world-brazilian-beef-beans", "Brazilian-Style Beef and Bean Bowl",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(981, 140), i(2199, 120), i(2660, 60), i(2409, 65), i(2382, 40), i(481, 120)),
            "Cook the rice, slice the beef and prepare the pepper and onion.",
            "Sear the beef and set it aside, then soften the onion and pepper and add the tomato.",
            "Warm the beans in the sauce, return the beef and serve the mixture over rice."
        ),
        r(
            "world-bbq-chicken-burger", "Barbecue Chicken Burger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2174, 75), i(481, 80), i(1005, 25), i(2399, 35), i(2382, 30)),
            "Flatten the chicken to an even thickness and cook the onion and tomato into a thick relish.",
            "Cook the chicken completely, add the cheese for the final minute and lightly toast the bun.",
            "Build the burger with lettuce, chicken and tomato-onion relish and serve immediately."
        ),
        r(
            "world-mango-chicken-burger", "Mango Chicken Burger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(994, 150), i(2174, 75), i(1186, 80), i(2399, 40), i(2407, 45), i(1001, 18)),
            "Flatten the chicken evenly, slice the mango and cucumber and wash the lettuce.",
            "Cook the chicken through and toast the cut sides of the bun until lightly crisp.",
            "Fill the bun with lettuce, chicken, cucumber and mango and finish with lime."
        ),
        r(
            "world-mushroom-cheese-burger", "Mushroom Cheeseburger",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(2680, 145), i(2174, 75), i(2384, 110), i(1005, 25), i(2399, 35), i(2421, 70)),
            "Shape the beef into a patty and slice the mushrooms to an even thickness.",
            "Cook the mushrooms until browned, cook the patty through and melt the cheese on top.",
            "Toast the bun and assemble it with lettuce, tomato, the patty and mushrooms."
        ),
        r(
            "world-white-spinach-pizza", "White Spinach and Mushroom Pizza",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1113, 90), i(2510, 65), i(1282, 90), i(2384, 100), i(2517, 55), i(2543, 5)),
            "Mix the flour with water into a soft dough, knead briefly, rest it and roll it thin.",
            "Wilt the spinach, brown the mushrooms and spread cottage cheese over the dough.",
            "Add vegetables and mozzarella and bake at high heat until crisp and golden."
        ),
        r(
            "world-mexican-vegetable-pizza", "Mexican-Style Vegetable Pizza",
            RecipeBooks.WORLD, RecipeCategories.MAIN_DISH,
            listOf(i(1113, 90), i(481, 110), i(2510, 55), i(2409, 70), i(1175, 55), i(2191, 65)),
            "Make a soft flour-and-water dough, knead it, let it rest and roll it into a thin base.",
            "Spread tomato over the base and scatter on pepper, peas and drained chickpeas.",
            "Top with mozzarella and bake at high heat until the base is cooked and the cheese bubbles."
        )
    )

    private fun i(foodId: Long, grams: Number, unit: String = "GRAM") =
        BuiltInIngredient(foodId, grams.toDouble(), unit)

    private fun r(
        key: String,
        name: String,
        book: String,
        category: String,
        ingredients: List<BuiltInIngredient>,
        vararg steps: String
    ): BuiltInRecipe {
        val spanish = BuiltInRecipeTranslations.forKey(key)
        val englishCore = steps.toList()
        require(englishCore.size == spanish.coreSteps.size) {
            "$key has ${englishCore.size} English core steps and " +
                "${spanish.coreSteps.size} Spanish core steps"
        }
        return BuiltInRecipe(
            key = key,
            name = name,
            nameEs = spanish.name,
            book = book,
            category = category,
            ingredients = ingredients,
            steps = detailedEnglishSteps(englishCore),
            stepsEs = detailedSpanishSteps(spanish.coreSteps)
        )
    }

    private fun detailedEnglishSteps(core: List<String>): List<String> = buildList {
        add("Gather and measure every ingredient. Wash the fresh produce and prepare a clean work surface.")
        add(core.first())
        if (core.size == 2) {
            add("Prepare the remaining ingredients in even pieces and keep the heat controlled so everything cooks consistently.")
            add(core.last())
        } else {
            addAll(core.drop(1))
        }
        add("Check the texture and doneness, adjust the seasoning to taste, and serve at the suggested temperature.")
    }

    private fun detailedSpanishSteps(core: List<String>): List<String> = buildList {
        add("Reúne y mide todos los ingredientes. Lava los productos frescos y prepara una superficie de trabajo limpia.")
        add(core.first())
        if (core.size == 2) {
            add("Prepara el resto de los ingredientes en trozos uniformes y controla el fuego para que todo se cocine por igual.")
            add(core.last())
        } else {
            addAll(core.drop(1))
        }
        add("Comprueba la textura y el punto de cocción, ajusta el condimento al gusto y sirve a la temperatura indicada.")
    }
}
