package com.textdoorman.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LearningTest {
    // Generic new-user model (public + synthetic data; no personal data).
    private val generic by lazy { SpamModel.fromJson(File("../../models/generic_model.json").readText()) }

    private val planPromos = listOf(
        "Hi, it's Evergreen Health Plan. Flu season is here! Members get a free flu shot at any in-network pharmacy. Learn more: ehp.org/flu",
        "Evergreen Health Plan: Summer is here! Find free and low-cost family activities near you with your member rewards: ehp.org/summer",
        "Hi, it's Evergreen Health Plan. Earn a \$25 gift card when you complete your annual wellness visit this year. Details: ehp.org/rewards",
    )
    private val politicalSpam =
        "URGENT: We're 5X-MATCHING every gift before midnight! Chip in \$10 now to save the Senate >> act.example-pac.org/x Stop2End"

    @Test
    fun correctionsGeneralizeToSimilarTextsOnly() {
        val before = Screener(generic)
        val target = planPromos[2]
        val spamBefore = before.score(politicalSpam)
        println("before: promo=%.3f political=%.3f".format(before.score(target), spamBefore))

        val after = Screener(generic, planPromos.take(2).mapIndexed { i, t -> Feedback(t, "", false, i.toLong()) })
        println("after 2 'not spam': promo=%.3f political=%.3f".format(after.score(target), after.score(politicalSpam)))

        assertTrue("similar unseen promo should now pass", after.score(target) < before.score(target))
        assertFalse("similar unseen promo should be kept", after.judge(target).block)
        assertTrue("unrelated political spam should still be blocked", after.judge(politicalSpam).block)
    }

    @Test
    fun senderCorrectionsAndLatestWins() {
        val fb = listOf(
            Feedback("whatever", "(555) 010-1234", spam = false, time = 1),
            Feedback("other", "+1 555-010-9999", spam = true, time = 2),
        )
        val s = Screener(generic, fb)
        assertFalse(s.judge(politicalSpam, "555-010-1234").block)          // allowed sender
        assertTrue(s.judge("See you at noon", "15550109999").block)       // blocked sender

        // Changing your mind: the newer correction for the same text wins.
        val t = planPromos[0]
        val flip = Screener(generic, listOf(Feedback(t, "", false, 1), Feedback(t, "", true, 2)))
        assertTrue(flip.score(t) >= generic.score(t) - 1e-9)
        assertEquals(0.0, Personalizer.train(generic, emptyList()).adjust(t), 0.0)
    }

    /**
     * Local-only: replays the developer's own labeled texts (not in the repo) in
     * date order through the GENERIC model, correcting every mistake as a user
     * would. Shows how fast a brand-new user's app becomes accurate.
     */
    @Test
    fun simulateNewUser() {
        val data = File("../../data/dataset.jsonl")
        assumeTrue("private dataset not present", data.exists())
        val rows = data.readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
            .sortedBy { it.optString("date") }

        val feedback = mutableListOf<Feedback>()
        val s = Screener(generic)
        val chunk = 100
        var fb = 0; var miss = 0; var n = 0
        var totalFb = 0; var totalMiss = 0
        println("texts      good BLOCKED   spam missed   corrections so far")
        for ((i, r) in rows.withIndex()) {
            val text = r.getString("text"); val spam = r.getString("label") == "block"
            val v = s.judge(text)
            if (v.block != spam) {
                if (v.block) { fb++; totalFb++ } else { miss++; totalMiss++ }
                feedback += Feedback(text, "", spam, i.toLong())
                s.learn(feedback)
            }
            n++
            if (n == chunk || i == rows.lastIndex) {
                println("%4d-%-4d  %6d         %6d        %4d".format(i - n + 2, i + 1, fb, miss, feedback.size))
                fb = 0; miss = 0; n = 0
            }
        }
        println("total: $totalFb good texts blocked, $totalMiss spam missed, ${feedback.size} corrections, ${rows.size} texts")
    }
}
