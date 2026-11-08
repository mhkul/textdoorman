package com.textdoorman.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SettingsState(
    val silencing: Boolean,
    val exactTimes: Boolean,
    val accessGranted: Boolean,
    val spamCorrections: Int,
    val notSpamCorrections: Int,
    val version: String,
)

class SettingsActions(
    val onBack: () -> Unit,
    val onSilencingChange: (Boolean) -> Unit,
    val onExactTimesChange: (Boolean) -> Unit,
    val onOpenAccess: () -> Unit,
    val onForgetCorrections: () -> Unit,
    val onClearHistory: () -> Unit,
    val onTestSpam: () -> Unit,
    val onTestNormal: () -> Unit,
    val onSendFeedback: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, snackbar: SnackbarHostState, a: SettingsActions) {
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = a.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Section("Screening")
            ListItem(
                headlineContent = { Text("Silence spam") },
                supportingContent = {
                    Text("When off, spam is only listed in the app and you're still notified.")
                },
                trailingContent = { Switch(checked = state.silencing, onCheckedChange = a.onSilencingChange) },
                modifier = Modifier.clickable { a.onSilencingChange(!state.silencing) },
            )
            ListItem(
                headlineContent = { Text("Show exact times") },
                supportingContent = { Text("Show “Nov 7, 5:52 PM” instead of “3 hr ago”.") },
                trailingContent = { Switch(checked = state.exactTimes, onCheckedChange = a.onExactTimesChange) },
                modifier = Modifier.clickable { a.onExactTimesChange(!state.exactTimes) },
            )
            ListItem(
                headlineContent = { Text("Notification access") },
                supportingContent = { Text(if (state.accessGranted) "Allowed" else "Not allowed — screening is off") },
                modifier = Modifier.clickable(onClick = a.onOpenAccess),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("What it has learned")
            val total = state.spamCorrections + state.notSpamCorrections
            ListItem(
                headlineContent = { Text("Your corrections") },
                supportingContent = {
                    Text(
                        if (total == 0) "None yet. Tap \"Not spam\" or \"Mark as spam\" on a text to teach it."
                        else "${state.spamCorrections} marked spam, ${state.notSpamCorrections} marked not spam."
                    )
                },
            )
            if (total > 0) {
                ListItem(
                    headlineContent = { Text("Forget my corrections", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Go back to how it worked when first installed.") },
                    modifier = Modifier.clickable {
                        confirm = "Forget all $total corrections? Text Doorman will go back to how it " +
                            "worked when first installed." to a.onForgetCorrections
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Clear history") },
                supportingContent = { Text("Remove the list of screened texts. Doesn't affect learning.") },
                modifier = Modifier.clickable {
                    confirm = "Clear the list of screened texts? Your texts stay in your messaging app." to
                        a.onClearHistory
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Help improve it")
            ListItem(
                headlineContent = { Text("Send feedback") },
                supportingContent = { Text("Share a summary of how it's working. You review it before sending.") },
                modifier = Modifier.clickable(onClick = a.onSendFeedback),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Try it")
            ListItem(
                headlineContent = { Text("Send a test spam text") },
                supportingContent = { Text("A fake fundraising text. It should be silenced with no sound.") },
                modifier = Modifier.clickable(onClick = a.onTestSpam),
            )
            ListItem(
                headlineContent = { Text("Send a test normal text") },
                supportingContent = { Text("A fake delivery notice. It should alert you as usual.") },
                modifier = Modifier.clickable(onClick = a.onTestNormal),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Privacy")
            Text(
                "Text Doorman checks texts on this phone. It never uploads your messages, " +
                    "contacts, or corrections. It only screens texts from numbers that aren't saved " +
                    "contacts, and screened texts stay in your messaging app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Credits")
            Text(
                "The spam model was trained in part on these public datasets (CC BY 4.0, modified):\n" +
                    "• SMS Spam Collection — T. A. Almeida & J. M. Gómez Hidalgo, UCI Machine Learning Repository\n" +
                    "• SMS Phishing Dataset — S. Mishra & D. Soni, Mendeley Data\n" +
                    "• JPM Corpus of 2024 campaign texts — Figshare\n" +
                    "License: creativecommons.org/licenses/by/4.0. Inclusion does not imply endorsement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "Version ${state.version}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) = Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
)
