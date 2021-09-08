/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions
import com.duckduckgo.app.global.rating.AppEnjoymentUserEventRecorder
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class BrowserViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Mock
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var mockOmnibarEntryConverter: OmnibarEntryConverter

    @Mock
    private lateinit var mockAutomaticDataClearer: DataClearer

    @Mock
    private lateinit var mockAppEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder

    @Mock
    private lateinit var mockAppEnjoymentPromptEmitter: AppEnjoymentPromptEmitter

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockUserEventsStore: UserEventsStore

    private lateinit var testee: BrowserViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        doReturn(MutableLiveData<AppEnjoymentPromptOptions>()).whenever(mockAppEnjoymentPromptEmitter).promptType

        testee = BrowserViewModel(
            tabRepository = mockTabRepository,
            queryUrlConverter = mockOmnibarEntryConverter,
            dataClearer = mockAutomaticDataClearer,
            appEnjoymentPromptEmitter = mockAppEnjoymentPromptEmitter,
            appEnjoymentUserEventRecorder = mockAppEnjoymentUserEventRecorder,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            pixel = mockPixel
        )

        testee.command.observeForever(mockCommandObserver)

        runBlocking<Unit> {
            whenever(mockTabRepository.add()).thenReturn(TAB_ID)
            whenever(mockOmnibarEntryConverter.convertQueryToUrl(any(), any(), any())).then { it.arguments.first() }
        }
    }

    @After
    fun after() {
        if (this::testee.isInitialized) {
            testee.command.removeObserver(mockCommandObserver)
        }
    }

    @Test
    fun whenNewTabRequestedThenTabAddedToRepository() = runBlocking<Unit> {
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onNewTabRequested()
        verify(mockTabRepository).add()
    }

    @Test
    fun whenNewTabRequestedFromSourceTabThenTabAddedToRepositoryWithSourceTabId() = runBlocking<Unit> {
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onNewTabRequested("sourceTabId")
        verify(mockTabRepository).addFromSourceTab(sourceTabId = "sourceTabId")
    }

    @Test
    fun whenOpenInNewTabRequestedThenTabAddedToRepository() = runBlocking<Unit> {
        val url = "http://example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onOpenInNewTabRequested(url)
        verify(mockTabRepository).add(url = url, skipHome = false)
    }

    @Test
    fun whenOpenInNewTabRequestedWithSourceTabIdThenTabAddedToRepositoryWithSourceTabId() = runBlocking<Unit> {
        val url = "http://example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onOpenInNewTabRequested(url, sourceTabId = "tabId")
        verify(mockTabRepository).addFromSourceTab(url = url, skipHome = false, sourceTabId = "tabId")
    }

    @Test
    fun whenTabsUpdatedAndNoTabsThenDefaultTabAddedToRepository() = runBlocking<Unit> {
        testee.onTabsUpdated(ArrayList())
        verify(mockTabRepository).addDefaultTab()
    }

    @Test
    fun whenTabsUpdatedWithTabsThenNewTabNotLaunched() = runBlocking {
        testee.onTabsUpdated(listOf(TabEntity(TAB_ID, "", "", skipHome = false, viewed = true, position = 0)))
        verify(mockCommandObserver, never()).onChanged(any())
    }

    @Test
    fun whenReloadDashboardResultReceivedThenRefreshTriggered() {
        testee.receivedDashboardResult(PrivacyDashboardActivity.RELOAD_RESULT_CODE)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.Refresh, commandCaptor.lastValue)
    }

    @Test
    fun whenUnknownDashboardResultReceivedThenNoCommandTriggered() {
        testee.receivedDashboardResult(1111)
        verify(mockCommandObserver, never()).onChanged(any())
    }

    @Test
    fun whenUserSelectedToRateAppThenPlayStoreCommandTriggered() {
        testee.onUserSelectedToRateApp(PromptCount.first())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchPlayStore, commandCaptor.lastValue)
    }

    @Test
    fun whenUserSelectedToGiveFeedbackThenFeedbackCommandTriggered() {
        testee.onUserSelectedToGiveFeedback(PromptCount.first())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchFeedbackView, commandCaptor.lastValue)
    }

    @Test
    fun whenViewStateCreatedThenWebViewContentShouldBeHidden() {
        assertTrue(testee.viewState.value!!.hideWebContent)
    }

    @Test
    fun whenOpenShortcutThenSelectByUrlOrNewTab() = coroutinesTestRule.runBlocking {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenShortcut(url)
        verify(mockTabRepository).selectByUrlOrNewTab(url)
    }

    @Test
    fun whenOpenShortcutThenFirePixel() {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenShortcut(url)
        verify(mockPixel).fire(AppPixelName.SHORTCUT_OPENED)
    }

    @Test
    fun whenOpenFavoriteThenSelectByUrlOrNewTab() = coroutinesTestRule.runBlocking {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenFavoriteFromWidget(url)
        verify(mockTabRepository).selectByUrlOrNewTab(url)
    }

    @Test
    fun whenOpenFavoriteFromWidgetThenFirePixel() = coroutinesTestRule.runBlocking {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenFavoriteFromWidget(url)
        verify(mockPixel).fire(AppPixelName.APP_FAVORITES_ITEM_WIDGET_LAUNCH)
    }

    companion object {
        const val TAB_ID = "TAB_ID"
    }
}
