package com.zame.nutrinest

import android.app.Application
import android.util.Log
import com.zame.nutrinest.data.prices.PriceDiagnostics
import com.zame.nutrinest.data.prices.configurationDiagnosis
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.prices.PriceSnapshotReader
import com.zame.nutrinest.data.prices.RoomPriceSnapshotCache
import com.zame.nutrinest.data.prices.HttpsRetailPriceProvider
import com.zame.nutrinest.data.prices.RegionalRetailPriceRepository
import com.zame.nutrinest.data.prices.RegionalShoppingPriceRepository
import com.zame.nutrinest.domain.stores.GeoResolver
import com.zame.nutrinest.data.stores.AreaPreferences
import com.zame.nutrinest.data.stores.RetailerSelectionRepository
import com.zame.nutrinest.data.repository.FoodReferenceRepository
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.LocalFoodReferenceRepository
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.data.repository.LocalMenuRepository
import com.zame.nutrinest.data.repository.LocalProfileRepository
import com.zame.nutrinest.data.repository.LocalRecipeRepository
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.ProfileRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.notifications.MenuReminderWorker
import com.zame.nutrinest.domain.stores.StoreRepository
import com.zame.nutrinest.data.prices.RetailCanonicalBindings
import com.zame.nutrinest.data.prices.RetailIngredientKeys
import org.json.JSONObject

class AppContainer(app: Application, private val database: NutriNestDatabase = NutriNestDatabase.get(app)) {
    val ingredientRepository: IngredientRepository =
        LocalIngredientRepository(database)

    val foodReferenceRepository: FoodReferenceRepository =
        LocalFoodReferenceRepository(database.foodReferenceDao())

    val recipeRepository: RecipeRepository =
        LocalRecipeRepository(database.recipeDao())

    val profileRepository: ProfileRepository =
        LocalProfileRepository(database.profileDao())

    val menuRepository: MenuRepository =
        LocalMenuRepository(database)

    private val priceCache = RoomPriceSnapshotCache(database)
    val priceSnapshots: PriceSnapshotReader = priceCache

    internal val retailerSelectionRepository = RetailerSelectionRepository(app)

    internal val areaPreferences = AreaPreferences(app)
    private val publisher by lazy {
        baseUrl.takeIf { BuildConfig.RETAIL_PRICES_ENABLED && it.isNotBlank() }?.let { HttpsRetailPriceProvider(it, priceCache) }
    }
    internal val publisherConfigured get() = publisher != null
    internal val geoResolver = GeoResolver({ checkNotNull(publisher) { "Price publisher unavailable" }.directory() })
    val storeRepository: StoreRepository = geoResolver

    private val retailBindings by lazy {
        app.assets.open("retail-canonical-profiles.json").bufferedReader().use {
            RetailCanonicalBindings(JSONObject(it.readText()))
        }
    }

    private suspend fun retailKeys(ids: Set<Long>): Map<Long, RetailIngredientKeys> =
        ids.chunked(900).flatMap { database.ingredientDao().retailCanonicalRows(it) }
            .mapNotNull { row -> retailBindings.resolve(row)?.let { row.ingredientId to it } }.toMap()

    private val baseUrl by lazy {
        @Suppress("DEPRECATION")
        app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.zame.nutrinest.RETAIL_PRICE_BASE_URL").orEmpty().trim()
    }

    internal val priceConfiguration get() = configurationDiagnosis(BuildConfig.RETAIL_PRICES_ENABLED, baseUrl.isNotBlank())

    init {
        if (BuildConfig.DEBUG) {
            PriceDiagnostics.sink = { Log.d("NutriNestPrices", it) }
            PriceDiagnostics.log {
                "stage=config enabled=${BuildConfig.RETAIL_PRICES_ENABLED} hasBaseUrl=${baseUrl.isNotBlank()} " +
                    "provider=${if (priceConfiguration == null) "HttpsRetailPriceProvider" else "none"} " +
                    "state=${priceConfiguration ?: "CONFIGURED"}"
            }
        }
    }

    internal val retailPrices by lazy {
        RegionalRetailPriceRepository(
            ingredientKey = { id -> retailKeys(setOf(id))[id] },
            stores = storeRepository,
            rememberArea = areaPreferences::select,
            provider = publisher,
            pricesEnabled = BuildConfig.RETAIL_PRICES_ENABLED,
            hasNetwork = { app.getSystemService(ConnectivityManager::class.java).activeNetwork != null },
            selection = { retailerSelectionRepository.preferences.value }
        )
    }

    internal val shoppingPrices by lazy {
        RegionalShoppingPriceRepository(retailPrices::load, ::retailKeys)
    }
}

class NutriNestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MenuReminderWorker.schedule(this)
    }
}
