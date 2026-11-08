package com.textdoorman.android.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textdoorman.android.R
import com.textdoorman.android.ScreenLog
import com.textdoorman.android.TimeFormat

enum class Filter(val label: String) { ALL("All"), SILENCED("Silenced"), ALLOWED("Allowed") }

data class HomeState(
    val accessGranted: Boolean,
    val silencing: Boolean,
    val entries: List<ScreenLog.Entry>,
    val silencedThisWeek: Int,
    val allowedThisWeek: Int,
    val exactTimes: Boolean = false,
    /** Ticks every minute so "5 min ago" stays current while the screen is open. */
    val now: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onGrantAccess: () -> Unit,
    onCorrect: (ScreenLog.Entry, Boolean) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val shown = state.entries.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.SILENCED -> it.blocked
            Filter.ALLOWED -> !it.blocked
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Doorman") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.accessGranted) StatusCard(state) else SetupCard(onGrantAccess)
            }
            if (state.accessGranted) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Filter.entries.forEach { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
                if (shown.isEmpty()) item { EmptyState(filter) }
                items(shown, key = { "${it.time}-${it.text.hashCode()}" }) { e ->
                    MessageCard(e, onCorrect, state.exactTimes, state.now)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: HomeState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_stat_shield), null, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Screening is on", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (state.silencing) "Spam from unknown numbers is silenced."
                        else "Spam is listed here, but you're still notified.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(state.silencedThisWeek, if (state.silencing) "silenced this week" else "spam this week")
                Stat(state.allowedThisWeek, "allowed this week")
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String) = Column {
    Text("$n", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun SetupCard(onGrantAccess: () -> Unit) {
    // Google Play "prominent disclosure": explain and get consent in-app before the system screen.
    var askConsent by rememberSaveable { mutableStateOf(false) }
    if (askConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text("Allow Text Doorman to read notifications?") },
            text = {
                Text(
                    "Text Doorman reads the notifications from your texting app (such as " +
                        "Google Messages) to check texts from numbers that aren't in your contacts, " +
                        "and hides the notification if the text looks like spam.\n\n" +
                        "It ignores notifications from other apps. Texts are checked on this phone " +
                        "and are never uploaded or shared.\n\n" +
                        "Next, Android will ask you to turn on notification access."
                )
            },
            confirmButton = { TextButton(onClick = { askConsent = false; onGrantAccess() }) { Text("Agree and continue") } },
            dismissButton = { TextButton(onClick = { askConsent = false }) { Text("Not now") } },
        )
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Turn on screening", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Text Doorman quiets spam texts from numbers that aren't in your contacts, " +
                    "like political fundraising from a new number every day. You keep using your " +
                    "regular texting app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "To do this, it needs notification access. Android will warn that this lets the app read " +
                    "notifications. Text Doorman only looks at texts, and everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { askConsent = true }) { Text("Set up screening") }
        }
    }
}

@Composable
private fun EmptyState(filter: Filter) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            when (filter) {
                Filter.ALL -> "No texts from unknown numbers yet.\nThey'll show up here as they arrive."
                Filter.SILENCED -> "Nothing silenced yet."
                Filter.ALLOWED -> "Nothing allowed yet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MessageCard(
    e: ScreenLog.Entry,
    onCorrect: (ScreenLog.Entry, Boolean) -> Unit,
    exactTimes: Boolean,
    now: Long,
) {
    var expanded by rememberSaveable(e.time) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val shownTime = if (exactTimes) TimeFormat.exact(ctx, e.time, now) else TimeFormat.relative(ctx, e.time, now)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        // Less bottom padding: the TextButton below brings its own.
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(
                    if (e.blocked) Icons.Filled.Close else Icons.Filled.Check,
                    if (e.blocked) scheme.errorContainer else scheme.secondaryContainer,
                    if (e.blocked) scheme.onErrorContainer else scheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.sender, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (e.blocked) (if (e.dismissed) "Silenced" else "Spam") else "Allowed") +
                            " · " + shownTime +
                            (if (e.isTest) " · Test" else ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                e.text.ifBlank { "(picture or attachment)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Received ${TimeFormat.exact(ctx, e.time, now)}\n${explain(e)}",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                val marked = e.userSaysSpam
                if (marked != null) {
                    Text(if (marked) "You marked this as spam" else "You marked this as not spam",
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { onCorrect(e, !marked) }) { Text("Change") }
                } else if (e.text.isNotBlank()) {
                    TextButton(onClick = { onCorrect(e, !e.blocked) }) {
                        Text(if (e.blocked) "Not spam" else "Mark as spam")
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = bg, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
    }
}

private fun explain(e: ScreenLog.Entry): String = when {
    e.reason.startsWith("you marked this sender") -> "Why: you previously marked this sender."
    e.reason == "verification code rule" -> "Why: looks like a verification code, which is always allowed."
    e.reason.startsWith("no text") -> "Why: pictures and attachments can't be checked, so they're always allowed."
    e.score != null -> {
        val pct = (e.score * 100).toInt().coerceIn(1, 99)
        val learned = if (e.reason.contains("corrections")) ", including what it learned from you" else ""
        if (e.blocked) "Why: $pct% likely to be spam$learned." else "Why: only $pct% likely to be spam$learned."
    }
    else -> "Why: ${e.reason}."
}
