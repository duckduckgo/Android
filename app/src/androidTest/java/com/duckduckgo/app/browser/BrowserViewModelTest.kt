/*
 * Copyright (c) 2017 DuckDuckGo
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
import android.content.Context
import android.net.Uri
import com.duckduckgo.app.browser.BrowserViewModel.NavigationCommand
import com.duckduckgo.app.browser.BrowserViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.trackerdetection.TrackerDetectionClient
import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.store.TrackerDataProvider
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import mock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class BrowserViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private val viewStateObserver: Observer<ViewState> = mock()

    @Mock
    private val queryObserver: Observer<String> = mock()

    @Mock
    private val navigationObserver: Observer<NavigationCommand> = mock()

    @Mock
    private val mockContext: Context = mock()

    @Mock
    private val mockTrackerService: TrackerListService = mock()


    private val testOmnibarConverter: OmnibarEntryConverter = object : OmnibarEntryConverter {
        override fun convertUri(input: String): String = "duckduckgo.com"
        override fun isWebUrl(inputQuery: String): Boolean = true
        override fun convertQueryToUri(inputQuery: String): Uri = Uri.parse("duckduckgo.com")
    }

    private lateinit var testee: BrowserViewModel

    @Before
    fun before() {
        testee = BrowserViewModel(testOmnibarConverter, TrackerDataProvider(mockContext), testTrackerDetector(), mockTrackerService, DuckDuckGoUrlDetector())
        testee.query.observeForever(queryObserver)
        testee.viewState.observeForever(viewStateObserver)
        testee.navigation.observeForever(navigationObserver)
    }

    @After
    fun after() {
        testee.query.removeObserver(queryObserver)
        testee.viewState.removeObserver(viewStateObserver)
        testee.navigation.removeObserver(navigationObserver)
    }

    @Test
    fun whenEmptyInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("")
        verify(queryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenBlankInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("     ")
        verify(queryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenNonEmptyInputThenQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("foo")
        verify(queryObserver).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsLoadingThenViewStateIsUpdated() {
        testee.loadingStateChange(true)
        assertTrue(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsNotLoadingThenViewStateIsUpdated() {
        testee.loadingStateChange(false)
        assertFalse(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatUrlFocusChangedGotFocusThenViewStateIsUpdated() {
        testee.urlFocusChanged(true)
        assertTrue(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlFocusChangedLostFocusThenViewStateIsUpdated() {
        testee.urlFocusChanged(false)
        assertFalse(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenNoUrlEverEnteredThenViewStateHasNull() {
        assertNull(testee.viewState.value!!.url)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        testee.urlChanged("duckduckgo.com")
        assertEquals("duckduckgo.com", testee.viewState.value!!.url)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged(0)
        assertEquals(0, testee.viewState.value!!.progress)

        testee.progressChanged(50)
        assertEquals(50, testee.viewState.value!!.progress)

        testee.progressChanged(100)
        assertEquals(100, testee.viewState.value!!.progress)
    }

    @Test
    fun whenUserDismissesKeyboardBeforeBrowserShownThenShouldNavigateToLandingPage() {
        testee.userDismissedKeyboard()
        verify(navigationObserver).onChanged(NavigationCommand.LANDING_PAGE)
    }

    @Test
    fun whenUserDismissesKeyboardAfterBrowserShownThenShouldNotNavigateToLandingPage() {
        testee.urlChanged("")
        verify(navigationObserver, never()).onChanged(NavigationCommand.LANDING_PAGE)
    }

    private fun testTrackerDetector(): TrackerDetector {
        val trackerDetector = TrackerDetector()
        trackerDetector.addClient(clientMock(ClientName.EASYLIST))
        trackerDetector.addClient(clientMock(ClientName.EASYPRIVACY))
        return trackerDetector
    }

    private fun clientMock(name: ClientName): TrackerDetectionClient {
        val client: TrackerDetectionClient = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), any())).thenReturn(false)
        return client
    }
}
