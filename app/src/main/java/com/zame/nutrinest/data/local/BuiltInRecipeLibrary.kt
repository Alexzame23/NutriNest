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
    val book: String,
    val category: String,
    val ingredients: List<BuiltInIngredient>,
    val steps: List<String>
)

/**
 * The offline starter library: 24 recipes per book and 72 in total.
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
    ) = BuiltInRecipe(key, name, book, category, ingredients, steps.toList())
}
