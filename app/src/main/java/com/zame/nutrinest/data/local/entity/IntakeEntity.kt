package com.zame.nutrinest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something a person ate that the plan didn't put there — the office birthday
 * cake, a beer, a handful of almonds.
 *
 * Logging one rebalances the rest of *that person's* day: their remaining
 * planned meals are re-portioned so the daily target still adds up. Free slots
 * are left out of the rebalancing (they are untracked by definition) and
 * nobody else's menu moves.
 *
 * [dayStart] is the epoch millis of local midnight, the same convention
 * `weekStart` uses for weeks, so a day's entries are one indexed lookup.
 */
@Entity(
    tableName = "intake_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId", "dayStart"])]
)
data class IntakeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dayStart: Long,
    val label: String,
    /** Set when the entry came from an ingredient rather than free text. */
    val grams: Double? = null,
    @Embedded val nutrition: NutritionValues,
    val loggedAt: Long = System.currentTimeMillis()
)
