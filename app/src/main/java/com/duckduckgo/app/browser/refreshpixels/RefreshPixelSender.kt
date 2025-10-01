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

package com.duckduckgo.app.browser.refreshpixels

import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.trackerdetection.blocklist.BlockListPixelsPlugin
import com.duckduckgo.app.trackerdetection.blocklist.get2XRefresh
import com.duckduckgo.app.trackerdetection.blocklist.get3XRefresh
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

interface RefreshPixelSender {
    fun sendMenuRefreshPixels()
    fun sendCustomTabRefreshPixel()
    fun sendPullToRefreshPixels()
    fun onRefreshPatternDetected(patternsDetected: Set<RefreshPattern>)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DuckDuckGoRefreshPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val blockListPixelsPlugin: BlockListPixelsPlugin,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : RefreshPixelSender {

    override fun sendMenuRefreshPixels() {
        pixel.fire(AppPixelName.MENU_ACTION_REFRESH_PRESSED)
        pixel.fire(AppPixelName.REFRESH_ACTION_DAILY_PIXEL, type = Daily())
    }

    override fun sendPullToRefreshPixels() {
        pixel.fire(AppPixelName.BROWSER_PULL_TO_REFRESH)
        pixel.fire(AppPixelName.REFRESH_ACTION_DAILY_PIXEL, type = Daily())
    }

    override fun sendCustomTabRefreshPixel() {
        pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_REFRESH)
    }

    override fun onRefreshPatternDetected(patternsDetected: Set<RefreshPattern>) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            patternsDetected.forEach { detectedPattern ->
                when (detectedPattern) {
                    RefreshPattern.TWICE_IN_12_SECONDS -> {
                        blockListPixelsPlugin.get2XRefresh()?.getPixelDefinitions()?.forEach {
                            pixel.fire(it.pixelName, it.params)
                        }
                        pixel.fire(AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS)
                    }

                    RefreshPattern.THRICE_IN_20_SECONDS -> {
                        pixel.fire(AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS)
                        blockListPixelsPlugin.get3XRefresh()?.getPixelDefinitions()?.forEach {
                            pixel.fire(it.pixelName, it.params)
                        }
                    }
                    else -> logcat(WARN) { "Unknown refresh pattern: $detectedPattern, no pixels fired" }
                }
            }
        }
    }
}
