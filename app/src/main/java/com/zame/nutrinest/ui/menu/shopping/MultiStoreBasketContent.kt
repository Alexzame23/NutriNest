package com.zame.nutrinest.ui.menu.shopping

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.components.RetailOfferDetails
import com.zame.nutrinest.ui.components.RetailObservationMetadata
import com.zame.nutrinest.ui.components.rememberEuroFormat
import com.zame.nutrinest.domain.prices.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun MultiStoreBasketContent(request: ShoppingBasketRequest, names: Map<Long, String>) {
    val catalog = request.catalog
    val region = catalog.snapshot.regionId.value
    val stores = remember(request) { catalog.snapshot.stores.filter {
        it.id in request.stores
    }.sortedBy { it.id.value } }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var maximum by rememberSaveable { mutableStateOf("2") }
    var travel by rememberSaveable { mutableStateOf(false) }
    var fees by rememberSaveable(region) { mutableStateOf(Bundle()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.multi_title)) }
        if (!expanded) return@Column
        OutlinedTextField(maximum, { maximum = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.multi_limit)) })
        Selection(stringResource(R.string.multi_travel), travel) { travel = it }
        val costs = mutableMapOf<CommercialId, VerifiedVisitCost>()
        var invalidFee = false
        for (store in stores) {
            if (travel) {
                val value = fees.getString(store.id.value).orEmpty()
                OutlinedTextField(value, { input -> fees = Bundle(fees).apply { putString(store.id.value, input) } },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.multi_fee, store.name)) })
                val evidenceKey = "source:${store.id.value}"
                val evidence = fees.getString(evidenceKey).orEmpty()
                OutlinedTextField(evidence, { input -> fees = Bundle(fees).apply { putString(evidenceKey, input) } },
                    singleLine = true, label = { Text(stringResource(R.string.multi_evidence)) })
                if (evidence.isNotBlank() && value.matches(Regex("[0-9]{1,8}([.,][0-9]{1,2})?"))) {
                    costs[store.id] = VerifiedVisitCost(value.replace(',', '.').toBigDecimal(), evidence.trim())
                } else if (value.isNotEmpty() || evidence.isNotBlank()) {
                    invalidFee = true
                }
            }
        }
        val limit = maximum.toIntOrNull()
        val ids = request.stores
        key(region, maximum, travel, fees) {
            var run by rememberSaveable { mutableStateOf(false) }
            Button(onClick = { run = true }, enabled = !run && limit != null && limit > 0 &&
                ids.isNotEmpty() && !invalidFee) {
                Text(stringResource(if (travel) R.string.multi_confirm else R.string.multi_compare))
            }
            if (run && limit != null && ids.isNotEmpty() && !invalidFee) key(request) {
                val result by produceState<MultiStoreBasketResult?>(null) {
                    value = withContext(Dispatchers.Default) {
                        MultiStoreBasketOptimizer.optimize(catalog.demands.filterKeys { it in request.itemIds },
                            request.retailers, catalog.snapshot.regionId, ids, catalog.snapshot,
                            catalog.policy.copy(now = request.now), limit, costs.takeIf { travel },
                            checkCancellation = { ensureActive() })
                    }
                }
                result?.let { MultiStoreSummary(it, names, catalog.snapshot) } ?: Text(stringResource(R.string.shopping_basket_loading))
            }
        }
    }
}

@Composable
private fun Selection(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, Modifier.padding(8.dp))
    }
}

@Composable
private fun MultiStoreSummary(result: MultiStoreBasketResult, names: Map<Long, String>, snapshot: PriceSnapshot) {
    val money = rememberEuroFormat()
    val plan = result.plan
    val sources = remember(snapshot) { snapshot.sources.associateBy { it.id } }
    val retailers = remember(snapshot) { snapshot.retailers.associateBy { it.id } }
    val covered = plan.lines.sumOf { it.itemIds.size }
    Text(stringResource(if (result.exact) R.string.multi_exact else R.string.multi_heuristic))
    if (plan.travelMode == TravelMode.MISSING_COSTS) Text(stringResource(R.string.multi_missing_costs))
    Text(stringResource(R.string.multi_coverage, covered, covered + plan.missing.size))
    plan.checkoutEur?.let { Text(stringResource(R.string.multi_checkout, money.format(it)),
        style = MaterialTheme.typography.titleMedium) }
    plan.totalWithVisitsEur?.let { Text(stringResource(R.string.multi_total, money.format(it))) }
    val reference = result.singleStoreReference
    if (reference != null) {
        val visit = reference.visits.single()
        Text(stringResource(R.string.multi_reference, visit.store.name))
        Text(stringResource(R.string.multi_gross, money.format(result.grossSavingsEur)))
        visit.purchases.map { it.price }.distinctBy { it.record.origin.sourceId to it.record.observedOn }
            .forEach { RetailObservationMetadata(it, sources.getValue(it.record.origin.sourceId)) }
    } else Text(stringResource(R.string.multi_no_reference))
    Text(result.netSavingsEur?.let { stringResource(R.string.multi_net, money.format(it)) }
        ?: stringResource(R.string.multi_net_unknown))
    for (visit in plan.visits) {
        Text(stringResource(R.string.multi_shop, visit.store.name, money.format(visit.checkoutEur)),
            style = MaterialTheme.typography.titleSmall)
        visit.fee?.let {
            Text(stringResource(R.string.multi_visit_cost, money.format(it.eur)))
            Text(stringResource(R.string.multi_visit_cost_source, it.evidence))
        }
        for (purchase in visit.purchases) {
            val source = sources.getValue(purchase.price.record.origin.sourceId)
            RetailOfferDetails(purchase.product, purchase.pack, purchase.price,
                retailers.getValue(visit.store.retailerId).name, visit.store.name, visit.store.postalCode,
                null, source, simulated = source.id.value == "fake-prices")
            Text(stringResource(R.string.shopping_prices_checkout, purchase.count.toString(),
                money.format(purchase.checkoutEur)))
        }
    }
    if (plan.lines.any { it.hasUnconfirmedStock }) Text(stringResource(R.string.shopping_basket_stock_unconfirmed))
    for (item in plan.missing) {
        val reasons = item.reasons.sorted().map { stringResource(it.label()) }.joinToString(" · ")
        Text(stringResource(R.string.shopping_basket_missing, names[item.itemId].orEmpty(), reasons))
    }
}
