package com.zame.nutrinest.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nutritional values expressed PER 100 g of the ingredient.
 * Embedded in [IngredientEntity]; recipe totals are computed from these.
 *
 * Deliberately limited to the four macros every screen needs. Fibre, sugars,
 * fatty acids, vitamins and minerals live in `ingredient_nutrients` so this
 * stays cheap to carry around in recipe and menu maths.
 */
data class NutritionValues(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,   // grams
    val fats: Double = 0.0,    // grams
    val protein: Double = 0.0  // grams
) {
    operator fun plus(other: NutritionValues) = NutritionValues(
        kcal + other.kcal,
        carbs + other.carbs,
        fats + other.fats,
        protein + other.protein
    )

    /** Scale from per-100g to an arbitrary quantity in grams. */
    fun forGrams(grams: Double): NutritionValues {
        val f = grams / 100.0
        return NutritionValues(kcal * f, carbs * f, fats * f, protein * f)
    }
}

/**
 * The twelve categories of the shipped food dataset, which is also the app's
 * ingredient taxonomy.
 *
 * The authoritative names, colours and ordering live in `ingredient_categories`
 * (imported from the dataset, so they stay in step with it). What is kept here
 * are the stable keys plus the short bilingual labels the UI uses in chips and
 * section headers — the official dataset names ("Pescados, moluscos, reptiles,
 * crustáceos y derivados") are far too long for those.
 */
object IngredientCategories {
    const val CEREALS = "CEREALS"
    const val VEGETABLES = "VEGETABLES"
    const val FRUITS = "FRUITS"
    const val LEGUMES = "LEGUMES"
    const val MEAT = "MEAT"
    const val FISH = "FISH"
    const val EGGS = "EGGS"
    const val DAIRY = "DAIRY"
    const val FATS_OILS = "FATS_OILS"
    const val SWEETS = "SWEETS"
    const val BEVERAGES = "BEVERAGES"
    const val MISC = "MISC"

    val ALL = listOf(
        CEREALS, VEGETABLES, FRUITS, LEGUMES, MEAT, FISH,
        EGGS, DAIRY, FATS_OILS, SWEETS, BEVERAGES, MISC
    )

    private val SHORT_NAMES_EN = mapOf(
        CEREALS to "Cereals",
        VEGETABLES to "Vegetables",
        FRUITS to "Fruits",
        LEGUMES to "Legumes & nuts",
        MEAT to "Meat",
        FISH to "Fish & seafood",
        EGGS to "Eggs",
        DAIRY to "Dairy",
        FATS_OILS to "Fats & oils",
        SWEETS to "Sweets",
        BEVERAGES to "Beverages",
        MISC to "Other"
    )

    private val SHORT_NAMES_ES = mapOf(
        CEREALS to "Cereales",
        VEGETABLES to "Verduras",
        FRUITS to "Frutas",
        LEGUMES to "Legumbres y frutos secos",
        MEAT to "Carnes",
        FISH to "Pescados y mariscos",
        EGGS to "Huevos",
        DAIRY to "Lácteos",
        FATS_OILS to "Grasas y aceites",
        SWEETS to "Dulces",
        BEVERAGES to "Bebidas",
        MISC to "Otros"
    )

    /** Short label for chips, section headers and list rows. */
    fun shortName(key: String, spanish: Boolean): String =
        (if (spanish) SHORT_NAMES_ES else SHORT_NAMES_EN)[key] ?: key

    /**
     * Fallback colour, used only before `ingredient_categories` has been read.
     * Mirrors `food_categories.color_hex` in the dataset.
     */
    val FALLBACK_COLORS = mapOf(
        CEREALS to 0xFFC8862FL,
        VEGETABLES to 0xFF6E8B3DL,
        FRUITS to 0xFFC25B3AL,
        LEGUMES to 0xFF8A6A3BL,
        MEAT to 0xFFA34733L,
        FISH to 0xFF3F7A8CL,
        EGGS to 0xFFD9A441L,
        DAIRY to 0xFFD8C9A8L,
        FATS_OILS to 0xFFB8912EL,
        SWEETS to 0xFF7A4A2BL,
        BEVERAGES to 0xFF5F7D8CL,
        MISC to 0xFF8C7B6BL
    )

    /**
     * Maps the fourteen free-text food types used before the dataset import
     * onto the categories above. The v4 → v5 migration builds its SQL `CASE`
     * from this map, so the two cannot drift apart.
     */
    val LEGACY_FOOD_TYPES = mapOf(
        "Pasta" to CEREALS,
        "Rice" to CEREALS,
        "Grains" to CEREALS,
        "Meat" to MEAT,
        "Fish" to FISH,
        "Vegetables" to VEGETABLES,
        "Fruit" to FRUITS,
        "Dairy" to DAIRY,
        "Eggs" to EGGS,
        "Legumes" to LEGUMES,
        "Nuts & Seeds" to LEGUMES,
        "Oils & Fats" to FATS_OILS,
        "Spices" to MISC,
        "Other" to MISC
    )
}

/**
 * How safe an ingredient is to eat without cooking.
 *
 * The dataset's `prep_state` records how a food was *measured* ("Pork, sirloin,
 * raw"), not whether eating it raw is a good idea, so this is derived at import
 * from category, preparation state and name keywords. It is guidance rather
 * than a food-safety ruling, and the user can correct it on any ingredient.
 */
object RawSafety {
    const val SAFE = "SAFE"
    const val CAUTION = "CAUTION"
    const val UNSAFE = "UNSAFE"
    const val UNKNOWN = "UNKNOWN"

    val ALL = listOf(SAFE, CAUTION, UNSAFE, UNKNOWN)
}

/** Preparation state a food's composition was measured in. */
object PrepState {
    const val UNSPECIFIED = "UNSPECIFIED"
    const val RAW = "RAW"
    const val BOILED = "BOILED"
    const val COOKED = "COOKED"
    const val FRIED = "FRIED"
    const val GRILLED = "GRILLED"
    const val ROASTED = "ROASTED"
    const val STEAMED = "STEAMED"
    const val CANNED = "CANNED"
    const val CURED = "CURED"
    const val SMOKED = "SMOKED"
    const val DRIED = "DRIED"
    const val FROZEN = "FROZEN"
    const val POWDERED = "POWDERED"
}

/** Confidence in an ingredient's composition data. */
object DataQuality {
    const val OK = "OK"
    const val SUSPECT = "SUSPECT"
    const val INCOMPLETE = "INCOMPLETE"
}

@Entity(
    tableName = "ingredients",
    indices = [
        Index("nameNorm"),
        Index("nameEsNorm"),
        Index("foodType"),
        Index("isFavorite"),
        Index("lastUsedAt")
    ]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Primary display name; the dataset's English name for imported foods. */
    val name: String,
    /** Spanish name, shown when the device is in Spanish. Null for most customs. */
    val nameEs: String? = null,
    /** Lowercase, accent-stripped [name]. Drives search and duplicate detection. */
    @ColumnInfo(defaultValue = "''") val nameNorm: String = "",
    /** Lowercase, accent-stripped [nameEs]. */
    val nameEsNorm: String? = null,
    /** Name without the preparation state, e.g. "Carrot" for "Carrot, boiled". */
    val baseName: String? = null,

    /**
     * One of [IngredientCategories]. Still stored in the original `foodType`
     * column: `ALTER TABLE ... RENAME COLUMN` needs SQLite 3.25 and minSdk 26
     * ships 3.18, so renaming would mean rebuilding the table and every foreign
     * key pointing at it for no functional gain.
     */
    @ColumnInfo(name = "foodType") val categoryKey: String,
    /** Supermarket aisle key, used to group the shopping list. */
    val aisleKey: String? = null,

    @Embedded val nutrition: NutritionValues,

    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val prepState: String = PrepState.UNSPECIFIED,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val rawSafety: String = RawSafety.UNKNOWN,
    val notes: String? = null,
    val servingSizeG: Double? = null,
    @ColumnInfo(defaultValue = "0") val isLiquid: Boolean = false,
    /** g per ml. Null means volume units can't be converted for this food. */
    val densityGml: Double? = null,
    /** 0-100: how much of the nutrient panel the dataset actually filled in. */
    @ColumnInfo(defaultValue = "0") val completenessPct: Int = 0,
    @ColumnInfo(defaultValue = "'OK'") val quality: String = DataQuality.OK,
    /** Provenance of imported rows, e.g. "BEDCA:1234". Null for user creations. */
    val sourceKey: String? = null,

    val imagePath: String? = null,
    /** false = shipped food database, true = created by the user. */
    val isCustom: Boolean = true,

    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    /** Times this ingredient has been added to a recipe. */
    @ColumnInfo(defaultValue = "0") val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),

    /**
     * Reserved for the future community feature: null = local-only.
     * When syncing is added, this holds the server-side id.
     */
    val remoteId: String? = null
) {
    /** True when the dataset left enough of the nutrient panel blank to warn about. */
    val hasIncompleteData: Boolean
        get() = quality != DataQuality.OK || completenessPct < 50

    /** Name to show for the given language, falling back to the primary name. */
    fun displayName(spanish: Boolean): String =
        if (spanish) nameEs?.takeIf { it.isNotBlank() } ?: name else name
}

/**
 * The course a recipe belongs to.
 *
 * The English wording is the stored key, not a label: it is written into
 * `recipes.category`, matched by the menu planner and already sits in every
 * user's database. Display names come from `strings.xml` instead — see
 * `recipeCategoryLabel` in the UI layer.
 */
object RecipeCategories {
    const val BREAKFAST = "Breakfast"
    const val MAIN_DISH = "Main Dish"
    const val SIDE_DISH = "Side Dish"
    const val DESSERT = "Dessert"
    const val SNACK = "Snack"
    const val DRINK = "Drink"

    val ALL = listOf(BREAKFAST, MAIN_DISH, SIDE_DISH, DESSERT, SNACK, DRINK)
}
