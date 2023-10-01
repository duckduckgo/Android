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
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.CredentialListItem.SuggestedCredential
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorterByTitleAndDomain
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
        assertTrue(testee.build(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun whenOneDirectSuggestionThenDividerAddedLast() {
        val suggestions = buildSuggestions(1)
        val list = testee.build(suggestions, emptyList())
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenTwoDirectSuggestionsThenDividerAddedLast() {
        val suggestions = buildSuggestions(2)
        val list = testee.build(suggestions, emptyList())
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenOneDirectSuggestionThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(1)
        val list = testee.build(suggestions, emptyList())
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTwoDirectSuggestionsThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(2)
        val list = testee.build(suggestions, emptyList())
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTenDirectSuggestionsThenThirteenListItemsReturned() {
        val suggestions = buildSuggestions(10)
        val list = testee.build(suggestions, emptyList())
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenDirectSuggestionAddedThenGroupNameIsCorrect() {
        val suggestions = buildSuggestions(1)
        val heading = testee.build(suggestions, emptyList()).first()
        assertTrue(heading is ListItem.GroupHeading)
    }

    @Test
    fun whenNoDirectSuggestionsButOneShareableThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(1)
        val list = testee.build(emptyList(), suggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenNoDirectSuggestionsButMultipleShareableThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(10)
        val list = testee.build(emptyList(), suggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenOneDirectAndOneShareableThenDirectSuggestionsAppearFirst() {
        val directSuggestions = buildSuggestions(1)
        val sharableSuggestions = buildSuggestions(1, startingIndex = directSuggestions.size)
        val list = testee.build(directSuggestions, sharableSuggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals(0L, (list[1] as SuggestedCredential).credentials.id)
        assertEquals(1L, (list[2] as SuggestedCredential).credentials.id)
    }

    @Test
    fun whenMultipleDirectAndSomeShareableThenDirectSuggestionsAppearFirst() {
        val directSuggestions = buildSuggestions(10, isShareable = false)
        val sharableSuggestions = buildSuggestions(1, isShareable = true, startingIndex = directSuggestions.size)
        val list = testee.build(directSuggestions, sharableSuggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + directSuggestions.size + sharableSuggestions.size, list.size)
        assertTrue(list[0] is ListItem.GroupHeading)
        assertEquals("direct", (list[1] as SuggestedCredential).credentials.username)
        assertEquals("shareable", (list[11] as SuggestedCredential).credentials.username)
    }

    private fun buildSuggestions(listSize: Int, startingIndex: Int = 0, isShareable: Boolean = false): List<LoginCredentials> {
        val list = mutableListOf<LoginCredentials>()
        for (i in 0 until listSize) {
            list.add(aCredential(id = startingIndex + i.toLong(), username = if (isShareable) "shareable" else "direct"))
        }
        return list
    }

    private fun aCredential(id: Long = 0, username: String): LoginCredentials {
        return LoginCredentials(id = id, domain = null, password = null, username = username)
    }

    companion object {
        private const val NUM_DIVIDERS = 1
        private const val NUM_SUGGESTION_HEADERS = 1
    }
}
