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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WideEventPageLoadPerformanceMonitorTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var pageLoadWideEvent: PageLoadWideEvent
    private lateinit var manager: PageLoadPerformanceMonitor

    @Before
    fun setup() {
        pageLoadWideEvent = mock()
        manager = WideEventPageLoadPerformanceMonitor(
            pageLoadWideEvent = pageLoadWideEvent,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun whenOnPageStartedCalled_andNotInProgressThenCallsStartPageLoad() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com"
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(false)

        manager.onPageStarted(tabId, url)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, url)
        verify(pageLoadWideEvent).startPageLoad(tabId, url)
    }

    @Test
    fun whenOnPageStartedCalled_andAlreadyInProgressThenDoesNotCallStartPageLoad() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com"
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(true)

        manager.onPageStarted(tabId, url)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, url)
        verify(pageLoadWideEvent, never()).startPageLoad(any(), any())
    }

    @Test
    fun whenOnPageVisibleCalledThenCallsRecordPageVisible() = runTest {
        val tabId = "tab-123"
        val url = "https://espn.com/nfl"
        val progress = 42
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(true)

        manager.onPageVisible(tabId, url, progress)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).recordPageVisible(tabId, progress)
    }

    @Test
    fun whenOnProgressChangedCalledThenCallsRecordExitedFixedProgress() = runTest {
        val tabId = "tab-123"
        val url = "https://en.wikipedia.org/wiki/DuckDuckGo"
        val progress = 75
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(true)

        manager.onProgressChanged(tabId, url)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).recordExitedFixedProgress(tabId)
    }

    @Test
    fun whenOnPageLoadSucceededCalledThenCallsFinishPageLoadWithSuccessOutcome() = runTest {
        val tabId = "tab-123"
        val url = "https://twitch.tv/directory"
        val isInForeground = true
        val activeRequestsOnStart = 5
        val concurrentRequestsOnFinish = 2
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(true)

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
            outcome = PageLoadOutcome.SUCCESS,
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
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(true)

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
            outcome = PageLoadOutcome.ERROR,
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
        // Initially not in progress for onPageStarted
        whenever(pageLoadWideEvent.isInProgress(tabId1, url1)).thenReturn(false, true)
        whenever(pageLoadWideEvent.isInProgress(tabId2, url2)).thenReturn(false, true)

        manager.onPageStarted(tabId1, url1)
        manager.onPageStarted(tabId2, url2)
        manager.onPageVisible(tabId1, url1, 30)
        manager.onPageVisible(tabId2, url2, 40)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId1, url1)
        verify(pageLoadWideEvent).startPageLoad(tabId2, url2)
        verify(pageLoadWideEvent).recordPageVisible(tabId1, 30)
        verify(pageLoadWideEvent).recordPageVisible(tabId2, 40)
    }

    @Test
    fun whenCompleteFlowExecutedThenAllMethodsCalledInOrder() = runTest {
        val tabId = "tab-123"
        val url = "https://reddit.com/r/privacy"
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(false, true)

        manager.onPageStarted(tabId, url)
        manager.onPageVisible(tabId, url, 25)
        manager.onProgressChanged(tabId, url)
        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = url,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 4,
            concurrentRequestsOnFinish = 1,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId, url)
        verify(pageLoadWideEvent).recordPageVisible(tabId, 25)
        verify(pageLoadWideEvent).recordExitedFixedProgress(tabId)
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = PageLoadOutcome.SUCCESS,
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
        whenever(pageLoadWideEvent.isInProgress(tabId, url)).thenReturn(false, true)

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
        verify(pageLoadWideEvent).startPageLoad(tabId, url)
        verify(pageLoadWideEvent).recordPageVisible(tabId, 15)
        verify(pageLoadWideEvent).finishPageLoad(
            tabId = tabId,
            outcome = PageLoadOutcome.ERROR,
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
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedUrl)).thenReturn(false)

        manager.onPageVisible(tabId, untrackedUrl, 50)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, untrackedUrl)
        verify(pageLoadWideEvent, never()).recordPageVisible(any(), any())
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenProgressChangedDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://facebook.com"
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedUrl)).thenReturn(false)

        manager.onProgressChanged(tabId, untrackedUrl)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, untrackedUrl)
        verify(pageLoadWideEvent, never()).recordExitedFixedProgress(any())
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageLoadSucceededDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://amazon.com"
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedUrl)).thenReturn(false)

        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = untrackedUrl,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 5,
            concurrentRequestsOnFinish = 2,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, untrackedUrl)
        verify(pageLoadWideEvent, never()).finishPageLoad(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun whenUrlNotInPageLoadedSitesThenPageLoadFailedDoesNothing() = runTest {
        val tabId = "tab-123"
        val untrackedUrl = "https://netflix.com"
        val errorDescription = "ERR_TIMEOUT - Request timeout"
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedUrl)).thenReturn(false)

        manager.onPageLoadFailed(
            tabId = tabId,
            url = untrackedUrl,
            errorDescription = errorDescription,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).isInProgress(tabId, untrackedUrl)
        verify(pageLoadWideEvent, never()).finishPageLoad(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun whenSubdomainOfTrackedSiteThenEventsAreTracked() = runTest {
        val tabId = "tab-123"
        val subdomainUrl = "https://mobile.twitter.com/duckduckgo"
        whenever(pageLoadWideEvent.isInProgress(tabId, subdomainUrl)).thenReturn(false)

        manager.onPageStarted(tabId, subdomainUrl)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId, subdomainUrl)
    }

    @Test
    fun whenTrackedUrlRedirectsToUntrackedUrlThenFinishIsIgnored() = runTest {
        val tabId = "tab-123"
        val trackedUrl = "https://espn.com"
        val untrackedRedirectUrl = "https://espn.co.uk"
        whenever(pageLoadWideEvent.isInProgress(tabId, trackedUrl)).thenReturn(false)
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedRedirectUrl)).thenReturn(false)

        // Start with tracked URL
        manager.onPageStarted(tabId, trackedUrl)
        // Redirect to untracked URL - isInProgress returns false, so we just return early
        manager.onPageLoadSucceeded(
            tabId = tabId,
            url = untrackedRedirectUrl,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 2,
            concurrentRequestsOnFinish = 1,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId, trackedUrl)
        // isInProgress(tab, untrackedUrl) returns false, so we exit early without calling finishPageLoad
        verify(pageLoadWideEvent, never()).finishPageLoad(
            tabId = any(),
            outcome = any(),
            errorCode = any(),
            isTabInForegroundOnFinish = any(),
            activeRequestsOnLoadStart = any(),
            concurrentRequestsOnFinish = any(),
        )
    }

    @Test
    fun whenTrackedUrlRedirectsToUntrackedUrlWithErrorThenFinishIsIgnored() = runTest {
        val tabId = "tab-456"
        val trackedUrl = "https://reddit.com"
        val untrackedRedirectUrl = "https://untracked-example.com"
        val errorDescription = "ERR_CONNECTION_REFUSED"
        whenever(pageLoadWideEvent.isInProgress(tabId, trackedUrl)).thenReturn(false)
        whenever(pageLoadWideEvent.isInProgress(tabId, untrackedRedirectUrl)).thenReturn(false)

        // Start with tracked URL
        manager.onPageStarted(tabId, trackedUrl)
        // Redirect to untracked URL with error - isInProgress returns false, so we just return early
        manager.onPageLoadFailed(
            tabId = tabId,
            url = untrackedRedirectUrl,
            errorDescription = errorDescription,
            isTabInForegroundOnFinish = true,
            activeRequestsOnLoadStart = 3,
            concurrentRequestsOnFinish = 0,
        )

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(pageLoadWideEvent).startPageLoad(tabId, trackedUrl)
        verify(pageLoadWideEvent, never()).finishPageLoad(
            tabId = any(),
            outcome = any(),
            errorCode = any(),
            isTabInForegroundOnFinish = any(),
            activeRequestsOnLoadStart = any(),
            concurrentRequestsOnFinish = any(),
        )
    }
}
