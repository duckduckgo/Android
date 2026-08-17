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
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.doSuspendableAnswer
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
    private val duckChatPixels: DuckChatPixels = mock()

    private val viewModel = ContextualSuggestionsViewModel(
        suggestedPromptsProvider = suggestedPromptsProvider,
        duckChatFeature = duckChatFeature,
        dispatchers = coroutineRule.testDispatcherProvider,
        duckChatPixels = duckChatPixels,
    )

    @Before
    fun setup() {
        whenever(duckChatFeature.contextualSuggestedPrompts()).thenReturn(suggestedPromptsToggle)
        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(true)
        runBlocking {
            stubProvider(emptyList())
            whenever(suggestedPromptsProvider.maxSuggestedPrompts()).thenReturn(4)
            whenever(suggestedPromptsProvider.prioritySuggestionIds()).thenReturn(setOf("translate-page"))
        }
    }

    private suspend fun stubProvider(
        result: List<ContextualSuggestedPrompt>,
        isSmart: Boolean = false,
        pageType: SuggestionsPageType = SuggestionsPageType.NONE,
    ) {
        whenever(suggestedPromptsProvider.resolveSuggestions(any()))
            .thenReturn(ResolvedPageSuggestions(suggestions = result, isSmart = isSmart, pageType = pageType))
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
    fun `when provider returns a summarize-page suggestion then it is rendered`() = runTest {
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
    fun `when page context lacks content then suggestions are not re-targeted`() = runTest {
        val tailored = ContextualSuggestedPrompt("key-takeaways", "What are the key takeaways?", "Key takeaways?", "summary")
        stubProvider(listOf(tailored))
        viewModel.onPageContextUpdated("""{"title":"Page A","url":"https://a.com","content":"a content"}""")

        viewModel.onPageContextUpdated("""{"title":"Page B","url":"https://b.com"}""")

        assertEquals(listOf(tailored), viewModel.viewState.value.suggestions)
        verify(suggestedPromptsProvider, times(1)).resolveSuggestions(any())
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
    fun `when feature disabled after suggestions rendered then next load clears them`() = runTest {
        stubProvider(listOf(ContextualSuggestedPrompt("id", "Label", "Prompt.", null)))
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        assertTrue(viewModel.viewState.value.suggestions.isNotEmpty())

        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(false)
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.viewState.value.suggestions.isEmpty())
        assertFalse(viewModel.viewState.value.loading)
    }

    @Test
    fun `when feature disabled after suggestions rendered then next page context clears them`() = runTest {
        stubProvider(listOf(ContextualSuggestedPrompt("id", "Label", "Prompt.", null)))
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        assertTrue(viewModel.viewState.value.suggestions.isNotEmpty())

        whenever(suggestedPromptsToggle.isEnabled()).thenReturn(false)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com/next","content":"c"}""")

        assertTrue(viewModel.viewState.value.suggestions.isEmpty())
    }

    @Test
    fun `when quick action slot reserved then at most three suggestions shown with priority included`() = runTest {
        val regular = (1..3).map { ContextualSuggestedPrompt("regular-$it", "Label $it", "Prompt $it.", null) }
        val translate = ContextualSuggestedPrompt("translate-page", "Translate this page", "Translate.", "translate")
        stubProvider(regular + translate)

        viewModel.onReservedQuickActionSlotsChanged(1)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertEquals(listOf("regular-1", "regular-2", "translate-page"), viewModel.viewState.value.suggestions.map { it.id })
    }

    @Test
    fun `when no quick action slot reserved then four suggestions shown`() = runTest {
        val regular = (1..3).map { ContextualSuggestedPrompt("regular-$it", "Label $it", "Prompt $it.", null) }
        val translate = ContextualSuggestedPrompt("translate-page", "Translate this page", "Translate.", "translate")
        stubProvider(regular + translate)

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertEquals(4, viewModel.viewState.value.suggestions.size)
    }

    @Test
    fun `when reserved slot released then full suggestion list restored`() = runTest {
        val regular = (1..3).map { ContextualSuggestedPrompt("regular-$it", "Label $it", "Prompt $it.", null) }
        val translate = ContextualSuggestedPrompt("translate-page", "Translate this page", "Translate.", "translate")
        stubProvider(regular + translate)
        viewModel.onReservedQuickActionSlotsChanged(1)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        viewModel.onReservedQuickActionSlotsChanged(0)

        assertEquals(4, viewModel.viewState.value.suggestions.size)
    }

    @Test
    fun `when fewer suggestions than budget and slot reserved then list unchanged`() = runTest {
        val suggestions = (1..2).map { ContextualSuggestedPrompt("regular-$it", "Label $it", "Prompt $it.", null) }
        stubProvider(suggestions)

        viewModel.onReservedQuickActionSlotsChanged(1)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertEquals(2, viewModel.viewState.value.suggestions.size)
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

    @Test
    fun `when suggestions become visible then viewed pixel fired once with smartness and page type`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)

        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels).reportContextualSuggestionsViewed(true, "recipe")
    }

    @Test
    fun `when suggestions stay visible across a re-resolve then viewed pixel not fired again`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com/2","content":"c"}""")

        verify(duckChatPixels, times(1)).reportContextualSuggestionsViewed(true, "recipe")
    }

    @Test
    fun `when page context arrives then page type is reported before suggestions finish resolving`() = runTest {
        val gate = CompletableDeferred<Unit>()
        whenever(suggestedPromptsProvider.resolveSuggestions(any())).doSuspendableAnswer {
            gate.await()
            ResolvedPageSuggestions(emptyList(), isSmart = true, pageType = SuggestionsPageType.RECIPE)
        }

        viewModel.load()
        viewModel.onPageContextUpdated(
            """{"title":"T","url":"https://example.com","content":"c","pageTypeSignals":{"jsonLdType":["Recipe"],"lang":"en"}}""",
        )
        coroutineRule.testDispatcher.scheduler.runCurrent()

        assertEquals(SuggestionsPageType.RECIPE, viewModel.currentPageType())
        gate.complete(Unit)
    }

    @Test
    fun `when suggestions cleared then page type no longer reports the previous page`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        assertEquals(SuggestionsPageType.RECIPE, viewModel.currentPageType())

        viewModel.clear()

        assertEquals(SuggestionsPageType.NONE, viewModel.currentPageType())
    }

    @Test
    fun `when a new load starts then page type no longer reports the previous page`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        assertEquals(SuggestionsPageType.RECIPE, viewModel.currentPageType())

        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()

        assertEquals(SuggestionsPageType.NONE, viewModel.currentPageType())
    }

    @Test
    fun `when sheet reopened then viewed pixel fires a fresh impression even if page context wins the race`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.load()
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels, times(2)).reportContextualSuggestionsViewed(true, "recipe")
    }

    @Test
    fun `when suggestions cleared and shown again then viewed pixel fires a fresh impression`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        viewModel.clear()
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        verify(duckChatPixels, times(2)).reportContextualSuggestionsViewed(true, "recipe")
    }

    @Test
    fun `when resolve returns no suggestions then viewed pixel not fired`() = runTest {
        stubProvider(emptyList())

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        verify(duckChatPixels, never()).reportContextualSuggestionsViewed(any(), any())
    }

    @Test
    fun `when load times out without page context then timed out pixel fired`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels).reportContextualSuggestionsContextCollectionTimedOut()
    }

    @Test
    fun `when page context arrives before timeout then timed out pixel not fired`() = runTest {
        viewModel.load()
        coroutineRule.testDispatcher.scheduler.runCurrent()
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(duckChatPixels, never()).reportContextualSuggestionsContextCollectionTimedOut()
    }

    @Test
    fun `when suggestion selected then selected pixel fired with suggestion id and page type`() = runTest {
        val tailored = ContextualSuggestedPrompt("summarize-video", "Summarize this video", "Summarize this video.", "summary")
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.VIDEO)
        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        viewModel.onSuggestionSelected("summarize-video")

        verify(duckChatPixels).reportContextualSuggestionSelected("summarize-video", "video")
    }

    @Test
    fun `when suggestions resolved then page type pixel value reflects the resolved page type`() = runTest {
        val tailored = ContextualSuggestedPrompt("shopping-list", "Generate a shopping list", "Create a shopping list.", null)
        stubProvider(listOf(tailored), isSmart = true, pageType = SuggestionsPageType.RECIPE)

        viewModel.onPageContextUpdated("""{"title":"T","url":"https://example.com","content":"c"}""")

        assertEquals(SuggestionsPageType.RECIPE, viewModel.currentPageType())
    }
}
