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
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionListBuilderTest {

    val testee = SuggestionListBuilder(context = InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun whenNoSuggestionThenEmptyListReturned() {
        assertTrue(testee.build(emptyList()).isEmpty())
    }

    @Test
    fun whenOneSuggestionThenDividerAddedLast() {
        val suggestions = buildSuggestions(1)
        val list = testee.build(suggestions)
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenTwoSuggestionsThenDividerAddedLast() {
        val suggestions = buildSuggestions(2)
        val list = testee.build(suggestions)
        assertTrue(list.last() is ListItem.Divider)
    }

    @Test
    fun whenOneSuggestionThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(1)
        val list = testee.build(suggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTwoSuggestionsThenCorrectNumberOfListItemsReturned() {
        val suggestions = buildSuggestions(2)
        val list = testee.build(suggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenTenSuggestionsThenThirteenListItemsReturned() {
        val suggestions = buildSuggestions(10)
        val list = testee.build(suggestions)
        assertEquals(NUM_SUGGESTION_HEADERS + NUM_DIVIDERS + suggestions.size, list.size)
    }

    @Test
    fun whenSuggestionAddedThenGroupNameIsCorrect() {
        val suggestions = buildSuggestions(1)
        val heading = testee.build(suggestions).first()
        assertTrue(heading is ListItem.GroupHeading)
    }

    private fun buildSuggestions(listSize: Int): List<LoginCredentials> {
        val list = mutableListOf<LoginCredentials>()
        for (i in 0 until listSize) {
            list.add(aCredential())
        }
        return list
    }

    private fun aCredential(): LoginCredentials {
        return LoginCredentials(id = null, domain = null, password = null, username = null)
    }

    companion object {
        private const val NUM_DIVIDERS = 1
        private const val NUM_SUGGESTION_HEADERS = 1
    }
}
