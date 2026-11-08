package com.textdoorman.android

/**
 * Builds the plain-text feedback a tester reviews and chooses to send.
 * Never includes sender numbers. Message text is included only for texts the
 * tester explicitly picks, and long digit runs (codes, account numbers) are masked.
 */
object FeedbackReport {

    enum class Buzz(val label: String) { NO("No"), YES("Yes"), NOT_SURE("Not sure") }

    data class Device(val model: String, val android: String, val appVersion: String, val daysInstalled: Long)

    /** Texts the tester corrected — the most useful thing to learn from. */
    fun mistakes(entries: List<ScreenLog.Entry>) =
        entries.filter { it.userSaysSpam != null && it.userSaysSpam != it.blocked && !it.isTest && it.text.isNotBlank() }

    fun build(
        entries: List<ScreenLog.Entry>,
        feedback: List<Feedback>,
        silencing: Boolean,
        device: Device,
        buzz: Buzz?,
        comments: String,
        includeTexts: List<ScreenLog.Entry>,
    ): String {
        val real = entries.filter { !it.isTest }
        val latest = feedback.groupBy { it.text }.values.map { fs -> fs.maxBy { it.time } }
        val wronglySilenced = real.count { it.blocked && it.userSaysSpam == false }
        val missedSpam = real.count { !it.blocked && it.userSaysSpam == true }
        return buildString {
            appendLine("Text Doorman tester feedback")
            appendLine()
            appendLine("Phone: ${device.model}, Android ${device.android}")
            appendLine("App version: ${device.appVersion}, installed ${device.daysInstalled} days")
            appendLine("Silencing: ${if (silencing) "on" else "off (log only)"}")
            appendLine()
            appendLine("Texts from unknown numbers in history: ${real.size}")
            appendLine("  judged spam: ${real.count { it.blocked }}")
            appendLine("  allowed: ${real.count { !it.blocked }}")
            appendLine("  pictures/attachments (not checked): ${real.count { it.text.isBlank() }}")
            appendLine("Wrongly silenced (you marked not spam): $wronglySilenced")
            appendLine("Missed spam (you marked spam): $missedSpam")
            appendLine("Corrections saved: ${latest.count { it.spam }} spam, ${latest.count { !it.spam }} not spam")
            appendLine()
            appendLine("Did a silenced text ever make a sound or vibrate? ${buzz?.label ?: "(not answered)"}")
            if (comments.isNotBlank()) {
                appendLine()
                appendLine("Comments:")
                appendLine(comments.trim())
            }
            if (includeTexts.isNotEmpty()) {
                appendLine()
                appendLine("Mistakes you chose to share (sender numbers removed, long numbers masked):")
                for (e in includeTexts) {
                    val what = if (e.blocked) "silenced, but not spam" else "allowed, but spam"
                    appendLine("- [$what] ${mask(e.text)}")
                }
            }
        }
    }

    /** Masks runs of 4+ digits (codes, account and phone numbers): "code 123456" -> "code ######". */
    fun mask(text: String): String =
        Regex("\\d[\\d\\s().-]{2,}\\d").replace(text) { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits >= 4) m.value.map { if (it.isDigit()) '#' else it }.joinToString("") else m.value
        }
}
