package com.textdoorman.android

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** When off, spam is only logged, never dismissed (safe trial mode). */
    fun dismissEnabled(c: Context) = sp(c).getBoolean("dismiss", true)
    fun setDismissEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("dismiss", on).apply()

    /** Show "Nov 7, 5:52 PM" instead of "3 hr ago". */
    fun exactTimes(c: Context) = sp(c).getBoolean("exact_times", false)
    fun setExactTimes(c: Context, on: Boolean) = sp(c).edit().putBoolean("exact_times", on).apply()
}
