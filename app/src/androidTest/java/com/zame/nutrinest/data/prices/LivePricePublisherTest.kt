package com.zame.nutrinest.data.prices

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.AppContainer
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.prices.CommercialId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LivePricePublisherTest {
    @Test fun downloadsVerifiedPublicationAndReadsTheSameBytesFromRoom() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("livePublisher") == "true")
        val expected = requireNotNull(args.getString("snapshotSha256"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NutriNestApp
        val region = CommercialId("es-madrid-28012")
        val name = "live-publisher-${UUID.randomUUID()}.db"
        fun open() = NutriNestDatabase.build(app, name)
        try {
            val db = open()
            val downloaded = try {
                val container = AppContainer(app, db)
                assertNull(container.priceSnapshots.read(region, Instant.now()))
                val batch = container.retailPrices.load("28012", Instant.now()) as RetailBatchPublication
                val geo = container.geoResolver
                val samples = args.getString("geoPostcodes") ?: "28012,15001,41001"
                for (cp in samples.split(",")) {
                    val rows = geo.findByArea(cp)
                    val expectedCoverage = if (batch.snapshot.stores.any { it.postalCode == cp })
                        com.zame.nutrinest.domain.stores.GeoCoverage.COBERTURA
                    else com.zame.nutrinest.domain.stores.GeoCoverage.SIN_COBERTURA
                    assertEquals(expectedCoverage, geo.coverage(rows)["open-prices"])
                    assertEquals(com.zame.nutrinest.domain.stores.GeoCoverage.FUENTE_NO_LOCALIZABLE,
                        geo.coverage(rows)["Superfudis"])
                }
                assertFalse(batch.simulated)
                assertTrue(batch.localStores.isNotEmpty())
                val sql = db.openHelper.readableDatabase
                fun localId(concept: String) = sql.query("""
                    SELECT m.ingredient_id FROM ingredient_canonical_map m JOIN canonical_ingredients c
                    ON c.canonical_id=m.canonical_id WHERE c.concept_key=? AND c.scope_key='global'
                """, arrayOf(concept)).use { check(it.moveToFirst()); it.getLong(0) }
                val offers = container.retailPrices.compare(localId("bedca:950"), "28012", Instant.now()) as RetailResult.Offers
                val offer = offers.rows.single { it.price.record.origin.recordKey == "357963" }
                assertEquals("nn.yogurt.drinking.flavoured", offer.match.canonicalIngredientId?.value)
                assertEquals("CULINARY_EQUIVALENT", offer.match.classification?.name)
                assertEquals(RetailResult.NoMatches, container.retailPrices.compare(localId("bedca:2538"), "28012", Instant.now()))
                batch.snapshot
            } finally {
                db.close()
            }
            assertEquals(setOf("open-prices", "nutrinest-editorial"), downloaded.sources.map { it.id.value }.toSet())
            assertTrue(downloaded.prices.isNotEmpty())
            assertTrue(downloaded.prices.all { it.record.origin.sourceId.value == "open-prices" })
            val reopened = open()
            try {
                val row = requireNotNull(reopened.priceSnapshotDao().find(region.value))
                val digest = MessageDigest.getInstance("SHA-256").digest(requireNotNull(row.payload))
                assertEquals(expected, digest.joinToString("") { "%02x".format(it) })
                assertEquals(expected, row.sha256)
                assertEquals(downloaded, RoomPriceSnapshotCache(reopened).read(region, Instant.now()))
            } finally {
                reopened.close()
            }
        } finally {
            app.deleteDatabase(name)
        }
    }
}
