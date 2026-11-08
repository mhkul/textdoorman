package com.textdoorman.android

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Kotlin model must give the same probabilities as scikit-learn. */
class SpamModelParityTest {
    @Test
    fun matchesPython() {
        val model = SpamModel.fromJson(File("../../models/generic_model.json").readText())
        val cases = javaClass.classLoader!!.getResource("parity.jsonl")!!.readText()
            .lines().filter { it.isNotBlank() }.map { JSONObject(it) }

        var worst = 0.0
        val failures = mutableListOf<String>()
        for (c in cases) {
            val text = c.getString("text")
            val diff = kotlin.math.abs(model.score(text) - c.getDouble("p"))
            worst = maxOf(worst, diff)
            if (diff > 1e-3) failures += "diff=%.5f text=%s".format(diff, text.take(60))
        }
        println("parity: ${cases.size} cases, worst diff %.2e".format(worst))
        assertTrue("Mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
