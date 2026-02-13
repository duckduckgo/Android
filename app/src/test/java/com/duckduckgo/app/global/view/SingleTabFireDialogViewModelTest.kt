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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DeleteBrowsingData
import com.duckduckgo.app.fire.ManualDataClearing
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin
import com.duckduckgo.app.global.view.SingleTabFireDialogViewModel.Command
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
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadStatus
import com.duckduckgo.duckchat.api.DuckChat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SingleTabFireDialogViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: SingleTabFireDialogViewModel

    private val mockFireDataStore: FireDataStore = mock()
    private val mockDataClearing: ManualDataClearing = mock()
    private val mockDataClearingWideEvent: DataClearingWideEvent = mock()
    private val mockPixel: Pixel = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockUserEventsStore: UserEventsStore = mock()
    private val mockFireButtonStore: FireButtonStore = mock()
    private val mockDispatcherProvider: DispatcherProvider = mock()
    private val mockDateProvider: DateProvider = mock()
    private val mockTabRepository: TabRepository = mock()
    private val mockWebViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val mockDownloadsRepository: DownloadsRepository = mock()
    private val mockDuckChat: DuckChat = mock()

    @Before
    fun setup() {
        whenever(mockDispatcherProvider.io()).thenReturn(coroutineTestRule.testDispatcherProvider.io())
        whenever(mockSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn("2025-12-15")

        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(0)

        runTest {
            whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)
            whenever(mockFireDataStore.getManualClearOptions()).thenReturn(emptySet())
            whenever(mockWebViewCapabilityChecker.isSupported(DeleteBrowsingData)).thenReturn(false)
            whenever(mockDownloadsRepository.getDownloads()).thenReturn(emptyList())
        }
    }

    private fun createViewModel() = SingleTabFireDialogViewModel(
        fireDataStore = mockFireDataStore,
        dataClearing = mockDataClearing,
        dataClearingWideEvent = mockDataClearingWideEvent,
        pixel = mockPixel,
        settingsDataStore = mockSettingsDataStore,
        userEventsStore = mockUserEventsStore,
        fireButtonStore = mockFireButtonStore,
        dispatcherProvider = mockDispatcherProvider,
        dateProvider = mockDateProvider,
        tabRepository = mockTabRepository,
        webViewCapabilityChecker = mockWebViewCapabilityChecker,
        downloadsRepository = mockDownloadsRepository,
        duckChat = mockDuckChat,
    )

    // region Initialization

    @Test
    fun `when initialized then viewState emits default values`() = runTest {
        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isDuckAiChatsSelected)
            assertFalse(state.isSingleTabEnabled)
            assertFalse(state.isFromTabSwitcher)
            assertFalse(state.showDuckAiSubtitle)
            assertTrue(state.showSiteDataSubtitle)
            assertFalse(state.showDownloadsSubtitle)
            assertTrue(state.shouldRestartAfterClearing)

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
    fun `when delete browsing data is supported then single tab is enabled`() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DeleteBrowsingData)).thenReturn(true)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.isSingleTabEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete browsing data is not supported then single tab is disabled`() = runTest {
        whenever(mockWebViewCapabilityChecker.isSupported(DeleteBrowsingData)).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isSingleTabEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when shown count is 0 then showSiteDataSubtitle is true`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(0)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.showSiteDataSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when shown count is 1 then showSiteDataSubtitle is true`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(1)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.showSiteDataSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when shown count is 2 then showSiteDataSubtitle is false`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(2)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showSiteDataSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when shown count is greater than 2 then showSiteDataSubtitle is false`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(5)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showSiteDataSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when started downloads exist then showDownloadsSubtitle is true`() = runTest {
        whenever(mockDownloadsRepository.getDownloads()).thenReturn(
            listOf(
                DownloadItem(
                    downloadId = 1L,
                    downloadStatus = DownloadStatus.STARTED,
                    fileName = "file.zip",
                    contentLength = 1000L,
                    createdAt = "2025-12-15",
                    filePath = "/path/file.zip",
                ),
            ),
        )

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.showDownloadsSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when no started downloads exist then showDownloadsSubtitle is false`() = runTest {
        whenever(mockDownloadsRepository.getDownloads()).thenReturn(
            listOf(
                DownloadItem(
                    downloadId = 1L,
                    downloadStatus = DownloadStatus.FINISHED,
                    fileName = "file.zip",
                    contentLength = 1000L,
                    createdAt = "2025-12-15",
                    filePath = "/path/file.zip",
                ),
            ),
        )

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showDownloadsSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when downloads list is empty then showDownloadsSubtitle is false`() = runTest {
        whenever(mockDownloadsRepository.getDownloads()).thenReturn(emptyList())

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showDownloadsSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when selected tab is duck ai and chats not selected then showDuckAiSubtitle is true`() = runTest {
        val duckAiUrl = "https://duck.ai/chat"
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = duckAiUrl, title = "Duck AI"),
        )
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.showDuckAiSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when selected tab is duck ai and chats selected then showDuckAiSubtitle is false`() = runTest {
        val duckAiUrl = "https://duck.ai/chat"
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = duckAiUrl, title = "Duck AI"),
        )
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(true)
        whenever(mockFireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS)).thenReturn(true)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showDuckAiSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when selected tab is not duck ai then showDuckAiSubtitle is false`() = runTest {
        val regularUrl = "https://example.com"
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = regularUrl, title = "Example"),
        )
        whenever(mockDuckChat.isDuckChatUrl(any())).thenReturn(false)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showDuckAiSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when no selected tab then showDuckAiSubtitle is false`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(null)

        testee = createViewModel()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.showDuckAiSubtitle)

            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region setOrigin

    @Test
    fun `when origin is tab switcher then viewState reflects tab switcher origin`() = runTest {
        testee = createViewModel()

        testee.setOrigin(FireDialogOrigin.TAB_SWITCHER)

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state.isFromTabSwitcher)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when origin is browser then viewState reflects non-tab-switcher origin`() = runTest {
        testee = createViewModel()

        testee.setOrigin(FireDialogOrigin.BROWSER)

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.isFromTabSwitcher)

            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onShow

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
    fun `when onShow called then shown count is incremented once`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(0)
        testee = createViewModel()

        testee.onShow()

        verify(mockSettingsDataStore).singleTabFireDialogShownCount = 1
    }

    @Test
    fun `when onShow called multiple times then shown count is incremented only once`() = runTest {
        whenever(mockSettingsDataStore.singleTabFireDialogShownCount).thenReturn(0)
        testee = createViewModel()

        testee.onShow()
        testee.onShow()

        verify(mockSettingsDataStore, times(1)).singleTabFireDialogShownCount = 1
    }

    // endregion

    // region onCancel

    @Test
    fun `when onCancel called then OnCancel command is sent`() = runTest {
        testee = createViewModel()

        testee.commands().test {
            testee.onCancel()

            assertEquals(Command.OnCancel, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onDeleteAllClicked

    @Test
    fun `when delete all clicked then pixels are fired`() = runTest {
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPixel).enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
        verify(mockPixel).enqueueFire(
            pixel = FIRE_DIALOG_ANIMATION,
            parameters = mapOf(FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    @Test
    fun `when delete all clicked for first time then daily pixel is fired and timestamp is stored`() = runTest {
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(null)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).storeLastFireButtonClearEventTime(any())
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete all clicked on same day then daily pixel is not fired again`() = runTest {
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(today)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPixel).enqueueFire(FIRE_DIALOG_CLEAR_PRESSED)
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING)
        verify(mockPixel, never()).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete all clicked on different day then daily pixel is fired and timestamp is updated`() = runTest {
        val yesterday = "2025-12-14"
        val today = "2025-12-15"
        whenever(mockFireButtonStore.lastEventSendTime).thenReturn(yesterday)
        whenever(mockDateProvider.getUtcIsoLocalDate()).thenReturn(today)
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).storeLastFireButtonClearEventTime(any())
        verify(mockPixel).enqueueFire(PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY)
    }

    @Test
    fun `when delete all clicked with animation enabled then play animation command is sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteAllClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.PlayAnimation, awaitItem())
            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete all clicked with animation disabled then play animation command is not sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(false)
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteAllClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete all clicked then data is cleared`() = runTest {
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearing).clearDataUsingManualFireOptions()
    }

    @Test
    fun `when delete all clicked then fire button use count is incremented`() = runTest {
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockFireButtonStore).incrementFireButtonUseCount()
    }

    @Test
    fun `when delete all clicked then user event is registered`() = runTest {
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockUserEventsStore).registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)
    }

    @Test
    fun `when delete all clicked then wide event is started and finished`() = runTest {
        testee = createViewModel()

        testee.onDeleteAllClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearingWideEvent).start(
            entryPoint = any(),
            clearOptions = any(),
        )
        verify(mockDataClearingWideEvent).finishSuccess()
    }

    @Test
    fun `when delete all clicked then clearing complete command is sent`() = runTest {
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteAllClicked()

            awaitItem() // Skip OnClearStarted
            awaitItem() // Skip PlayAnimation

            assertEquals(Command.ClearingComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onDeleteThisTabClicked

    @Test
    fun `when delete this tab clicked then shouldRestartAfterClearing is set to false`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.onDeleteThisTabClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        testee.viewState.test {
            val state = awaitItem()

            assertFalse(state.shouldRestartAfterClearing)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete this tab clicked then animation pixel is fired`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.onDeleteThisTabClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPixel).enqueueFire(
            pixel = FIRE_DIALOG_ANIMATION,
            parameters = mapOf(FIRE_ANIMATION to Pixel.PixelValues.FIRE_ANIMATION_INFERNO),
        )
    }

    @Test
    fun `when delete this tab clicked with animation enabled then play animation command is sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(true)
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteThisTabClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.PlayAnimation, awaitItem())
            assertEquals(Command.OnSingleTabClearComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete this tab clicked with animation disabled then play animation command is not sent`() = runTest {
        whenever(mockSettingsDataStore.fireAnimationEnabled).thenReturn(false)
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteThisTabClicked()

            assertEquals(Command.OnClearStarted, awaitItem())
            assertEquals(Command.OnSingleTabClearComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete this tab clicked with selected tab then single tab data is cleared`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.onDeleteThisTabClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearing).clearSingleTabData("tab1")
    }

    @Test
    fun `when delete this tab clicked without selected tab then single tab data is not cleared`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(null)
        testee = createViewModel()

        testee.onDeleteThisTabClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearing, never()).clearSingleTabData(any())
    }

    @Test
    fun `when delete this tab clicked then OnSingleTabClearComplete command is sent`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.commands().test {
            testee.onDeleteThisTabClicked()

            awaitItem() // Skip OnClearStarted
            awaitItem() // Skip PlayAnimation

            assertEquals(Command.OnSingleTabClearComplete, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when delete this tab clicked then data clearing does not use manual fire options`() = runTest {
        whenever(mockTabRepository.getSelectedTab()).thenReturn(
            TabEntity(tabId = "tab1", url = "https://example.com", title = "Example"),
        )
        testee = createViewModel()

        testee.onDeleteThisTabClicked()

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockDataClearing, never()).clearDataUsingManualFireOptions()
    }

    // endregion
}
