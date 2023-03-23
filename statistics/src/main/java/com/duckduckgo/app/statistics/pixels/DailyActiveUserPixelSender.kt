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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.api.FeatureEnabledPlugin
import com.duckduckgo.app.statistics.api.RefreshRetentionAtbPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class DailyActiveUserPixelSender @Inject constructor(
    private val context: Context,
    private val pixel: Pixel,
    private val plugins: PluginPoint<FeatureEnabledPlugin>,
) : RefreshRetentionAtbPlugin {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PIXELS_PREF_FILE, Context.MODE_PRIVATE)

    override fun onSearchRetentionAtbRefreshed() {
        tryToFireDailyPixel(StatisticsPixelName.DAILY_ACTIVE.pixelName)
    }

    override fun onAppRetentionAtbRefreshed() {
        tryToFireDailyPixel(StatisticsPixelName.DAILY_ACTIVE.pixelName)
    }

    private fun tryToFireDailyPixel(
        pixelName: String,
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)
        val parameters = addFeatureParameters()
        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            Timber.d("Firing daily active pixel with parameters: $parameters")
            pixel.fire(pixelName, parameters, emptyMap())
                .also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun addFeatureParameters(): Map<String, String> {
        val parameters = mutableMapOf<String, String>()
        plugins.getPlugins().forEach { plugin ->
            Timber.d("Daily active pixel with feature $plugin")
            parameters[plugin.featureName()] = plugin.isFeatureEnabled().toBinaryString()
        }
        return parameters
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
