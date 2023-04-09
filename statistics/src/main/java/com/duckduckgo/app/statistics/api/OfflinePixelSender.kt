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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName.*
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.di.DaggerSet
import io.reactivex.Completable
import io.reactivex.Completable.*
import kotlin.reflect.KMutableProperty0
import timber.log.Timber

/**
 * Most pixels are "send and forget" however we sometimes need to guarantee that a pixel will be
 * sent. In those cases we schedule them to happen as part of our app data sync.
 */
class OfflinePixelSender constructor(
    private val offlineCountCountDataStore: OfflinePixelCountDataStore,
    private val pixelSender: PixelSender,
    private val offlinePixels: DaggerSet<OfflinePixel>,
) {

    fun sendOfflinePixels(): Completable {
        return mergeDelayError(
            listOf(
                sendApplicationKilledPixel(),
                sendWebRendererCrashedPixel(),
                sendWebRendererKilledPixel(),
                sendCookieDatabaseNotFoundPixel(),
                sendCookieDatabaseOpenErrorPixel(),
                sendCookieDatabaseDeleteErrorPixel(),
                sendCookieDatabaseCorruptedErrorPixel(),
                *offlinePixels.map { it.send() }.toTypedArray(),
            ),
        )
    }

    private fun sendApplicationKilledPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::applicationCrashCount, APPLICATION_CRASH)
    }

    private fun sendWebRendererCrashedPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::webRendererGoneCrashCount,
            WEB_RENDERER_GONE_CRASH,
        )
    }

    private fun sendWebRendererKilledPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::webRendererGoneKilledCount,
            WEB_RENDERER_GONE_KILLED,
        )
    }

    private fun sendCookieDatabaseDeleteErrorPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::cookieDatabaseDeleteErrorCount,
            COOKIE_DATABASE_DELETE_ERROR,
        )
    }

    private fun sendCookieDatabaseOpenErrorPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::cookieDatabaseOpenErrorCount,
            COOKIE_DATABASE_OPEN_ERROR,
        )
    }

    private fun sendCookieDatabaseNotFoundPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::cookieDatabaseNotFoundCount,
            COOKIE_DATABASE_NOT_FOUND,
        )
    }

    private fun sendCookieDatabaseCorruptedErrorPixel(): Completable {
        return sendPixelCount(
            offlineCountCountDataStore::cookieDatabaseCorruptedCount,
            COOKIE_DATABASE_CORRUPTED_ERROR,
        )
    }

    private fun sendPixelCount(
        counter: KMutableProperty0<Int>,
        pixelName: Pixel.PixelName,
    ): Completable {
        return defer {
            val count = counter.get()
            if (count == 0) {
                return@defer complete()
            }
            val params = mapOf(COUNT to count.toString())
            pixelSender.sendPixel(pixelName.pixelName, params, emptyMap()).andThen {
                Timber.v("Offline pixel sent ${pixelName.pixelName} count: $count")
                counter.set(0)
            }
        }
    }
}
