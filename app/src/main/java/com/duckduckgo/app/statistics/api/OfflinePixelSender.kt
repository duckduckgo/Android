/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.WEB_RENDERER_GONE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.WEB_RENDERER_GONE_OTHER
import com.duckduckgo.app.statistics.store.OfflinePixelDataStore
import io.reactivex.Completable
import io.reactivex.Completable.complete
import io.reactivex.Completable.defer
import timber.log.Timber
import javax.inject.Inject


/**
 * Most pixels are "send and forget" however we sometimes need to guarantee that a pixel will be sent.
 * In those cases we schedule them to happen as part of our app data sync.
 */
class OfflinePixelSender @Inject constructor(
    private val dataStore: OfflinePixelDataStore,
    private val pixel: Pixel
) {

    fun sendWebRendererGonePixel(): Completable {
        return defer {

            if (dataStore.webRendererGoneCrashCount == 0 && dataStore.webRendererGoneOtherCount == 0) {
                return@defer complete()
            }
            val params = mapOf(
                WEB_RENDERER_GONE_CRASH to dataStore.webRendererGoneCrashCount.toString(),
                WEB_RENDERER_GONE_OTHER to dataStore.webRendererGoneOtherCount.toString()
            )

            pixel.fireCompletable(WEB_RENDERER_GONE.pixelName, params).andThen {
                Timber.v("Offline pixel sent ${WEB_RENDERER_GONE.pixelName} crashes: ${dataStore.webRendererGoneCrashCount} other: ${dataStore.webRendererGoneOtherCount} ")
                dataStore.webRendererGoneCrashCount = 0
                dataStore.webRendererGoneOtherCount = 0
            }
        }
    }
}
