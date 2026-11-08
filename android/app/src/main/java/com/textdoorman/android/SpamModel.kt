package com.textdoorman.android

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * TF-IDF + logistic regression spam model, exported from scikit-learn by
 * tools/export_model.py. Reproduces sklearn's tokenization exactly so scores
 * match the Python model (checked by SpamModelParityTest).
 */
class SpamModel private constructor(
    private val intercept: Double,
    val threshold: Double,
    private val vectorizers: List<Vectorizer>,
) {
    /** Probability (0..1) that [text] is spam. */
    fun score(text: String): Double = sigmoid(logit(text))

    /** Raw log-odds that [text] is spam; personal corrections add to this. */
    fun logit(text: String): Double {
        val lower = text.lowercase(Locale.ROOT)
        var z = intercept
        for (v in vectorizers) z += v.dot(lower)
        return z
    }

    private class Vectorizer(
        val charWb: Boolean,
        val nMin: Int,
        val nMax: Int,
        val index: HashMap<String, Int>,
        val idf: DoubleArray,
        val coef: DoubleArray,
    ) {
        /** coef · l2normalize(sublinear_tf * idf) for this vectorizer's features. */
        fun dot(lower: String): Double {
            val counts = HashMap<Int, Int>()
            val add: (String) -> Unit = { gram ->
                val i = index[gram]
                if (i != null) counts[i] = (counts[i] ?: 0) + 1
            }
            if (charWb) charWbNgrams(lower, add) else wordNgrams(lower, add)
            var dot = 0.0
            var norm2 = 0.0
            for ((i, c) in counts) {
                val w = (1.0 + ln(c.toDouble())) * idf[i]
                dot += w * coef[i]
                norm2 += w * w
            }
            return if (norm2 == 0.0) 0.0 else dot / sqrt(norm2)
        }

        private fun wordNgrams(lower: String, add: (String) -> Unit) {
            val tokens = TOKEN.findAll(lower).map { it.value }.toList()
            for (n in nMin..nMax) {
                if (n > tokens.size) break
                for (i in 0..tokens.size - n) {
                    add(if (n == 1) tokens[i] else tokens.subList(i, i + n).joinToString(" "))
                }
            }
        }

        // Mirrors sklearn's _char_wb_ngrams, working in code points like Python strings.
        private fun charWbNgrams(lower: String, add: (String) -> Unit) {
            for (word in pySplit(lower)) {
                val w = IntArray(word.size + 2)
                w[0] = ' '.code; w[w.size - 1] = ' '.code
                word.copyInto(w, 1)
                for (n in nMin..nMax) {
                    var offset = 0
                    add(cpString(w, 0, minOf(n, w.size)))
                    while (offset + n < w.size) {
                        offset++
                        add(cpString(w, offset, offset + n))
                    }
                    if (offset == 0) break
                }
            }
        }
    }

    companion object {
        fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

        // sklearn default token_pattern r"(?u)\b\w\w+\b". Desktop Java needs (?U) for
        // Unicode \w; Android's ICU regex is Unicode-aware already and rejects the flag.
        internal val TOKEN = runCatching { Regex("(?U)\\b\\w\\w+\\b") }
            .getOrElse { Regex("\\b\\w\\w+\\b") }

        fun fromJson(json: String): SpamModel {
            val o = JSONObject(json)
            val vs = o.getJSONArray("vectorizers")
            val list = (0 until vs.length()).map { k ->
                val v = vs.getJSONObject(k)
                val terms = v.getJSONArray("terms")
                val index = HashMap<String, Int>(terms.length() * 2)
                for (i in 0 until terms.length()) index[terms.getString(i)] = i
                Vectorizer(
                    charWb = v.getString("analyzer") == "char_wb",
                    nMin = v.getInt("ngram_min"),
                    nMax = v.getInt("ngram_max"),
                    index = index,
                    idf = v.getJSONArray("idf").toDoubles(),
                    coef = v.getJSONArray("coef").toDoubles(),
                )
            }
            return SpamModel(o.getDouble("intercept"), o.getDouble("threshold"), list)
        }

        private fun JSONArray.toDoubles() = DoubleArray(length()) { getDouble(it) }

        private fun cpString(cps: IntArray, from: Int, to: Int) = String(cps, from, to - from)

        // Python's str.isspace()
        private fun isPySpace(cp: Int) =
            Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85 || cp in 0x1c..0x1f

        /** Python's str.split() with no args, returning code-point arrays. */
        private fun pySplit(s: String): List<IntArray> {
            val out = ArrayList<IntArray>()
            val cur = ArrayList<Int>()
            s.codePoints().forEach { cp ->
                if (isPySpace(cp)) {
                    if (cur.isNotEmpty()) { out.add(cur.toIntArray()); cur.clear() }
                } else cur.add(cp)
            }
            if (cur.isNotEmpty()) out.add(cur.toIntArray())
            return out
        }
    }
}
