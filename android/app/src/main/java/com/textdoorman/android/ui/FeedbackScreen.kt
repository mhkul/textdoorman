package com.textdoorman.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textdoorman.android.Feedback
import com.textdoorman.android.FeedbackReport
import com.textdoorman.android.ScreenLog

/**
 * Lets a tester review exactly what will be sent before sending it.
 * Nothing leaves the phone unless they tap Send and pick an app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    entries: List<ScreenLog.Entry>,
    feedback: List<Feedback>,
    silencing: Boolean,
    device: FeedbackReport.Device,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    var buzz by rememberSaveable { mutableStateOf<FeedbackReport.Buzz?>(null) }
    var comments by rememberSaveable { mutableStateOf("") }
    val mistakes = remember(entries) { FeedbackReport.mistakes(entries) }
    val chosen = remember { mutableStateListOf<ScreenLog.Entry>() }
    val report = FeedbackReport.build(entries, feedback, silencing, device, buzz, comments, chosen.toList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send feedback") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Thanks for testing! This sends a summary to the developer. You'll see everything " +
                    "before it's sent, and nothing is sent automatically.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Column {
                Text("Has a silenced text ever made a sound or vibrated?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackReport.Buzz.entries.forEach { b ->
                        FilterChip(selected = buzz == b, onClick = { buzz = b }, label = { Text(b.label) })
                    }
                }
            }

            OutlinedTextField(
                value = comments, onValueChange = { comments = it },
                label = { Text("Comments (optional)") },
                placeholder = { Text("What worked, what didn't, anything confusing…") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )

            if (mistakes.isNotEmpty()) {
                Column {
                    Text("Share texts it got wrong? (optional)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "These help most. Only the texts you check are included. Sender numbers are never " +
                            "included, and long numbers like codes are masked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    mistakes.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = e in chosen,
                                onCheckedChange = { if (it) chosen += e else chosen -= e },
                            )
                            Text(
                                (if (e.blocked) "Silenced, not spam: " else "Missed spam: ") + FeedbackReport.mask(e.text),
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Column {
                Text("What will be sent", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp))
                }
            }

            Button(onClick = { onSend(report) }, modifier = Modifier.fillMaxWidth()) { Text("Send…") }
        }
    }
}
