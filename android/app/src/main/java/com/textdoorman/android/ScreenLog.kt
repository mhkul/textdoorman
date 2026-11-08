package com.textdoorman.android

import android.content.Context
import org.json.JSONObject
import java.io.File

/** On-device record of every screened text. Never leaves the phone. */
object ScreenLog {
    data class Entry(
        val time: Long, val app: String, val sender: String, val text: String,
        val score: Double?, val blocked: Boolean, val dismissed: Boolean, val reason: String,
        /** null = no correction; true = user said spam; false = user said not spam. */
        val userSaysSpam: Boolean? = null,
    ) {
        /** Posted by the app's own "Send a test text" buttons. */
        val isTest get() = app == "com.textdoorman.android"
    }

    private const val MAX_ENTRIES = 500

    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Bumps whenever the log changes, so the UI can refresh live. */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version
    private fun changed() { _version.value++ }
    private fun file(ctx: Context) = File(ctx.filesDir, "screen_log.jsonl")

    private fun Entry.toJson() = JSONObject()
        .put("time", time).put("app", app).put("sender", sender).put("text", text)
        .put("score", score ?: JSONObject.NULL).put("blocked", blocked)
        .put("dismissed", dismissed).put("reason", reason)
        .put("userSaysSpam", userSaysSpam ?: JSONObject.NULL)

    private fun parse(line: String): Entry {
        val o = JSONObject(line)
        return Entry(
            o.getLong("time"), o.getString("app"), o.getString("sender"), o.getString("text"),
            if (o.isNull("score")) null else o.getDouble("score"),
            o.getBoolean("blocked"), o.getBoolean("dismissed"), o.getString("reason"),
            if (!o.has("userSaysSpam") || o.isNull("userSaysSpam")) null else o.getBoolean("userSaysSpam"),
        )
    }

    private fun readAll(ctx: Context): List<Entry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map(::parse)
    }

    private fun writeAll(ctx: Context, entries: List<Entry>) =
        file(ctx).writeText(entries.joinToString("") { it.toJson().toString() + "\n" })

    @Synchronized
    fun add(ctx: Context, e: Entry) {
        file(ctx).appendText(e.toJson().toString() + "\n")
        val all = readAll(ctx)
        if (all.size > MAX_ENTRIES) writeAll(ctx, all.takeLast(MAX_ENTRIES))
        changed()
    }

    @Synchronized
    fun recent(ctx: Context, n: Int = 100): List<Entry> = readAll(ctx).takeLast(n).reversed()

    /** Remember the user's correction on this entry so the log can show it. */
    @Synchronized
    fun mark(ctx: Context, entry: Entry, spam: Boolean) {
        writeAll(ctx, readAll(ctx).map {
            if (it.time == entry.time && it.text == entry.text) it.copy(userSaysSpam = spam) else it
        })
        changed()
    }

    /** Forget all on-screen corrections (used when the user resets learning). */
    @Synchronized
    fun clearMarks(ctx: Context) {
        writeAll(ctx, readAll(ctx).map { it.copy(userSaysSpam = null) })
        changed()
    }

    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete(); changed() }
}
