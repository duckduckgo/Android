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
package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.terms.DuckAiTermsRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VoiceChatViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val inputState = MutableStateFlow(emptyDuckAiState())
    private val accepted = MutableStateFlow(true)
    private val nativeInputStateProvider: NativeInputStateProvider = mock {
        on { state } doReturn inputState
        on { stateForTab("edit") } doReturn inputState
    }
    private val termsRepository: DuckAiTermsRepository = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)

    private lateinit var testee: VoiceChatViewModel

    @Before
    fun setUp() {
        whenever(termsRepository.observeTermsAccepted(BrowserMode.REGULAR)).thenReturn(accepted)
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = true))
        testee = VoiceChatViewModel(nativeInputStateProvider, termsRepository, feature, BrowserMode.REGULAR)
    }

    @Test
    fun whenTermsAcceptedAndInputIsEmptyThenVoiceIsAvailable() = runTest {
        testee.available(editTabId = null).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTermsNotAcceptedThenVoiceIsNotAvailable() = runTest {
        accepted.value = false

        testee.available(editTabId = null).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTermsGetAcceptedThenVoiceBecomesAvailable() = runTest {
        accepted.value = false

        testee.available(editTabId = null).test {
            assertFalse(awaitItem())

            accepted.value = true

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFlagIsOffThenVoiceIsAvailableEvenWithTermsNotAccepted() = runTest {
        accepted.value = false
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = false))

        testee.available(editTabId = null).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnTheEditSurfaceThenVoiceIgnoresTheTerms() = runTest {
        accepted.value = false

        testee.available(editTabId = "edit").test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenThereIsTextThenVoiceIsNotAvailable() = runTest {
        inputState.value = emptyDuckAiState().copy(hasText = true)

        testee.available(editTabId = null).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun emptyDuckAiState() = NativeInputState(
        inputMode = InputMode.SEARCH_AND_DUCK_AI,
        inputContext = InputContext.DUCK_AI,
        toggleSelection = ToggleSelection.DUCK_AI,
        voiceChatAvailable = true,
    )
}
