package com.textdoorman.android

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

/**
 * Watches notifications from the user's texting app. When a text arrives from
 * a sender that isn't a saved contact and the model judges it spam, the
 * notification is dismissed. The message itself stays in the texting app.
 */
class ScreenerService : NotificationListenerService() {

    private val worker = Executors.newSingleThreadExecutor()
    // Texting apps re-post the same notification as it updates; judge each message once.
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isTextingNotification(sbn)) return
        worker.execute { runCatching { handle(sbn) }.onFailure { Log.e(TAG, "screening failed", it) } }
    }

    private fun isTextingNotification(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (sbn.packageName == packageName) return n.channelId == TestMessages.CHANNEL
        return sbn.packageName in KNOWN_SMS_APPS ||
            sbn.packageName == Telephony.Sms.getDefaultSmsPackage(this)
    }

    private fun handle(sbn: StatusBarNotification) {
        val msg = extract(sbn.notification) ?: return
        if (msg.isGroup || !looksUnknown(msg.sender)) return

        val dedupeKey = "${sbn.key}|${msg.text.hashCode()}"
        synchronized(seen) { if (seen.put(dedupeKey, true) != null) return }

        val verdict = Screener.get(this).judge(msg.text, msg.sender)
        val dismiss = verdict.block && Prefs.dismissEnabled(this)
        if (dismiss) cancelNotification(sbn.key)

        // Prefer the time the text itself carries; fall back to the notification's time,
        // then to now. Ignore obviously bad values (zero, or in the future).
        val now = System.currentTimeMillis()
        val time = listOf(msg.time, sbn.notification.`when`, sbn.postTime)
            .firstOrNull { it in 1..(now + 60_000) } ?: now

        ScreenLog.add(this, ScreenLog.Entry(
            time = time, app = sbn.packageName, sender = msg.sender,
            text = msg.text, score = verdict.score, blocked = verdict.block,
            dismissed = dismiss, reason = verdict.reason,
        ))
        Log.i(TAG, "screened sender=${msg.sender} block=${verdict.block} p=${verdict.score} dismissed=$dismiss")
    }

    /** [time] is the message's own timestamp, or 0 if the texting app didn't provide one. */
    private data class Msg(val sender: String, val text: String, val isGroup: Boolean, val time: Long = 0)

    private fun extract(n: Notification): Msg? {
        val extras = n.extras
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        // Messaging apps use MessagingStyle; the newest message is last.
        @Suppress("DEPRECATION") // typed overload needs API 33; minSdk is 29
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!bundles.isNullOrEmpty()) {
            val last = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles).lastOrNull()
            if (last != null) {
                val sender = last.senderPerson?.name?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                return Msg(sender, last.text?.toString().orEmpty(), isGroup, last.timestamp)
            }
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        return Msg(title, text, isGroup)
    }

    companion object {
        private const val TAG = "TextDoorman"

        val KNOWN_SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )

        // Texting apps show a contact's name for saved contacts, and the raw
        // number or short code for everyone else. No contacts permission needed.
        private val NUMBER_LIKE = Regex("^[+()\\d\\s.\\-]{3,}$")

        fun looksUnknown(sender: String) = NUMBER_LIKE.matches(sender.trim())
    }
}
