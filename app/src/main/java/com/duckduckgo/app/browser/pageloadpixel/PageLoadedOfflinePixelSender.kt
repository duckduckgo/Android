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

import com.duckduckgo.app.browser.WebViewPixelName
import com.duckduckgo.app.statistics.api.OfflinePixel
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.DEFAULT
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.Completable
import javax.inject.Inject

private const val ELAPSED_TIME = "elapsed_time"
private const val WEBVIEW_VERSION = "webview_version"
private const val TRACKER_OPTIMIZATION_ENABLED = "tracker_optimization_enabled"

// This is used to ensure the app version we send is the one from the moment the page was loaded, and not then the pixel is fired later on
private const val APP_VERSION = "app_version_when_page_loaded"

@ContributesMultibinding(AppScope::class)
class PageLoadedOfflinePixelSender @Inject constructor(
    private val pageLoadedPixelDao: PageLoadedPixelDao,
    private val pixelSender: PixelSender,
) : OfflinePixel {
    override fun send(): Completable {
        return Completable.defer {
            val pendingPixels = pageLoadedPixelDao.all()
            pendingPixels.map {
                return@defer pixelSender.sendPixel(
                    WebViewPixelName.WEB_PAGE_LOADED.pixelName,
                    mapOf(
                        APP_VERSION to it.appVersion,
                        ELAPSED_TIME to it.elapsedTime.toString(),
                        WEBVIEW_VERSION to it.webviewVersion,
                        TRACKER_OPTIMIZATION_ENABLED to it.trackerOptimizationEnabled.toString(),
                    ),
                    mapOf(),
                    DEFAULT,
                ).ignoreElement().doOnComplete {
                    pageLoadedPixelDao.deleteAll()
                }
            }
            return@defer Completable.complete()
        }
    }
}
