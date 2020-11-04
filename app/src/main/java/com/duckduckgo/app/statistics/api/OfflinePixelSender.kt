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

import com.duckduckgo.app.global.exception.UncaughtExceptionEntity
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_APP_VERSION
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_MESSAGE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.EXCEPTION_TIMESTAMP
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import io.reactivex.Completable
import io.reactivex.Completable.*
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import kotlin.reflect.KMutableProperty0

/**
 * Most pixels are "send and forget" however we sometimes need to guarantee that a pixel will be sent.
 * In those cases we schedule them to happen as part of our app data sync.
 */
class OfflinePixelSender @Inject constructor(
    private val offlineCountCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val pixelSender: PixelSender
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
                sendUncaughtExceptionsPixel()
            )
        )
    }

    private fun sendApplicationKilledPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::applicationCrashCount, APPLICATION_CRASH)
    }

    private fun sendWebRendererCrashedPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::webRendererGoneCrashCount, WEB_RENDERER_GONE_CRASH)
    }

    private fun sendWebRendererKilledPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::webRendererGoneKilledCount, WEB_RENDERER_GONE_KILLED)
    }

    private fun sendCookieDatabaseDeleteErrorPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::cookieDatabaseDeleteErrorCount, COOKIE_DATABASE_DELETE_ERROR)
    }

    private fun sendCookieDatabaseOpenErrorPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::cookieDatabaseOpenErrorCount, COOKIE_DATABASE_OPEN_ERROR)
    }

    private fun sendCookieDatabaseNotFoundPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::cookieDatabaseNotFoundCount, COOKIE_DATABASE_NOT_FOUND)
    }

    private fun sendCookieDatabaseCorruptedErrorPixel(): Completable {
        return sendPixelCount(offlineCountCountDataStore::cookieDatabaseCorruptedCount, COOKIE_DATABASE_CORRUPTED_ERROR)
    }

    private fun sendUncaughtExceptionsPixel(): Completable {
        return defer {

            val pixels = mutableListOf<Completable>()
            val exceptions = runBlocking { uncaughtExceptionRepository.getExceptions() }

            exceptions.forEach { exception ->
                Timber.d("Analysing exception $exception")
                val pixelName = determinePixelName(exception)
                val params = mapOf(
                    EXCEPTION_MESSAGE to exception.message,
                    EXCEPTION_APP_VERSION to exception.version,
                    EXCEPTION_TIMESTAMP to exception.formattedTimestamp()
                )

                val pixel = pixelSender.sendPixel(pixelName, params)
                    .doOnComplete {
                        Timber.d("Sent pixel with params: $params containing exception; deleting exception with id=${exception.id}")
                        runBlocking { uncaughtExceptionRepository.deleteException(exception.id) }
                    }

                pixels.add(pixel)
            }

            return@defer mergeDelayError(pixels)
        }
    }

    private fun determinePixelName(exception: UncaughtExceptionEntity): String {
        return when (exception.exceptionSource) {
            GLOBAL -> APPLICATION_CRASH_GLOBAL
            SHOULD_INTERCEPT_REQUEST -> APPLICATION_CRASH_WEBVIEW_SHOULD_INTERCEPT
            ON_PAGE_STARTED -> APPLICATION_CRASH_WEBVIEW_PAGE_STARTED
            ON_PAGE_FINISHED -> APPLICATION_CRASH_WEBVIEW_PAGE_FINISHED
            SHOULD_OVERRIDE_REQUEST -> APPLICATION_CRASH_WEBVIEW_OVERRIDE_REQUEST
            ON_HTTP_AUTH_REQUEST -> APPLICATION_CRASH_WEBVIEW_HTTP_AUTH_REQUEST
            SHOW_CUSTOM_VIEW -> APPLICATION_CRASH_WEBVIEW_SHOW_CUSTOM_VIEW
            HIDE_CUSTOM_VIEW -> APPLICATION_CRASH_WEBVIEW_HIDE_CUSTOM_VIEW
            ON_PROGRESS_CHANGED -> APPLICATION_CRASH_WEBVIEW_ON_PROGRESS_CHANGED
            RECEIVED_PAGE_TITLE -> APPLICATION_CRASH_WEBVIEW_RECEIVED_PAGE_TITLE
            SHOW_FILE_CHOOSER -> APPLICATION_CRASH_WEBVIEW_SHOW_FILE_CHOOSER
        }.pixelName
    }

    private fun sendPixelCount(counter: KMutableProperty0<Int>, pixelName: Pixel.PixelName): Completable {
        return defer {
            val count = counter.get()
            if (count == 0) {
                return@defer complete()
            }
            val params = mapOf(COUNT to count.toString())
            pixelSender.sendPixel(pixelName.pixelName, params).andThen {
                Timber.v("Offline pixel sent ${pixelName.pixelName} count: $count")
                counter.set(0)
            }
        }
    }
}
