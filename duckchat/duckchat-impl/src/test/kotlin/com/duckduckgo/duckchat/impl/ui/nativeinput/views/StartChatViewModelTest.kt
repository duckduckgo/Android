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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.ToggleSelection
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StartChatViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val featureShowSettingsFlow = MutableStateFlow(true)
    private val enableDuckChatFlow = MutableStateFlow(true)
    private val providerStateFlow = MutableSharedFlow<NativeInputState>(replay = 1)

    private val duckAiFeatureState: DuckAiFeatureState = mock<DuckAiFeatureState>().also {
        whenever(it.showSettings).thenReturn(featureShowSettingsFlow)
    }
    private val duckChatInternal: DuckChatInternal = mock<DuckChatInternal>().also {
        whenever(it.observeEnableDuckChatUserSetting()).thenReturn(enableDuckChatFlow)
    }
    private val nativeInputStateProvider: NativeInputStateProvider = mock<NativeInputStateProvider>().also {
        whenever(it.state).thenReturn(providerStateFlow)
    }

    private val testee = StartChatViewModel(duckAiFeatureState, duckChatInternal, nativeInputStateProvider)

    @Test
    fun whenSearchOnlyModeAndSearchToggleAndDuckAiEnabledThenVisible() = runTest {
        featureShowSettingsFlow.value = true
        enableDuckChatFlow.value = true
        providerStateFlow.emit(stateOf(InputMode.SEARCH_ONLY, ToggleSelection.SEARCH))

        testee.isVisible.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFeatureDisabledThenNotVisible() = runTest {
        featureShowSettingsFlow.value = false
        enableDuckChatFlow.value = true
        providerStateFlow.emit(stateOf(InputMode.SEARCH_ONLY, ToggleSelection.SEARCH))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSettingDisabledThenNotVisible() = runTest {
        featureShowSettingsFlow.value = true
        enableDuckChatFlow.value = false
        providerStateFlow.emit(stateOf(InputMode.SEARCH_ONLY, ToggleSelection.SEARCH))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInputModeIsSearchAndDuckAiThenNotVisible() = runTest {
        providerStateFlow.emit(stateOf(InputMode.SEARCH_AND_DUCK_AI, ToggleSelection.SEARCH))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleSelectionIsDuckAiThenNotVisible() = runTest {
        providerStateFlow.emit(stateOf(InputMode.SEARCH_ONLY, ToggleSelection.DUCK_AI))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStateLaterChangesToVisibleThenIsVisibleEmitsTrue() = runTest {
        providerStateFlow.emit(stateOf(InputMode.SEARCH_AND_DUCK_AI, ToggleSelection.SEARCH))

        testee.isVisible.test {
            assertFalse(awaitItem())

            providerStateFlow.emit(stateOf(InputMode.SEARCH_ONLY, ToggleSelection.SEARCH))

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stateOf(mode: InputMode, toggle: ToggleSelection): NativeInputState = NativeInputState(
        inputMode = mode,
        inputContext = InputContext.BROWSER,
        toggleSelection = toggle,
    )
}
