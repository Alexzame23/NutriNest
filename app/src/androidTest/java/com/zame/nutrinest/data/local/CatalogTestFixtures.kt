package com.zame.nutrinest.data.local

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*

/** Synthetic user installation, including non-default factors and non-integer quantities. */
internal object CatalogTestFixtures {
    fun seed(db: SupportSQLiteDatabase, version: Int) {
        db.execSQL("INSERT INTO ingredients (id,name,foodType,kcal,carbs,fats,protein,isCustom) VALUES (11,'Fixture food','EGGS',123.45,4.3,6.2,12.6,1), (12,'Other fixture','EGGS',91,2,3,4,1)")
        db.execSQL("INSERT INTO recipes (id,name,category,createdAt,isFavorite) VALUES (21,'Fixture recipe','Main Dish',123,1)")
        db.execSQL("INSERT INTO recipe_ingredients (recipeId,ingredientId,grams) VALUES (21,11,137.123456789)")
        db.execSQL("INSERT INTO recipe_steps (id,recipeId,position,text) VALUES (22,21,0,'Keep this exact step')")
        db.execSQL("INSERT INTO profiles (id,name,dateOfBirth,sex,heightCm,weightKg,activityLevel,goal,formula,unitSystem,colorIndex,createdAt,weightLossRate) VALUES (31,'Fixture person',123,'FEMALE',163.7,61.2,'MODERATE','MAINTAIN','AUTO','METRIC',2,456,'MODERATE')")
        db.execSQL("INSERT INTO weight_entries (id,profileId,weightKg,recordedAt) VALUES (32,31,61.125,789)")
        db.execSQL("INSERT INTO weekly_menus (id,weekStart,slotsSpec,createdAt) VALUES (41,1000,'LUNCH:1',123)")
        if (version >= 7) {
            db.execSQL("INSERT INTO planned_meals (id,menuId,dayOfWeek,slot,recipeId,isLocked,isCheatMeal,course,profileId,isFree) VALUES (42,41,0,'LUNCH',21,1,0,'MAIN',31,0)")
            db.execSQL("INSERT INTO meal_portions (plannedMealId,profileId,factor,baseFactor) VALUES (42,31,1.375,0.925)")
            db.execSQL("INSERT INTO profile_meal_plans (profileId,slot,share,isFree) VALUES (31,'LUNCH',0.43,0)")
            db.execSQL("INSERT INTO intake_entries (id,profileId,dayStart,label,grams,kcal,carbs,fats,protein,loggedAt) VALUES (51,31,123,'Fixture intake',87.123,101.2,3.4,5.6,7.8,999)")
            db.execSQL("UPDATE recipe_ingredients SET quantityUnit='LITRE'")
        } else {
            db.execSQL("INSERT INTO planned_meals (id,menuId,dayOfWeek,slot,recipeId,isLocked,isCheatMeal) VALUES (42,41,0,'LUNCH',21,1,0)")
            db.execSQL("INSERT INTO meal_portions (plannedMealId,profileId,factor) VALUES (42,31,1.375)")
        }
        db.execSQL("INSERT INTO shopping_items (id,menuId,ingredientId,grams,purchased) VALUES (43,41,11,543.210987,1)")
        if (version >= 5) {
            db.execSQL("UPDATE ingredients SET isFavorite=1, usageCount=7, nameNorm='fixture', densityGml=1.07, sourceKey='BEDCA:123' WHERE id=11")
            db.execSQL("UPDATE ingredients SET sourceKey='BEDCA:123' WHERE id=12")
            db.execSQL("INSERT OR IGNORE INTO ingredient_categories VALUES ('EGGS','Eggs','Huevos','egg','#FFFFFF',1)")
            db.execSQL("INSERT INTO shopping_aisles VALUES ('FIXTURE','Fixture','Fixture',1)")
            db.execSQL("INSERT INTO nutrient_defs VALUES ('FIXTURE','Fixture','Fixture','g','fixture',0,1,NULL)")
            db.execSQL("INSERT INTO ingredient_nutrients VALUES (11,'FIXTURE',0.123456789)")
            db.execSQL("INSERT INTO ingredient_portions VALUES (900061,11,'Synthetic unit','Unidad ficticia',43.7,'UNIT',1)")
            db.execSQL("INSERT INTO ingredient_aliases VALUES (900062,11,'Fixture alias','fixture alias','en')")
            db.execSQL("INSERT INTO ingredient_quality_issues VALUES (900063,11,'FIXTURE','WARNING','Preserve')")
            db.execSQL("INSERT INTO food_dataset_meta VALUES ('fixture','original metadata')")
        }
        if (version >= 8) {
            db.execSQL("UPDATE recipes SET nameEs='Receta de prueba'")
            db.execSQL("UPDATE recipe_steps SET textEs='Conserva el paso'")
        }
    }

    /** Independent raw row snapshots (including NULL/type and exact double bits). */
    fun snapshot(
        db: SupportSQLiteDatabase,
        tables: List<String>,
        projections: Map<String, String> = emptyMap()
    ): Map<String, List<String>> = tables.associateWith { table ->
        db.query("SELECT ${projections[table] ?: "*"} FROM `$table`").use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i ->
                val value = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> "NULL"
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toRawBits().toString()
                    Cursor.FIELD_TYPE_BLOB -> c.getBlob(i).contentToString()
                    else -> c.getString(i)
                }
                "${c.getColumnName(i)}:${c.getType(i)}:${value.length}:$value"
            }) }.sorted()
        }
    }

    fun tables(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'room_%' AND name NOT LIKE 'sqlite_%' ORDER BY name"
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { check(it.moveToFirst()); it.getLong(0) }

    fun rejected(db: SupportSQLiteDatabase, sql: String) {
        try { db.execSQL(sql); fail("Accepted invalid SQL: $sql") }
        catch (_: SQLiteConstraintException) { /* expected */ }
    }

    /** Same invalid writes exercised against Room fresh DDL and the explicit migration. */
    fun constraints(db: SupportSQLiteDatabase) {
        // MigrationTestHelper's raw connection does not inherit Room's FK configuration.
        db.setForeignKeyConstraintsEnabled(true)
        assertEquals(1L, scalar(db, "PRAGMA foreign_keys"))
        val baseline = db.query("SELECT revision_id FROM catalog_revision LIMIT 1").use { it.moveToFirst(); it.getString(0) }
        val revision = "review-fixture"
        db.execSQL("INSERT INTO catalog_revision (revision_id,revision_key,parent_revision_id,kind,state,source_namespace,manifest_json,manifest_sha256,rules_version,actor,created_at) VALUES (?,?,?,'REVIEW','STAGED','FIXTURE','{}',?,'fixture-v1','tester',1)",
            arrayOf<Any>(revision, revision, baseline, CatalogFingerprint.sha256("{}")))
        val evidence = "INSERT INTO conversion_evidence (evidence_id,ingredient_id,unit_key,grams_per_unit,mass_basis,method,status,evidence_version,confidence,source_locator,source_excerpt,conditions,reviewed_by,reviewed_at,revision_id)"
        rejected(db, "$evidence VALUES ('bad',11,'UNIT:fixture',-1,'EDIBLE','DOCUMENTED','ACCEPTED','fixture-v1','HIGH','fixture','fixture','fixture','tester',1,'$revision')")
        rejected(db, "$evidence VALUES ('gross',11,'UNIT:fixture',43.7,'GROSS','DOCUMENTED','ACCEPTED','fixture-v1','HIGH','fixture','fixture','fixture','tester',1,'$revision')")
        rejected(db, "$evidence VALUES ('unreviewed',11,'UNIT:fixture',43.7,'EDIBLE','DOCUMENTED','ACCEPTED','fixture-v1','HIGH','fixture','fixture','fixture',NULL,NULL,'$revision')")
        rejected(db, "$evidence VALUES ('infinite',11,'UNIT:fixture',1e999,'EDIBLE','DOCUMENTED','ACCEPTED','fixture-v1','HIGH','fixture','fixture','fixture','tester',1,'$revision')")
        db.execSQL("$evidence VALUES ('accepted',11,'UNIT:fixture',43.7,'EDIBLE','DOCUMENTED','ACCEPTED','fixture-v1','HIGH','fixture','fixture','fixture','tester',1,'$revision')")
        db.execSQL("$evidence VALUES ('legacy',11,'PORTION:fixture',NULL,'UNKNOWN','LEGACY_PORTION','UNVERIFIED','fixture-v1','UNVERIFIED','fixture','fixture','fixture',NULL,NULL,'$revision')")
        val option = "INSERT INTO ingredient_unit_options (option_id,ingredient_id,unit_key,unit_kind,label_en,evidence_id,enabled,revision_id)"
        rejected(db, "$option VALUES ('wrong-food',12,'UNIT:fixture','COUNT','Fixture','accepted',1,'$revision')")
        rejected(db, "$option VALUES ('wrong-unit',11,'UNIT:other','COUNT','Fixture','accepted',1,'$revision')")
        rejected(db, "$option VALUES ('disabled-wrong-food',12,'UNIT:fixture','COUNT','Fixture','accepted',0,'$revision')")
        rejected(db, "$option VALUES ('disabled-wrong-unit',11,'UNIT:other','COUNT','Fixture','accepted',0,'$revision')")
        rejected(db, "$option VALUES ('not-accepted',11,'PORTION:fixture','PORTION','Fixture','legacy',1,'$revision')")
        db.execSQL("$option VALUES ('ok',11,'UNIT:fixture','COUNT','Fixture','accepted',1,'$revision')")
        rejected(db, "$option VALUES ('duplicate',11,'UNIT:fixture','COUNT','Fixture','accepted',1,'$revision')")
        rejected(db, "UPDATE ingredient_catalog_status SET state='INVALID' WHERE ingredient_id=11")
        rejected(db, "UPDATE ingredient_canonical_map SET record_sha256='invalid' WHERE ingredient_id=11")
        rejected(db, "UPDATE canonical_ingredients SET name_en='',name_es=NULL")
        rejected(db, "UPDATE ingredients SET kcal=999 WHERE id=11")
        rejected(db, "DELETE FROM ingredient_portions WHERE ingredientId=11")
        rejected(db, "DELETE FROM ingredients WHERE id=11")
        rejected(db, "DELETE FROM conversion_evidence WHERE evidence_id='accepted'")
        rejected(db, "DELETE FROM catalog_revision WHERE revision_id='$baseline'")
        db.execSQL("UPDATE ingredients SET isFavorite=0,usageCount=usageCount+1 WHERE id=11")
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
