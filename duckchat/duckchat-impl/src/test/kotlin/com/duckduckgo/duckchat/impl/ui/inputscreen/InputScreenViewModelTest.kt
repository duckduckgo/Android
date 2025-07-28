package com.duckduckgo.duckchat.impl.ui.inputscreen

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitSearch
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InputScreenViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val autoComplete: AutoComplete = mock()
    private val history: NavigationHistory = mock()
    private val appCoroutineScope: CoroutineScope = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val autoCompleteSettings: AutoCompleteSettings = mock()

    @Before
    fun setup() {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        whenever(autoComplete.autoComplete(any())).thenReturn(
            flowOf(AutoCompleteResult("", listOf(AutoCompleteDefaultSuggestion("suggestion")))),
        )
    }

    private fun createViewModel(currentOmnibarText: String = ""): InputScreenViewModel {
        return InputScreenViewModel(
            currentOmnibarText = currentOmnibarText,
            autoComplete = autoComplete,
            dispatchers = coroutineRule.testDispatcherProvider,
            history = history,
            appCoroutineScope = appCoroutineScope,
            voiceSearchAvailability = voiceSearchAvailability,
            autoCompleteSettings = autoCompleteSettings,
        )
    }

    @Test
    fun `when initialized with web URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() = runTest {
        val viewModel = createViewModel("https://example.com")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with search query and autocomplete enabled then autocomplete suggestions should be visible`() = runTest {
        val viewModel = createViewModel("search query")

        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with empty text and autocomplete enabled then autocomplete suggestions should be hidden`() = runTest {
        val viewModel = createViewModel("")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with duck URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() = runTest {
        val viewModel = createViewModel("duck://results?q=test")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with http URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() = runTest {
        val viewModel = createViewModel("http://example.com")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when user modifies initial web URL text then autocomplete suggestions should become visible`() = runTest {
        val viewModel = createViewModel("https://example.com")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        viewModel.onSearchInputTextChanged("https://example.com/modified")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when user modifies initial search query then autocomplete suggestions should remain visible`() = runTest {
        val viewModel = createViewModel("search query")

        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        viewModel.onSearchInputTextChanged("modified search")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when user clears text after modifying initial URL then autocomplete suggestions should be hidden`() = runTest {
        val viewModel = createViewModel("https://example.com")

        viewModel.onSearchInputTextChanged("modified")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        viewModel.onSearchInputTextChanged("")
        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when autocomplete settings disabled then autocomplete suggestions should always be hidden`() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        val viewModel = createViewModel("search query")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when autocomplete settings disabled and user modifies text then autocomplete suggestions should remain hidden`() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        val viewModel = createViewModel("https://example.com")

        viewModel.onSearchInputTextChanged("modified")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with URL containing whitespace then autocomplete suggestions should be hidden initially`() = runTest {
        val viewModel = createViewModel("  https://example.com  ")

        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when user restores original URL after modification then autocomplete suggestions should remain visible`() = runTest {
        val viewModel = createViewModel("https://example.com")

        // User modifies text
        viewModel.onSearchInputTextChanged("modified")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        // User restores original URL
        viewModel.onSearchInputTextChanged("https://example.com")

        // Should still show autocomplete because user has moved beyond initial state
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when onActivityResume called then autocomplete settings are refreshed`() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        val viewModel = createViewModel("search query")

        // Initially disabled
        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        // Settings change to enabled
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
        viewModel.onActivityResume()

        // Should now show autocomplete for search query
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with partial URL then autocomplete suggestions should be visible`() = runTest {
        val viewModel = createViewModel("example")

        // Partial URLs that don't match web URL pattern should show autocomplete
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when initialized with complete domain then autocomplete suggestions should be hidden initially`() = runTest {
        val viewModel = createViewModel("example.com")

        // Complete domain names are treated as web URLs and suppress autocomplete initially
        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when text input contains special characters then autocomplete visibility follows URL pattern`() = runTest {
        // Test with URL-like string with special characters
        var viewModel = createViewModel("https://example.com?query=test&param=value")
        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        // Test with non-URL string with special characters
        viewModel = createViewModel("search with @special #characters")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when user types new text character by character then autocomplete behavior is consistent`() = runTest {
        val viewModel = createViewModel("https://example.com")

        // Initially hidden
        assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        // Type additional characters
        viewModel.onSearchInputTextChanged("https://example.com/")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        viewModel.onSearchInputTextChanged("https://example.com/p")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        viewModel.onSearchInputTextChanged("https://example.com/page")
        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
    }

    @Test
    fun `when search query provided and autocomplete enabled then autoCompleteSuggestionResults emits autocomplete results`() = runTest {
        val expectedSuggestions = listOf(
            AutoCompleteDefaultSuggestion("suggestion 1"),
            AutoCompleteSearchSuggestion("suggestion 2", isUrl = false, isAllowedInTopHits = true),
        )
        val expectedResult = AutoCompleteResult("test query", expectedSuggestions)

        whenever(autoComplete.autoComplete("test query")).thenReturn(flowOf(expectedResult))

        val viewModel = createViewModel("test query")

        assertEquals(expectedResult, viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when web URL provided initially then autoCompleteSuggestionResults remains empty until user modifies input`() = runTest {
        val expectedResult = AutoCompleteResult("modified", listOf(AutoCompleteDefaultSuggestion("suggestion")))
        whenever(autoComplete.autoComplete("modified")).thenReturn(flowOf(expectedResult))

        val viewModel = createViewModel("https://example.com")

        assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

        viewModel.onSearchInputTextChanged("modified")
        advanceTimeBy(301) // Wait for debounce

        assertEquals(expectedResult, viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when autocomplete service throws exception then autoCompleteSuggestionResults remains at initial state`() = runTest {
        whenever(autoComplete.autoComplete(any())).thenReturn(flow { throw IOException("Network error") })

        val viewModel = createViewModel("test query")

        assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when search input text changes rapidly then autoComplete is debounced`() = runTest {
        val result1 = AutoCompleteResult("test", listOf(AutoCompleteDefaultSuggestion("suggestion 1")))
        val result2 = AutoCompleteResult("testing", listOf(AutoCompleteDefaultSuggestion("suggestion 2")))

        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(result1))
        whenever(autoComplete.autoComplete("testing")).thenReturn(flowOf(result2))

        val viewModel = createViewModel("test")

        // First emission should be immediate
        assertEquals(result1, viewModel.autoCompleteSuggestionResults.value)

        // Change text rapidly
        viewModel.onSearchInputTextChanged("testing")

        // Should not trigger autocomplete immediately due to debounce
        verify(autoComplete, never()).autoComplete("testing")

        // Wait for debounce period
        advanceTimeBy(301)

        // Now should get the second result
        assertEquals(result2, viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when autocomplete settings disabled then autoCompleteSuggestionResults remains empty`() = runTest {
        whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
        val expectedResult = AutoCompleteResult("test", listOf(AutoCompleteDefaultSuggestion("suggestion")))
        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(expectedResult))

        val viewModel = createViewModel("test")

        // Should remain empty even with search query when autocomplete disabled
        assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

        // Autocomplete service should not be called
        verify(autoComplete, never()).autoComplete(any())
    }

    @Test
    fun `when empty text provided then autoCompleteSuggestionResults remains empty`() = runTest {
        val viewModel = createViewModel("")

        assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

        verify(autoComplete, never()).autoComplete(any())
    }

    @Test
    fun `when autocomplete result contains IAM suggestion then hasUserSeenHistoryIAM is tracked`() = runTest {
        val resultWithIAM = AutoCompleteResult(
            "test",
            listOf(
                AutoCompleteDefaultSuggestion("suggestion"),
                AutoCompleteInAppMessageSuggestion,
            ),
        )
        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(resultWithIAM))

        val viewModel = createViewModel("test")

        // Should emit the result with IAM
        assertEquals(resultWithIAM, viewModel.autoCompleteSuggestionResults.value)

        // When autocomplete suggestions are gone, should submit that user saw IAM
        viewModel.autoCompleteSuggestionsGone()

        verify(autoComplete).submitUserSeenHistoryIAM()
    }

    @Test
    fun `when autocomplete result does not contain IAM suggestion then submitUserSeenHistoryIAM is not called`() = runTest {
        val resultWithoutIAM = AutoCompleteResult(
            "test",
            listOf(AutoCompleteDefaultSuggestion("suggestion")),
        )
        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(resultWithoutIAM))

        val viewModel = createViewModel("test")

        // Should emit the result without IAM
        assertEquals(resultWithoutIAM, viewModel.autoCompleteSuggestionResults.value)

        // When autocomplete suggestions are gone, should not submit IAM tracking
        viewModel.autoCompleteSuggestionsGone()

        verify(autoComplete, never()).submitUserSeenHistoryIAM()
    }

    @Test
    fun `when text input changes from empty to non-empty then autoCompleteSuggestionResults updates`() = runTest {
        val expectedResult = AutoCompleteResult("test", listOf(AutoCompleteDefaultSuggestion("suggestion")))
        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(expectedResult))

        val viewModel = createViewModel("")

        // Initially empty
        assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

        // Change to non-empty text
        viewModel.onSearchInputTextChanged("test")
        advanceTimeBy(301) // Wait for debounce

        // Should get autocomplete results
        assertEquals(expectedResult, viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when text input changes from non-empty to empty then autoCompleteSuggestionResults changes to empty value`() = runTest {
        val initialResult = AutoCompleteResult("test", listOf(AutoCompleteDefaultSuggestion("suggestion")))
        val clearedResult = AutoCompleteResult("", emptyList())
        whenever(autoComplete.autoComplete("test")).thenReturn(flowOf(initialResult))

        val viewModel = createViewModel("test")

        // Initially has results
        assertEquals(initialResult, viewModel.autoCompleteSuggestionResults.value)

        // Change to empty text
        viewModel.onSearchInputTextChanged("")

        // Should retain last value since the flow stops emitting when shouldShowAutoComplete becomes false
        assertEquals(clearedResult, viewModel.autoCompleteSuggestionResults.value)
    }

    @Test
    fun `when chat text is web url then submitButtonIcon is SEND`() {
        val viewModel = createViewModel()
        viewModel.onChatSelected()
        viewModel.onChatInputTextChanged("https://example.com")
        assertEquals(SubmitButtonIcon.SEND, viewModel.submitButtonIconState.value.icon)
    }

    @Test
    fun `when chat text is not web url then submitButtonIcon is SEND`() {
        val viewModel = createViewModel()
        viewModel.onChatSelected()
        viewModel.onChatInputTextChanged("example")
        assertEquals(SubmitButtonIcon.SEND, viewModel.submitButtonIconState.value.icon)
    }

    @Test
    fun `when search text is web url then submitButtonIcon is SEND`() {
        val viewModel = createViewModel()
        viewModel.onSearchInputTextChanged("https://example.com")
        assertEquals(SubmitButtonIcon.SEND, viewModel.submitButtonIconState.value.icon)
    }

    @Test
    fun `when search text is not web url then submitButtonIcon is SEARCH`() {
        val viewModel = createViewModel()
        viewModel.onSearchInputTextChanged("example")
        assertEquals(SubmitButtonIcon.SEARCH, viewModel.submitButtonIconState.value.icon)
    }

    @Test
    fun `when onSearchSubmitted then emit SubmitSearch Command`() {
        val viewModel = createViewModel()
        val query = "example"

        viewModel.onSearchSubmitted(query)

        assertEquals(SubmitSearch(query), viewModel.command.value)
    }

    @Test
    fun `when onChatSubmitted with web url then emit SubmitSearch Command`() {
        val viewModel = createViewModel()
        val url = "https://example.com"

        viewModel.onChatSubmitted(url)

        assertEquals(SubmitSearch(url), viewModel.command.value)
    }

    @Test
    fun `when onChatSubmitted with query then emit SubmitChat Command`() {
        val viewModel = createViewModel()
        val query = "example"

        viewModel.onChatSubmitted(query)

        assertEquals(SubmitChat(query), viewModel.command.value)
    }

    @Test
    fun `when onChatInputTextChanged with empty query then showChatLogo should be true`() {
        val viewModel = createViewModel("initial text")

        viewModel.onChatInputTextChanged("")

        assertTrue(viewModel.visibilityState.value.showChatLogo)
    }

    @Test
    fun `when onChatInputTextChanged with different text than initial then showChatLogo should be false`() {
        val viewModel = createViewModel("initial text")

        viewModel.onChatInputTextChanged("different text")

        assertFalse(viewModel.visibilityState.value.showChatLogo)
    }

    @Test
    fun `when onChatInputTextChanged with same initial text but autocomplete suggestions visible then showChatLogo should be false`() = runTest {
        val initialText = "test query"
        val viewModel = createViewModel(initialText)

        assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

        viewModel.onChatInputTextChanged(initialText)

        assertFalse(viewModel.visibilityState.value.showChatLogo)
    }

    @Test
    fun `when onChatInputTextChanged with web URL then showChatLogo logic still applies`() {
        val viewModel = createViewModel("https://example.com")

        // Initially true (same as initial text, no autocomplete suggestions for URL)
        viewModel.onChatInputTextChanged("https://example.com")
        assertTrue(viewModel.visibilityState.value.showChatLogo)

        // Change to different URL - should be false
        viewModel.onChatInputTextChanged("https://different.com")
        assertFalse(viewModel.visibilityState.value.showChatLogo)

        // Change to empty - should be true
        viewModel.onChatInputTextChanged("")
        assertTrue(viewModel.visibilityState.value.showChatLogo)
    }
}
