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

package com.duckduckgo.app.global.view

import android.net.Uri
import app.cash.turbine.test
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.GranularFireDialogViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_ANIMATION
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_CLEAR_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.FIRE_DIALOG_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING
import com.duckduckgo.app.pixels.AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DateProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDateTime

class GranularFireDialogViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: GranularFireDialogViewModel

    private val mockTabRepository: TabRepository = mock()
    private val mockFireDataStore: FireDataStore = mock()
    private val mockDataClearing: ManualDataClearing = mock()
    private val mockPixel: Pixel = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockUserEventsStore: UserEventsStore = mock()
    private val mockFireButtonStore: FireButtonStore = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockNavigationHistory: NavigationHistory = mock()
    private val mockDateProvider: DateProvider = mock()
    private val dataClearingWideEvent: DataClearingWideEvent = mock()

    private val tabsFlow = MutableStateFlow<List<TabEntity>>(emptyList())
    private val selectedOptionsFlow = MutableStateFlow<Set<FireClearOption>>(emptySet())

    @Before
    fun setup() {
        whenever(mockTabRepository.flowTabs).thenReturn(tabsFlow)
        whenever(mockFireDataStore.getManualClearOptionsFlow()).thenReturn(selectedOptionsFlow)
        whenever(mockDispatcherProvider.io()).thenReturn(coroutineTestRule.testDispatcherProvider.io())
        whenever(mockSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn("2025-12-15")

        runTest {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)
            whenever(mockNavigationHistory.isHistoryUserEnabled()).thenReturn(false)
            whenever(mockNavigationHistory.getHistory()).thenReturn(flowOf(emptyList()))
        }
    }

    private fun createViewModel() = GranularFireDialogViewModel(
        tabRepository = mockTabRepository,
        fireDataStore = mockFireDataStore,
        dataClearing = mockDataClearing,
        pixel = mockPixel,
        settingsDataStore = mockSettingsDataStore,
        userEventsStore = mockUserEventsStore,
        fireButtonStore = mockFireButtonStore,
        dispatcherProvider = mockDispatcherProvider,
        duckChat = mockDuckChat,
        navigationHistory = mockNavigationHistory,
        dateProvider = mockDateProvider,
        dataClearingWideEvent = dataClearingWideEvent,
    )

    @Test
    fun `when initialized then viewState emits default values`() = runTest {
        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertEquals(0, state.tabCount)
            assertEquals(0, state.siteCount)
            assertFalse(state.isHistoryEnabled)
            assertTrue(state.selectedOptions.isEmpty())
            assertFalse(state.isDuckChatClearingEnabled)
            assertFalse(state.isDeleteButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when tabs are present then viewState reflects tab count`() = runTest {
        testee = createViewModel()
        val tabs = listOf(
            TabEntity(tabId = "1", url = "https://example.com", title = "Example"),
            TabEntity(tabId = "2", url = "https://test.com", title = "Test"),
        )

        testee.viewState.test {
            skipItems(1) // Skip initial state

            tabsFlow.value = tabs

            val state = awaitItem()
            assertEquals(2, state.tabCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when options are selected then viewState reflects selected options`() = runTest {
        testee = createViewModel()
        val options = setOf(FireClearOption.TABS, FireClearOption.DATA)

        testee.viewState.test {
            skipItems(1) // Skip initial state

            selectedOptionsFlow.value = options

            val state = awaitItem()
            assertEquals(options, state.selectedOptions)
            assertTrue(state.isDeleteButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when duck chat was opened before then viewState shows duck chat clearing enabled`() = runTest {
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.isDuckChatClearingEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when duck chat was not opened then viewState shows duck chat clearing disabled`() = runTest {
        whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isDuckChatClearingEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when history is enabled then viewState reflects history enabled and site count`() = runTest {
        val uri1 = mock<Uri>().apply {
            whenever(host).thenReturn("example.com")
        }
        val uri2 = mock<Uri>().apply {
            whenever(host).thenReturn("test.com")
        }
        val historyEntries = listOf(
            HistoryEntry.VisitedPage(
                url = uri1,
                title = "Example",
                visits = listOf(LocalDateTime.now()),
            ),
            HistoryEntry.VisitedPage(
                url = uri2,
                title = "Test",
                visits = listOf(LocalDateTime.now()),
            ),
        )
        whenever(mockNavigationHistory.isHistoryUserEnabled()).thenReturn(true)
        whenever(mockNavigationHistory.getHistory()).thenReturn(flowOf(historyEntries))

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.isHistoryEnabled)
            assertEquals(2, state.siteCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when history is disabled then viewState reflects history disabled and zero site count`() = runTest {
        whenever(mockNavigationHistory.isHistoryUserEnabled()).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isHistoryEnabled)
            assertEquals(0, state.siteCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when history has duplicate domains then site count reflects unique domains`() = runTest {
        val uri1 = mock<Uri>().apply {
            whenever(host).thenReturn("example.com")
        }
        val uri2 = mock<Uri>().apply {
            whenever(host).thenReturn("example.com")
        }
        val uri3 = mock<Uri>().apply {
            whenever(host).thenReturn("test.com")
        }
        val historyEntries = listOf(
            HistoryEntry.VisitedPage(
                url = uri1,
                title = "Page 1",
                visits = listOf(LocalDateTime.now()),
            ),
            HistoryEntry.VisitedPage(
                url = uri2,
                title = "Page 2",
                visits = listOf(LocalDateTime.now()),
            ),
            HistoryEntry.VisitedPage(
                url = uri3,
                title = "Test",
                visits = listOf(LocalDateTime.now()),
            ),
        )
        whenever(mockNavigationHistory.isHistoryUserEnabled()).thenReturn(true)
        whenever(mockNavigationHistory.getHistory()).thenReturn(flowOf(historyEntries))

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertEquals(2, state.siteCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when no options selected then delete button is disabled`() = runTest {
        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.selectedOptions.isEmpty())
            assertFalse(state.isDeleteButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when options selected then delete button is enabled`() = runTest {
        testee = createViewModel()

        testee.viewState.test {
            skipItems(1)

            selectedOptionsFlow.value = setOf(FireClearOption.TABS)

            val state = awaitItem()
            assertTrue(state.isDeleteButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when option toggled on then option is added to store`() = runTest {
        testee = createViewModel()

        testee.onOptionToggled(FireClearOption.TABS, isChecked = true)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireDataStore).addManualClearOption(FireClearOption.TABS)
    }

    @Test
    fun `when option toggled off then option is removed from store`() = runTest {
        testee = createViewModel()

        testee.onOptionToggled(FireClearOption.DATA, isChecked = false)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireDataStore).removeManualClearOption(FireClearOption.DATA)
    }

    @Test
    fun `when delete clicked then pixels are fired`() = runTest {
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPixel).enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
        verify(mockPixel).enqueueFire(
            pixel = FIRE_DIALOG_ANIMATION,
            parameters = mapOf(FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    @Test
    fun `when delete clicked for first time then daily pixel is fired and timestamp is stored`() = runTest {
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(null)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).storeLastFireButtonClearEventTime(any())
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete clicked on same day then daily pixel is not fired again`() = runTest {
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(today)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPixel).enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
        verify(mockPixel, never()).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete clicked on different day then daily pixel is fired and timestamp is updated`() = runTest {
        val yesterday = "2025-12-14"
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(yesterday)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).storeLastFireButtonClearEventTime(any())
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete clicked with animation enabled then play animation command is sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.PlayAnimation, awaitItem())
            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete clicked with animation disabled then play animation command is not sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(false)
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete clicked then data is cleared`() = runTest {
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearing).clearDataUsingManualFireOptions()
    }

    @Test
    fun `when delete clicked then fire button use count is incremented`() = runTest {
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).incrementFireButtonUseCount()
    }

    @Test
    fun `when delete clicked then user event is registered`() = runTest {
        testee = createViewModel()

        testee.onDeleteClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockUserEventsStore).registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
    }

    @Test
    fun `when delete clicked then clearing complete command is sent`() = runTest {
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteClicked()

            awaitItem() // Skip OnClearStarted
            awaitItem() // Skip PlayAnimation

            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete clicked with multiple options then all operations execute in correct order`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        testee = createViewModel()
        selectedOptionsFlow.value = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS)

        testee.commands().test {
            testee.onDeleteClicked()

            coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

            verify(mockPixel).enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
            verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
            verify(mockPixel).enqueueFire(
                pixel = FIRE_DIALOG_ANIMATION,
                parameters = mapOf(FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
            )

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.PlayAnimation, awaitItem())

            verify(mockFireButtonStore).incrementFireButtonUseCount()
            verify(mockUserEventsStore).registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
            verify(mockDataClearing).clearDataUsingManualFireOptions()

            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when onShow called then OnShow command is sent`() = runTest {
        testee = createViewModel()

        testee.commands().test {
            testee.onShow()

            assertEquals(Command.OnShow, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when onShow called then FIRE_DIALOG_SHOWN pixel is fired`() = runTest {
        testee = createViewModel()

        testee.onShow()

        verify(mockPixel).fire(FIRE_DIALOG_SHOWN)
    }

    @Test
    fun `when onShow called multiple times then FIRE_DIALOG_SHOWN pixel is fired only once`() = runTest {
        testee = createViewModel()

        testee.onShow()
        testee.onShow()

        verify(mockPixel, times(1)).fire(FIRE_DIALOG_SHOWN)
    }

    @Test
    fun `when onCancel called then OnCancel command is sent`() = runTest {
        testee = createViewModel()

        testee.commands().test {
            testee.onCancel()

            assertEquals(Command.OnCancel, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }
}
