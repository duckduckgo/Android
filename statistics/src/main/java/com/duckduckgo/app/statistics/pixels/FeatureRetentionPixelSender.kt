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

package com.duckduckgo.app.statistics.pixels

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.app.statistics.api.RefreshRetentionAtbPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(AppScope::class)
class FeatureRetentionPixelSender @Inject constructor(
    private val context: Context,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val plugins: PluginPoint<BrowserFeatureStateReporterPlugin>,
) : RefreshRetentionAtbPlugin {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(PIXELS_PREF_FILE, Context.MODE_PRIVATE) }

    override fun onSearchRetentionAtbRefreshed() {
        coroutineScope.launch(dispatcherProvider.io()) {
            tryToFireDailyPixel(StatisticsPixelName.BROWSER_DAILY_ACTIVE_FEATURE_STATE.pixelName)
        }
    }

    override fun onAppRetentionAtbRefreshed() {
        coroutineScope.launch(dispatcherProvider.io()) {
            tryToFireDailyPixel(StatisticsPixelName.BROWSER_DAILY_ACTIVE_FEATURE_STATE.pixelName)
        }
    }

    private fun tryToFireDailyPixel(
        pixelName: String,
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        val parameters = mutableMapOf<String, String>()
        plugins.getPlugins().forEach { plugin ->
            val featureState = plugin.featureState()
            parameters[featureState.second] = featureState.first.toBinaryString()
        }

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            pixel.fire(pixelName, parameters, emptyMap())
                .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    companion object {
        private const val PIXELS_PREF_FILE = "com.duckduckgo.mobile.android.dau.pixels"
    }
}

private fun String.appendTimestampSuffix(): String {
    return "${this}_timestamp"
}

fun Boolean.toBinaryString(): String = if (this) "1" else "0"
