package com.textdoorman.android

import android.content.Context
import android.text.format.DateUtils
import java.util.Calendar

/** How screened texts' times are shown. Follows the phone's 12/24-hour setting. */
object TimeFormat {

    /** "Nov 7, 5:52 PM" (year added if it isn't this year). */
    fun exact(ctx: Context, t: Long, now: Long = System.currentTimeMillis()): String {
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        flags = flags or if (sameYear(t, now)) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
        return DateUtils.formatDateTime(ctx, t, flags)
    }

    /**
     * Elapsed time while it's recent, then day names with a time. Avoids "2 days ago",
     * which counts calendar days and can mean anything from 25 to 71 hours.
     */
    fun relative(ctx: Context, t: Long, now: Long = System.currentTimeMillis()): String {
        val ago = now - t
        val clock = { DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_TIME) }
        return when {
            ago < 60_000L -> "Just now"
            ago < 3_600_000L -> "${ago / 60_000L} min ago"
            ago < 6 * 3_600_000L || daysBetween(t, now) == 0 -> "${ago / 3_600_000L} hr ago"
            daysBetween(t, now) == 1 -> "Yesterday, ${clock()}"
            daysBetween(t, now) < 7 -> DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY) + ", " + clock()
            else -> DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
                if (sameYear(t, now)) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
        }
    }

    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
    private fun sameYear(a: Long, b: Long) = cal(a).get(Calendar.YEAR) == cal(b).get(Calendar.YEAR)

    /** Calendar days from [t] to [now] in the phone's time zone (0 = same day). */
    internal fun daysBetween(t: Long, now: Long): Int {
        fun midnight(x: Long) = cal(x).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((midnight(now) - midnight(t)) / 86_400_000.0).toInt()
    }
}
