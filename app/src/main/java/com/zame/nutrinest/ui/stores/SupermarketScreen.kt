package com.zame.nutrinest.ui.stores

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.stores.ApproximateLocation
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.prices.PriceSnapshot
import com.zame.nutrinest.domain.stores.NearbyStore
import com.zame.nutrinest.domain.stores.ScopedPriceCoverage
import com.zame.nutrinest.domain.stores.StorePriceCoverage
import com.zame.nutrinest.domain.stores.coverageFor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NEARBY_RADIUS_KM = 5.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupermarketScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val container = (context.applicationContext as NutriNestApp).container
    val location = remember(context) { ApproximateLocation(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var manualResults by remember { mutableStateOf<List<NearbyStore>?>(null) }
    var nearbyResults by remember { mutableStateOf<List<NearbyStore>?>(null) }
    var selectedId by remember { mutableStateOf<CommercialId?>(null) }
    var granted by remember { mutableStateOf(location.hasPermission()) }
    var locationMessage by remember { mutableStateOf<Int?>(null) }
    var locationJob by remember { mutableStateOf<Job?>(null) }

    fun clearNearby() {
        locationJob?.cancel()
        locationJob = null
        if (nearbyResults != null) selectedId = null
        nearbyResults = null
    }

    fun updatePermission() {
        val allowed = location.hasPermission()
        if (granted && !allowed) {
            clearNearby()
            locationMessage = R.string.supermarkets_location_revoked
        }
        if (!granted && allowed) locationMessage = null
        granted = allowed
    }

    fun locate() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        clearNearby()
        locationMessage = R.string.supermarkets_location_loading
        locationJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val point = location.locate()
            updatePermission()
            if (granted) {
                locationMessage = if (point == null) R.string.supermarkets_location_unavailable else null
                if (point != null) {
                    selectedId = null
                    nearbyResults = container.storeRepository.findNearby(point, NEARBY_RADIUS_KM)
                }
            }
            locationJob = null
        }
        locationJob?.start()
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        updatePermission()
        if (granted) locate() else locationMessage = R.string.supermarkets_location_denied
    }

    DisposableEffect(lifecycle, location) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> updatePermission()
                Lifecycle.Event.ON_STOP -> {
                    if (locationJob != null) {
                        locationJob?.cancel()
                        locationJob = null
                        locationMessage = null
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val results = nearbyResults ?: manualResults
    val selected = results?.firstOrNull { it.entry.store.id == selectedId }?.entry
    val regionId = selected?.area?.regionId
    var snapshot by remember { mutableStateOf<PriceSnapshot?>(null) }
    LaunchedEffect(lifecycle, regionId) {
        snapshot = null
        if (regionId == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                val now = Instant.now()
                val cached = container.priceSnapshots.read(regionId, now)
                snapshot = cached
                if (cached != null) {
                    delay(Duration.between(now, cached.expiresAt).toMillis().coerceAtLeast(1))
                    snapshot = null
                }
                awaitCancellation()
            } finally {
                snapshot = null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.supermarkets_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = { Text(stringResource(R.string.supermarkets_manual_query)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    enabled = query.isNotBlank(),
                    onClick = {
                        clearNearby()
                        if (locationMessage == R.string.supermarkets_location_loading) locationMessage = null
                        manualResults = container.storeRepository.findByArea(query)
                        selectedId = null
                    }
                ) { Text(stringResource(R.string.supermarkets_search)) }
            }
            item {
                Column {
                    Text(stringResource(R.string.supermarkets_location_explainer))
                    TextButton(
                        enabled = locationJob == null,
                        onClick = {
                            updatePermission()
                            if (granted) locate() else requestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    ) { Text(stringResource(R.string.supermarkets_use_location)) }
                    locationMessage?.let { Text(stringResource(it)) }

                    if (nearbyResults != null || locationJob != null) {
                        TextButton(onClick = {
                            clearNearby()
                            locationMessage = null
                        }) { Text(stringResource(R.string.supermarkets_clear_location)) }
                    }
                    if (granted || locationMessage == R.string.supermarkets_location_denied) {
                        TextButton(onClick = {
                            clearNearby()
                            if (granted) locationMessage = null
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ))
                        }) { Text(stringResource(R.string.supermarkets_permission_settings)) }
                    }
                }
            }
            item {
                Text(stringResource(R.string.supermarkets_pilot_coverage))
                TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
                    Text(stringResource(R.string.supermarkets_map_attribution))
                }
                if (results?.isEmpty() == true) Text(stringResource(R.string.supermarkets_no_service))
                if (!results.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.supermarkets_choose_store),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            items(results.orEmpty(), key = { it.entry.store.id.value }) { nearby ->
                StoreOption(nearby, selectedId == nearby.entry.store.id) {
                    selectedId = nearby.entry.store.id
                }
            }
            if (selected != null) {
                item { PriceCoverage(snapshot.coverageFor(selected, Instant.now())) }
            }
        }
    }
}

@Composable
private fun StoreOption(nearby: NearbyStore, selected: Boolean, onSelect: () -> Unit) {
    val entry = nearby.entry
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(entry.store.name, style = MaterialTheme.typography.titleMedium)
                Text("${entry.address} · ${entry.area.postalCode} ${entry.area.municipality}")
                nearby.distanceKm?.let {
                    Text(stringResource(R.string.supermarkets_distance, it))
                }
            }
        }
        Text(stringResource(R.string.supermarkets_verified, displayDate(entry.area.verifiedOn)))
        TextButton(onClick = { uriHandler.openUri(entry.directoryUrl) }) {
            Text(stringResource(R.string.supermarkets_source_link, entry.store.name))
        }
    }
}

@Composable
private fun PriceCoverage(coverage: StorePriceCoverage) {
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.supermarkets_price_coverage), style = MaterialTheme.typography.titleMedium)
        coverage.publishedAt?.let {
            Text(stringResource(R.string.supermarkets_snapshot_date, displayDate(it.atZone(ZoneId.systemDefault()).toLocalDate())))
        }
        if (coverage.atStore.isEmpty()) {
            Text(stringResource(R.string.supermarkets_no_store_prices))
        } else {
            coverage.atStore.forEach {
                CoverageGroup(stringResource(R.string.supermarkets_store_scope), it)
            }
        }
        if (coverage.declaredAreas.isNotEmpty()) {
            Text(stringResource(R.string.supermarkets_declared_areas), style = MaterialTheme.typography.titleSmall)
            coverage.declaredAreas.forEach { group ->
                val scopeLabel = if (group.scope is ObservationScope.Online) R.string.supermarkets_online_scope
                    else R.string.supermarkets_zone_scope
                CoverageGroup(stringResource(scopeLabel, requireNotNull(group.zone).name), group)
            }
        }
        Text(stringResource(R.string.supermarkets_stock_unverified))
    }
}

@Composable
private fun CoverageGroup(label: String, group: ScopedPriceCoverage) {
    val dates = group.observations.map { it.record.observedOn }
    val from = displayDate(dates.min())
    val to = displayDate(dates.max())
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(if (from == to) stringResource(R.string.supermarkets_observed_on, from)
        else stringResource(R.string.supermarkets_observed_range, from, to))
    Text(group.source.attribution)
}

@Composable
private fun displayDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
