package com.zame.nutrinest.data.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ProviderRecord
import com.zame.nutrinest.domain.prices.Store
import com.zame.nutrinest.domain.stores.DirectoryStore
import com.zame.nutrinest.domain.stores.DiscoveryArea
import com.zame.nutrinest.domain.stores.GeoPoint
import com.zame.nutrinest.domain.stores.LocalStoreRepository
import java.time.LocalDate

internal object PilotStoreDirectory {
    private val area = DiscoveryArea(
        CommercialId("es-madrid-28012"), "Madrid", "28012", LocalDate.of(2026, 11, 18)
    )
    private val directory = CommercialId("carrefour-directory")

    val repository = LocalStoreRepository(
        listOf(
            entry("lavapies", "Carrefour Market Lavapiés", "Calle Valencia, 2",
                GeoPoint(40.40828, -3.70102), "622204638", "lavapies_-_s.aspx"),
            entry("tirso-de-molina", "Carrefour Market Tirso de Molina", "Plaza Tirso de Molina, 15",
                GeoPoint(40.41266, -3.70493), "6918727592", "tirso-de-molina-madrid_-_s.aspx")
        )
    )

    private fun entry(
        key: String,
        name: String,
        address: String,
        point: GeoPoint,
        osmNode: String,
        page: String
    ) = DirectoryStore(
        Store(CommercialId("carrefour-directory.$key"), ProviderRecord(directory, key),
            CommercialId("carrefour"), name, area.postalCode, "node/$osmNode"),
        area, address, point,
        "https://www.carrefour.es/tiendas-carrefour/supermercados/carrefour-market/$page"
    )
}
