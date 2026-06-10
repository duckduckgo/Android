package com.duckduckgo.app.autocomplete.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCompleteUtilsKtTest {

    @Test
    fun testWhenQueryIsJustWhitespaces_ThenTokensAreEmpty() {
        val query = "  \t\n\t\t \t \t  \n\n\n "
        val tokens = query.tokensFrom()

        assertEquals(0, tokens.size)
    }

    @Test
    fun testWhenQueryContainsTabsOrNewlines_ThenResultIsTheSameAsIfThereAreSpaces() {
        val spaceQuery = "testing query tokens"
        val tabQuery = "testing\tquery\ttokens"
        val newlineQuery = "testing\nquery\ntokens"
        val spaceTokens = spaceQuery.tokensFrom()
        val tabTokens = tabQuery.tokensFrom()
        val newlineTokens = newlineQuery.tokensFrom()

        assertEquals(listOf("testing", "query", "tokens"), spaceTokens)
        assertEquals(spaceTokens, tabTokens)
        assertEquals(spaceTokens, newlineTokens)
    }
}
