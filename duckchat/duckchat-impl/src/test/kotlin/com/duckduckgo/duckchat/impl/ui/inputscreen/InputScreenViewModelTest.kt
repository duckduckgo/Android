package com.duckduckgo.duckchat.impl.ui.inputscreen

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.AnimateLogoToProgress
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SetInputModeWidgetScrollPosition
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SetLogoProgress
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.ShowKeyboard
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitSearch
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.InputFieldCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.usage.InputScreenSessionUsageMetric
import com.duckduckgo.duckchat.impl.inputscreen.ui.session.InputScreenSessionStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InputScreenViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val autoComplete: AutoComplete = mock()
    private val history: NavigationHistory = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val autoCompleteSettings: AutoCompleteSettings = mock()
    private val pixel: Pixel = mock()
    private val inputScreenSessionStore: InputScreenSessionStore = mock()
    private val duckChat: DuckChat = mock()
    private val inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel = mock()
    private val inputScreenSessionUsageMetric: InputScreenSessionUsageMetric = mock()
    private val inputScreenConfigResolver: InputScreenConfigResolver = mock()

    @Before
    fun setup() =
        runTest {
            whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(true)
            whenever(autoComplete.autoComplete(any())).thenReturn(
                flowOf(AutoCompleteResult("", listOf(AutoCompleteDefaultSuggestion("suggestion")))),
            )
            whenever(duckChat.wasOpenedBefore()).thenReturn(false)
            whenever(inputScreenConfigResolver.useTopBar()).thenReturn(true)
            whenever(voiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)
        }

    private fun createViewModel(currentOmnibarText: String = ""): InputScreenViewModel =
        InputScreenViewModel(
            currentOmnibarText = currentOmnibarText,
            autoComplete = autoComplete,
            dispatchers = coroutineRule.testDispatcherProvider,
            history = history,
            appCoroutineScope = coroutineRule.testScope,
            voiceSearchAvailability = voiceSearchAvailability,
            autoCompleteSettings = autoCompleteSettings,
            pixel = pixel,
            sessionStore = inputScreenSessionStore,
            duckChat = duckChat,
            inputScreenDiscoveryFunnel = inputScreenDiscoveryFunnel,
            inputScreenSessionUsageMetric = inputScreenSessionUsageMetric,
            inputScreenConfigResolver = inputScreenConfigResolver,
        )

    @Test
    fun `when initialized with web URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with search query and autocomplete enabled then autocomplete suggestions should be visible`() =
        runTest {
            val viewModel = createViewModel("search query")

            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with empty text and autocomplete enabled then autocomplete suggestions should be hidden`() =
        runTest {
            val viewModel = createViewModel("")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with duck URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() =
        runTest {
            val viewModel = createViewModel("duck://results?q=test")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with http URL and autocomplete enabled then autocomplete suggestions should be hidden initially`() =
        runTest {
            val viewModel = createViewModel("http://example.com")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when user modifies initial web URL text then autocomplete suggestions should become visible`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
            viewModel.onSearchInputTextChanged("https://example.com/modified")
            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when user modifies initial search query then autocomplete suggestions should remain visible`() =
        runTest {
            val viewModel = createViewModel("search query")

            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
            viewModel.onSearchInputTextChanged("modified search")
            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when user clears text after modifying initial URL then autocomplete suggestions should be hidden`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            viewModel.onSearchInputTextChanged("modified")
            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
            viewModel.onSearchInputTextChanged("")
            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when autocomplete settings disabled then autocomplete suggestions should always be hidden`() =
        runTest {
            whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
            val viewModel = createViewModel("search query")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when autocomplete settings disabled and user modifies text then autocomplete suggestions should remain hidden`() =
        runTest {
            whenever(autoCompleteSettings.autoCompleteSuggestionsEnabled).thenReturn(false)
            val viewModel = createViewModel("https://example.com")

            viewModel.onSearchInputTextChanged("modified")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with URL containing whitespace then autocomplete suggestions should be hidden initially`() =
        runTest {
            val viewModel = createViewModel("  https://example.com  ")

            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when user restores original URL after modification then autocomplete suggestions should remain visible`() =
        runTest {
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
    fun `when onActivityResume called then autocomplete settings are refreshed`() =
        runTest {
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
    fun `when initialized with partial URL then autocomplete suggestions should be visible`() =
        runTest {
            val viewModel = createViewModel("example")

            // Partial URLs that don't match web URL pattern should show autocomplete
            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when initialized with complete domain then autocomplete suggestions should be hidden initially`() =
        runTest {
            val viewModel = createViewModel("example.com")

            // Complete domain names are treated as web URLs and suppress autocomplete initially
            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when text input contains special characters then autocomplete visibility follows URL pattern`() =
        runTest {
            // Test with URL-like string with special characters
            var viewModel = createViewModel("https://example.com?query=test&param=value")
            assertFalse(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)

            // Test with non-URL string with special characters
            viewModel = createViewModel("search with @special #characters")
            assertTrue(viewModel.visibilityState.value.autoCompleteSuggestionsVisible)
        }

    @Test
    fun `when user types new text character by character then autocomplete behavior is consistent`() =
        runTest {
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
    fun `when search query provided and autocomplete enabled then autoCompleteSuggestionResults emits autocomplete results`() =
        runTest {
            val expectedSuggestions =
                listOf(
                    AutoCompleteDefaultSuggestion("suggestion 1"),
                    AutoCompleteSearchSuggestion("suggestion 2", isUrl = false, isAllowedInTopHits = true),
                )
            val expectedResult = AutoCompleteResult("test query", expectedSuggestions)

            whenever(autoComplete.autoComplete("test query")).thenReturn(flowOf(expectedResult))

            val viewModel = createViewModel("test query")

            assertEquals(expectedResult, viewModel.autoCompleteSuggestionResults.value)
        }

    @Test
    fun `when web URL provided initially then autoCompleteSuggestionResults remains empty until user modifies input`() =
        runTest {
            val expectedResult = AutoCompleteResult("modified", listOf(AutoCompleteDefaultSuggestion("suggestion")))
            whenever(autoComplete.autoComplete("modified")).thenReturn(flowOf(expectedResult))

            val viewModel = createViewModel("https://example.com")

            assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

            viewModel.onSearchInputTextChanged("modified")
            advanceTimeBy(301) // Wait for debounce

            assertEquals(expectedResult, viewModel.autoCompleteSuggestionResults.value)
        }

    @Test
    fun `when autocomplete service throws exception then autoCompleteSuggestionResults remains at initial state`() =
        runTest {
            whenever(autoComplete.autoComplete(any())).thenReturn(flow { throw IOException("Network error") })

            val viewModel = createViewModel("test query")

            assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)
        }

    @Test
    fun `when search input text changes rapidly then autoComplete is debounced`() =
        runTest {
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
    fun `when autocomplete settings disabled then autoCompleteSuggestionResults remains empty`() =
        runTest {
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
    fun `when empty text provided then autoCompleteSuggestionResults remains empty`() =
        runTest {
            val viewModel = createViewModel("")

            assertEquals(AutoCompleteResult("", emptyList()), viewModel.autoCompleteSuggestionResults.value)

            verify(autoComplete, never()).autoComplete(any())
        }

    @Test
    fun `when autocomplete result contains IAM suggestion then hasUserSeenHistoryIAM is tracked`() =
        runTest {
            val resultWithIAM =
                AutoCompleteResult(
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
    fun `when autocomplete result does not contain IAM suggestion then submitUserSeenHistoryIAM is not called`() =
        runTest {
            val resultWithoutIAM =
                AutoCompleteResult(
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
    fun `when text input changes from empty to non-empty then autoCompleteSuggestionResults updates`() =
        runTest {
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
    fun `when text input changes from non-empty to empty then autoCompleteSuggestionResults changes to empty value`() =
        runTest {
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
    fun `when chat text is web url then submitButtonIcon is SEND`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onChatSelected()
            viewModel.onChatInputTextChanged("https://example.com")
            assertEquals(SubmitButtonIcon.SEND, viewModel.submitButtonIconState.value.icon)
        }

    @Test
    fun `when chat text is not web url then submitButtonIcon is SEND`() =
        runTest {
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
    fun `when onSearchSubmitted then emit SubmitSearch Command`() =
        runTest {
            val viewModel = createViewModel()
            val query = "example"

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onSearchSubmitted(query)

            assertEquals(SubmitSearch(query), viewModel.command.value)
        }

    @Test
    fun `when onChatSubmitted with web url then emit SubmitSearch Command`() =
        runTest {
            val viewModel = createViewModel()
            val url = "https://example.com"

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onChatSubmitted(url)

            assertEquals(SubmitSearch(url), viewModel.command.value)
        }

    @Test
    fun `when onChatSubmitted with query then emit SubmitChat Command`() =
        runTest {
            val viewModel = createViewModel()
            val query = "example"

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

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
    fun `when onChatInputTextChanged with same initial text but autocomplete suggestions visible then showChatLogo should be false`() =
        runTest {
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

    @Test
    fun `when onChatInputTextChanged with empty query then newLineButtonVisible is false`() {
        val viewModel = createViewModel()

        viewModel.onChatInputTextChanged("")

        assertFalse(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onChatInputTextChanged with non-empty query then newLineButtonVisible is true`() {
        val viewModel = createViewModel()

        viewModel.onChatInputTextChanged("query")

        assertTrue(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onChatInputTextChanged with newline query then newLineButtonVisible is false`() {
        val viewModel = createViewModel()

        viewModel.onChatInputTextChanged("\n")

        assertFalse(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onChatInputTextChanged with query containing text and newlines then newLineButtonVisible is true`() {
        val viewModel = createViewModel()

        viewModel.onChatInputTextChanged("query\n123")

        assertTrue(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onRemoveSearchSuggestionConfirmed then refreshSuggestions triggered and autoComplete results updated`() =
        runTest {
            val initialResult = AutoCompleteResult("query", listOf(AutoCompleteDefaultSuggestion("suggestion 1")))
            val refreshedResult = AutoCompleteResult("query", listOf(AutoCompleteDefaultSuggestion("suggestion 2")))

            val flow1 = MutableSharedFlow<AutoCompleteResult>()
            val flow2 = MutableSharedFlow<AutoCompleteResult>()
            whenever(autoComplete.autoComplete("query"))
                .thenReturn(flow1)
                .thenReturn(flow2)

            val viewModel = createViewModel("query")

            flow1.emit(initialResult)
            advanceUntilIdle()
            assertEquals(initialResult, viewModel.autoCompleteSuggestionResults.value)

            viewModel.onRemoveSearchSuggestionConfirmed(
                AutoCompleteHistorySuggestion(phrase = "query", url = "example.com", title = "title", isAllowedInTopHits = true),
            )

            flow2.emit(refreshedResult)
            advanceUntilIdle()
            assertEquals(refreshedResult, viewModel.autoCompleteSuggestionResults.value)

            verify(autoComplete, times(2)).autoComplete("query")
        }

    @Test
    fun `when initialized with web URL then inputFieldState canExpand should be false initially`() {
        val viewModel = createViewModel("https://example.com")

        assertFalse(viewModel.inputFieldState.value.canExpand)
    }

    @Test
    fun `when initialized with search query then inputFieldState canExpand should be true`() {
        val viewModel = createViewModel("search query")

        assertTrue(viewModel.inputFieldState.value.canExpand)
    }

    @Test
    fun `when user modifies initial web URL text then inputFieldState canExpand should become true`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            assertFalse(viewModel.inputFieldState.value.canExpand)
            viewModel.onSearchInputTextChanged("https://example.com/modified")
            assertTrue(viewModel.inputFieldState.value.canExpand)
        }

    @Test
    fun `when user modifies initial search query then inputFieldState canExpand should remain true`() =
        runTest {
            val viewModel = createViewModel("search query")

            assertTrue(viewModel.inputFieldState.value.canExpand)
            viewModel.onSearchInputTextChanged("modified search")
            assertTrue(viewModel.inputFieldState.value.canExpand)
        }

    @Test
    fun `when user restores original URL after modification then inputFieldState canExpand should remain true`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            // User modifies text
            viewModel.onSearchInputTextChanged("modified")
            assertTrue(viewModel.inputFieldState.value.canExpand)

            // User restores original URL
            viewModel.onSearchInputTextChanged("https://example.com")

            // Should still allow expansion because user has moved beyond initial state
            assertTrue(viewModel.inputFieldState.value.canExpand)
        }

    @Test
    fun `when onInputFieldTouched called then inputFieldState canExpand should become true`() {
        val viewModel = createViewModel("https://example.com")

        assertFalse(viewModel.inputFieldState.value.canExpand)
        viewModel.onInputFieldTouched()
        assertTrue(viewModel.inputFieldState.value.canExpand)
    }

    @Test
    fun `when onInputFieldTouched called then canExpand remains true even after text changes`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            // Touch input field to enable expansion
            viewModel.onInputFieldTouched()
            assertTrue(viewModel.inputFieldState.value.canExpand)

            // Change text - should still allow expansion
            viewModel.onSearchInputTextChanged("new text")
            assertTrue(viewModel.inputFieldState.value.canExpand)

            // Back to URL - should still allow expansion
            viewModel.onSearchInputTextChanged("https://example.com")
            assertTrue(viewModel.inputFieldState.value.canExpand)
        }

    @Test
    fun `when initialized with web URL then SelectAll command should be sent`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            viewModel.inputFieldCommand.test {
                val receivedCommand = awaitItem()
                assertEquals(InputFieldCommand.SelectAll, receivedCommand)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when initialized with duck URL then SelectAll command should be sent`() =
        runTest {
            val viewModel = createViewModel("duck://results?q=test")

            viewModel.inputFieldCommand.test {
                val receivedCommand = awaitItem()
                assertEquals(InputFieldCommand.SelectAll, receivedCommand)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when initialized with search query then SelectAll command should NOT be sent`() =
        runTest {
            val viewModel = createViewModel("search query")

            viewModel.inputFieldCommand.test {
                expectNoEvents()
            }
        }

    @Test
    fun `when user modifies URL text after initialization then no additional SelectAll commands are sent`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            viewModel.inputFieldCommand.test {
                // Should receive initial SelectAll
                val initialCommand = awaitItem()
                assertEquals(InputFieldCommand.SelectAll, initialCommand)

                // Modify text
                viewModel.onSearchInputTextChanged("https://example.com/page")

                // Should not receive any additional commands
                expectNoEvents()
            }
        }

    @Test
    fun `when restoreAutoCompleteScrollPosition called then RestoreAutoCompleteScrollPosition command sent and keyboard shown`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.storeAutoCompleteScrollPosition(firstVisibleItemPosition = 123, itemOffsetTop = 456)
            viewModel.restoreAutoCompleteScrollPosition()

            assertEquals(SearchCommand.RestoreAutoCompleteScrollPosition(123, 456), viewModel.searchTabCommand.value)
            assertEquals(ShowKeyboard, viewModel.command.value)
        }

    @Test
    fun `when initialized with web URL then showSearchLogo should be true initially`() {
        val viewModel = createViewModel("https://example.com")

        assertTrue(viewModel.visibilityState.value.showSearchLogo)
    }

    @Test
    fun `when initialized with search query then showSearchLogo should be false due to autocomplete suggestions`() =
        runTest {
            val viewModel = createViewModel("search query")

            // Should be false because autocomplete suggestions are visible for search queries
            assertFalse(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when onNewTabPageContentChanged with true then showSearchLogo should be false`() =
        runTest {
            val viewModel = createViewModel()

            // Initially true
            assertTrue(viewModel.visibilityState.value.showSearchLogo)

            viewModel.onNewTabPageContentChanged(hasContent = true)

            // Should become false when new tab page has content
            assertFalse(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when onNewTabPageContentChanged with false then showSearchLogo should be true if autocomplete not visible`() =
        runTest {
            val viewModel = createViewModel()

            // Set new tab page to have content first
            viewModel.onNewTabPageContentChanged(hasContent = true)
            assertFalse(viewModel.visibilityState.value.showSearchLogo)

            // Remove content from new tab page
            viewModel.onNewTabPageContentChanged(hasContent = false)

            // Should become true again when no content and no autocomplete suggestions
            assertTrue(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when new tab page has content and autocomplete becomes visible then showSearchLogo should remain false`() =
        runTest {
            val viewModel = createViewModel()

            // Set new tab page to have content
            viewModel.onNewTabPageContentChanged(hasContent = true)
            assertFalse(viewModel.visibilityState.value.showSearchLogo)

            // Trigger autocomplete (this would normally make showSearchLogo false anyway)
            viewModel.onSearchInputTextChanged("test query")

            // Should remain false
            assertFalse(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when new tab page has no content but autocomplete is visible then showSearchLogo should be false`() =
        runTest {
            val viewModel = createViewModel()

            // Ensure new tab page has no content
            viewModel.onNewTabPageContentChanged(hasContent = false)

            // Trigger autocomplete suggestions
            viewModel.onSearchInputTextChanged("test query")

            // Should be false because autocomplete suggestions are visible
            assertFalse(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when new tab page has no content and autocomplete is not visible then showSearchLogo should be true`() =
        runTest {
            val viewModel = createViewModel("https://example.com")

            // Ensure new tab page has no content
            viewModel.onNewTabPageContentChanged(hasContent = false)

            // Should be true because no content and no autocomplete suggestions (URL doesn't trigger autocomplete initially)
            assertTrue(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when user transitions from autocomplete visible to hidden then showSearchLogo should be true if new tab page has no content`() =
        runTest {
            val viewModel = createViewModel("search query")

            // Initially false due to autocomplete
            assertFalse(viewModel.visibilityState.value.showSearchLogo)

            // Ensure new tab page has no content
            viewModel.onNewTabPageContentChanged(hasContent = false)

            // Clear text to hide autocomplete
            viewModel.onSearchInputTextChanged("")

            // Should become true when autocomplete hidden and no new tab content
            assertTrue(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when user transitions from autocomplete visible to hidden but new tab page has content then showSearchLogo should remain false`() =
        runTest {
            val viewModel = createViewModel("search query")

            // Initially false due to autocomplete
            assertFalse(viewModel.visibilityState.value.showSearchLogo)

            // Set new tab page to have content
            viewModel.onNewTabPageContentChanged(hasContent = true)

            // Clear text to hide autocomplete
            viewModel.onSearchInputTextChanged("")

            // Should remain false because new tab page has content
            assertFalse(viewModel.visibilityState.value.showSearchLogo)
        }

    @Test
    fun `when fireShownPixel then DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN pixels is fired`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.fireShownPixel()

            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_DAILY, type = Daily())
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN_COUNT, type = Count)
        }

    @Test
    fun `when onSearchSubmitted then query submitted pixels are fired`() =
        runTest {
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)
            whenever(inputScreenSessionStore.setHasUsedSearchMode(any())).thenReturn(Unit)

            viewModel.onSearchSubmitted("query")

            val expectedParams = mapOf("text_length_bucket" to "short")
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED, parameters = expectedParams)
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY, type = Daily())
        }

    @Test
    fun `when onSearchSubmitted then inputScreenSessionUsage onSearchSubmitted is called`() =
        runTest {
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onSearchSubmitted("query")

            verify(inputScreenSessionUsageMetric).onSearchSubmitted()
        }

    @Test
    fun `when onChatSubmitted then prompt submitted pixels are fired`() =
        runTest {
            whenever(duckChat.wasOpenedBefore()).thenReturn(false)
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onChatSubmitted("prompt")

            verify(pixel).fire(
                pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED,
                parameters = mapOf(
                    DuckChatPixelParameters.WAS_USED_BEFORE to "0",
                    "text_length_bucket" to "short",
                ),
            )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY, type = Daily())
        }

    @Test
    fun `when onChatSubmitted and DuckChat was used before then prompt submitted pixel includes was_used_before parameter as 1`() =
        runTest {
            whenever(duckChat.wasOpenedBefore()).thenReturn(true)
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onChatSubmitted("prompt")

            val expectedParams =
                mapOf(
                    DuckChatPixelParameters.WAS_USED_BEFORE to "1",
                    DuckChatPixelParameters.TEXT_LENGTH_BUCKET to "short",
                )
            verify(pixel).fire(
                pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED,
                parameters = expectedParams,
            )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY, type = Daily())
        }

    @Test
    fun `when onChatSubmitted then inputScreenSessionUsage onPromptSubmitted is called`() =
        runTest {
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onChatSubmitted("prompt")

            verify(inputScreenSessionUsageMetric).onPromptSubmitted()
        }

    @Test
    fun `when onSearchSelected and user was in chat mode with text then mode switched pixel is fired with correct parameters`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onChatSelected()
            viewModel.onChatInputTextChanged("some chat text")
            viewModel.onSearchSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_search",
                    "had_text" to "true",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onSearchSelected and user was in chat mode without text then mode switched pixel is fired with correct parameters`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onChatSelected()
            viewModel.onSearchSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_search",
                    "had_text" to "false",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onSearchSelected and user was not in chat mode then mode switched pixel is not fired`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onSearchSelected()

            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED)
        }

    @Test
    fun `when onChatSelected and user was in search mode with text then mode switched pixel is fired with correct parameters`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onSearchSelected()
            viewModel.onSearchInputTextChanged("some search text")
            viewModel.onChatSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_duckai",
                    "had_text" to "true",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onChatSelected and user was in search mode without text then mode switched pixel is fired with correct parameters`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onSearchSelected()
            viewModel.onChatSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_duckai",
                    "had_text" to "false",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onChatSelected and user was not in search mode then mode switched pixel is not fired`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onChatSelected()

            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED)
        }

    @Test
    fun `when both search and chat modes used in same session then both modes pixel is fired`() =
        runTest {
            val viewModel = createViewModel()
            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(true)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(true)

            viewModel.onSearchSubmitted("query")

            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES)
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY, type = Daily())
        }

    @Test
    fun `when only search mode used then both modes pixel is not fired`() =
        runTest {
            val viewModel = createViewModel()
            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(true)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onSearchSubmitted("query")

            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES)
            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY, type = Daily())
        }

    @Test
    fun `when only chat mode used then both modes pixel is not fired`() =
        runTest {
            val viewModel = createViewModel()
            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(true)

            viewModel.onChatSubmitted("prompt")

            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES)
            verify(pixel, never()).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY, type = Daily())
        }

    @Test
    fun `when onChatSelected then new line button is visible`() {
        val viewModel = createViewModel()
        viewModel.onChatSelected()
        assertTrue(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onSearchSelected then new line button is not visible`() {
        val viewModel = createViewModel()
        viewModel.onSearchSelected()
        assertFalse(viewModel.visibilityState.value.newLineButtonVisible)
    }

    @Test
    fun `when onSearchSubmitted with newlines then they are replaced with spaces`() =
        runTest {
            val viewModel = createViewModel()
            val queryWithNewlines = "first line\nsecond line\nthird line"
            val expected = "first line second line third line"

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onSearchSubmitted(queryWithNewlines)

            assertEquals(SubmitSearch(expected), viewModel.command.value)
        }

    @Test
    fun `when userSelectedAutocomplete with chat then command and pixel sent`() =
        runTest {
            val viewModel = createViewModel()
            val query = "example"

            val duckAIPrompt = AutoComplete.AutoCompleteSuggestion.AutoCompleteDuckAIPrompt(query)

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)
            whenever(duckChat.wasOpenedBefore()).thenReturn(false)
            val params = mapOf(DuckChatPixelParameters.WAS_USED_BEFORE to false.toBinaryString())

            viewModel.userSelectedAutocomplete(duckAIPrompt)

            assertEquals(SubmitChat(query), viewModel.command.value)

            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_AUTOCOMPLETE_EXPERIMENTAL, params)
            verify(autoComplete).fireAutocompletePixel(any(), any(), any())
        }

    @Test
    fun `when onSearchSubmitted then discovery funnel onSearchSubmitted is called`() =
        runTest {
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onSearchSubmitted("query")

            verify(inputScreenDiscoveryFunnel).onSearchSubmitted()
        }

    @Test
    fun `when onChatSubmitted with prompt then discovery funnel onPromptSubmitted is called`() =
        runTest {
            val viewModel = createViewModel()

            whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
            whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

            viewModel.onChatSubmitted("prompt")

            verify(inputScreenDiscoveryFunnel).onPromptSubmitted()
        }

    @Test
    fun `when onChatInputTextChanged then chatInputTextState is updated`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onChatInputTextChanged("  test chat input  ")

            // The chatInputTextState is trimmed and stored internally
            // We can verify this indirectly by testing the mode switch pixel behavior
            viewModel.onChatSelected()
            viewModel.onSearchSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_search",
                    "had_text" to "true", // Should be true because we have trimmed text
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onChatInputTextChanged with empty text then chatInputTextState reflects empty state`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onChatInputTextChanged("some text")
            viewModel.onChatInputTextChanged("   ") // Only whitespace, should be trimmed to empty

            // Verify through mode switch behavior
            viewModel.onChatSelected()
            viewModel.onSearchSelected()

            val expectedParams =
                mapOf(
                    "direction" to "to_search",
                    "had_text" to "false", // Should be false because trimmed text is empty
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED, expectedParams)
        }

    @Test
    fun `when onSendButtonClicked in search mode then pixel is fired with search mode parameter`() =
        runTest {
            val viewModel = createViewModel()

            // Set to search mode
            viewModel.onSearchSelected()

            viewModel.onSendButtonClicked()

            val expectedParams =
                mapOf(
                    DuckChatPixelParameters.INPUT_SCREEN_MODE to "search",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED, expectedParams)
        }

    @Test
    fun `when onSendButtonClicked in chat mode then pixel is fired with aiChat mode parameter`() =
        runTest {
            val viewModel = createViewModel()

            // Set to chat mode
            viewModel.onChatSelected()

            viewModel.onSendButtonClicked()

            val expectedParams =
                mapOf(
                    DuckChatPixelParameters.INPUT_SCREEN_MODE to "aiChat",
                )
            verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_SUBMIT_PRESSED, expectedParams)
        }

    @Test
    fun `when onSearchSubmitted then correct text_length_bucket parameters are sent`() =
        runTest {
            data class TestCase(
                val query: String,
                val expectedBucket: String,
            )

            val testCases =
                listOf(
                    // 5 characters
                    TestCase("short", "short"),
                    // 33 characters
                    TestCase("this is a medium length query text", "medium"),
                    // 85 characters
                    TestCase("this is a very long query that should be categorized as long text length bucket", "long"),
                    // 15 characters - boundary
                    TestCase("exactly15chars!", "short"),
                    // 16 characters - boundary
                    TestCase("exactly16chars!!", "medium"),
                    // 40 characters - boundary
                    TestCase("this text has exactly forty characters!", "medium"),
                    // 41 characters - boundary
                    TestCase("this text has exactly forty-one characters", "long"),
                    // 100 characters - boundary
                    TestCase("this text has exactly one hundred characters to test the boundary between long and very long", "long"),
                    // 101 characters - boundary
                    TestCase("this text has exactly one hundred and one characters to test the boundary between long and very long!", "very_long"),
                )

            testCases.forEach { testCase ->
                val viewModel = createViewModel()
                whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
                whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

                viewModel.onSearchSubmitted(testCase.query)

                verify(pixel).fire(
                    DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED,
                    parameters = mapOf("text_length_bucket" to testCase.expectedBucket),
                )
                clearInvocations(pixel) // Reset mock for next iteration
            }
        }

    @Test
    fun `when onChatSubmitted then correct text_length_bucket parameters are sent`() =
        runTest {
            data class TestCase(
                val prompt: String,
                val expectedBucket: String,
            )

            val testCases =
                listOf(
                    // 5 characters
                    TestCase("short", "short"),
                    // 33 characters
                    TestCase("this is a medium length query text", "medium"),
                    // 85 characters
                    TestCase("this is a very long query that should be categorized as long text length bucket", "long"),
                    // 15 characters - boundary
                    TestCase("exactly15chars!", "short"),
                    // 16 characters - boundary
                    TestCase("exactly16chars!!", "medium"),
                    // 40 characters - boundary
                    TestCase("this text has exactly forty characters!", "medium"),
                    // 41 characters - boundary
                    TestCase("this text has exactly forty-one characters", "long"),
                    // 100 characters - boundary
                    TestCase("this text has exactly one hundred characters to test the boundary between long and very long", "long"),
                    // 101 characters - boundary
                    TestCase("this text has exactly one hundred and one characters to test the boundary between long and very long!", "very_long"),
                )

            testCases.forEach { testCase ->
                val viewModel = createViewModel()
                whenever(inputScreenSessionStore.hasUsedSearchMode()).thenReturn(false)
                whenever(inputScreenSessionStore.hasUsedChatMode()).thenReturn(false)

                viewModel.onChatSubmitted(testCase.prompt)

                val expectedParams =
                    mapOf(
                        DuckChatPixelParameters.WAS_USED_BEFORE to "0",
                        DuckChatPixelParameters.TEXT_LENGTH_BUCKET to testCase.expectedBucket,
                    )
                verify(pixel).fire(
                    DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED,
                    parameters = expectedParams,
                )
                clearInvocations(pixel) // Reset mock for next iteration
            }
        }

    @Test
    fun `when onTabTapped to position 1 and has no content then animate logo to position 1`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onTabTapped(index = 1)

            assertEquals(AnimateLogoToProgress(1f), viewModel.command.value)
        }

    @Test
    fun `when onTabTapped to position 0 and has no content then animate logo to position 0`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onPageSelected(position = 1)
            viewModel.onTabTapped(index = 0)

            assertEquals(AnimateLogoToProgress(0f), viewModel.command.value)
        }

    @Test
    fun `when onTabTapped at current position then AnimateLogoToProgress is not emitted`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            viewModel.onTabTapped(index = 0)

            assertEquals(0, capturedCommands.size)
        }

    @Test
    fun `when onTabTapped and has content then AnimateLogoToProgress is not emitted`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            viewModel.onNewTabPageContentChanged(hasContent = true)
            viewModel.onTabTapped(index = 1)

            assertEquals(0, capturedCommands.size)
        }

    @Test
    fun `when onPageScrolled at position 0 and not tap transition then SetLogoProgress and SetInputModeWidgetScrollPosition emitted`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            val position = 0
            val positionOffset = 0.3f
            val easedOffset = positionOffset * positionOffset * 2f

            viewModel.onPageScrolled(position, positionOffset)

            assertEquals(2, capturedCommands.size)
            assertEquals(SetLogoProgress(positionOffset), capturedCommands[0])
            assertEquals(SetInputModeWidgetScrollPosition(position = position, offset = easedOffset), capturedCommands[1])
        }

    @Test
    fun `when onPageScrolled at position 1 and not tap transition then SetLogoProgress and SetInputModeWidgetScrollPosition emitted`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            val position = 1
            val positionOffset = 0.7f
            val logoProgress = 1f - positionOffset
            val easedOffset = 1f - (1f - positionOffset) * (1f - positionOffset) * 2f

            viewModel.onPageScrolled(position, positionOffset)

            assertEquals(2, capturedCommands.size)
            assertEquals(SetLogoProgress(logoProgress), capturedCommands[0])
            assertEquals(SetInputModeWidgetScrollPosition(position = position, offset = easedOffset), capturedCommands[1])
        }

    @Test
    fun `when onPageScrolled and has content then SetLogoProgress is 1f`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            viewModel.onNewTabPageContentChanged(hasContent = true)

            val position = 0
            val positionOffset = 0.3f
            val easedOffset = positionOffset * positionOffset * 2f

            viewModel.onPageScrolled(position, positionOffset)

            assertEquals(2, capturedCommands.size)
            assertEquals(SetLogoProgress(1f), capturedCommands[0])
            assertEquals(SetInputModeWidgetScrollPosition(position = position, offset = easedOffset), capturedCommands[1])
        }

    @Test
    fun `when onPageScrolled during tap transition then SetLogoProgress and SetInputModeWidgetScrollPosition are not emitted`() =
        runTest {
            val viewModel = createViewModel()
            val capturedCommands = mutableListOf<Command>()

            viewModel.command.observeForever { command ->
                if (command != null) {
                    capturedCommands.add(command)
                }
            }

            viewModel.onTabTapped(index = 1)
            capturedCommands.clear()

            val position = 0
            val positionOffset = 0.3f

            viewModel.onPageScrolled(position, positionOffset)

            assertEquals(0, capturedCommands.size)
        }

    @Test
    fun `when using top bar and autocomplete suggestions are visible then bottomFadeVisible should be true`() =
        runTest {
            whenever(inputScreenConfigResolver.useTopBar()).thenReturn(true)
            val viewModel = createViewModel("search query")

            assertTrue(viewModel.visibilityState.value.bottomFadeVisible)
        }

    @Test
    fun `when using bottom bar and autocomplete suggestions are visible then bottomFadeVisible should be false`() =
        runTest {
            whenever(inputScreenConfigResolver.useTopBar()).thenReturn(false)
            val viewModel = createViewModel("search query")

            assertFalse(viewModel.visibilityState.value.bottomFadeVisible)
        }

    @Test
    fun `when using top bar and autocomplete suggestions are hidden then bottomFadeVisible should be false`() =
        runTest {
            whenever(inputScreenConfigResolver.useTopBar()).thenReturn(true)
            val viewModel = createViewModel("https://example.com")

            assertFalse(viewModel.visibilityState.value.bottomFadeVisible)
        }

    @Test
    fun `when onSubmitMessageAvailableChange called then submitButtonVisible is updated correctly`() =
        runTest {
            val viewModel = createViewModel()

            // Initially false
            assertFalse(viewModel.visibilityState.value.submitButtonVisible)

            // Set to true
            viewModel.onSubmitMessageAvailableChange(true)
            assertTrue(viewModel.visibilityState.value.submitButtonVisible)

            // Set to false
            viewModel.onSubmitMessageAvailableChange(false)
            assertFalse(viewModel.visibilityState.value.submitButtonVisible)
        }

    @Test
    fun `when onTabSwitcherTapped then emit TabSwitcherRequested Command`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onTabSwitcherTapped()

            assertEquals(Command.TabSwitcherRequested, viewModel.command.value)
        }

    @Test
    fun `when onFireButtonTapped then emit FireButtonRequested Command`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onFireButtonTapped()

            assertEquals(Command.FireButtonRequested, viewModel.command.value)
        }

    @Test
    fun `when onBrowserMenuTapped then emit MenuRequested Command`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onBrowserMenuTapped()

            assertEquals(Command.MenuRequested, viewModel.command.value)
        }

    @Test
    fun `when any button is visible then actionButtonsContainerVisible should be true`() =
        runTest {
            data class TestCase(
                val name: String,
                val setup: (InputScreenViewModel) -> Unit,
                val expectedVisible: Boolean,
            )

            val testCases =
                listOf(
                    TestCase(
                        name = "submit button visible",
                        setup = { it.onSubmitMessageAvailableChange(true) },
                        expectedVisible = true,
                    ),
                    TestCase(
                        name = "voice input button visible (default)",
                        setup = { /* voice input is visible by default in the test setup */ },
                        expectedVisible = true,
                    ),
                    TestCase(
                        name = "new line button visible",
                        setup = { it.onChatInputTextChanged("test") },
                        expectedVisible = true,
                    ),
                    TestCase(
                        name = "no buttons visible",
                        setup = {
                            // Disable voice input
                            whenever(voiceSearchAvailability.isVoiceSearchAvailable).thenReturn(false)
                            it.onActivityResume()
                            it.onSubmitMessageAvailableChange(false)
                            // newLineButtonVisible is false by default
                        },
                        expectedVisible = false,
                    ),
                )

            testCases.forEach { testCase ->
                val viewModel = createViewModel()
                testCase.setup(viewModel)
                assertEquals(
                    "Failed for test case: ${testCase.name}",
                    testCase.expectedVisible,
                    viewModel.visibilityState.value.actionButtonsContainerVisible,
                )
            }
        }
}
