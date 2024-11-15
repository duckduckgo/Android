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
import com.duckduckgo.app.pixels.AppPixelName.RELOAD_THREE_TIMES_WITHIN_20_SECONDS
import com.duckduckgo.app.pixels.AppPixelName.RELOAD_TWICE_WITHIN_12_SECONDS
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.trackerdetection.blocklist.BlockListPixelsPlugin
import com.duckduckgo.app.trackerdetection.blocklist.get2XRefresh
import com.duckduckgo.app.trackerdetection.blocklist.get3XRefresh
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RefreshPixelSender {
    fun sendMenuRefreshPixels()
    fun sendCustomTabRefreshPixel()
    fun sendPullToRefreshPixels()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DuckDuckGoRefreshPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val dao: RefreshDao,
    private val currentTimeProvider: CurrentTimeProvider,
    private val blockListPixelsPlugin: BlockListPixelsPlugin,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : RefreshPixelSender {

    override fun sendMenuRefreshPixels() {
        sendTimeBasedPixels()
        pixel.fire(AppPixelName.MENU_ACTION_REFRESH_PRESSED)
        pixel.fire(AppPixelName.REFRESH_ACTION_DAILY_PIXEL, type = Daily())
    }

    override fun sendPullToRefreshPixels() {
        sendTimeBasedPixels()
        pixel.fire(AppPixelName.BROWSER_PULL_TO_REFRESH)
        pixel.fire(AppPixelName.REFRESH_ACTION_DAILY_PIXEL, type = Daily())
    }

    override fun sendCustomTabRefreshPixel() {
        sendTimeBasedPixels()
        pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_REFRESH)
    }

    private fun sendTimeBasedPixels() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val now = currentTimeProvider.currentTimeMillis()
            val twelveSecondsAgo = now - TWELVE_SECONDS
            val twentySecondsAgo = now - TWENTY_SECONDS

            val refreshes = dao.updateRecentRefreshes(twentySecondsAgo, now)

            if (refreshes.count { it.timestamp >= twelveSecondsAgo } >= 2) {
                pixel.fire(RELOAD_TWICE_WITHIN_12_SECONDS)
                blockListPixelsPlugin.get2XRefresh()?.getPixelDefinitions()?.forEach {
                    pixel.fire(it.pixelName, it.params)
                }
            }
            if (refreshes.size >= 3) {
                pixel.fire(RELOAD_THREE_TIMES_WITHIN_20_SECONDS)

                blockListPixelsPlugin.get3XRefresh()?.getPixelDefinitions()?.forEach {
                    pixel.fire(it.pixelName, it.params)
                }
            }
        }
    }

    companion object {
        const val TWENTY_SECONDS = 20000L
        const val TWELVE_SECONDS = 12000L
    }
}
