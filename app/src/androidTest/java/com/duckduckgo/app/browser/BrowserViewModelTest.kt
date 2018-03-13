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

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.DisplayMessage
import com.duckduckgo.app.browser.BrowserViewModel.Command.NewTab
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.nhaarman.mockito_kotlin.lastValue
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.Arrays.asList

class BrowserViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockCommandObserver: Observer<BrowserViewModel.Command>

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<BrowserViewModel.Command>

    @Mock
    private lateinit var mockTabRepository: TabRepository

    private lateinit var testee: BrowserViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = BrowserViewModel(mockTabRepository)
        testee.command.observeForever(mockCommandObserver)
        whenever(mockTabRepository.add()).thenReturn(TAB_ID)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenNewSearchRequestedThenNewTabTriggered() {
        testee.onNewSearchRequested()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, NewTab(TAB_ID))
    }

    @Test
    fun whenNewTabRequestedThenNewTabTriggered() {
        testee.onNewTabRequested("http://example.com")
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, NewTab(TAB_ID, "http://example.com"))
    }

    @Test
    fun whenSharedTextReceivedThenNewTabWithQueryTriggered() {
        testee.onSharedTextReceived("a query")
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, NewTab(TAB_ID, "a query"))
    }

    @Test
    fun whenTabsUpdatedAndNoTabsThenNewTabLaunched() {
        testee.onTabsUpdated(ArrayList())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, NewTab(TAB_ID))
    }

    @Test
    fun whenTabsUpdatedWithTabsThenNewTabNotLaunched() {
        testee.onTabsUpdated(asList(TabEntity(TAB_ID, "", "")))
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenReloadDashboardResultReceivedThenRefreshTriggered() {
        testee.receivedDashboardResult(PrivacyDashboardActivity.RELOAD_RESULT_CODE)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, Command.Refresh)
    }

    @Test
    fun whenUnknownDashboardResultReceivedThenNoCommandTriggered() {
        testee.receivedDashboardResult(1111)
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenClearRequestedThenDeleteAllCalledOnRepository() {
        testee.onClearRequested()
        verify(mockTabRepository).deleteAll()
    }

    @Test
    fun whenClearCompleteThenMessageDisplayed() {
        testee.onClearComplete()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(commandCaptor.lastValue, DisplayMessage(R.string.fireDataCleared))
    }

    companion object {
        const val TAB_ID = "TAB_ID"
    }

}