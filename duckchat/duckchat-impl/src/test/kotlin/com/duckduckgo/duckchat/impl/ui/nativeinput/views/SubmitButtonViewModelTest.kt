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

class SubmitButtonViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val inputState = MutableStateFlow(duckAiState(hasText = true))
    private val accepted = MutableStateFlow(false)
    private val nativeInputStateProvider: NativeInputStateProvider = mock {
        on { state } doReturn inputState
        on { stateForTab("edit") } doReturn inputState
    }
    private val termsRepository: DuckAiTermsRepository = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)

    private lateinit var testee: SubmitButtonViewModel

    @Before
    fun setUp() {
        whenever(termsRepository.observeTermsAccepted(BrowserMode.REGULAR)).thenReturn(accepted)
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = true))
        testee = SubmitButtonViewModel(nativeInputStateProvider, termsRepository, feature, BrowserMode.REGULAR)
    }

    @Test
    fun whenTermsNotAcceptedAndThereIsInputThenAskLabelIsShown() = runTest {
        testee.viewState(editTabId = null).test {
            val state = awaitItem()

            assertTrue(state.visible)
            assertTrue(state.askLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInputIsEmptyThenNoSubmitButtonAndNoAskLabel() = runTest {
        inputState.value = duckAiState(hasText = false)

        testee.viewState(editTabId = null).test {
            val state = awaitItem()

            assertFalse(state.visible)
            assertFalse(state.askLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTermsGetAcceptedThenAskLabelGoesAway() = runTest {
        testee.viewState(editTabId = null).test {
            assertTrue(awaitItem().askLabel)

            accepted.value = true

            assertFalse(awaitItem().askLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFlagIsOffThenAskLabelIsNeverShown() = runTest {
        feature.nativeToSConsent().setRawStoredState(Toggle.State(enable = false))

        testee.viewState(editTabId = null).test {
            assertFalse(awaitItem().askLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnTheEditSurfaceThenAskLabelIsNeverShown() = runTest {
        testee.viewState(editTabId = "edit").test {
            assertFalse(awaitItem().askLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun duckAiState(hasText: Boolean) = NativeInputState(
        inputMode = InputMode.SEARCH_AND_DUCK_AI,
        inputContext = InputContext.DUCK_AI,
        toggleSelection = ToggleSelection.DUCK_AI,
        hasText = hasText,
    )
}
