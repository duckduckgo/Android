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

package com.duckduckgo.app.browser.pageload

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WideEventPageLoadManagerTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var pageLoadWideEvent: PageLoadWideEvent
    private lateinit var manager: PageLoadManager

    @Before
    fun setup() {
        pageLoadWideEvent = mock()
        manager = WideEventPageLoadManager(
            pageLoadWideEvent = pageLoadWideEvent,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenOnPageStartedCalled_andNotInProgressThenCallsStartPageLoad() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com"
        whenever(pageLoadWideEvent.isInProgress(tabId)).thenReturn(false)

        manager.onPageStarted(tabId, url)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId)
        verify(pageLoadWideEvent).startPageLoad(tabId)
    }

    @Test
    fun whenOnPageStartedCalled_andAlreadyInProgressThenDoesNotCallStartPageLoad() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com"
        whenever(pageLoadWideEvent.isInProgress(tabId)).thenReturn(true)

        manager.onPageStarted(tabId, url)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId)
        verify(pageLoadWideEvent, org.mockito.kotlin.never()).startPageLoad(org.mockito.kotlin.any())
    }

    @Test
    fun whenOnPageVisibleCalledThenCallsRecordPageVisible() = runTest {
        val tabId = "tab-123"
        val url = "https://espn.com/nfl"
        val progress = 42

        manager.onPageVisible(tabId, url, progress)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).recordPageVisible(tabId, progress)
    }

    @Test
    fun whenOnProgressChangedCalledThenCallsRecordExitedFixedProgress() = runTest {
        val tabId = "tab-123"
        val url = "https://en.wikipedia.org/wiki/DuckDuckGo"
        val progress = 75

        manager.onProgressChanged(tabId, url, progress)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).recordExitedFixedProgress(tabId, progress)
    }

    @Test
    fun whenOnPageLoadSucceededCalledThenCallsFinishPageLoadWithSuccessOutcome() = runTest {
        val tabId = "tab-123"
        val url = "https://twitch.tv/directory"
        val isInForeground = true
        val activeRequestsOnStart = 5
        val concurrentRequestsOnFinish = 2

        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = url,
            isTabInForegroundOnFinish = isInForeground,
            activeRequestsOnLoadStart = activeRequestsOnStart,
            concurrentRequestsOnFinish = concurrentRequestsOnFinish,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = isInForeground,
            activeRequestsOnLoadStart = activeRequestsOnStart,
            concurrentRequestsOnFinish = concurrentRequestsOnFinish,
        )
    }

    @Test
    fun whenOnPageLoadFailedCalledThenCallsFinishPageLoadWithErrorOutcome() = runTest {
        val tabId = "tab-123"
        val url = "https://twitter.com/duckduckgo"
        val errorDescription = "ERR_CONNECTION_REFUSED - Connection refused"
        val isInForeground = false
        val activeRequestsOnStart = 3
        val concurrentRequestsOnFinish = 1

        manager.onPageLoadFailed(
            tabId = tabId,
            url = url,
            errorDescription = errorDescription,
            isTabInForegroundOnFinish = isInForeground,
            activeRequestsOnLoadStart = activeRequestsOnStart,
            concurrentRequestsOnFinish = concurrentRequestsOnFinish,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = "error",
            errorCode = errorDescription,
            isTabInForegroundOnFinish = isInForeground,
            activeRequestsOnLoadStart = activeRequestsOnStart,
            concurrentRequestsOnFinish = concurrentRequestsOnFinish,
        )
    }

    @Test
    fun whenMultipleTabsTrackedThenEachTabIsHandledIndependently() = runTest {
        val tabId1 = "tab-1"
        val tabId2 = "tab-2"
        val url1 = "https://ebay.com"
        val url2 = "https://weather.com"
        whenever(pageLoadWideEvent.isInProgress(tabId1)).thenReturn(false)
        whenever(pageLoadWideEvent.isInProgress(tabId2)).thenReturn(false)

        manager.onPageStarted(tabId1, url1)
        manager.onPageStarted(tabId2, url2)
        manager.onPageVisible(tabId1, url1, 30)
        manager.onPageVisible(tabId2, url2, 40)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId1)
        verify(pageLoadWideEvent).startPageLoad(tabId2)
        verify(pageLoadWideEvent).recordPageVisible(tabId1, 30)
        verify(pageLoadWideEvent).recordPageVisible(tabId2, 40)
    }

    @Test
    fun whenCompleteFlowExecutedThenAllMethodsCalledInOrder() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com/r/privacy"
        whenever(pageLoadWideEvent.isInProgress(tabId)).thenReturn(false)

        manager.onPageStarted(tabId, url)
        manager.onPageVisible(tabId, url, 25)
        manager.onProgressChanged(tabId, url, 60)
        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = url,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 4,
            concurrentRequestsOnFinish = 1,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId)
        verify(pageLoadWideEvent).recordPageVisible(tabId, 25)
        verify(pageLoadWideEvent).recordExitedFixedProgress(tabId, 60)
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = "success",
            errorCode = null,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 4,
            concurrentRequestsOnFinish = 1,
        )
    }

    @Test
    fun whenPageLoadFailsThenFailureFlowCompleted() = runTest {
        val tabId = "tab-456"
        val url = "https://espn.com"
        val errorDescription = "ERR_NAME_NOT_RESOLVED - DNS lookup failed"
        whenever(pageLoadWideEvent.isInProgress(tabId)).thenReturn(false)

        manager.onPageStarted(tabId, url)
        manager.onPageVisible(tabId, url, 15)
        manager.onPageLoadFailed(
            tabId = tabId,
            url = url,
            errorDescription = errorDescription,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 2,
            concurrentRequestsOnFinish = 0,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId)
        verify(pageLoadWideEvent).recordPageVisible(tabId, 15)
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = "error",
            errorCode = errorDescription,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 2,
            concurrentRequestsOnFinish = 0,
        )
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageStartedDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://example.com"

        manager.onPageStarted(tabId, untrackedUrl)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verifyNoInteractions(pageLoadWideEvent)
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageVisibleDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://google.com"

        manager.onPageVisible(tabId, untrackedUrl, 50)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verifyNoInteractions(pageLoadWideEvent)
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenProgressChangedDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://facebook.com"

        manager.onProgressChanged(tabId, untrackedUrl, 75)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verifyNoInteractions(pageLoadWideEvent)
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageLoadSucceededDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://amazon.com"

        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = untrackedUrl,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 5,
            concurrentRequestsOnFinish = 2,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verifyNoInteractions(pageLoadWideEvent)
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageLoadFailedDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://netflix.com"
        val errorDescription = "ERR_TIMEOUT - Request timeout"

        manager.onPageLoadFailed(
            tabId = tabId,
            url = untrackedUrl,
            errorDescription = errorDescription,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        org.mockito.kotlin.verifyNoInteractions(pageLoadWideEvent)
    }

    @Test
    fun whenSubdomainOfTrackedSiteThenEventsAreTracked() = runTest {
        val tabId = "tab-123"
        val subdomainUrl = "https://mobile.twitter.com/duckduckgo"
        whenever(pageLoadWideEvent.isInProgress(tabId)).thenReturn(false)

        manager.onPageStarted(tabId, subdomainUrl)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId)
    }
}
