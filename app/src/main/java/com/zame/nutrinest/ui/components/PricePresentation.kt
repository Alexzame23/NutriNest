package com.zame.nutrinest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.zame.nutrinest.R
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Currency

@Composable
internal fun PriceLoading() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_500)
        slow = true
    }
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(if (slow) R.string.retail_slow else R.string.retail_loading))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun PriceMetadata(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PriceAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )) { Text(label) }
}

@Composable
internal fun PriceLink(onClick: () -> Unit, label: String) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface
    )) { Text(label) }
}

@Composable
internal fun rememberEuroFormat(fractionDigits: Int = 2): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, fractionDigits) {
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("EUR")
            maximumFractionDigits = fractionDigits
        }
    }
}
