package com.zame.nutrinest.data.local

import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.*
import com.zame.nutrinest.data.prices.RoomPriceSnapshotCache
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.data.repository.LocalMenuRepository
import com.zame.nutrinest.data.repository.LocalRecipeRepository
import com.zame.nutrinest.domain.nutrition.*
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.MenuGenerator
import com.zame.nutrinest.domain.prices.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class PerformanceBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val now = Instant.parse("2026-11-18T13:00:00Z")
    private val region = CommercialId("test-region")

    @get:Rule val migrations = MigrationTestHelper(
        instrumentation, NutriNestDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Before fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nutrinestPerf") == "true")
    }

    @Test fun catalogAndMenu() = runBlocking<Unit> {
        val name = "perf25-catalog.db"
        try {
            measure("fresh_catalog_room_open", 5, 1, { context.deleteDatabase(name) }) {
                withDatabase(name) {
                    check(it.ingredientDao().count() >= 900)
                }
            }
            measure("existing_catalog_room_open", 9) {
                withDatabase(name) { check(it.ingredientDao().count() >= 900) }
            }
            withDatabase(name) { db ->
                val repository = LocalIngredientRepository(db)
                val ingredientIds = repository.observeAll().first().take(8).map { it.id }
                for (term in listOf("aceite", "puerco", "platano", "zzzz")) {
                    measure("search_$term", 21) {
                        check(repository.search(term).first().isNotEmpty() == (term != "zzzz"))
                    }
                }
                repeat(3) { index ->
                    db.profileDao().insert(ProfileEntity(name = "Profile $index", dateOfBirth = 631152000000L,
                        sex = BiologicalSex.FEMALE, heightCm = 165.0, weightKg = 64.0,
                        activityLevel = ActivityLevel.LIGHT, goal = Goal.MAINTAIN))
                }
                val recipes = LocalRecipeRepository(db.recipeDao())
                var count = 0
                measure("create_recipe_8_ingredients_4_steps", 21) {
                    val id = recipes.create(RecipeEntity(name = "Perf recipe ${++count}",
                        category = RecipeCategories.MAIN_DISH),
                        ingredientIds.map { RecipeIngredientInput(it, 75.5) },
                        List(4) { "Step $it" to null })
                    check(id > 0)
                }
                val menus = LocalMenuRepository(db, MenuGenerator(Random(25)))
                val config = GenerationConfig(MealSlot.entries.take(3).associateWith { 1f },
                    excludeRecentlyUsed = false)
                measure("generate_menu_7_days_3_profiles", 11) {
                    check(menus.generate(1794787200000L, config) is GenerationResult.Success)
                }
                Log.i(TAG, "catalog_db_bytes=${context.getDatabasePath(name).length()}")
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun migrateCatalog() = runBlocking<Unit> {
        val name = "perf25-v8.db"
        try {
            measure("v8_to_v11_with_import", 5, 1, {
                context.deleteDatabase(name)
                migrations.createDatabase(name, 8).close()
            }) {
                withDatabase(name) {
                    check(it.ingredientDao().count() >= 900)
                    check(it.openHelper.writableDatabase.version == 11)
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun compareBaskets() = runBlocking<Unit> {
        val policy = ComparisonPolicy(now, ZoneOffset.UTC)
        for (shops in listOf(3, 6)) {
            val data = snapshot(shops)
            val stores = data.stores.map { it.id }.toSet()
            val retailers = data.retailers.map { it.id }.toSet()
            val evidence = data.reviewedMassContents().groupBy { it.match.ingredientKey }
            val allDemands = (0 until 100).associate { row ->
                row.toLong() to PriceDemand(id("food-$row"),
                    Quantity((100 + (row * 67) % 1600).toDouble(), UnitDefinition.GRAM, MassBasis.NET))
            }
            for (lines in listOf(10, 50, 100)) {
                val demands = allDemands.filterKeys { it < lines }
                measure("compare_${lines}_${shops}", 21) {
                    check(PriceComparisonEngine.compareBatch(demands, data, policy, data.prices, evidence)
                        .values.all { it.options.size == shops * 2 })
                }
                measure("single_${lines}_${shops}", 21) {
                    check(SingleStoreBasketOptimizer.optimize(demands, retailers, region, stores, data, policy)
                        .winner?.coveredCount == lines)
                }
                measure("multi_${lines}_${shops}", 21) {
                    val result = MultiStoreBasketOptimizer.optimize(demands, retailers, region, stores, data, policy, 3)
                    check(result.plan.complete && result.exact && result.evaluatedSubsets == if (shops == 3) 7 else 41)
                }
            }
        }
    }

    @Test fun priceCache() = runBlocking<Unit> {
        val name = "perf25-cache.db"
        try {
            context.deleteDatabase(name)
            val payload = instrumentation.context.assets.open("price-snapshot-v1.json").use { it.readBytes() }
            val metadata = JSONObject(payload.decodeToString())
            val manifest = JSONObject().apply {
                for (key in listOf("contractVersion", "regionId", "revision", "generatedAt", "expiresAt")) {
                    put(key, metadata.get(key))
                }
                put("byteLength", payload.size)
                put("sha256", MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) })
            }.toString().encodeToByteArray()
            withDatabase(name) { db ->
                val cache = RoomPriceSnapshotCache(db)
                check(cache.install(manifest, payload, now))
                measure("cache_read_parse", 31) { check(cache.read(region, now)?.revision == 1L) }
                Log.i(TAG, "cache_payload_bytes=${payload.size} process_pss_kib=${Debug.getPss()}")
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun snapshot(shops: Int): PriceSnapshot {
        val source = PriceSource(id("simulator"), "Simulator", SourceKind.OPEN_DATA, "test", "test")
        fun origin(key: String) = ProviderRecord(source.id, key)
        val retailers = List(shops) { Retailer(id("chain-$it"), origin("chain-$it"), "Chain $it") }
        val stores = retailers.mapIndexed { i, retailer ->
            Store(id("store-$i"), origin("store-$i"), retailer.id, "Store $i")
        }
        val products = List(100) { CommercialProduct(id("product-$it"), origin("product-$it"), "Product $it") }
        val packs = products.flatMapIndexed { i, product ->
            listOf(250, 500).map { grams ->
                val key = "format-$i-$grams"
                ProductPackage(id(key), origin(key), product.id, null, WeightMode.FIXED, SaleUnit.PACKAGE,
                    NetContent(grams.toBigDecimal(), ContentUnit.G), 1)
            }
        }
        val matches = packs.mapIndexed { i, pack ->
            IngredientProductMatch(origin("match-$i"), id("food-${i / 2}"), 1, pack.id, MatchKind.EXACT, Confidence.HIGH)
        }
        val prices = stores.flatMapIndexed { shop, store ->
            packs.mapIndexed { i, pack ->
                PriceObservation(ObservationRecord(origin("quote-$shop-$i"), 1, store.retailerId,
                    pack.id, ObservationScope.AtStore(store.id), now.atOffset(ZoneOffset.UTC).toLocalDate(),
                    now, Confidence.HIGH),
                    Money((79 + (i * 23 + shop * 31) % 450).toBigDecimal().movePointLeft(2), Currency.EUR),
                    SaleUnit.PACKAGE, PriceKind.REGULAR, null, null)
            }
        }
        return PriceSnapshot(1, region, 1, now.minusSeconds(600), now.plusSeconds(3600),
            listOf(source), retailers, stores, emptyList(), products, packs, matches, prices, emptyList())
    }

    private suspend fun measure(label: String, samples: Int, warmup: Int = 5,
        setup: () -> Unit = {}, block: suspend () -> Unit) {
        check(Looper.myLooper() != Looper.getMainLooper())
        val times = List(warmup + samples) {
            setup()
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }.drop(warmup)
        Log.i(TAG, "$label ms=${times.joinToString(",")}")
    }

    private fun id(value: String) = CommercialId(value)

    private suspend fun withDatabase(name: String, block: suspend (NutriNestDatabase) -> Unit) {
        val db = NutriNestDatabase.build(context, name)
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private companion object { const val TAG = "NutriPerf25" }
}
