package com.textdoorman.android

import android.content.Context
import org.json.JSONObject
import java.io.File

/** The user's Spam / Not spam corrections. Stored on the phone only. */
object FeedbackStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "feedback.jsonl")

    @Synchronized
    fun all(ctx: Context): List<Feedback> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }.map {
            val o = JSONObject(it)
            Feedback(o.getString("text"), o.getString("sender"), o.getBoolean("spam"), o.getLong("time"))
        }
    }

    /** Saves a correction and retrains. Returns the text's new spam score. */
    fun record(ctx: Context, text: String, sender: String, spam: Boolean): Double {
        synchronized(this) {
            file(ctx).appendText(
                JSONObject().put("text", text).put("sender", sender).put("spam", spam)
                    .put("time", System.currentTimeMillis()).toString() + "\n")
        }
        val s = Screener.get(ctx)
        s.learn(all(ctx))
        return s.score(text)
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
        Screener.get(ctx).learn(emptyList())
        ScreenLog.clearMarks(ctx)
    }
}
