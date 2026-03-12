package com.duckduckgo.aihistorysearch.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppDebloaterTest {

    @Test
    fun `buildPrompt contains the query`() {
        val prompt = LlamaCppDebloater.buildPrompt("what was that article about fasting")
        assertTrue(prompt.contains("what was that article about fasting"))
    }

    @Test
    fun `buildPrompt contains Gemma chat template markers`() {
        val prompt = LlamaCppDebloater.buildPrompt("some query")
        assertTrue(prompt.contains("<start_of_turn>user"))
        assertTrue(prompt.contains("<end_of_turn>"))
        assertTrue(prompt.contains("<start_of_turn>model"))
    }

    @Test
    fun `buildPrompt instructs to output only the query`() {
        val prompt = LlamaCppDebloater.buildPrompt("some query")
        assertTrue(prompt.contains("Output only the search query"))
    }

    @Test
    fun `buildPrompt does not contain placeholder text`() {
        val prompt = LlamaCppDebloater.buildPrompt("my query")
        assertFalse(prompt.contains("\$query"))
    }
}
