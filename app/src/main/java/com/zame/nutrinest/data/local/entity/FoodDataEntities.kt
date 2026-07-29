package com.zame.nutrinest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Tables imported from the shipped food dataset (`assets/nutrinest_foods.db`,
 * derived from BEDCA). Everything here is bilingual: the dataset ships Spanish
 * and English side by side, and the app picks per device locale while searching
 * across both.
 *
 * See `FoodDatabaseImporter` for how these are populated.
 */

/** Nutrient groups used to section the detail screen. */
object NutrientGroups {
    const val ENERGY = "ENERGY"
    const val MACRO = "MACRO"
    const val SUGAR = "SUGAR"
    const val FAT = "FAT"
    const val FATTY_ACID = "FATTY_ACID"
    const val MINERAL = "MINERAL"
    const val VITAMIN = "VITAMIN"

    /** Display order on the ingredient detail screen. */
    val DISPLAY_ORDER = listOf(MACRO, FAT, FATTY_ACID, SUGAR, VITAMIN, MINERAL)
}

/**
 * Nutrient tags the app references by name. The dataset defines 81 in total;
 * these are the ones with dedicated UI or validation rules.
 */
object NutrientTags {
    const val ENERGY = "ENERC"
    const val PROTEIN = "PROT"
    const val FAT = "FAT"
    const val CARBS = "CHO"
    const val FIBRE = "FIBT"
    const val SUGARS = "SUGAR"
    const val STARCH = "STARCH"
    const val SAT_FAT = "FASAT"
    const val ALCOHOL = "ALC"
    const val WATER = "WATER"
    const val SODIUM = "NA"

    /** Shown in the macro block of the detail screen, in this order. */
    val MACRO_PANEL = listOf(CARBS, FIBRE, SUGARS, FAT, SAT_FAT, PROTEIN)
}

@Entity(tableName = "ingredient_categories")
data class IngredientCategoryEntity(
    @PrimaryKey val key: String,
    val nameEn: String,
    val nameEs: String,
    val iconKey: String,
    val colorHex: String,
    val sortOrder: Int
) {
    fun displayName(spanish: Boolean): String = if (spanish) nameEs else nameEn
}

@Entity(tableName = "shopping_aisles")
data class ShoppingAisleEntity(
    @PrimaryKey val key: String,
    val nameEn: String,
    val nameEs: String,
    val sortOrder: Int
) {
    fun displayName(spanish: Boolean): String = if (spanish) nameEs else nameEn
}

/**
 * Definition of a nutrient: label, unit and the EU daily reference value
 * (Reg. 1169/2011) used to render "% of your daily reference" bars.
 */
@Entity(tableName = "nutrient_defs", indices = [Index(value = ["nutrientGroup", "sortOrder"])])
data class NutrientDefEntity(
    @PrimaryKey val tag: String,
    val nameEn: String,
    val nameEs: String,
    val unit: String,
    val nutrientGroup: String,
    /** Headline nutrients, shown before the rest of their group. */
    val isPrimary: Boolean,
    val sortOrder: Int,
    /** Nutrient reference value per day, null when the EU defines none. */
    val nrv: Double?
) {
    fun displayName(spanish: Boolean): String = if (spanish) nameEs else nameEn
}

/**
 * A nutrient value for one ingredient, per 100 g.
 *
 * `tag` references [NutrientDefEntity] logically but without a foreign key: an
 * index on 30k+ rows to enforce it would cost ~370 KB for a constraint the
 * importer already guarantees.
 */
@Entity(
    tableName = "ingredient_nutrients",
    primaryKeys = ["ingredientId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IngredientNutrientEntity(
    val ingredientId: Long,
    val tag: String,
    val value: Double
)

/** Household measure ("Tablespoon", "Glass (200 ml)") and its weight in grams. */
@Entity(
    tableName = "ingredient_portions",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ingredientId")]
)
data class IngredientPortionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val labelEn: String,
    val labelEs: String,
    val grams: Double,
    /** UNIT, SLICE, CUP, TBSP, TSP, PINCH, HANDFUL, PORTION, GLASS, CAN, BOTTLE, SPLASH. */
    val portionType: String,
    val isDefault: Boolean = false
) {
    fun displayLabel(spanish: Boolean): String = if (spanish) labelEs else labelEn
}

/**
 * Search synonym, so "capsicum" finds "bell pepper" and "jugo" finds "zumo".
 * The dataset ships 1164 of these across both languages.
 */
@Entity(
    tableName = "ingredient_aliases",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ingredientId"), Index("aliasNorm")]
)
data class IngredientAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val alias: String,
    /** Lowercase, accent-stripped [alias]. */
    val aliasNorm: String,
    /** "es" or "en". */
    val lang: String
)

/**
 * A data-quality note carried over from the dataset's own validation pass
 * (energy mismatch, mass balance out of range, saturated fat above total fat…).
 * Surfaced as warnings on the detail screen.
 */
@Entity(
    tableName = "ingredient_quality_issues",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("ingredientId")]
)
data class IngredientQualityIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val code: String,
    /** INFO, WARN or ERROR. */
    val severity: String,
    val detail: String?
)

/**
 * Key/value copy of the dataset's `db_metadata`: version, basis quantity and
 * the attribution text the detail screen is required to display.
 */
@Entity(tableName = "food_dataset_meta")
data class FoodDatasetMetaEntity(
    @PrimaryKey val key: String,
    val value: String
) {
    companion object {
        const val DATASET_VERSION = "dataset_version"
        const val ATTRIBUTION = "attribution"
        const val BASIS_QUANTITY = "basis_quantity"
        const val SOURCE = "source"
    }
}

// ── Query results ────────────────────────────────────────────────────────

/** Everything the detail screen needs, in one observed query. */
data class IngredientWithDetails(
    @Embedded val ingredient: IngredientEntity,
    @Relation(parentColumn = "id", entityColumn = "ingredientId")
    val portions: List<IngredientPortionEntity>,
    @Relation(parentColumn = "id", entityColumn = "ingredientId")
    val aliases: List<IngredientAliasEntity>,
    @Relation(parentColumn = "id", entityColumn = "ingredientId")
    val issues: List<IngredientQualityIssueEntity>
)

/** A nutrient value joined to its definition, ready to render. */
data class NutrientValueWithDef(
    val tag: String,
    val value: Double,
    val nameEn: String,
    val nameEs: String,
    val unit: String,
    val nutrientGroup: String,
    val isPrimary: Boolean,
    val sortOrder: Int,
    val nrv: Double?
) {
    fun displayName(spanish: Boolean): String = if (spanish) nameEs else nameEn

    /** Percentage of the EU daily reference value, or null when none is defined. */
    val nrvPercent: Double?
        get() = nrv?.takeIf { it > 0 }?.let { value / it * 100.0 }
}

/** Row of the "used in" section on the detail screen. */
data class RecipeUsage(
    val recipeId: Long,
    val recipeName: String,
    val grams: Double
)

/** A category with its live ingredient count, for the grouped list. */
data class CategoryWithCount(
    val key: String,
    val nameEn: String,
    val nameEs: String,
    val colorHex: String,
    val iconKey: String,
    val sortOrder: Int,
    val ingredientCount: Int
)
