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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.UriString
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val sites = listOf(
    "bbc.com",
    "ebay.com",
    "espn.com",
    "nytimes.com",
    "reddit.com",
    "twitch.tv",
    "twitter.com",
    "wikipedia.org",
    "weather.com",
)

interface PageLoadedHandler {
    operator fun invoke(url: String, start: Long, end: Long)
}

@ContributesBinding(AppScope::class)
class RealPageLoadedHandler @Inject constructor(
    private val deviceInfo: DeviceInfo,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val pageLoadedPixelDao: PageLoadedPixelDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val optimizeTrackerEvaluationRCWrapper: OptimizeTrackerEvaluationRCWrapper,
) : PageLoadedHandler {

    override operator fun invoke(url: String, start: Long, end: Long) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (sites.any { UriString.sameOrSubdomain(url, it) }) {
                pageLoadedPixelDao.add(
                    PageLoadedPixelEntity(
                        elapsedTime = end - start,
                        webviewVersion = webViewVersionProvider.getMajorVersion(),
                        appVersion = deviceInfo.appVersion,
                        trackerOptimizationEnabled = optimizeTrackerEvaluationRCWrapper.enabled,
                    ),
                )
            }
        }
    }
}
