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

import app.cash.turbine.test
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.NonGranularFireDialogViewModel.Command
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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DateProvider
import com.duckduckgo.common.utils.DispatcherProvider
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

class NonGranularFireDialogViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: NonGranularFireDialogViewModel

    private val mockFireDataStore: FireDataStore = mock()
    private val mockDataClearing: ManualDataClearing = mock()
    private val mockPixel: Pixel = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockUserEventsStore: UserEventsStore = mock()
    private val mockFireButtonStore: FireButtonStore = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockDateProvider: DateProvider = mock()
    private val dataClearingWideEvent: DataClearingWideEvent = mock()

    @Before
    fun setup() {
        whenever(mockDispatcherProvider.io()).thenReturn(coroutineTestRule.testDispatcherProvider.io())
        whenever(mockSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn("2025-12-15")

        runTest {
            whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)
        }
    }

    private fun createViewModel() = NonGranularFireDialogViewModel(
        fireDataStore = mockFireDataStore,
        dataClearing = mockDataClearing,
        pixel = mockPixel,
        settingsDataStore = mockSettingsDataStore,
        userEventsStore = mockUserEventsStore,
        fireButtonStore = mockFireButtonStore,
        dispatcherProvider = mockDispatcherProvider,
        dateProvider = mockDateProvider,
        dataClearingWideEvent = dataClearingWideEvent,
    )

    @Test
    fun `when initialized then viewState emits default values`() = runTest {
        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isDuckAiChatsSelected)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when duck ai chats selected in data store then viewState reflects selection`() = runTest {
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(true)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.isDuckAiChatsSelected)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when duck ai chats not selected in data store then viewState reflects deselection`() = runTest {
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isDuckAiChatsSelected)

            cancelAndConsumeRemainingEvents()
        }
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
