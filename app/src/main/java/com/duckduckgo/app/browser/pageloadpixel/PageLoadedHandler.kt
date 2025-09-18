/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.pageloadpixel

import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedSites.Companion.sites
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

interface PageLoadedHandler {
    fun onPageLoaded(
        url: String,
        title: String?,
        start: Long,
        end: Long,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    )
}

@ContributesBinding(AppScope::class)
class RealPageLoadedHandler @Inject constructor(
    private val deviceInfo: DeviceInfo,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val pageLoadedPixelDao: PageLoadedPixelDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val autoconsent: Autoconsent,
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper,
) : PageLoadedHandler {

    override fun onPageLoaded(
        url: String,
        title: String?,
        start: Long,
        end: Long,
        isTabInForegroundOnFinish: Boolean,
        activeRequestsOnLoadStart: Int,
        concurrentRequestsOnFinish: Int,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (sites.any { UriString.sameOrSubdomain(url, it) }) {
                pageLoadedPixelDao.add(
                    PageLoadedPixelEntity(
                        elapsedTime = end - start,
                        webviewVersion = webViewVersionProvider.getMajorVersion(),
                        appVersion = deviceInfo.appVersion,
                        cpmEnabled = autoconsent.isAutoconsentEnabled(),
                        trackerOptimizationEnabled = optimizeTrackerEvaluationRCWrapper.enabled,
                    ),
                )
            }
            logcat(LogPriority.DEBUG) {
                "Page load time: ${end - start}, isTabInForegroundOnFinish: $isTabInForegroundOnFinish, " +
                    "activeRequestsOnLoadStart: $activeRequestsOnLoadStart, " +
                    "concurrentRequestsOnFinish: $concurrentRequestsOnFinish"
            }
        }
    }
}
