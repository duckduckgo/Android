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

package com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions.reader

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsNativeReader
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.DelegatingChatSuggestionsReader
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.RealChatSuggestionsReader
import com.duckduckgo.duckchat.store.api.DuckAiChatStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class DelegatingChatSuggestionsReaderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val nativeReader: ChatSuggestionsNativeReader = mock()
    private val webViewReader: RealChatSuggestionsReader = mock()
    private val store: DuckAiChatStore = mock()
    private lateinit var reader: DelegatingChatSuggestionsReader

    private val fakeSuggestion = ChatSuggestion("id", "Title", LocalDateTime.now(), false)

    @Before
    fun setup() {
        reader = DelegatingChatSuggestionsReader(nativeReader, webViewReader, store)
    }

    @Test
    fun `fetchSuggestions uses native reader when migrated`() = runTest {
        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeReader.fetchSuggestions("")).thenReturn(listOf(fakeSuggestion))

        val result = reader.fetchSuggestions()

        assertEquals(listOf(fakeSuggestion), result)
        verify(nativeReader).fetchSuggestions("")
        verify(webViewReader, never()).fetchSuggestions("")
    }

    @Test
    fun `fetchSuggestions uses webView reader when not migrated`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(webViewReader.fetchSuggestions("")).thenReturn(listOf(fakeSuggestion))

        val result = reader.fetchSuggestions()

        assertEquals(listOf(fakeSuggestion), result)
        verify(webViewReader).fetchSuggestions("")
        verify(nativeReader, never()).fetchSuggestions("")
    }

    @Test
    fun `fetchSuggestions tears down previous reader when switching`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(webViewReader.fetchSuggestions("")).thenReturn(emptyList())
        reader.fetchSuggestions() // sets webViewReader as active

        whenever(store.hasMigrated()).thenReturn(true)
        whenever(nativeReader.fetchSuggestions("")).thenReturn(emptyList())
        reader.fetchSuggestions() // switches to nativeReader

        verify(webViewReader).tearDown()
    }

    @Test
    fun `tearDown delegates to active reader`() = runTest {
        whenever(store.hasMigrated()).thenReturn(false)
        whenever(webViewReader.fetchSuggestions("")).thenReturn(emptyList())
        reader.fetchSuggestions() // activates webViewReader

        reader.tearDown()

        verify(webViewReader).tearDown()
    }

    @Test
    fun `tearDown does nothing when no reader has been activated`() {
        reader.tearDown()

        verify(nativeReader, never()).tearDown()
        verify(webViewReader, never()).tearDown()
    }
}
