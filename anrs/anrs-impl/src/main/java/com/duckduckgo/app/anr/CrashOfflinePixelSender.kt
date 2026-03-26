/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.anr

import android.util.Base64
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_GLOBAL
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_GLOBAL_VERIFIED_INSTALL
import com.duckduckgo.app.anrs.store.UncaughtExceptionDao
import com.duckduckgo.app.statistics.api.OfflinePixel
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.Completable
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class CrashOfflinePixelSender @Inject constructor(
    private val uncaughtExceptionDao: UncaughtExceptionDao,
    private val pixelSender: PixelSender,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val isVerifiedPlayStoreInstall: IsVerifiedPlayStoreInstall,
) : OfflinePixel {
    override fun send(): Completable {
        return Completable.defer {
            val pixels = mutableListOf<Completable>()
            val exceptions = uncaughtExceptionDao.all()

            exceptions.forEach { exception ->
                logcat { "Analysing exception $exception" }
                val ss = Base64.encodeToString(exception.stackTrace.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
                val params =
                    mapOf(
                        EXCEPTION_SHORT_NAME to exception.shortName,
                        EXCEPTION_MESSAGE to exception.message,
                        EXCEPTION_PROCESS_NAME to exception.processName,
                        EXCEPTION_STACK_TRACE to ss,
                        EXCEPTION_APP_VERSION to exception.version,
                        EXCEPTION_TIMESTAMP to exception.timestamp,
                        EXCEPTION_WEBVIEW_VERSION to webViewVersionProvider.getFullVersion(),
                        EXCEPTION_CUSTOM_TAB to exception.customTab.toString(),
                    )

                if (isVerifiedPlayStoreInstall()) {
                    val verifiedPixel = pixelSender.sendPixel(
                        pixelName = APPLICATION_CRASH_GLOBAL_VERIFIED_INSTALL.pixelName,
                        parameters = params,
                        encodedParameters = emptyMap(),
                        type = Count,
                    )
                    pixels.add(verifiedPixel.ignoreElement())
                }

                val pixel =
                    pixelSender.sendPixel(APPLICATION_CRASH_GLOBAL.pixelName, params, emptyMap(), Count).ignoreElement().doOnComplete {
                        logcat { "Sent pixel with params: $params containing exception; deleting exception with id=${exception.hash}" }
                        uncaughtExceptionDao.delete(exception)
                    }

                pixels.add(pixel)
            }

            return@defer Completable.mergeDelayError(pixels)
        }
    }

    companion object {
        private const val EXCEPTION_SHORT_NAME = "sn"
        private const val EXCEPTION_MESSAGE = "m"
        private const val EXCEPTION_PROCESS_NAME = "pn"
        private const val EXCEPTION_STACK_TRACE = "ss"
        private const val EXCEPTION_APP_VERSION = "v"
        private const val EXCEPTION_TIMESTAMP = "t"
        private const val EXCEPTION_WEBVIEW_VERSION = "webView"
        private const val EXCEPTION_CUSTOM_TAB = "customTab"
    }
}
