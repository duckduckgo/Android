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

package com.duckduckgo.app.browser.httperrors

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface HttpErrorPixels {
    fun updateCountPixel(httpErrorPixelName: HttpErrorPixelName)
    suspend fun update5xxCountPixel(httpErrorPixelName: HttpErrorPixelName, statusCode: Int)
    fun fireCountPixel(httpErrorPixelName: HttpErrorPixelName)
    fun fire5xxCountPixels()
}

@ContributesBinding(AppScope::class)
class RealHttpErrorPixels @Inject constructor(
    private val pixel: Pixel,
    private val context: Context,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val externalVpnDetector: ExternalVpnDetector,
    private val androidBrowserConfig: AndroidBrowserConfigFeature,
) : HttpErrorPixels {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }
    private val pixel5xxKeys: MutableSet<String> by lazy {
        preferences.getStringSet(PIXEL_5XX_KEYS_SET, mutableSetOf()) ?: mutableSetOf()
    }

    override fun updateCountPixel(httpErrorPixelName: HttpErrorPixelName) {
        val count = preferences.getInt(httpErrorPixelName.appendCountSuffix(), 0)
        preferences.edit { putInt(httpErrorPixelName.appendCountSuffix(), count + 1) }
    }

    override suspend fun update5xxCountPixel(
        httpErrorPixelName: HttpErrorPixelName,
        statusCode: Int,
    ) {
        // Kill switch
        if (!androidBrowserConfig.self().isEnabled() || !androidBrowserConfig.httpError5xxPixel().isEnabled()) {
            return
        }

        val pProVpnConnected = runCatching {
            networkProtectionState.isRunning()
        }.getOrDefault(false)

        val externalVpnConnected = runCatching {
            externalVpnDetector.isExternalVpnDetected()
        }.getOrDefault(false)

        val webViewFullVersion = webViewVersionProvider.getFullVersion()

        val pixelPrefKey = "${httpErrorPixelName.pixelName}|$statusCode|$pProVpnConnected|$externalVpnConnected|$webViewFullVersion|_count"

        val updatedSet = pixel5xxKeys
        updatedSet.add(pixelPrefKey)
        val count = preferences.getInt(pixelPrefKey, 0)
        preferences.edit {
            putInt(pixelPrefKey, count + 1)
            putStringSet(PIXEL_5XX_KEYS_SET, updatedSet)
        }
    }

    override fun fireCountPixel(httpErrorPixelName: HttpErrorPixelName) {
        val now = Instant.now().toEpochMilli()

        val count = preferences.getInt(httpErrorPixelName.appendCountSuffix(), 0)
        if (count == 0) {
            return
        }

        val timestamp = preferences.getLong(httpErrorPixelName.appendTimestampSuffix(), 0L)
        if (timestamp == 0L || now >= timestamp) {
            pixel.fire(httpErrorPixelName, mapOf(HttpErrorPixelParameters.HTTP_ERROR_CODE_COUNT to count.toString()))
                .also {
                    preferences.edit {
                        putLong(httpErrorPixelName.appendTimestampSuffix(), now.plus(TimeUnit.HOURS.toMillis(WINDOW_INTERVAL_HOURS)))
                        putInt(httpErrorPixelName.appendCountSuffix(), 0)
                    }
                }
        }
    }

    override fun fire5xxCountPixels() {
        // Kill switch
        if (!androidBrowserConfig.self().isEnabled() || !androidBrowserConfig.httpError5xxPixel().isEnabled()) {
            return
        }

        val now = Instant.now().toEpochMilli()
        val updatedSet = pixel5xxKeys
        updatedSet.forEach { pixelKey ->
            val count = preferences.getInt(pixelKey, 0)
            if (count != 0) {
                val timestamp = preferences.getLong("${pixelKey}_timestamp", 0L)
                if (timestamp == 0L || now >= timestamp) {
                    pixelKey.split("|").let { split ->
                        if (split.size == 6) {
                            val httpErrorPixelName = HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY
                            val statusCode = split[1].toInt()
                            val pProVpnConnected = split[2].toBoolean()
                            val externalVpnConnected = split[3].toBoolean()
                            val webViewFullVersion = split[4]
                            pixel.fire(
                                httpErrorPixelName,
                                mapOf(
                                    HttpErrorPixelParameters.HTTP_ERROR_CODE_COUNT to count.toString(),
                                    "error_code" to statusCode.toString(),
                                    "ppro_user" to pProVpnConnected.toString(),
                                    "vpn_user" to externalVpnConnected.toString(),
                                    "webview_version" to webViewFullVersion,
                                ),
                            )
                        }
                    }
                        .also {
                            preferences.edit {
                                putLong("${pixelKey}_timestamp", now.plus(TimeUnit.HOURS.toMillis(WINDOW_INTERVAL_HOURS)))
                                putInt(pixelKey, 0)
                            }
                        }
                }
            }
        }
    }

    private fun HttpErrorPixelName.appendTimestampSuffix(): String {
        return "${this.pixelName}_timestamp"
    }

    private fun HttpErrorPixelName.appendCountSuffix(): String {
        return "${this.pixelName}_count"
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.browser.httperrors"
        private const val WINDOW_INTERVAL_HOURS = 24L
        internal const val PIXEL_5XX_KEYS_SET = "pixel_5xx_keys_set"
    }
}
