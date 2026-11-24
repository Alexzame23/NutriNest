package com.zame.nutrinest.data.prices

import com.zame.nutrinest.data.local.dao.RetailCanonicalRow
import com.zame.nutrinest.domain.prices.CommercialId
import org.json.JSONObject

internal data class RetailIngredientKeys(val legacy: CommercialId?, val canonical: CommercialId? = null) {
    fun select(published: Set<CommercialId>): CommercialId? = canonical?.takeIf { it in published } ?: legacy
}

/** Local canonical UUIDs differ across installations. Bind only the reviewed global catalog revision. */
internal class RetailCanonicalBindings(json: JSONObject) {
    private val revision = json.getString("revision")
    private val sourceHash = json.getString("sourceSha256")
    private val bindings = json.getJSONArray("profiles").let { profiles ->
        (0 until profiles.length()).associate { index ->
            val profile = profiles.getJSONObject(index)
            profile.getString("conceptKey") to (profile.getString("name") to CommercialId(profile.getString("canonicalIngredientId")))
        }
    }

    fun resolve(row: RetailCanonicalRow): RetailIngredientKeys? {
        val binding = bindings[row.conceptKey]
        val canonical = binding?.second?.takeIf {
            revision == row.rulesVersion && sourceHash == row.sourceHash && binding.first == row.canonicalName
        }
        val legacy = row.legacyRecordKey?.takeIf { it.matches(Regex("[0-9]{1,32}")) }?.let { CommercialId("bedca.$it") }
        return if (canonical == null && legacy == null) null else RetailIngredientKeys(legacy, canonical)
    }
}
