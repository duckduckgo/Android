/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.firebutton

import app.cash.turbine.test
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.firebutton.AutomaticDataClearingSettingsViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class AutomaticDataClearingSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AutomaticDataClearingSettingsViewModel

    private val mockFireDataStore: FireDataStore = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()
    private val mockPixel: Pixel = mock()

    private val duckAiShowClearDuckAIChatHistoryFlow = MutableStateFlow(false)
    private val automaticClearOptionsFlow = MutableStateFlow<Set<FireClearOption>>(emptySet())
    private val clearWhenOptionFlow = MutableStateFlow(ClearWhenOption.APP_EXIT_ONLY)

    @Before
    fun before() = runTest {
        whenever(mockDuckAiFeatureState.showClearDuckAIChatHistory).thenReturn(duckAiShowClearDuckAIChatHistoryFlow)
        whenever(mockFireDataStore.getAutomaticClearOptionsFlow()).thenReturn(automaticClearOptionsFlow)
        whenever(mockFireDataStore.getAutomaticallyClearWhenOptionFlow()).thenReturn(clearWhenOptionFlow)
        whenever(mockFireDataStore.getAutomaticClearOptions()).thenReturn(emptySet())
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )
    }

    @Test
    fun whenNoOptionsSelectedThenAutomaticClearingIsDisabled() = runTest {
        automaticClearOptionsFlow.value = emptySet()

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.automaticClearingEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOptionsSelectedThenAutomaticClearingIsEnabled() = runTest {
        automaticClearOptionsFlow.value = setOf(FireClearOption.TABS)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.automaticClearingEnabled)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenTabsOptionSelectedThenClearTabsIsTrue() = runTest {
        automaticClearOptionsFlow.value = setOf(FireClearOption.TABS)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.clearTabs)
            assertFalse(state.clearData)
            assertFalse(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDataOptionSelectedThenClearDataIsTrue() = runTest {
        automaticClearOptionsFlow.value = setOf(FireClearOption.DATA)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.clearTabs)
            assertTrue(state.clearData)
            assertFalse(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiChatsOptionSelectedThenClearDuckAiChatsIsTrue() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)
        automaticClearOptionsFlow.value = setOf(FireClearOption.DUCKAI_CHATS)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.clearTabs)
            assertFalse(state.clearData)
            assertTrue(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAllOptionsSelectedThenAllFlagsAreTrue() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)
        automaticClearOptionsFlow.value = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.clearTabs)
            assertTrue(state.clearData)
            assertTrue(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenClearWhenOptionChangedThenStateIsUpdated() = runTest {
        clearWhenOptionFlow.value = ClearWhenOption.APP_EXIT_OR_15_MINS

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(ClearWhenOption.APP_EXIT_OR_15_MINS, state.clearWhenOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiUsedAndFFEnabledThenShowDuckAiChatsOptionIsTrue() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.showDuckAiChatsOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiNotUsedThenShowDuckAiChatsOptionIsFalse() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showDuckAiChatsOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiFFDisabledThenShowDuckAiChatsOptionIsFalse() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = false
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.showDuckAiChatsOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnlyDuckAiChatsSelectedAndDuckAiNotAvailableThenAutomaticClearingIsDisabled() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = false
        automaticClearOptionsFlow.value = setOf(FireClearOption.DUCKAI_CHATS)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.automaticClearingEnabled)
            assertFalse(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiChatsAndOtherOptionsSelectedAndDuckAiNotAvailableThenOnlyOtherOptionsAreEnabled() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = false
        automaticClearOptionsFlow.value = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.automaticClearingEnabled)
            assertTrue(state.clearTabs)
            assertTrue(state.clearData)
            assertFalse(state.clearDuckAiChats)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiChatsSelectedAndDuckChatNotOpenedBeforeThenDuckAiChatsIsFiltered() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)
        automaticClearOptionsFlow.value = setOf(FireClearOption.DUCKAI_CHATS)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.automaticClearingEnabled)
            assertFalse(state.clearDuckAiChats)
            assertFalse(state.showDuckAiChatsOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiChatsSelectedAndDuckAiFullyAvailableThenDuckAiChatsIsEnabled() = runTest {
        duckAiShowClearDuckAIChatHistoryFlow.value = true
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)
        automaticClearOptionsFlow.value = setOf(FireClearOption.DUCKAI_CHATS)

        testee = AutomaticDataClearingSettingsViewModel(
            mockFireDataStore,
            mockDuckChat,
            mockDuckAiFeatureState,
            coroutineTestRule.testDispatcherProvider,
            mockPixel,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.automaticClearingEnabled)
            assertTrue(state.clearDuckAiChats)
            assertTrue(state.showDuckAiChatsOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAutomaticClearingToggledOnThenDefaultOptionsAreSet() = runTest {
        testee.onAutomaticClearingToggled(true)

        verify(mockFireDataStore).setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
    }

    @Test
    fun whenAutomaticClearingToggledOffThenOptionsAreCleared() = runTest {
        testee.onAutomaticClearingToggled(false)

        verify(mockFireDataStore).setAutomaticClearOptions(emptySet())
    }

    @Test
    fun whenOptionToggledOnThenOptionIsAdded() = runTest {
        testee.onOptionToggled(FireClearOption.TABS, true)

        verify(mockFireDataStore).addAutomaticClearOption(FireClearOption.TABS)
    }

    @Test
    fun whenOptionToggledOffThenOptionIsRemoved() = runTest {
        testee.onOptionToggled(FireClearOption.DATA, false)

        verify(mockFireDataStore).removeAutomaticClearOption(FireClearOption.DATA)
    }

    @Test
    fun whenClearWhenClickedThenShowClearWhenDialogCommandIsSent() = runTest {
        whenever(mockFireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_OR_30_MINS)

        testee.commands.test {
            testee.onClearWhenClicked()

            assertEquals(Command.ShowClearWhenDialog(ClearWhenOption.APP_EXIT_OR_30_MINS), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenClearWhenClickedThenPixelIsFired() = runTest {
        whenever(mockFireDataStore.getAutomaticallyClearWhenOption()).thenReturn(ClearWhenOption.APP_EXIT_ONLY)

        testee.onClearWhenClicked()

        verify(mockPixel).fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    @Test
    fun whenClearWhenOptionSelectedThenOptionIsSaved() = runTest {
        testee.onClearWhenOptionSelected(ClearWhenOption.APP_EXIT_OR_60_MINS)

        verify(mockFireDataStore).setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_60_MINS)
    }

    @Test
    fun whenNoOptionsModifiedThenOnScreenExitDoesNotFirePixel() = runTest {
        testee.onScreenExit()

        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenOptionsModifiedThenOnScreenExitFiresPixelWithCurrentOptions() = runTest {
        whenever(mockFireDataStore.getAutomaticClearOptions()).thenReturn(
            setOf(FireClearOption.TABS, FireClearOption.DUCKAI_CHATS),
        )

        testee.onAutomaticClearingToggled(true)
        testee.onScreenExit()

        verify(mockPixel).fire(
            AppPixelName.DATA_CLEARING_AUTOMATIC_OPTIONS_UPDATED,
            mapOf(
                "tabs" to "true",
                "data" to "false",
                "chats" to "true",
            ),
        )
    }

    @Test
    fun whenOnScreenExitCalledMultipleTimesWithoutChangesThenPixelFiresOnlyOnce() = runTest {
        whenever(mockFireDataStore.getAutomaticClearOptions()).thenReturn(
            setOf(FireClearOption.TABS),
        )

        testee.onScreenExit()
        testee.onScreenExit()
        testee.onScreenExit()

        verify(mockPixel, times(1)).fire(
            AppPixelName.DATA_CLEARING_AUTOMATIC_OPTIONS_UPDATED,
            mapOf(
                "tabs" to "true",
                "data" to "false",
                "chats" to "false",
            ),
        )
    }
}
