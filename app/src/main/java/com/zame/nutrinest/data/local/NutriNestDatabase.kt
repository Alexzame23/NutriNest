package com.zame.nutrinest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zame.nutrinest.data.local.dao.IngredientDao
import com.zame.nutrinest.data.local.dao.MenuDao
import com.zame.nutrinest.data.local.dao.ProfileDao
import com.zame.nutrinest.data.local.dao.RecipeDao
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.MealPortionEntity
import com.zame.nutrinest.data.local.entity.MenuConverters
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import com.zame.nutrinest.data.local.entity.ProfileConverters
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientCrossRef
import com.zame.nutrinest.data.local.entity.RecipeStepEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        IngredientEntity::class,
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
    version = 4,
    exportSchema = true
)
@TypeConverters(ProfileConverters::class, MenuConverters::class)
abstract class NutriNestDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
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

        fun get(context: Context): NutriNestDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NutriNestDatabase::class.java,
                    "nutrinest.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(SeedCallback(context.applicationContext))
                    .build()
                    .also { instance = it }
            }
    }

    private class SeedCallback(private val context: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                get(context).ingredientDao().insertAll(STARTER_INGREDIENTS)
            }
        }
    }
}

/** Values per 100 g. Placeholder set until the full database is provided. */
private val STARTER_INGREDIENTS = listOf(
    builtIn("Chicken breast", "Meat", 165.0, 0.0, 3.6, 31.0),
    builtIn("White rice (cooked)", "Rice", 130.0, 28.0, 0.3, 2.7),
    builtIn("Spaghetti (dry)", "Pasta", 371.0, 74.7, 1.5, 13.0),
    builtIn("Salmon", "Fish", 208.0, 0.0, 13.0, 20.0),
    builtIn("Broccoli", "Vegetables", 34.0, 6.6, 0.4, 2.8),
    builtIn("Tomato", "Vegetables", 18.0, 3.9, 0.2, 0.9),
    builtIn("Olive oil", "Oils & Fats", 884.0, 0.0, 100.0, 0.0),
    builtIn("Egg", "Eggs", 155.0, 1.1, 11.0, 13.0),
    builtIn("Whole milk", "Dairy", 61.0, 4.8, 3.3, 3.2),
    builtIn("Banana", "Fruit", 89.0, 22.8, 0.3, 1.1),
    builtIn("Lentils (cooked)", "Legumes", 116.0, 20.1, 0.4, 9.0),
    builtIn("Oats", "Grains", 389.0, 66.3, 6.9, 16.9),
    builtIn("Almonds", "Nuts & Seeds", 579.0, 21.6, 49.9, 21.2),
    builtIn("Greek yogurt", "Dairy", 59.0, 3.6, 0.4, 10.0),
    builtIn("Potato", "Vegetables", 77.0, 17.5, 0.1, 2.0)
)

private fun builtIn(
    name: String, type: String,
    kcal: Double, carbs: Double, fats: Double, protein: Double
) = IngredientEntity(
    name = name,
    foodType = type,
    nutrition = NutritionValues(kcal, carbs, fats, protein),
    isCustom = false
)
