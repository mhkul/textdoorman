package com.textdoorman.android

import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random

/** One user correction: [spam] is what the user says the text really is. */
data class Feedback(val text: String, val sender: String, val spam: Boolean, val time: Long)

/**
 * Learns from the user's corrections on the phone. The shipped model stays
 * frozen; this adds a personal adjustment to its log-odds:
 *
 *   score = sigmoid(model.logit(text) + w · features(text))
 *
 * w is trained by logistic regression with the frozen model's logit as a fixed
 * offset, so a handful of corrections is enough to flip similar texts, while
 * unrelated texts are left alone. Features are hashed word unigrams and
 * bigrams, so words the shipped model has never seen can still be learned
 * (a new brand or sender name, say).
 */
class Personalizer private constructor(private val w: Map<Int, Double>) {

    fun adjust(text: String): Double {
        if (w.isEmpty()) return 0.0
        var s = 0.0
        for ((i, v) in features(text)) s += (w[i] ?: 0.0) * v
        return s
    }

    val size get() = w.size

    companion object {
        private const val DIM = 1 shl 20
        private const val EPOCHS = 40
        private const val LR = 0.5
        private const val L2 = 1e-4

        val EMPTY = Personalizer(emptyMap())

        /** Retrains from scratch on all corrections (cheap: there are only ever a few hundred). */
        fun train(model: SpamModel, feedback: List<Feedback>): Personalizer {
            if (feedback.isEmpty()) return EMPTY
            val xs = feedback.map { features(it.text) }
            val base = feedback.map { model.logit(it.text) }
            val ys = feedback.map { if (it.spam) 1.0 else 0.0 }
            val w = HashMap<Int, Double>()
            val order = feedback.indices.toMutableList()
            val rnd = Random(0)
            repeat(EPOCHS) {
                order.shuffle(rnd)
                for (k in order) {
                    var z = base[k]
                    for ((i, v) in xs[k]) z += (w[i] ?: 0.0) * v
                    val g = SpamModel.sigmoid(z) - ys[k]
                    for ((i, v) in xs[k]) {
                        val wi = w[i] ?: 0.0
                        w[i] = wi - LR * (g * v + L2 * wi)
                    }
                }
            }
            return Personalizer(w)
        }

        /** Binary hashed word unigrams + bigrams, L2-normalized. */
        internal fun features(text: String): Map<Int, Double> {
            val toks = SpamModel.TOKEN.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
            val idx = HashSet<Int>()
            for (i in toks.indices) {
                idx += hash(toks[i])
                if (i + 1 < toks.size) idx += hash(toks[i] + " " + toks[i + 1])
            }
            if (idx.isEmpty()) return emptyMap()
            val v = 1.0 / sqrt(idx.size.toDouble())
            return idx.associateWith { v }
        }

        // FNV-1a: stable across runs and devices, unlike String.hashCode guarantees.
        private fun hash(s: String): Int {
            var h = 0x811c9dc5.toInt()
            for (c in s) { h = h xor c.code; h *= 0x01000193 }
            return (h ushr 1) % DIM
        }
    }
}
