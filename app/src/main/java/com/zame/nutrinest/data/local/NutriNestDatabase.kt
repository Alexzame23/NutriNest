package com.zame.nutrinest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.dao.FoodReferenceDao
import com.zame.nutrinest.data.local.dao.IngredientDao
import com.zame.nutrinest.data.local.dao.MenuDao
import com.zame.nutrinest.data.local.dao.ProfileDao
import com.zame.nutrinest.data.local.dao.RecipeDao
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientAliasEntity
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientCategoryEntity
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.IngredientNutrientEntity
import com.zame.nutrinest.data.local.entity.IngredientPortionEntity
import com.zame.nutrinest.data.local.entity.IngredientQualityIssueEntity
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.MenuConverters
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.ProfileConverters
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientCrossRef
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.ShoppingAisleEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.data.local.entity.WeightEntryEntity
import com.zame.nutrinest.util.TextNormalizer

@Database(
    entities = [
        IngredientEntity::class,
        IngredientCategoryEntity::class,
        ShoppingAisleEntity::class,
        NutrientDefEntity::class,
        IngredientNutrientEntity::class,
        IngredientPortionEntity::class,
        IngredientAliasEntity::class,
        IngredientQualityIssueEntity::class,
        FoodDatasetMetaEntity::class,
        RecipeEntity::class,
        RecipeIngredientCrossRef::class,
        RecipeStepEntity::class,
        ProfileEntity::class,
        WeightEntryEntity::class,
        WeeklyMenuEntity::class,
        PlannedMealEntity::class,
        MealPortionEntity::class,
        ShoppingItemEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(ProfileConverters::class, MenuConverters::class)
abstract class NutriNestDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun foodReferenceDao(): FoodReferenceDao
    abstract fun recipeDao(): RecipeDao
    abstract fun profileDao(): ProfileDao
    abstract fun menuDao(): MenuDao

    companion object {
        @Volatile private var instance: NutriNestDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `dateOfBirth` INTEGER NOT NULL,
                        `sex` TEXT NOT NULL,
                        `heightCm` REAL NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `bodyFatPercent` REAL,
                        `activityLevel` TEXT NOT NULL,
                        `goal` TEXT NOT NULL,
                        `formula` TEXT NOT NULL,
                        `unitSystem` TEXT NOT NULL,
                        `dietaryPreferences` TEXT,
                        `allergies` TEXT,
                        `colorIndex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `remoteId` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weight_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` INTEGER NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `recordedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_weight_entries_profileId` " +
                            "ON `weight_entries` (`profileId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `profiles` ADD COLUMN `weightLossRate` " +
                            "TEXT NOT NULL DEFAULT 'MODERATE'"
                )
            }
        }

        /** v3 → v4: weekly menu planner tables. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `weekly_menus` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `weekStart` INTEGER NOT NULL,
                        `slotsSpec` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `remoteId` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_menus_weekStart` " +
                            "ON `weekly_menus` (`weekStart`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `planned_meals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menuId` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `slot` TEXT NOT NULL,
                        `recipeId` INTEGER NOT NULL,
                        `isLocked` INTEGER NOT NULL,
                        `isCheatMeal` INTEGER NOT NULL,
                        FOREIGN KEY(`menuId`) REFERENCES `weekly_menus`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_planned_meals_menuId` " +
                            "ON `planned_meals` (`menuId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_planned_meals_recipeId` " +
                            "ON `planned_meals` (`recipeId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meal_portions` (
                        `plannedMealId` INTEGER NOT NULL,
                        `profileId` INTEGER NOT NULL,
                        `factor` REAL NOT NULL,
                        PRIMARY KEY(`plannedMealId`, `profileId`),
                        FOREIGN KEY(`plannedMealId`) REFERENCES `planned_meals`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_meal_portions_profileId` " +
                            "ON `meal_portions` (`profileId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menuId` INTEGER NOT NULL,
                        `ingredientId` INTEGER NOT NULL,
                        `grams` REAL NOT NULL,
                        `purchased` INTEGER NOT NULL,
                        FOREIGN KEY(`menuId`) REFERENCES `weekly_menus`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shopping_items_menuId` " +
                            "ON `shopping_items` (`menuId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shopping_items_ingredientId` " +
                            "ON `shopping_items` (`ingredientId`)"
                )
            }
        }

        /**
         * v4 → v5: the shipped food database.
         *
         * Widens `ingredients` into a full food record, adds the tables the
         * dataset brings with it (categories, aisles, nutrient definitions,
         * per-nutrient values, household portions, synonyms, quality notes) and
         * swaps the fourteen ad-hoc food types for the dataset's twelve
         * categories.
         *
         * Only the schema is set up here; the 35 000 rows of data arrive in
         * `onOpen` via [FoodDatabaseImporter], which also covers a fresh install
         * and a later dataset refresh from a single place.
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addIngredientColumns(db)
                createFoodDataTables(db)
                dropUnusedStarterIngredients(db)
                mapLegacyFoodTypes(db)
                backfillNormalisedNames(db)
            }
        }

        private fun addIngredientColumns(db: SupportSQLiteDatabase) {
            listOf(
                "`nameEs` TEXT",
                "`nameNorm` TEXT NOT NULL DEFAULT ''",
                "`nameEsNorm` TEXT",
                "`baseName` TEXT",
                "`aisleKey` TEXT",
                "`prepState` TEXT NOT NULL DEFAULT 'UNSPECIFIED'",
                "`rawSafety` TEXT NOT NULL DEFAULT 'UNKNOWN'",
                "`notes` TEXT",
                "`servingSizeG` REAL",
                "`isLiquid` INTEGER NOT NULL DEFAULT 0",
                "`densityGml` REAL",
                "`completenessPct` INTEGER NOT NULL DEFAULT 0",
                "`quality` TEXT NOT NULL DEFAULT 'OK'",
                "`sourceKey` TEXT",
                "`isFavorite` INTEGER NOT NULL DEFAULT 0",
                "`usageCount` INTEGER NOT NULL DEFAULT 0",
                "`lastUsedAt` INTEGER",
                "`createdAt` INTEGER NOT NULL DEFAULT 0",
                "`updatedAt` INTEGER NOT NULL DEFAULT 0"
            ).forEach { db.execSQL("ALTER TABLE `ingredients` ADD COLUMN $it") }

            val now = System.currentTimeMillis()
            db.execSQL(
                "UPDATE `ingredients` SET `createdAt` = ?, `updatedAt` = ? WHERE `createdAt` = 0",
                arrayOf<Any>(now, now)
            )

            listOf("nameNorm", "nameEsNorm", "foodType", "isFavorite", "lastUsedAt").forEach {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_$it` ON `ingredients` (`$it`)"
                )
            }
        }

        private fun createFoodDataTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ingredient_categories` (
                    `key` TEXT NOT NULL,
                    `nameEn` TEXT NOT NULL,
                    `nameEs` TEXT NOT NULL,
                    `iconKey` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `shopping_aisles` (
                    `key` TEXT NOT NULL,
                    `nameEn` TEXT NOT NULL,
                    `nameEs` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `nutrient_defs` (
                    `tag` TEXT NOT NULL,
                    `nameEn` TEXT NOT NULL,
                    `nameEs` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `nutrientGroup` TEXT NOT NULL,
                    `isPrimary` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `nrv` REAL,
                    PRIMARY KEY(`tag`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_nutrient_defs_nutrientGroup_sortOrder` " +
                        "ON `nutrient_defs` (`nutrientGroup`, `sortOrder`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ingredient_nutrients` (
                    `ingredientId` INTEGER NOT NULL,
                    `tag` TEXT NOT NULL,
                    `value` REAL NOT NULL,
                    PRIMARY KEY(`ingredientId`, `tag`),
                    FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ingredient_portions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `ingredientId` INTEGER NOT NULL,
                    `labelEn` TEXT NOT NULL,
                    `labelEs` TEXT NOT NULL,
                    `grams` REAL NOT NULL,
                    `portionType` TEXT NOT NULL,
                    `isDefault` INTEGER NOT NULL,
                    FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ingredient_portions_ingredientId` " +
                        "ON `ingredient_portions` (`ingredientId`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ingredient_aliases` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `ingredientId` INTEGER NOT NULL,
                    `alias` TEXT NOT NULL,
                    `aliasNorm` TEXT NOT NULL,
                    `lang` TEXT NOT NULL,
                    FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ingredient_aliases_ingredientId` " +
                        "ON `ingredient_aliases` (`ingredientId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ingredient_aliases_aliasNorm` " +
                        "ON `ingredient_aliases` (`aliasNorm`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ingredient_quality_issues` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `ingredientId` INTEGER NOT NULL,
                    `code` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `detail` TEXT,
                    FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ingredient_quality_issues_ingredientId` " +
                        "ON `ingredient_quality_issues` (`ingredientId`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `food_dataset_meta` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )
        }

        /**
         * Clears out the fifteen placeholder ingredients that shipped before the
         * real dataset, so they don't sit alongside their imported equivalents.
         * Any the user already cooked with are kept: the foreign keys are
         * RESTRICT, and an ingredient vanishing would take its recipe with it.
         */
        private fun dropUnusedStarterIngredients(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM `ingredients`
                WHERE `isCustom` = 0
                  AND `id` NOT IN (SELECT `ingredientId` FROM `recipe_ingredients`)
                  AND `id` NOT IN (SELECT `ingredientId` FROM `shopping_items`)
                """.trimIndent()
            )
        }

        /** Rewrites the legacy free-text food types as dataset category keys. */
        private fun mapLegacyFoodTypes(db: SupportSQLiteDatabase) {
            val cases = IngredientCategories.LEGACY_FOOD_TYPES.entries.joinToString(" ") {
                "WHEN '${it.key}' THEN '${it.value}'"
            }
            val knownKeys = IngredientCategories.ALL.joinToString { "'$it'" }
            db.execSQL(
                """
                UPDATE `ingredients`
                SET `foodType` = CASE `foodType` $cases ELSE '${IngredientCategories.MISC}' END
                WHERE `foodType` NOT IN ($knownKeys)
                """.trimIndent()
            )
        }

        /**
         * Fills in the searchable name for rows that predate it. SQLite cannot
         * strip accents, so this is the one part of the migration that walks
         * rows — and there are only ever a handful left at this point.
         */
        private fun backfillNormalisedNames(db: SupportSQLiteDatabase) {
            val names = mutableListOf<Pair<Long, String>>()
            db.query("SELECT `id`, `name` FROM `ingredients` WHERE `nameNorm` = ''").use { cursor ->
                while (cursor.moveToNext()) {
                    names += cursor.getLong(0) to cursor.getString(1)
                }
            }
            names.forEach { (id, name) ->
                db.execSQL(
                    "UPDATE `ingredients` SET `nameNorm` = ? WHERE `id` = ?",
                    arrayOf<Any>(TextNormalizer.normalize(name), id)
                )
            }
        }

        fun get(context: Context): NutriNestDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /** Internal so instrumented tests can open a fresh, uncached instance. */
        internal fun build(appContext: Context): NutriNestDatabase =
            Room.databaseBuilder(appContext, NutriNestDatabase::class.java, "nutrinest.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(FoodImportCallback(appContext))
                .build()
    }

    /**
     * Loads the shipped food database once the schema is ready.
     *
     * `onOpen` rather than `onCreate` or the migration, so that a fresh install,
     * an upgrade and a later dataset refresh all take the same path — the
     * importer decides what is needed by comparing dataset versions. It costs one
     * indexed lookup per launch and does real work only when they differ.
     */
    private class FoodImportCallback(private val context: Context) : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            FoodDatabaseImporter.importIfNeeded(context, db)
        }
    }
}
