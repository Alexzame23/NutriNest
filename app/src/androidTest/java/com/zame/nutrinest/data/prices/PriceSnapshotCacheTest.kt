package com.zame.nutrinest.data.prices

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.prices.PromotionEligibility
import com.zame.nutrinest.domain.prices.StockState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PriceSnapshotCacheTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val region = CommercialId("test-region")
    private val now = Instant.parse("2026-11-18T13:00:00Z")
    private val expiry = Instant.parse("2026-11-20T12:00:00Z")
    private val fixture: String
        get() = instrumentation.context.assets.open("price-snapshot-v1.json").bufferedReader().use { it.readText() }

    @get:Rule
    val migration = MigrationTestHelper(
        instrumentation, NutriNestDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun decodesPublicJsonWithoutConflatingPriceAndStock() {
        val snapshot = SnapshotJson.snapshot(fixture.toByteArray())
        assertEquals("1.2345", snapshot.prices.first().money.amount.toPlainString())
        assertEquals("00036000291452", snapshot.packages.first().gtin?.normalized)
        assertEquals("08001", snapshot.stores.single().postalCode)
        assertEquals(2, snapshot.packages.first().unitsPerPackage)
        assertEquals("fusilli", snapshot.products.first().attributes["format"])
        assertNull(snapshot.prices.first().record.observedAt)
        assertEquals(PromotionEligibility.LOYALTY, snapshot.prices.last().eligibility)
        assertTrue(snapshot.prices.last().record.scope is ObservationScope.Online)
        assertEquals(StockState.OUT_OF_STOCK, snapshot.availability.single().state)
        assertEquals(snapshot.prices.first().record.origin, snapshot.availability.single().record.origin)
        assertEquals(region, SnapshotJson.manifest(manifest(fixture.toByteArray())).regionId)
    }

    @Test
    fun rejectsInvalidFormatsReferencesAndDuplicateRecords() {
        val json = fixture
        val invalid = listOf(
            json.replace("\"1.2345\"", "1.2345"),
            json.replace("\"1.2345\"", "\"1e1\""),
            json.replace("\"per\":\"PACKAGE\"", "\"per\":\"KG\""),
            json.replace("\"retailerId\":\"retailer-a\",\"packageId\"", "\"retailerId\":\"retailer-b\",\"packageId\""),
            json.replace("\"recordKey\":\"offer-b\"", "\"recordKey\":\"offer-a\""),
            json.replace("\"storeId\":\"store-a\"", "\"storeId\":\"store-a\",\"zoneId\":\"zone-a\""),
            json.replace("\"contractVersion\": 1", "\"contractVersion\": 2"),
            json.replace("\"regionId\": \"test-region\"", "\"regionId\": \"test-region\", \"regionId\": \"other\"")
        )
        for (example in invalid) {
            assertFalse("Fixture mutation must change the input", example == json)
            assertThrows(IllegalArgumentException::class.java) { SnapshotJson.snapshot(example.toByteArray()) }
        }
        assertThrows(IOException::class.java) {
            SnapshotJson.snapshot(json.replace("\"regionId\"", "regionId").toByteArray())
        }
    }

    @Test
    fun validatesBeforeAtomicReplacementAndPreservesRevisionOnWithdrawal() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, NutriNestDatabase::class.java).build()
        try {
            val cache = RoomPriceSnapshotCache(db)
            val first = fixture.toByteArray()
            val second = fixture.replace("\"revision\": 1", "\"revision\": 2").toByteArray()
            assertNull(cache.read(region, now))
            assertTrue(cache.install(manifest(first), first, now))
            assertFalse(cache.install(manifest(first), first, now))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { cache.install(manifest(first), first.copyOf().also { it[0] = 0 }, now) }
            }
            assertEquals(1L, cache.read(region, now)?.revision)
            assertTrue(cache.install(manifest(second, 2), second, now))
            assertFalse(cache.install(manifest(first), first, now))
            val changed = second.toString(Charsets.UTF_8).replace("1.2345", "1.2346").toByteArray()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { cache.install(manifest(changed, 2), changed, now) }
            }
            cache.withdraw(region, 1)
            assertEquals(2L, cache.read(region, now)?.revision)
            cache.withdraw(region, 2)
            assertNull(cache.read(region, now))
            assertFalse(cache.install(manifest(second, 2), second, now))
            val third = fixture.replace("\"revision\": 1", "\"revision\": 3").toByteArray()
            assertTrue(cache.install(manifest(third, 3), third, now))
            assertNull(cache.read(region, expiry))
            assertEquals(1, cache.pruneExpired(expiry))
            assertNull(db.priceSnapshotDao().find(region.value)?.payload)
            assertEquals(3L, db.priceSnapshotDao().find(region.value)?.revision)
        } finally {
            db.close()
        }
    }

    @Test
    fun upgrade10To11KeepsExistingRowsAndCreatesEmptyIndexedCache() {
        val name = "price-cache-migration-test"
        migration.createDatabase(name, 10).use { db ->
            db.execSQL("INSERT INTO food_dataset_meta (`key`, value) VALUES ('fixture', 'existing')")
        }
        migration.runMigrationsAndValidate(name, 11, true).use { db ->
            db.query("SELECT value FROM food_dataset_meta WHERE `key`='fixture'").use {
                assertTrue(it.moveToFirst())
                assertEquals("existing", it.getString(0))
            }
            db.query("SELECT COUNT(*) FROM price_snapshot_cache").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("EXPLAIN QUERY PLAN SELECT * FROM price_snapshot_cache WHERE regionId='test-region'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(3).contains("INDEX"))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test
    fun applicationProvidesTheLocalReadInterface() {
        val app = instrumentation.targetContext.applicationContext as com.zame.nutrinest.NutriNestApp
        assertNotNull(app.container.priceSnapshots)
    }

    private fun manifest(bytes: ByteArray, revision: Long = 1): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return """
            {"contractVersion":1,"regionId":"test-region","revision":$revision,
             "generatedAt":"2026-11-18T12:00:00Z","expiresAt":"2026-11-20T12:00:00Z",
             "sha256":"$digest","byteLength":${bytes.size}}
        """.trimIndent().toByteArray()
    }
}
