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
import com.zame.nutrinest.data.local.entity.IntakeEntryEntity
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.MenuConverters
import com.zame.nutrinest.data.local.entity.NutrientDefEntity
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.ProfileConverters
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
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
        ProfileMealPlanEntity::class,
        WeightEntryEntity::class,
        WeeklyMenuEntity::class,
        PlannedMealEntity::class,
        MealPortionEntity::class,
        ShoppingItemEntity::class,
        IntakeEntryEntity::class
    ],
    version = 8,
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
                // v2 -> v3 had to add weightLossRate with a temporary SQL
                // default. Room's entity has no database default, so normalize
                // the table while preserving both profiles and weight history.
                db.execSQL(
                    """
                    CREATE TABLE `_new_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `dateOfBirth` INTEGER NOT NULL,
                        `sex` TEXT NOT NULL,
                        `heightCm` REAL NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `bodyFatPercent` REAL,
                        `activityLevel` TEXT NOT NULL,
                        `goal` TEXT NOT NULL,
                        `weightLossRate` TEXT NOT NULL,
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
                    INSERT INTO `_new_profiles` (
                        `id`, `name`, `dateOfBirth`, `sex`, `heightCm`, `weightKg`,
                        `bodyFatPercent`, `activityLevel`, `goal`, `weightLossRate`,
                        `formula`, `unitSystem`, `dietaryPreferences`, `allergies`,
                        `colorIndex`, `createdAt`, `remoteId`
                    ) SELECT
                        `id`, `name`, `dateOfBirth`, `sex`, `heightCm`, `weightKg`,
                        `bodyFatPercent`, `activityLevel`, `goal`, `weightLossRate`,
                        `formula`, `unitSystem`, `dietaryPreferences`, `allergies`,
                        `colorIndex`, `createdAt`, `remoteId`
                    FROM `profiles`
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE TABLE `_weight_entries_backup` AS SELECT * FROM `weight_entries`"
                )
                db.execSQL("DROP TABLE `weight_entries`")
                db.execSQL("DROP TABLE `profiles`")
                db.execSQL("ALTER TABLE `_new_profiles` RENAME TO `profiles`")
                db.execSQL(
                    """
                    CREATE TABLE `weight_entries` (
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
                    "INSERT INTO `weight_entries` SELECT * FROM `_weight_entries_backup`"
                )
                db.execSQL("DROP TABLE `_weight_entries_backup`")
                db.execSQL(
                    "CREATE INDEX `index_weight_entries_profileId` " +
                        "ON `weight_entries` (`profileId`)"
                )
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

        /**
         * v5 → v6: per-person menus.
         *
         * Existing meals remain shared, while the new schema also supports
         * profile-specific meals, courses, free slots, and intake tracking.
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addMenuScopeColumns(db)
                rebuildPlannedMeals(db)
                addPortionBaseFactor(db)
                createPerPersonTables(db)
            }
        }

        private fun addMenuScopeColumns(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "weekly_menus", "scope")) {
                db.execSQL(
                    "ALTER TABLE `weekly_menus` ADD COLUMN `scope` TEXT NOT NULL DEFAULT 'SHARED'"
                )
            }
            if (!columnExists(db, "weekly_menus", "coursesSpec")) {
                db.execSQL(
                    "ALTER TABLE `weekly_menus` ADD COLUMN `coursesSpec` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** Rebuilds `planned_meals` so a free slot may have no recipe. */
        private fun rebuildPlannedMeals(db: SupportSQLiteDatabase) {
            // Dropping the parent table can cascade into meal_portions while
            // foreign keys are enabled. Keep an explicit copy so upgrades do
            // not silently lose everybody's serving factors.
            db.execSQL("DROP TABLE IF EXISTS `_meal_portions_backup`")
            db.execSQL(
                "CREATE TABLE `_meal_portions_backup` AS " +
                    "SELECT `plannedMealId`, `profileId`, `factor` FROM `meal_portions`"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `planned_meals_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `menuId` INTEGER NOT NULL,
                    `dayOfWeek` INTEGER NOT NULL,
                    `slot` TEXT NOT NULL,
                    `recipeId` INTEGER,
                    `isLocked` INTEGER NOT NULL,
                    `isCheatMeal` INTEGER NOT NULL,
                    `course` TEXT NOT NULL,
                    `profileId` INTEGER,
                    `isFree` INTEGER NOT NULL,
                    FOREIGN KEY(`menuId`) REFERENCES `weekly_menus`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `planned_meals_new`
                    (`id`, `menuId`, `dayOfWeek`, `slot`, `recipeId`, `isLocked`,
                     `isCheatMeal`, `course`, `profileId`, `isFree`)
                SELECT `id`, `menuId`, `dayOfWeek`, `slot`, `recipeId`, `isLocked`,
                       `isCheatMeal`, 'SINGLE', NULL, 0
                FROM `planned_meals`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `planned_meals`")
            db.execSQL("ALTER TABLE `planned_meals_new` RENAME TO `planned_meals`")
            db.execSQL(
                "INSERT OR REPLACE INTO `meal_portions` " +
                    "(`plannedMealId`, `profileId`, `factor`) " +
                    "SELECT `plannedMealId`, `profileId`, `factor` " +
                    "FROM `_meal_portions_backup`"
            )
            db.execSQL("DROP TABLE `_meal_portions_backup`")
            listOf("menuId", "recipeId", "profileId").forEach {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_planned_meals_$it` " +
                        "ON `planned_meals` (`$it`)"
                )
            }
        }

        /** Existing portions are, by definition, exactly what was planned. */
        private fun addPortionBaseFactor(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "meal_portions", "baseFactor")) {
                db.execSQL(
                    "ALTER TABLE `meal_portions` ADD COLUMN `baseFactor` REAL NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE `meal_portions` SET `baseFactor` = `factor`")
            }
        }

        private fun createPerPersonTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `profile_meal_plans` (
                    `profileId` INTEGER NOT NULL,
                    `slot` TEXT NOT NULL,
                    `share` REAL NOT NULL,
                    `isFree` INTEGER NOT NULL,
                    PRIMARY KEY(`profileId`, `slot`),
                    FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `intake_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profileId` INTEGER NOT NULL,
                    `dayStart` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `grams` REAL,
                    `kcal` REAL NOT NULL,
                    `carbs` REAL NOT NULL,
                    `fats` REAL NOT NULL,
                    `protein` REAL NOT NULL,
                    `loggedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_intake_entries_profileId_dayStart` " +
                    "ON `intake_entries` (`profileId`, `dayStart`)"
            )
        }

        /**
         * Keeps recipe presentation choices with the recipe while retaining
         * grams as the canonical calculation unit. Also removes the unsuitable
         * human-milk reference row when it has never been used by the person.
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Some pre-release v6 databases ran an incomplete per-person
                // migration. Finish it here so those installs can recover.
                if (!tableExists(db, "profile_meal_plans")) {
                    addMenuScopeColumns(db)
                    rebuildPlannedMeals(db)
                    addPortionBaseFactor(db)
                    createPerPersonTables(db)
                }
                if (!columnExists(db, "recipes", "coverImageCrop")) {
                    db.execSQL(
                        "ALTER TABLE `recipes` ADD COLUMN `coverImageCrop` " +
                            "TEXT NOT NULL DEFAULT 'CENTER'"
                    )
                }
                if (!columnExists(db, "recipe_ingredients", "quantityUnit")) {
                    db.execSQL(
                        "ALTER TABLE `recipe_ingredients` ADD COLUMN `quantityUnit` " +
                            "TEXT NOT NULL DEFAULT 'GRAM'"
                    )
                }
                // Do not discard a user's historic recipe or shopping entry.
                // Referenced legacy rows are hidden from ingredient browsing.
                db.execSQL(
                    "DELETE FROM `ingredients` WHERE `sourceKey` = 'BEDCA:2494' " +
                        "AND NOT EXISTS (SELECT 1 FROM `recipe_ingredients` " +
                        "WHERE `ingredientId` = `ingredients`.`id`) " +
                        "AND NOT EXISTS (SELECT 1 FROM `shopping_items` " +
                        "WHERE `ingredientId` = `ingredients`.`id`)"
                )
            }
        }

        /** v7 → v8: bilingual built-in recipe titles and instructions. */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "recipes", "nameEs")) {
                    db.execSQL("ALTER TABLE `recipes` ADD COLUMN `nameEs` TEXT")
                }
                if (!columnExists(db, "recipe_steps", "textEs")) {
                    db.execSQL("ALTER TABLE `recipe_steps` ADD COLUMN `textEs` TEXT")
                }
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
            db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$table'"
            ).use { it.moveToFirst() }

        private fun columnExists(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ): Boolean = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
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
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
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
            BuiltInRecipeSeeder.seedIfNeeded(db)
        }
    }
}
