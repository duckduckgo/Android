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

import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedSites
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages page load event tracking lifecycle.
 * Coordinates page load events across the browser, tracking visibility, progress, and completion.
 */
interface PageLoadPerformanceMonitor {
    /**
     * Called when a page starts loading.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     */
    fun onPageStarted(tabId: String, url: String)

    /**
     * Called when a page becomes visible to the user.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     * @param progress The current page load progress (0-100)
     */
    fun onPageVisible(tabId: String, url: String, progress: Int)

    /**
     * Called when page progress changes and escapes the fixed progress state.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page being loaded
     * @param progress The page load progress
     */
    fun onProgressChanged(tabId: String, url: String, progress: Int)

    /**
     * Called when a page completes loading successfully.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page that completed loading
     * @param isTabInForegroundOnFinish Whether the tab was in the foreground when completed
     * @param activeRequestsOnLoadStart Number of parallel requests when page load started
     * @param concurrentRequestsOnFinish Number of concurrent requests when page finished
     */
    fun onPageLoadSucceeded(
        tabId: String,
        url: String,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    )

    /**
     * Called when a page fails to load.
     * @param tabId The unique identifier for the tab
     * @param url The URL of the page that failed to load
     * @param errorDescription Description of the error that occurred
     * @param isTabInForegroundOnFinish Whether the tab was in the foreground when failed
     * @param activeRequestsOnLoadStart Number of parallel requests when page load started
     * @param concurrentRequestsOnFinish Number of concurrent requests when page failed
     */
    fun onPageLoadFailed(
        tabId: String,
        url: String,
        errorDescription: String,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    )
}

private const val ABOUT_BLANK = "about:blank"

@ContributesBinding(AppScope::class)
class WideEventPageLoadPerformanceMonitor @Inject constructor(
    private val pageLoadWideEvent: PageLoadWideEvent,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PageLoadPerformanceMonitor {

    override fun onPageStarted(tabId: String, url: String) {
        if (!shouldTrackUrl(url)) return
        if (pageLoadWideEvent.isInProgress(tabId, url)) return
        appCoroutineScope.launch {
            pageLoadWideEvent.startPageLoad(tabId, url)
        }
    }

    override fun onPageVisible(tabId: String, url: String, progress: Int) {
        if (!pageLoadWideEvent.isInProgress(tabId, url)) return
        appCoroutineScope.launch {
            pageLoadWideEvent.recordPageVisible(tabId, progress)
        }
    }

    override fun onProgressChanged(tabId: String, url: String, progress: Int) {
        if (!pageLoadWideEvent.isInProgress(tabId, url)) return
        appCoroutineScope.launch {
            pageLoadWideEvent.recordExitedFixedProgress(tabId, progress)
        }
    }

    override fun onPageLoadSucceeded(
        tabId: String,
        url: String,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    ) {
        if (!pageLoadWideEvent.isInProgress(tabId, url)) return
        appCoroutineScope.launch {
            pageLoadWideEvent.finishPageLoad(
                tabId = tabId,
                outcome = "success",
                errorCode = null,
                isTabInForegroundOnFinish = isTabInForegroundOnFinish,
                activeRequestsOnLoadStart = activeRequestsOnLoadStart,
                concurrentRequestsOnFinish = concurrentRequestsOnFinish,
            )
        }
    }

    override fun onPageLoadFailed(
        tabId: String,
        url: String,
        errorDescription: String,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    ) {
        if (!pageLoadWideEvent.isInProgress(tabId, url)) return
        appCoroutineScope.launch {
            pageLoadWideEvent.finishPageLoad(
                tabId = tabId,
                outcome = "error",
                errorCode = errorDescription,
                isTabInForegroundOnFinish = isTabInForegroundOnFinish,
                activeRequestsOnLoadStart = activeRequestsOnLoadStart,
                concurrentRequestsOnFinish = concurrentRequestsOnFinish,
            )
        }
    }

    private fun shouldTrackUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url == ABOUT_BLANK) return false
        return runCatching {
            PageLoadedSites.perfSites.any { site -> UriString.sameOrSubdomain(url, site) }
        }.getOrDefault(false)
    }
}
