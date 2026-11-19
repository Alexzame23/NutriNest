package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.domain.planner.PlannerFixtures.config
import com.zame.nutrinest.domain.planner.PlannerFixtures.menu
import com.zame.nutrinest.domain.planner.PlannerFixtures.plan
import com.zame.nutrinest.domain.planner.PlannerFixtures.recipePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MenuGeneratorTest {

    private val generator = MenuGenerator(Random(42))
    private val ana = plan(profileId = 1, dailyCalories = 2000)
    private val leo = plan(profileId = 2, dailyCalories = 3000)

    private fun generate(
        plans: List<DailyPlan> = listOf(ana, leo),
        config: GenerationConfig = config()
    ) = generator.generate(plans, recipePool(), config).menu()

    // ── Scope ───────────────────────────────────────────────────────────

    @Test
    fun `no profiles is a failure, not an empty week`() {
        val result = generator.generate(emptyList(), recipePool(), config())
        assertEquals(
            GenerationResult.Failure(FailureReason.NO_PROFILES),
            result
        )
    }

    @Test
    fun `a shared week is one dish per slot, eaten by everyone`() {
        val menu = generate(config = config { copy(scope = MenuScope.SHARED) })

        assertEquals("3 slots x 7 days", 21, menu.meals.size)
        assertTrue("nothing belongs to one person", menu.meals.all { it.profileId == null })
        assertTrue(
            "everyone has a portion of every dish",
            menu.meals.all { it.portions.keys == setOf(1L, 2L) }
        )
    }

    @Test
    fun `an individual week gives everyone their own dish`() {
        val menu = generate(config = config { copy(scope = MenuScope.INDIVIDUAL) })

        assertEquals("3 slots x 7 days x 2 people", 42, menu.meals.size)
        assertTrue("every row belongs to somebody", menu.meals.all { it.profileId != null })
        assertTrue(
            "and only ever feeds them",
            menu.meals.all { it.portions.keys == setOf(it.profileId) }
        )
    }

    @Test
    fun `a per-meal week is shared except where somebody eats apart`() {
        // Tuesday lunch: the child eats at school.
        val menu = generate(
            config = config {
                copy(
                    scope = MenuScope.PER_MEAL,
                    overrides = setOf(MealOverride(day = 1, slot = MealSlot.LUNCH, profileId = 2))
                )
            }
        )

        val tuesdayLunch = menu.meals.filter {
            it.dayOfWeek == 1 && it.slot == MealSlot.LUNCH
        }
        assertEquals("the slot splits in two", 2, tuesdayLunch.size)

        val childs = tuesdayLunch.single { it.profileId == 2L }
        val everyone = tuesdayLunch.single { it.profileId == null }
        assertNotEquals("and they are different dishes", childs.recipeId, everyone.recipeId)
        assertEquals("the rest of the house eats together", setOf(1L), everyone.portions.keys)

        val mondayLunch = menu.meals.filter { it.dayOfWeek == 0 && it.slot == MealSlot.LUNCH }
        assertEquals("every other lunch is still shared", 1, mondayLunch.size)
        assertEquals(setOf(1L, 2L), mondayLunch.single().portions.keys)
    }

    // ── Portions ────────────────────────────────────────────────────────

    @Test
    fun `each person is served their own share of a shared dish`() {
        val menu = generate(config = config { copy(scope = MenuScope.SHARED) })
        val lunch = menu.meals.first { it.slot == MealSlot.LUNCH }
        val recipeKcal = 500.0 // every fixture recipe

        // 40% of the slot: 800 kcal for Ana, 1200 for Leo.
        assertEquals(800.0 / recipeKcal, lunch.portions.getValue(1), 0.001)
        assertEquals(1200.0 / recipeKcal, lunch.portions.getValue(2), 0.001)
    }

    // ── Free slots ──────────────────────────────────────────────────────

    @Test
    fun `a free slot is a placeholder for that person only`() {
        val fasting = plan(profileId = 1, freeSlots = setOf(MealSlot.BREAKFAST))
        val menu = generate(plans = listOf(fasting, leo))

        val breakfasts = menu.meals.filter { it.dayOfWeek == 0 && it.slot == MealSlot.BREAKFAST }
        val free = breakfasts.single { it.isFree }
        assertEquals("it is hers", 1L, free.profileId)
        assertNull("with nothing to cook", free.recipeId)
        assertTrue("and nobody is served from it", free.portions.isEmpty())

        val shared = breakfasts.single { !it.isFree }
        assertEquals("the other person still eats", setOf(2L), shared.portions.keys)
    }

    @Test
    fun `freeing a slot leaves the other meals on their original budgets`() {
        // The example from the spec: 3000 kcal, 20/40/40, dinner free.
        val plan = plan(
            profileId = 1,
            dailyCalories = 3000,
            shares = mapOf(
                MealSlot.BREAKFAST to 0.2f, MealSlot.LUNCH to 0.4f, MealSlot.DINNER to 0.4f
            ),
            freeSlots = setOf(MealSlot.DINNER)
        )
        val menu = generator.generate(listOf(plan), recipePool(), config()).menu()
        val monday = menu.meals.filter { it.dayOfWeek == 0 }

        val breakfast = monday.single { it.slot == MealSlot.BREAKFAST }
        val lunch = monday.single { it.slot == MealSlot.LUNCH }
        assertEquals("600 kcal, not renormalised up", 600.0 / 500.0, breakfast.portions.getValue(1), 0.001)
        assertEquals("1200 kcal, not renormalised up", 1200.0 / 500.0, lunch.portions.getValue(1), 0.001)
        assertTrue("dinner is simply unplanned", monday.single { it.slot == MealSlot.DINNER }.isFree)
    }

    @Test
    fun `a free slot buys nothing`() {
        val fasting = plan(profileId = 1, freeSlots = setOf(MealSlot.BREAKFAST))
        val menu = generator.generate(listOf(fasting), recipePool(), config()).menu()

        val freeRecipeIds = menu.meals.filter { it.isFree }.mapNotNull { it.recipeId }
        assertTrue("free slots carry no recipe at all", freeRecipeIds.isEmpty())
        // The fixture ingredient is 100 kcal per 100 g, so grams == kcal: only
        // the tracked lunch (800) and dinner (600) reach the list, seven times.
        assertEquals(7 * (800.0 + 600.0), menu.shoppingTotals.values.sum(), 0.5)
    }

    @Test
    fun `random free meals free the whole household`() {
        val menu = generate(config = config { copy(cheatMeals = 3) })
        val free = menu.meals.filter { it.isFree }

        assertEquals(3, free.size)
        assertTrue("they are the household's", free.all { it.profileId == null })
        assertTrue("and flagged as the generator's own doing", free.all { it.isCheatMeal })
        assertTrue("with nothing planned", free.all { it.recipeId == null })
    }

    // ── Recipe books ───────────────────────────────────────────────────

    @Test
    fun `generation only uses recipes from selected books`() {
        val recipes = (1L..24L).map { id ->
            PlannerFixtures.recipe(id, folder = if (id <= 12) "Book A" else "Book B")
        }
        val menu = generator.generate(
            listOf(ana),
            recipes,
            config { copy(includedFolders = setOf("Book A")) }
        ).menu()

        assertTrue(menu.meals.mapNotNull { it.recipeId }.all { it <= 12L })
    }

    @Test
    fun `selecting no books is an explicit no matching recipes failure`() {
        val result = generator.generate(
            listOf(ana),
            recipePool(),
            config { copy(includedFolders = emptySet()) }
        )

        assertEquals(
            GenerationResult.Failure(FailureReason.NO_MATCHING_RECIPES),
            result
        )
    }

    // ── Courses ─────────────────────────────────────────────────────────

    @Test
    fun `a two-course lunch splits the slot budget across both dishes`() {
        val menu = generator.generate(
            listOf(ana),
            recipePool(),
            config {
                copy(
                    slotCourses = mapOf(
                        MealSlot.LUNCH to listOf(MealCourse.FIRST, MealCourse.MAIN)
                    )
                )
            }
        ).menu()

        val lunch = menu.meals.filter { it.dayOfWeek == 0 && it.slot == MealSlot.LUNCH }
        assertEquals(listOf(MealCourse.FIRST, MealCourse.MAIN), lunch.map { it.course })

        // 40% of 2000 kcal = 800, split 40/60.
        assertEquals(320.0 / 500.0, lunch[0].portions.getValue(1), 0.001)
        assertEquals(480.0 / 500.0, lunch[1].portions.getValue(1), 0.001)
        assertNotEquals("and they are two different dishes", lunch[0].recipeId, lunch[1].recipeId)
    }

    @Test
    fun `adding a dessert takes its share out of the same slot`() {
        val menu = generator.generate(
            listOf(ana),
            recipePool(),
            config {
                copy(
                    slotCourses = mapOf(
                        MealSlot.DINNER to listOf(MealCourse.SINGLE, MealCourse.DESSERT)
                    )
                )
            }
        ).menu()

        val dinner = menu.meals.filter { it.dayOfWeek == 0 && it.slot == MealSlot.DINNER }
        val servedKcal = dinner.sumOf { it.portions.getValue(1) * 500.0 }
        assertEquals("the slot still adds up to 30% of 2000 kcal", 600.0, servedKcal, 0.001)
        assertTrue(
            "and the dessert is smaller than the main",
            dinner.single { it.course == MealCourse.DESSERT }.portions.getValue(1) <
                dinner.single { it.course == MealCourse.SINGLE }.portions.getValue(1)
        )
    }

    // ── Locks ───────────────────────────────────────────────────────────

    @Test
    fun `a household lock pins the meal for everyone`() {
        val menu = generate(
            config = config {
                copy(locks = listOf(MealLock(days = setOf(0, 3), slot = MealSlot.DINNER, recipeId = 7)))
            }
        )

        val pinned = menu.meals.filter { it.slot == MealSlot.DINNER && it.dayOfWeek in setOf(0, 3) }
        assertEquals(2, pinned.size)
        assertTrue(pinned.all { it.recipeId == 7L && it.isLocked && it.profileId == null })
        assertTrue("everyone eats it", pinned.all { it.portions.keys == setOf(1L, 2L) })
    }

    @Test
    fun `a lock scoped to one person leaves the household menu alone`() {
        val menu = generate(
            config = config {
                copy(
                    locks = listOf(
                        MealLock(
                            days = setOf(2),
                            slot = MealSlot.LUNCH,
                            recipeId = 7,
                            profileId = 2
                        )
                    )
                )
            }
        )

        val wednesdayLunch = menu.meals.filter {
            it.dayOfWeek == 2 && it.slot == MealSlot.LUNCH
        }
        val his = wednesdayLunch.single { it.profileId == 2L }
        assertEquals(7L, his.recipeId)
        assertTrue(his.isLocked)

        val hers = wednesdayLunch.single { it.profileId == null }
        assertFalse("the shared dish is not pinned", hers.isLocked)
        assertEquals("and only feeds the rest of the house", setOf(1L), hers.portions.keys)
    }

    // ── Shopping list ───────────────────────────────────────────────────

    @Test
    fun `the shopping list adds up the whole household`() {
        val menu = generate(config = config { copy(scope = MenuScope.SHARED) })
        val grams = menu.meals.sumOf { meal ->
            meal.portions.values.sum() * 500.0 // 1 g of ingredient per kcal here
        }
        assertEquals(grams, menu.shoppingTotals.values.sum(), 0.5)
    }

    @Test
    fun `an unsafe locked recipe contributes neither meals nor shopping quantities`() {
        val safe = PlannerFixtures.recipe(1)
        val blocked = PlannerFixtures.recipe(2).let { recipe ->
            val item = recipe.ingredients.single()
            recipe.copy(ingredients = listOf(item.copy(ingredient = item.ingredient.copy(nameEsNorm = "almendra molida"))))
        }
        val profile = ProfileEntity(
            id = 1, name = "Fixture", dateOfBirth = 0, sex = BiologicalSex.FEMALE,
            heightCm = 170.0, weightKg = 70.0, activityLevel = ActivityLevel.LIGHT,
            goal = Goal.MAINTAIN, allergies = "Alérgica a los frutos secos"
        )
        val plan = plan(1, shares = mapOf(MealSlot.LUNCH to 1f), dailyCalories = 1000)
        val config = config(slotWeights = mapOf(MealSlot.LUNCH to 1f)) {
            copy(maxRecipeRepeats = 7, locks = listOf(MealLock(setOf(0), MealSlot.LUNCH, blocked.recipe.id)))
        }
        val result = generator.generate(listOf(plan), listOf(blocked, safe), config, profiles = listOf(profile)).menu()
        assertEquals(List(7) { safe.recipe.id }, result.meals.map { it.recipeId })
        assertTrue(result.meals.none { it.isLocked })
        assertEquals(setOf(1L), result.shoppingTotals.keys)
        assertEquals(7000.0, result.shoppingTotals.getValue(1L), 1e-9)
        assertEquals(GenerationResult.Failure(FailureReason.NOT_ENOUGH_RECIPES),
            generator.generate(listOf(plan), listOf(blocked, safe), config.copy(maxRecipeRepeats = 6),
                profiles = listOf(profile)))
        assertEquals(GenerationResult.Failure(FailureReason.NO_MATCHING_RECIPES),
            generator.generate(listOf(plan), listOf(blocked), config, profiles = listOf(profile)))
    }
}
