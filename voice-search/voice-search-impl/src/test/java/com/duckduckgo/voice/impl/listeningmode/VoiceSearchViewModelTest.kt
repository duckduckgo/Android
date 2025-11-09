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

package com.duckduckgo.voice.impl.listeningmode

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.impl.listeningmode.OnDeviceSpeechRecognizer.Event
import com.duckduckgo.voice.impl.listeningmode.VoiceSearchViewModel.Command
import com.duckduckgo.voice.impl.listeningmode.VoiceSearchViewModel.ViewState
import com.duckduckgo.voice.store.VoiceSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

class VoiceSearchViewModelTest {
    private lateinit var testee: VoiceSearchViewModel

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var speechRecognizer: OnDeviceSpeechRecognizer

    @Mock
    private lateinit var voiceSearchRepository: VoiceSearchRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = VoiceSearchViewModel(speechRecognizer, voiceSearchRepository)
    }

    @Test
    fun whenStartThenStartSpeechRecognizer() {
        testee.startVoiceSearch()

        verify(speechRecognizer).start(any())
    }

    @Test
    fun whenStopThenStopSpeechRecognizer() {
        testee.stopVoiceSearch()

        verify(speechRecognizer).stop()
    }

    @Test
    fun whenStartThenEmitInitialViewState() = runTest {
        testee.startVoiceSearch()

        testee.viewState().test {
            assertEquals(ViewState("", ""), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPartialResultsReceivedThenEmitNewViewState() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.PartialResultReceived("This is the result"))

        testee.viewState().test {
            assertEquals(ViewState("This is the result", ""), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }

        testee.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenPartialResultsExceeds30WordsThenEmitNewViewStateAndEmitHandleSpeechRecognitionSuccessCommand() = runTest {
        val result = "fermentum leo vel orci porta non pulvinar neque laoreet suspendisse interdum consectetur libero id faucibus nisl tincidunt " +
            "eget nullam non nisi est sit amet facilisis magna etiam tempor orci eu lobortis"
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.PartialResultReceived(result))

        testee.viewState().test {
            assertEquals(ViewState(result, ""), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }

        testee.commands().test {
            assertEquals(Command.HandleSpeechRecognitionSuccess(result), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVolumeUpdateReceivedThenEmitUpdateVoiceIndicatorCommand() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.VolumeUpdateReceived(10f))

        testee.commands().test {
            assertEquals(Command.UpdateVoiceIndicator(10f), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRecognitionSuccessThenEmitHandleSpeechRecognitionSuccessCommand() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.RecognitionSuccess("Final result"))

        testee.commands().test {
            assertEquals(Command.HandleSpeechRecognitionSuccess("Final result"), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRecognitionTimedoutWithNoPartialResultThenEmitTerminateVoiceSearch() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.RecognitionTimedOut(0))

        testee.commands().test {
            assertEquals(Command.TerminateVoiceSearch(0), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRecognitionFailsThenEmitTerminateVoiceSearch() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.RecognitionFailed(0))

        testee.commands().test {
            assertEquals(Command.TerminateVoiceSearch(0), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRecognitionTimedoutWithPartialResultThenEmiHandleSpeechRecognitionSuccessCommand() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())

        captor.firstValue.invoke(Event.PartialResultReceived("This is the result"))
        captor.firstValue.invoke(Event.RecognitionTimedOut(0))

        testee.commands().test {
            assertEquals(Command.HandleSpeechRecognitionSuccess("This is the result"), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenViewModelRestartedWithNoPartiaLResultThenEmitViewStateWithNoUnsentResult() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())
        testee.startVoiceSearch()
        captor.firstValue.invoke(Event.PartialResultReceived("First"))

        testee.viewState().test {
            assertEquals(ViewState("First", ""), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPartialResultReceivedAndViewModelRestartedThenEmitViewStateWithUnsentResult() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())
        captor.firstValue.invoke(Event.PartialResultReceived("First"))

        testee.startVoiceSearch()

        testee.viewState().test {
            assertEquals(ViewState("First", "First"), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenViewModelRestartedAndRecognitionSuccessEmittedThenEmitViewStateWithAppendedResultAndUnsentResult() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())
        captor.firstValue.invoke(Event.PartialResultReceived("First"))
        testee.startVoiceSearch()

        captor.firstValue.invoke(Event.RecognitionSuccess("Second"))

        testee.commands().test {
            assertEquals(Command.HandleSpeechRecognitionSuccess("First Second"), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserInitiatesSearchCompleteThenEmitHandleSpeechRecognitionSuccessCommand() = runTest {
        val captor = argumentCaptor<(Event) -> Unit>()
        testee.startVoiceSearch()
        verify(speechRecognizer).start(captor.capture())
        captor.firstValue.invoke(Event.PartialResultReceived("Test"))

        testee.userInitiatesSearchComplete()

        testee.commands().test {
            assertEquals(Command.HandleSpeechRecognitionSuccess("Test"), expectMostRecentItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUpdateSelectedModeWithDuckAiThenUseDuckAiMode() = runTest {
        testee.updateSelectedMode(VoiceSearchMode.DUCK_AI)

        testee.viewState().test {
            val state = expectMostRecentItem()
            assertEquals(VoiceSearchMode.DUCK_AI, state.selectedMode)
            cancelAndConsumeRemainingEvents()
        }
        verify(voiceSearchRepository).setLastSelectedMode(VoiceSearchMode.DUCK_AI)
    }

    @Test
    fun whenUpdateSelectedModeWithSearchThenUseSearchMode() = runTest {
        testee.updateSelectedMode(VoiceSearchMode.SEARCH)

        testee.viewState().test {
            val state = expectMostRecentItem()
            assertEquals(VoiceSearchMode.SEARCH, state.selectedMode)
            cancelAndConsumeRemainingEvents()
        }
        verify(voiceSearchRepository).setLastSelectedMode(VoiceSearchMode.SEARCH)
    }
}
