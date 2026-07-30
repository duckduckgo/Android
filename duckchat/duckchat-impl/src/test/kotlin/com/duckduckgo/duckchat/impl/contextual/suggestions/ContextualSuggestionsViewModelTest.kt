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

package com.duckduckgo.duckchat.impl.contextual.suggestions

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContextualSuggestionsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val suggestedPromptsProvider: ContextualSuggestedPromptsProvider = mock()
    private val duckChatFeature: DuckChatFeature = mock()
    private val suggestedPromptsToggle: Toggle = mock()

    private val viewModel = ContextualSuggestionsViewModel(
        suggestedPromptsProvider = suggestedPromptsProvider,
        duckChatFeature = duckChatFeature,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setup() {
        whenever(duckChatFeature.contextualSuggestedPrompts()).thenReturn(suggestedPromptsToggle)
        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(true)
        runBlocking { stubProvider(emptyList()) }
    }

    private suspend fun stubProvider(result: List<ContextualSuggestedPrompt>) {
        whenever(suggestedPromptsProvider.resolveSuggestions(any())).thenReturn(result)
    }

    @Test
    fun `when feature disabled then load does nothing`() = runTest {
        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(false)

        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.viewState.value.loading)
        assertTrue(viewModel.viewState.value.suggestions.isEmpty())
        verify(suggestedPromptsProvider, never()).resolveSuggestions(any())
    }

    @Test
    fun `when load starts then loading state begins`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.viewState.value.loading)
    }

    @Test
    fun `when timeout elapses without page context then defaults resolved with no url`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.viewState.value.loading)
        val captor = argumentCaptor<ResolvePageSuggestionsInput>()
        verify(suggestedPromptsProvider).resolveSuggestions(captor.capture())
        assertEquals(null, captor.lastValue.url)
        assertEquals(null, captor.lastValue.pageTypeSignals)
    }

    @Test
    fun `when page context arrives while loading then suggestions resolved and timeout tick is a no-op`() = runTest {
        val tailored = ContextualSuggestedPrompt("summarize-video", "Summarize this video", "Summarize this video.", "summary")
        stubProvider(listOf(tailored))

        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(tailored), viewModel.viewState.value.suggestions)
        assertFalse(viewModel.viewState.value.loading)
        verify(suggestedPromptsProvider, times(1)).resolveSuggestions(any())
    }

    @Test
    fun `when page context has page-type signals then they are parsed and passed to the provider`() = runTest {
        viewModel.onPageContextUpdated(
            """
            {"title":"T","url":"https://www.bbc.co.uk/food","content":"c",
             "pageTypeSignals":{"jsonLdType":["Recipe"],"ogType":"article","lang":"en"}}
            """.trimIndent(),
        )

        val captor = argumentCaptor<ResolvePageSuggestionsInput>()
        verify(suggestedPromptsProvider, atLeastOnce()).resolveSuggestions(captor.capture())
        val input = captor.lastValue
        assertEquals(listOf("Recipe"), input.pageTypeSignals?.jsonLdType)
        assertEquals("article", input.pageTypeSignals?.ogType)
        assertEquals("en", input.pageTypeSignals?.lang)
        assertEquals("https://www.bbc.co.uk/food", input.url)
    }

    @Test
    fun `when provider returns the summarize-page fallback then it is rendered`() = runTest {
        val fallback = ContextualSuggestedPrompt("summarize-page", "Summarize this page", "Summarize this page.", "summary")
        stubProvider(listOf(fallback))

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertEquals(listOf(fallback), viewModel.viewState.value.suggestions)
    }

    @Test
    fun `when page context arrives after timeout then defaults upgraded to page-tailored suggestions`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        val tailored = ContextualSuggestedPrompt("explain-repo", "Explain this repo", "Explain this repo.", null)
        stubProvider(listOf(tailored))
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://github.com/duckduckgo","content":"c"}""")

        assertEquals(listOf(tailored), viewModel.viewState.value.suggestions)
    }

    @Test
    fun `when page context is not valid json then it is ignored`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()

        viewModel.onPageContextUpdated("not json")

        assertTrue(viewModel.viewState.value.loading)
        verify(suggestedPromptsProvider, never()).resolveSuggestions(any())
    }

    @Test
    fun `when feature disabled then page context is ignored`() = runTest {
        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(false)

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertTrue(viewModel.viewState.value.suggestions.isEmpty())
        verify(suggestedPromptsProvider, never()).resolveSuggestions(any())
    }

    @Test
    fun `when cleared then suggestions and loading reset`() = runTest {
        val tailored = ContextualSuggestedPrompt("id", "Label", "Prompt.", null)
        stubProvider(listOf(tailored))
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        viewModel.clear()

        assertTrue(viewModel.viewState.value.suggestions.isEmpty())
        assertFalse(viewModel.viewState.value.loading)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when start surface cycles then stale timeout from previous cycle cannot cut the new load short`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceTimeBy(1_000)
        viewModel.clear()

        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.viewState.value.loading)

        coroutineRule.testDispatcher.scheduler.advanceTimeBy(4_500)

        assertTrue(viewModel.viewState.value.loading)
        verify(suggestedPromptsProvider, never()).resolveSuggestions(any())

        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.viewState.value.loading)
    }
}
