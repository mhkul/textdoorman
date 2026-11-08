package com.textdoorman.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackReportTest {
    private fun entry(text: String, sender: String, blocked: Boolean, says: Boolean?, app: String = "com.google.android.apps.messaging") =
        ScreenLog.Entry(1L, app, sender, text, 0.9, blocked, blocked, "model", says)

    @Test
    fun masksLongNumbersOnly() {
        assertEquals("Your code is ######.", FeedbackReport.mask("Your code is 123456."))
        assertEquals("Call (###) ###-####", FeedbackReport.mask("Call (555) 010-1234"))
        assertEquals("Chip in \$10 by 5 pm", FeedbackReport.mask("Chip in \$10 by 5 pm"))
    }

    @Test
    fun reportNeverContainsSenderNumbersOrUnsharedTexts() {
        val wrong = entry("Your appointment is 11/14 at 3pm, ref 98765", "(555) 010-1234", blocked = true, says = false)
        val missed = entry("CHIP IN NOW Stop2End", "(555) 010-9999", blocked = false, says = true)
        val untouched = entry("Private text nobody shared", "(555) 010-7777", blocked = false, says = null)
        val test = entry("test", "(555) 010-0000", blocked = true, says = false, app = "com.textdoorman.android")
        val all = listOf(wrong, missed, untouched, test)

        assertEquals(listOf(wrong, missed), FeedbackReport.mistakes(all))

        val r = FeedbackReport.build(all, emptyList(), true,
            FeedbackReport.Device("Pixel 9", "17", "0.2", 5), FeedbackReport.Buzz.NO, "", listOf(wrong))
        println(r)
        assertFalse(r.contains("010-"))                       // no sender numbers
        assertFalse(r.contains("Private text"))                // only chosen texts
        assertFalse(r.contains("CHIP IN"))                     // not chosen
        assertTrue(r.contains("ref #####"))                    // masked
        assertTrue(r.contains("Wrongly silenced (you marked not spam): 1"))
        assertTrue(r.contains("Missed spam (you marked spam): 1"))
        assertTrue(r.contains("Texts from unknown numbers in history: 3"))  // test text excluded
    }
}
