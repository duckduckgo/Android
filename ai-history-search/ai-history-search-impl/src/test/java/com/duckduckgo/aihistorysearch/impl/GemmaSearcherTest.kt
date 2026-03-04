package com.duckduckgo.aihistorysearch.impl

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.history.api.HistoryEntry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class GemmaSearcherTest {

    // Pass a nonexistent path so no real model is loaded during tests
    private val testee = GemmaSearcher(modelPath = "/nonexistent/model.bin")

    @Test
    fun whenPromptBuiltThenQueryIsIncluded() {
        val prompt = testee.buildPrompt("mortgage rates", entries(3))
        assertTrue(prompt.contains("mortgage rates"))
    }

    @Test
    fun whenPromptBuiltThenHistoryEntriesAreIncluded() {
        val prompt = testee.buildPrompt("any query", entries(3))
        assertTrue(prompt.contains("Page 0"))
        assertTrue(prompt.contains("example.com/0"))
    }

    @Test
    fun whenPromptBuiltWithManyEntriesThenCappedAtMax() {
        val prompt = testee.buildPrompt("query", entries(100))
        val historyLines = prompt.lines().count { it.startsWith("-") }
        assertTrue(
            "Expected at most ${GemmaSearcher.MAX_ENTRIES} history lines, got $historyLines",
            historyLines <= GemmaSearcher.MAX_ENTRIES,
        )
    }

    @Test
    fun whenPromptBuiltThenIncludesRankingAndSynthesisInstructions() {
        val prompt = testee.buildPrompt("query", entries(5))
        assertTrue("Prompt should ask for ranking", prompt.contains("most relevant"))
        assertTrue("Prompt should ask for synthesis", prompt.contains("summary"))
    }

    private fun entries(count: Int): List<HistoryEntry.VisitedPage> =
        (0 until count).map { i ->
            HistoryEntry.VisitedPage(
                url = Uri.parse("https://example.com/$i"),
                title = "Page $i",
                visits = listOf(LocalDateTime.now()),
            )
        }
}
