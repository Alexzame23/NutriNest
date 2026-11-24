package com.zame.nutrinest.ui.stores

import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import kotlinx.coroutines.CancellationException
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
import com.zame.nutrinest.BuildConfig
import com.zame.nutrinest.data.prices.PriceDiagnostics
import com.zame.nutrinest.data.prices.RetailBatchPublication
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.isRetailNetworkFailure
import com.zame.nutrinest.domain.stores.GeoCoverage
import com.zame.nutrinest.ui.components.RetailPriceStatus
import com.zame.nutrinest.ui.components.RetailRefreshNotice
import com.zame.nutrinest.ui.components.RetailStatusMessage
import com.zame.nutrinest.ui.components.presentation
import com.zame.nutrinest.data.stores.ApproximateLocation
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ObservationScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext



@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupermarketScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val container = (context.applicationContext as NutriNestApp).container
    val location = remember(context) { ApproximateLocation(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()

    var history by remember { mutableStateOf(container.areaPreferences.recent) }
    var query by remember { mutableStateOf(history.firstOrNull().orEmpty()) }
    var radius by remember { mutableStateOf(5) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var radiusAvailable by remember { mutableStateOf(container.geoResolver.hasGeolocatedStores()) }
    var manualFromCache by remember { mutableStateOf(false) }
    var directoryFailure by remember { mutableStateOf<RetailPriceStatus?>(null) }
    var searching by remember { mutableStateOf(false) }
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

    suspend fun refreshDirectory(): Boolean = try {
        container.storeRepository.refresh()
        radiusAvailable = container.geoResolver.hasGeolocatedStores()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (error: Exception) {
        directoryFailure = if (error.isRetailNetworkFailure()) RetailPriceStatus.NetworkError
            else RetailPriceStatus.ProviderError
        false
    }

    fun locate() {
        searchJob?.cancel()
        searching = false
        manualResults = null
        directoryFailure = null
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        clearNearby()
        locationMessage = R.string.supermarkets_location_loading
        locationJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val point = location.locate()
            updatePermission()
            if (granted) {
                locationMessage = if (point == null) R.string.supermarkets_location_unavailable else null
                if (point != null && refreshDirectory() && location.hasPermission() && !radiusAvailable) {
                    locationMessage = R.string.geo_no_coordinates
                } else if (point != null && radiusAvailable && directoryFailure == null && location.hasPermission()) {
                    selectedId = null
                    nearbyResults = container.storeRepository.findNearby(point, radius.toDouble()).also {
                        PriceDiagnostics.log { "stage=discovery.radius before=${container.storeRepository.knownStores().size} after=${it.size}" }
                    }
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
    var revisionRequest by remember(selected) { mutableStateOf(0) }
    var publication by remember(selected) { mutableStateOf<RetailBatchPublication?>(null) }
    var priceStatus by remember(selected) { mutableStateOf(RetailPriceStatus.Loading) }
    var observedCoverage by remember(selected) { mutableStateOf<StorePriceCoverage?>(null) }
    LaunchedEffect(lifecycle, selected, revisionRequest) {
        if (selected == null || !BuildConfig.RETAIL_PRICES_ENABLED) return@LaunchedEffect
        val area = selected.area
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var expiry: Job? = null
            fun show(batch: RetailBatchPublication) {
                expiry?.cancel()
                expiry = launch {
                    while (Instant.now() < batch.snapshot.expiresAt) {
                        val coverage = withContext(Dispatchers.Default) {
                            batch.snapshot.coverageFor(selected, Instant.now())
                        }
                        observedCoverage = coverage
                        publication = batch
                        val deadline = coverage.nextChange ?: break
                        delay(Duration.between(Instant.now(), deadline).toMillis().coerceAtLeast(1))
                    }
                    publication = null
                    priceStatus = RetailPriceStatus.StaleData
                }
            }

            try {
                priceStatus = RetailPriceStatus.Loading
                publication = null
                val now = Instant.now()
                container.retailPrices.cached(area.postalCode, now)
                    ?.takeIf { it.snapshot.regionId == area.regionId }?.let(::show)
                when (val batch = container.retailPrices.load(area.postalCode, now)) {
                    is RetailBatchPublication -> if (batch.snapshot.regionId == area.regionId) {
                        show(batch)
                    } else {
                        expiry?.cancel()
                        publication = null
                        priceStatus = RetailPriceStatus.NoProviderCoverage
                    }
                    is RetailBatchUnavailable -> {
                        expiry?.cancel()
                        publication = null
                        priceStatus = batch.presentation()
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                expiry?.cancel()
                publication = null
                priceStatus = if (error.isRetailNetworkFailure()) RetailPriceStatus.NetworkError
                    else RetailPriceStatus.ProviderError
            }
            kotlinx.coroutines.awaitCancellation()
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
                    onValueChange = {
                        searchJob?.cancel()
                        searching = false
                        clearNearby()
                        manualResults = null
                        directoryFailure = null
                        query = it.take(80)
                    },
                    label = { Text(stringResource(R.string.supermarkets_manual_query)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(history) { postal ->
                TextButton(onClick = {
                    searchJob?.cancel()
                    searching = false
                    clearNearby()
                    manualResults = null
                    selectedId = null
                    directoryFailure = null
                    query = postal
                }) { Text(postal) }
            }
            item {
                Button(
                    enabled = !searching && (validSpanishPostalCode(query.trim()) ||
                        (query.isNotBlank() && query.none { it.isDigit() })),
                    onClick = {
                        clearNearby()
                        if (locationMessage == R.string.supermarkets_location_loading) locationMessage = null
                        searchJob?.cancel()
                        selectedId = null
                        manualResults = null
                        directoryFailure = null
                        val area = query.trim()
                        container.areaPreferences.select(area)
                        history = container.areaPreferences.recent
                        searching = true
                        searchJob = coroutineScope.launch {
                            try {
                                val refreshed = refreshDirectory()
                                val entries = container.storeRepository.findByArea(area)
                                val saved = if (entries.isEmpty() && validSpanishPostalCode(area))
                                    container.retailPrices.cached(area, Instant.now())?.localStores?.values
                                else null
                                manualFromCache = saved != null
                                if (refreshed || entries.isNotEmpty() || saved != null) {
                                    manualResults = saved?.map { NearbyStore(it, null) } ?: entries
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                directoryFailure = if (error.isRetailNetworkFailure()) RetailPriceStatus.NetworkError
                                    else RetailPriceStatus.ProviderError
                            } finally {
                                if (isActive) searching = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.supermarkets_search)) }
            }
            item {
                Column {
                    Text(stringResource(R.string.supermarkets_location_explainer))
                    if (radiusAvailable) Row {
                        listOf(5, 10, 25).forEach { km ->
                            TextButton(enabled = radius != km, onClick = {
                                radius = km
                                if (nearbyResults != null || locationJob != null) locate()
                            }) { Text("$km km") }
                        }
                    }
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
                if (searching) RetailStatusMessage(RetailPriceStatus.Loading)
                directoryFailure?.let {
                    if (it == RetailPriceStatus.ProviderError) RetailStatusMessage(it, R.string.geo_lookup_failed)
                    else RetailStatusMessage(it)
                }
                results?.let { rows ->
                    if (nearbyResults == null && manualFromCache) Text(stringResource(R.string.retail_cached))
                    container.geoResolver.coverage(rows).forEach { (provider, coverage) ->
                        if (nearbyResults != null || !manualFromCache || coverage == GeoCoverage.FUENTE_NO_LOCALIZABLE) {
                            val label = when (coverage) {
                                GeoCoverage.COBERTURA -> R.string.geo_directory_covered
                                GeoCoverage.SIN_COBERTURA -> R.string.geo_directory_uncovered
                                GeoCoverage.FUENTE_NO_LOCALIZABLE -> R.string.geo_directory_unlocatable
                            }
                            Text(stringResource(label, provider))
                        }
                    }
                }
                TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) {
                    Text(stringResource(R.string.supermarkets_map_attribution))
                }
                if (results?.isEmpty() == true) Text(stringResource(R.string.supermarkets_no_service))
                if (results == null && !searching && directoryFailure == null && locationJob == null) {
                    Text(stringResource(R.string.geo_choose_area))
                }
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
            if (selected != null && BuildConfig.RETAIL_PRICES_ENABLED) {
                item {
                    TextButton(onClick = { revisionRequest++ }) {
                        Text(stringResource(R.string.shopping_prices_refresh))
                    }
                    val batch = publication
                    val coverage = observedCoverage
                    if (batch == null || coverage == null) {
                        RetailStatusMessage(priceStatus)
                    } else {
                        val hasObservations = coverage.atStore.isNotEmpty() || coverage.declaredAreas.isNotEmpty()
                        RetailRefreshNotice(batch.fromCache && hasObservations, batch.refreshIssue)
                        if (batch.simulated) Text(stringResource(R.string.retail_simulated))
                        if (!batch.fromCache && !batch.simulated && hasObservations) {
                            RetailStatusMessage(RetailPriceStatus.LiveData)
                        }
                        if (coverage.hasStaleObservations && !hasObservations) {
                            RetailStatusMessage(RetailPriceStatus.StaleData)
                        }
                        PriceCoverage(coverage)
                    }
                }
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
                Text(listOf(entry.address, entry.area.postalCode, entry.area.municipality).filter { it.isNotBlank() }.joinToString(" · "))
                nearby.distanceKm?.let {
                    Text(stringResource(R.string.supermarkets_distance, it))
                }
            }
        }
        Text(stringResource(R.string.supermarkets_verified, displayDate(entry.area.verifiedOn)))
        entry.directoryUrl?.let { url ->
            TextButton(onClick = { uriHandler.openUri(url) }) { Text(stringResource(R.string.supermarkets_source_link, entry.store.name)) }
        }
    }
}

@Composable
private fun PriceCoverage(coverage: StorePriceCoverage) {
    if (BuildConfig.DEBUG) LaunchedEffect(coverage) {
        PriceDiagnostics.log {
            "stage=discovery.ui atStore=${coverage.atStore.sumOf { it.observations.size }} " +
                "declaredAreas=${coverage.declaredAreas.sumOf { it.observations.size }}"
        }
    }
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.supermarkets_price_coverage), style = MaterialTheme.typography.titleMedium)
        coverage.publishedAt?.let {
            Text(stringResource(R.string.supermarkets_snapshot_date, displayDate(it.atZone(ZoneId.systemDefault()).toLocalDate())))
        }
        if (coverage.atStore.isEmpty() && (!coverage.hasStaleObservations || coverage.declaredAreas.isNotEmpty())) {
            RetailStatusMessage(RetailPriceStatus.NoPricesInArea, R.string.supermarkets_no_store_prices)
        }
        coverage.atStore.forEach {
            CoverageGroup(stringResource(R.string.supermarkets_store_scope), it)
        }
        if (coverage.declaredAreas.isNotEmpty()) {
            Text(stringResource(R.string.supermarkets_declared_areas), style = MaterialTheme.typography.titleSmall)
            coverage.declaredAreas.forEach { group ->
                val scopeLabel = if (group.scope is ObservationScope.Online) R.string.supermarkets_online_scope
                    else R.string.supermarkets_zone_scope
                val label = group.zone?.let { stringResource(scopeLabel, it.name) }
                    ?: stringResource(R.string.supermarkets_online_unspecified)
                CoverageGroup(label, group)
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
    Text(stringResource(R.string.retail_source, group.source.name, group.source.attribution))
}

@Composable
private fun displayDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
