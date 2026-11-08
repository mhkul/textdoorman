package com.textdoorman.android

import android.content.Context

data class Verdict(val block: Boolean, val score: Double?, val reason: String)

/**
 * Decides block/keep for a text from an unknown sender, in this order:
 *  1. no text (picture/attachment)          -> keep
 *  2. sender the user marked "Not spam"     -> keep
 *  3. sender the user marked "Spam"         -> block
 *  4. looks like a verification code        -> keep
 *  5. shipped model + personal corrections  -> block if score >= threshold
 */
class Screener(
    private val model: SpamModel,
    feedback: List<Feedback> = emptyList(),
) {
    @Volatile private var personal: Personalizer = Personalizer.EMPTY
    @Volatile private var allowed: Set<String> = emptySet()
    @Volatile private var blocked: Set<String> = emptySet()

    init { learn(feedback) }

    val threshold get() = model.threshold

    fun judge(text: String, sender: String = ""): Verdict {
        val t = text.trim()
        if (t.isEmpty()) return Verdict(false, null, "no text (picture or attachment)")
        val key = senderKey(sender)
        if (key.isNotEmpty() && key in allowed) return Verdict(false, null, "you marked this sender not spam")
        if (key.isNotEmpty() && key in blocked) return Verdict(true, null, "you marked this sender spam")
        if (looksLikePasscode(t)) return Verdict(false, null, "verification code rule")
        val p = score(t)
        return Verdict(p >= model.threshold, p, if (personal.size > 0) "model + your corrections" else "model")
    }

    fun score(text: String) = SpamModel.sigmoid(model.logit(text) + personal.adjust(text))

    /** Rebuild everything learned from the full list of corrections (latest per text wins). */
    @Synchronized
    fun learn(feedback: List<Feedback>) {
        val latest = feedback.groupBy { it.text }.values.map { it.maxBy { f -> f.time } }
        personal = Personalizer.train(model, latest)
        // The newest correction per sender decides which list that sender is on.
        val bySender = latest.filter { senderKey(it.sender).isNotEmpty() }
            .groupBy { senderKey(it.sender) }.mapValues { (_, fs) -> fs.maxBy { it.time }.spam }
        allowed = bySender.filterValues { !it }.keys
        blocked = bySender.filterValues { it }.keys
    }

    companion object {
        private val CODE_WORDS = Regex(
            "\\b(code|passcode|pin|otp)\\b|verification|one[- ]time", RegexOption.IGNORE_CASE)
        private val CODE = Regex("(?<![\\d(+-])\\d{4,8}(?![\\d)-])|\\b[A-Z0-9]{3}-[A-Z0-9]{3}\\b")
        // Callback scams pair a fake code with "call us at <number>"; let the model judge those.
        private val PHONE = Regex("\\(?\\b\\d{3}\\)?[\\s.-]*\\d{3}[\\s.-]*\\d{4}\\b")

        fun looksLikePasscode(t: String) =
            CODE_WORDS.containsMatchIn(t) && CODE.containsMatchIn(t) && !PHONE.containsMatchIn(t)

        /** Digits only, last 10, so "(555) 010-1234" and "+15550101234" match. */
        fun senderKey(sender: String): String = sender.filter { it.isDigit() }.takeLast(10)

        @Volatile private var instance: Screener? = null

        fun get(context: Context): Screener = instance ?: synchronized(this) {
            instance ?: Screener(
                SpamModel.fromJson(
                    context.assets.open("screener_model.json").bufferedReader().use { it.readText() }),
                FeedbackStore.all(context),
            ).also { instance = it }
        }
    }
}
