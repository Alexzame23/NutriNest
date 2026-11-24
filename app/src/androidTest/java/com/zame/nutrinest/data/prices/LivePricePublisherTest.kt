package com.zame.nutrinest.data.prices

import androidx.room.Room
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
        fun open() = Room.databaseBuilder(app, NutriNestDatabase::class.java, name).build()
        try {
            val db = open()
            val downloaded = try {
                val container = AppContainer(app, db)
                assertNull(container.priceSnapshots.read(region, Instant.now()))
                val batch = container.retailPrices.load("28012", Instant.now()) as RetailBatchPublication
                assertFalse(batch.simulated)
                assertTrue(batch.localStores.isNotEmpty())
                batch.snapshot
            } finally {
                db.close()
            }
            assertEquals("open-prices", downloaded.sources.single().id.value)
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
