/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui.nativeinput.textselection

import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.wideevents.DuckAiSelectionJourneyWideEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TextSelectionRepositoryTest {

    private val duckChatPixels: DuckChatPixels = mock()
    private val selectionJourney: DuckAiSelectionJourneyWideEvent = mock()

    private val testee = RealTextSelectionRepository(duckChatPixels, selectionJourney)

    @Test
    fun whenSelectionAddedThenItIsStoredAgainstThatTab() {
        assertTrue(testee.add(TAB, "selected words", URL))

        val selections = testee.selections(TAB).value
        assertEquals(1, selections.size)
        assertEquals("selected words", selections.first().text)
        assertEquals(URL, selections.first().url)
    }

    @Test
    fun whenSelectionAddedThenTextIsTrimmed() {
        testee.add(TAB, "  padded  ", URL)

        assertEquals("padded", testee.selections(TAB).value.first().text)
    }

    @Test
    fun whenBlankSelectionAddedThenNothingIsStored() {
        assertFalse(testee.add(TAB, "   ", URL))

        assertTrue(testee.selections(TAB).value.isEmpty())
    }

    @Test
    fun whenSameTextAddedTwiceThenItIsStoredOnce() {
        testee.add(TAB, "selected words", URL)

        assertTrue(testee.add(TAB, "selected words", URL))

        assertEquals(1, testee.selections(TAB).value.size)
        verify(duckChatPixels).reportContextualSelectionAttached()
    }

    @Test
    fun whenSelectionsForDifferentTabsThenTheyAreKeptApart() {
        testee.add(TAB, "first tab", URL)
        testee.add("other-tab", "second tab", URL)

        assertEquals(listOf("first tab"), testee.selections(TAB).value.map { it.text })
        assertEquals(listOf("second tab"), testee.selections("other-tab").value.map { it.text })
    }

    @Test
    fun whenLimitNotYetExceededThenLimitNotReached() {
        fillToLimit()

        assertFalse(testee.limitReached(TAB).value)
    }

    @Test
    fun whenAddRefusedAtLimitThenLimitReachedAndNothingStored() {
        fillToLimit()

        assertFalse(testee.add(TAB, "one too many", URL))

        assertTrue(testee.limitReached(TAB).value)
        assertEquals(TextSelectionRepository.MAX_SELECTIONS, testee.selections(TAB).value.size)
        verify(duckChatPixels).reportContextualSelectionLimitReached()
    }

    @Test
    fun whenSelectionRemovedAfterLimitThenLimitReachedCleared() {
        fillToLimit()
        testee.add(TAB, "one too many", URL)

        testee.remove(TAB, testee.selections(TAB).value.first().id)

        assertFalse(testee.limitReached(TAB).value)
    }

    @Test
    fun whenConsumedThenSelectionsReturnedAndCleared() {
        testee.add(TAB, "first", URL)
        testee.add(TAB, "second", URL)

        val consumed = testee.consume(TAB)

        assertEquals(listOf("first", "second"), consumed.map { it.text })
        assertTrue(testee.selections(TAB).value.isEmpty())
    }

    @Test
    fun whenConsumedThenLimitReachedCleared() {
        fillToLimit()
        testee.add(TAB, "one too many", URL)

        testee.consume(TAB)

        assertFalse(testee.limitReached(TAB).value)
    }

    @Test
    fun whenUnknownIdRemovedThenNothingChanges() {
        testee.add(TAB, "selected words", URL)

        testee.remove(TAB, "not-a-real-id")

        assertEquals(1, testee.selections(TAB).value.size)
        verify(duckChatPixels, never()).reportContextualSelectionRemoved()
        verify(selectionJourney, never()).onSelectionRemoved(any())
    }

    @Test
    fun whenSelectionAttachedThenJourneyToldTheRunningCount() {
        testee.add(TAB, "first", URL)
        testee.add(TAB, "second", URL)

        verify(selectionJourney).onSelectionAttached(1)
        verify(selectionJourney).onSelectionAttached(2)
    }

    @Test
    fun whenLastSelectionRemovedThenJourneyToldNoneRemain() {
        testee.add(TAB, "selected words", URL)

        testee.remove(TAB, testee.selections(TAB).value.first().id)

        verify(duckChatPixels).reportContextualSelectionRemoved()
        verify(selectionJourney).onSelectionRemoved(0)
    }

    private fun fillToLimit() {
        repeat(TextSelectionRepository.MAX_SELECTIONS) { testee.add(TAB, "selection $it", URL) }
    }

    private companion object {
        const val TAB = "tab-1"
        const val URL = "https://example.com"
    }
}
