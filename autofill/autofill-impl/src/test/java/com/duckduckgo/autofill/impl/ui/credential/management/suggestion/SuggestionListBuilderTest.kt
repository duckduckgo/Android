/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.impl.ui.credential.management.suggestion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorterByTitleAndDomain
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilderTest.Type.DIRECT
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilderTest.Type.QUERY
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilderTest.Type.SHAREABLE
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.CredentialListItem.SuggestedCredential
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionListBuilderTest {

    val testee = SuggestionListBuilder(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        sorter = CredentialListSorterByTitleAndDomain(AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())),
    )

    @Test
    fun whenNoSuggestionThenEmptyListReturned() {
        assertTrue(testee.build(emptyList(), emptyList(), emptyList(), allowBreakageReporting = false).isEmpty())
    }

    @Test
    fun whenOneDirectSuggestionThenDividerAddedLast() {
        val suggestions = buildSuggestions(1, type = DIRECT)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenOneQuerySuggestionThenDividerAddedLast() {
        val suggestions = buildSuggestions(1, type = QUERY)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenTwoDirectSuggestionsThenDividerAddedLast() {
        val suggestions = buildSuggestions(2, type = DIRECT)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenOneDirectSuggestionThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(1, type = DIRECT)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTwoDirectSuggestionsThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(2, type = DIRECT)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTenDirectSuggestionsThenThirteenListItemsReturned() {
        val suggestions = buildSuggestions(10, type = DIRECT)
        val list = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenDirectSuggestionAddedThenGroupNameIsCorrect() {
        val suggestions = buildSuggestions(1, type = DIRECT)
        val heading = testee.build(emptyList(), suggestions, emptyList(), allowBreakageReporting = false).first()
        assertTrue(heading is ListItem.GroupHeading)
    }

    @Test
    fun whenNoDirectSuggestionsButOneShareableThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(1, type = DIRECT)
        val list = testee.build(emptyList(), emptyList(), suggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenNoDirectSuggestionsButMultipleShareableThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(10, type = DIRECT)
        val list = testee.build(emptyList(), emptyList(), suggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenOneDirectAndOneShareableThenDirectSuggestionsAppearFirst() {
        val directSuggestions = buildSuggestions(1, type = DIRECT)
        val sharableSuggestions = buildSuggestions(1, startingIndex = directSuggestions.size, type = SHAREABLE)
        val list = testee.build(emptyList(), directSuggestions, sharableSuggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals(DIRECT.name, (list[1] as SuggestedCredential).credentials.username)
        assertEquals(SHAREABLE.name, (list[2] as SuggestedCredential).credentials.username)
    }

    @Test
    fun whenOneQueryAndOneDirectAndOneShareableThenQuerySuggestionsAppearFirst() {
        val querySuggestions = buildSuggestions(1, type = QUERY)
        val directSuggestions = buildSuggestions(1, startingIndex = querySuggestions.size, type = DIRECT)
        val sharableSuggestions = buildSuggestions(1, startingIndex = querySuggestions.size + directSuggestions.size, type = SHAREABLE)
        val list = testee.build(querySuggestions, directSuggestions, sharableSuggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + querySuggestions.size + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals(QUERY.name, (list[1] as SuggestedCredential).credentials.username)
        assertEquals(DIRECT.name, (list[2] as SuggestedCredential).credentials.username)
        assertEquals(SHAREABLE.name, (list[3] as SuggestedCredential).credentials.username)
    }

    @Test
    fun whenOneQueryAndOneDirectWithDuplicatesThenRemoveDuplicates() {
        val credential = aCredential(username = "test")
        val querySuggestions = listOf(credential)
        val directSuggestions = listOf(credential)
        val sharableSuggestions = emptyList<LoginCredentials>()
        val list = testee.build(querySuggestions, directSuggestions, sharableSuggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + 1, list.size)
    }

    @Test
    fun whenMultipleDirectAndSomeShareableThenDirectSuggestionsAppearFirst() {
        val directSuggestions = buildSuggestions(10, type = DIRECT)
        val sharableSuggestions = buildSuggestions(1, type = SHAREABLE, startingIndex = directSuggestions.size)
        val list = testee.build(emptyList(), directSuggestions, sharableSuggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals(DIRECT.name, (list[1] as SuggestedCredential).credentials.username)
        assertEquals(SHAREABLE.name, (list[11] as SuggestedCredential).credentials.username)
    }

    @Test
    fun whenMultipleQueryAndMultipleDirectAndSomeShareableThenQuerySuggestionsAppearFirst() {
        val querySuggestions = buildSuggestions(10, type = QUERY)
        val directSuggestions = buildSuggestions(10, type = DIRECT, startingIndex = querySuggestions.size)
        val sharableSuggestions = buildSuggestions(1, type = SHAREABLE, startingIndex = querySuggestions.size + directSuggestions.size)
        val list = testee.build(querySuggestions, directSuggestions, sharableSuggestions, allowBreakageReporting = false)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + querySuggestions.size + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals(QUERY.name, (list[1] as SuggestedCredential).credentials.username)
        assertEquals(DIRECT.name, (list[11] as SuggestedCredential).credentials.username)
        assertEquals(SHAREABLE.name, (list[21] as SuggestedCredential).credentials.username)
    }

    private fun buildSuggestions(listSize: Int, startingIndex: Int = 0, type: Type): List<LoginCredentials> {
        val list = mutableListOf<LoginCredentials>()
        for (i in 0 until listSize) {
            list.add(aCredential(id = startingIndex + i.toLong(), username = type.name))
        }
        return list
    }

    private enum class Type {
        QUERY,
        DIRECT,
        SHAREABLE,
    }

    private fun aCredential(id: Long = 0, username: String): LoginCredentials {
        return LoginCredentials(id = id, domain = null, password = null, username = username)
    }

    companion object {
        private const val NUM_DIVIDERS = 1
        private const val NUM_SUGGESTION_HEADERS = 1
    }
}
