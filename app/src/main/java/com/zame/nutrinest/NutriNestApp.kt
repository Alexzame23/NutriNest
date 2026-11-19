package com.zame.nutrinest

import android.app.Application
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.prices.PriceSnapshotReader
import com.zame.nutrinest.data.prices.RoomPriceSnapshotCache
import com.zame.nutrinest.data.prices.HttpsRetailPriceProvider
import com.zame.nutrinest.data.prices.RegionalRetailPriceRepository
import com.zame.nutrinest.data.prices.RegionalShoppingPriceRepository
import com.zame.nutrinest.data.stores.PilotStoreDirectory
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
import com.zame.nutrinest.domain.prices.CommercialId

class AppContainer(app: Application) {
    private val database = NutriNestDatabase.get(app)

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

    val priceSnapshots: PriceSnapshotReader = RoomPriceSnapshotCache(database)

    internal val retailerSelectionRepository = RetailerSelectionRepository(app)

    val storeRepository: StoreRepository = PilotStoreDirectory.repository

    private suspend fun retailKeys(ids: Set<Long>): Map<Long, CommercialId> =
        ids.chunked(900).flatMap { database.ingredientDao().retailBedcaRecordKeys(it) }
            .mapNotNull { row ->
                row.recordKey.takeIf { it.matches(Regex("[0-9]{1,32}")) }
                    ?.let { row.ingredientId to CommercialId("bedca.$it") }
            }.toMap()

    internal val retailPrices by lazy {
        @Suppress("DEPRECATION")
        val baseUrl = app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.zame.nutrinest.RETAIL_PRICE_BASE_URL").orEmpty().trim()
        RegionalRetailPriceRepository(
            ingredientKey = { id -> retailKeys(setOf(id))[id] },
            stores = storeRepository,
            provider = baseUrl.takeIf(String::isNotBlank)?.let(::HttpsRetailPriceProvider),
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
