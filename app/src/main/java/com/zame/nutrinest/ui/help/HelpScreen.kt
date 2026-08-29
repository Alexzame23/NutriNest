package com.zame.nutrinest.ui.help

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R

private data class HelpTopic(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    @param:StringRes val bodyRes: Int
)

private val helpTopics = listOf(
    HelpTopic(Icons.Rounded.AccountCircle, R.string.help_profiles_title, R.string.help_profiles_summary, R.string.help_profiles_body),
    HelpTopic(Icons.Rounded.Restaurant, R.string.help_ingredients_title, R.string.help_ingredients_summary, R.string.help_ingredients_body),
    HelpTopic(Icons.AutoMirrored.Rounded.MenuBook, R.string.help_recipes_title, R.string.help_recipes_summary, R.string.help_recipes_body),
    HelpTopic(Icons.Rounded.Tune, R.string.help_configuration_title, R.string.help_configuration_summary, R.string.help_configuration_body),
    HelpTopic(Icons.Rounded.CalendarMonth, R.string.help_generation_title, R.string.help_generation_summary, R.string.help_generation_body),
    HelpTopic(Icons.Rounded.FilterAlt, R.string.help_selection_title, R.string.help_selection_summary, R.string.help_selection_body),
    HelpTopic(Icons.Rounded.Lock, R.string.help_locked_title, R.string.help_locked_summary, R.string.help_locked_body),
    HelpTopic(Icons.Rounded.Checklist, R.string.help_free_meals_title, R.string.help_free_meals_summary, R.string.help_free_meals_body),
    HelpTopic(Icons.Rounded.SwapHoriz, R.string.help_replacement_title, R.string.help_replacement_summary, R.string.help_replacement_body),
    HelpTopic(Icons.Rounded.ShoppingCart, R.string.help_shopping_title, R.string.help_shopping_summary, R.string.help_shopping_body),
    HelpTopic(Icons.Rounded.Notifications, R.string.help_notifications_title, R.string.help_notifications_summary, R.string.help_notifications_body),
    HelpTopic(Icons.Rounded.Language, R.string.help_language_title, R.string.help_language_summary, R.string.help_language_body)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTopics by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.help_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") { HelpIntroCard() }

            items(helpTopics, key = { it.titleRes }) { topic ->
                val expanded = topic.titleRes in expandedTopics
                HelpTopicCard(
                    topic = topic,
                    expanded = expanded,
                    onToggle = {
                        expandedTopics = if (expanded) {
                            expandedTopics - topic.titleRes
                        } else {
                            expandedTopics + topic.titleRes
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HelpIntroCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.nutrinest_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.help_intro_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.help_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(topic.icon, contentDescription = null, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(topic.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        stringResource(topic.summaryRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.help_collapse_section else R.string.help_expand_section
                    ),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded) {
                Spacer(Modifier.size(14.dp))
                Text(
                    stringResource(topic.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
