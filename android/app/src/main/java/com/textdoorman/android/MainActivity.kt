package com.textdoorman.android

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.textdoorman.android.ui.FeedbackScreen
import com.textdoorman.android.ui.HomeScreen
import com.textdoorman.android.ui.HomeState
import com.textdoorman.android.ui.ScreenerTheme
import com.textdoorman.android.ui.SettingsActions
import com.textdoorman.android.ui.SettingsScreen
import com.textdoorman.android.ui.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Re-checked every time the app comes back from Android's settings.
    private var accessGranted by mutableStateOf(false)
    private var resumeCount by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        accessGranted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            .contains(ComponentName(this, ScreenerService::class.java).flattenToString())
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScreenerTheme { App() } }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val ctx = this
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        // "home" | "settings" | "feedback"
        var screen by rememberSaveable { mutableStateOf("home") }
        var silencing by remember { mutableStateOf(Prefs.dismissEnabled(ctx)) }
        var exactTimes by remember { mutableStateOf(Prefs.exactTimes(ctx)) }
        var feedbackTick by remember { mutableStateOf(0) }
        // Clock for relative times ("5 min ago"); refreshed every 30s and on every resume.
        val now by produceState(System.currentTimeMillis(), resumeCount) {
            while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
        }
        val logVersion by ScreenLog.version.collectAsStateWithLifecycle()

        val entries by produceState(emptyList<ScreenLog.Entry>(), logVersion, resumeCount) {
            value = withContext(Dispatchers.IO) { ScreenLog.recent(ctx, 200) }
        }
        val feedback by produceState(emptyList<Feedback>(), feedbackTick, logVersion) {
            value = withContext(Dispatchers.IO) { FeedbackStore.all(ctx) }
        }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (!ok) scope.launch { snackbar.showSnackbar("Allow notifications to send test texts.") }
        }
        fun sendTest(body: String) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            if (!accessGranted) {
                scope.launch { snackbar.showSnackbar("Turn on notification access first, so the test can be screened.") }
                return
            }
            TestMessages.post(ctx, body)
            scope.launch { snackbar.showSnackbar("Test text sent. Check the list on the main screen.") }
        }
        val openAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        BackHandler(enabled = screen != "home") { screen = if (screen == "feedback") "settings" else "home" }

        if (screen == "feedback") {
            val info = packageManager.getPackageInfo(packageName, 0)
            FeedbackScreen(
                entries = entries,
                feedback = feedback,
                silencing = silencing,
                device = FeedbackReport.Device(
                    model = "${Build.MANUFACTURER} ${Build.MODEL}",
                    android = Build.VERSION.RELEASE,
                    appVersion = info.versionName ?: "",
                    daysInstalled = (System.currentTimeMillis() - info.firstInstallTime) / 86_400_000L,
                ),
                onBack = { screen = "settings" },
                onSend = { report ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Text Doorman feedback")
                        putExtra(Intent.EXTRA_TEXT, report)
                        if (AppConfig.FEEDBACK_EMAIL.isNotBlank()) {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.FEEDBACK_EMAIL))
                        }
                    }
                    startActivity(Intent.createChooser(send, "Send feedback with"))
                },
            )
        } else if (screen == "settings") {
            SettingsScreen(
                SettingsState(
                    silencing = silencing,
                    exactTimes = exactTimes,
                    accessGranted = accessGranted,
                    spamCorrections = feedback.groupBy { it.text }.values.count { fs -> fs.maxBy { it.time }.spam },
                    notSpamCorrections = feedback.groupBy { it.text }.values.count { fs -> !fs.maxBy { it.time }.spam },
                    version = packageManager.getPackageInfo(packageName, 0).versionName ?: "",
                ),
                snackbar,
                SettingsActions(
                    onBack = { screen = "home" },
                    onSilencingChange = { on -> Prefs.setDismissEnabled(ctx, on); silencing = on },
                    onExactTimesChange = { on -> Prefs.setExactTimes(ctx, on); exactTimes = on },
                    onOpenAccess = openAccess,
                    onForgetCorrections = {
                        scope.launch {
                            withContext(Dispatchers.IO) { FeedbackStore.clear(ctx) }
                            feedbackTick++
                            snackbar.showSnackbar("Corrections forgotten.")
                        }
                    },
                    onClearHistory = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ScreenLog.clear(ctx) }
                            snackbar.showSnackbar("History cleared.")
                        }
                    },
                    onTestSpam = { sendTest(TestMessages.SPAM) },
                    onTestNormal = { sendTest(TestMessages.NORMAL) },
                    onSendFeedback = { screen = "feedback" },
                ),
            )
        } else {
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val week = entries.filter { it.time >= weekAgo && !it.isTest }
            HomeScreen(
                HomeState(
                    accessGranted = accessGranted,
                    silencing = silencing,
                    entries = entries,
                    silencedThisWeek = week.count { it.blocked },
                    allowedThisWeek = week.count { !it.blocked },
                    exactTimes = exactTimes,
                    now = now,
                ),
                snackbar,
                onOpenSettings = { screen = "settings" },
                onGrantAccess = openAccess,
                onCorrect = { entry, spam ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            FeedbackStore.record(ctx, entry.text, entry.sender, spam)
                            ScreenLog.mark(ctx, entry, spam)
                        }
                        feedbackTick++
                        snackbar.showSnackbar(
                            if (spam) "Got it. Texts like this and from this number will be silenced."
                            else "Got it. Texts like this and from this number will be allowed."
                        )
                    }
                },
            )
        }
    }
}
