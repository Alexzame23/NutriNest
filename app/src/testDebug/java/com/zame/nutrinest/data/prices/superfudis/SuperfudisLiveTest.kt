package com.zame.nutrinest.data.prices.superfudis

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SuperfudisLiveTest {
    @Test
    fun readsPublicPricesForAUserBasket() {
        assumeTrue(System.getenv("NUTRINEST_SUPERFUDIS_LIVE") == "1")
        val query = System.getenv("NUTRINEST_SUPERFUDIS_QUERY") ?: "leche semidesnatada 1 l"
        val source: SpanishBasketPriceSource = SuperfudisPriceSource()
        val sample = try {
            runBlocking { source.compare(listOf(query), SuperfudisPriceSource.RETAILERS) }
        } catch (error: SuperfudisHttpException) {
            println("POST ${SuperfudisPriceSource.ENDPOINT} HTTP ${error.status}")
            throw error
        } catch (error: SocketTimeoutException) {
            println("POST ${SuperfudisPriceSource.ENDPOINT} timeout before a complete response")
            throw error
        }
        println("POST ${SuperfudisPriceSource.ENDPOINT} HTTP ${sample.status} providerStatus=${sample.providerStatus}")
        val offers = sample.items.flatMap { it.offers }
        val priced = offers.filter { it.priceEur != null }
        val unavailable = sample.items.flatMap { it.unavailableRetailers }.distinct()
        println("items=${sample.items.size} unresolved=${sample.unresolvedItems.size} pricedOffers=${priced.size} " +
            "retailers=${priced.map { it.retailer }.distinct()} unavailable=$unavailable")
        priced.firstOrNull()?.let { offer ->
            val product = offer.product
            println("example retailer=${offer.retailer} product=${product.name} brand=${product.brand} " +
                "ean=${product.ean} format=${product.format} units=${product.units} " +
                "unitSize=${product.unitSize} ${product.unitSizeUnit} totalVolume=${product.totalVolume} " +
                "price=${offer.priceEur} EUR comparison=${offer.eurPerUnit}/${offer.comparisonUnit} " +
                "match=${offer.matchType} priceDate=${offer.priceDate}")
        }
        println(sample.locationWarning)
        assertTrue("Expected at least one real price in the public response", priced.isNotEmpty())
    }
}
