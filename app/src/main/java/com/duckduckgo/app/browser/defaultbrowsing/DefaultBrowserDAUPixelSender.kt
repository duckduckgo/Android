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

package com.duckduckgo.app.browser.defaultbrowsing

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.api.RefreshRetentionAtbPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

@ContributesMultibinding(AppScope::class)
class DefaultBrowserDAUPixelSender @Inject constructor(
    private val context: Context,
    private val pixel: Pixel,
    private val defaultBrowserDetector: DefaultBrowserDetector,
) : RefreshRetentionAtbPlugin {

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PIXELS_PREF_FILE, Context.MODE_PRIVATE)

    override fun onSearchRetentionAtbRefreshed() {
        // no-op
    }

    override fun onAppRetentionAtbRefreshed() {
        tryToFireDailyPixel(AppPixelName.DEFAULT_BROWSER_ENABLED_DAU.pixelName)
    }

    private fun tryToFireDailyPixel(
        pixelName: String,
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            val pixelName = if (defaultBrowserDetector.isDefaultBrowser()) {
                AppPixelName.DEFAULT_BROWSER_ENABLED_DAU.pixelName
            } else {
                AppPixelName.DEFAULT_BROWSER_DISABLED_DAU.pixelName
            }
            pixel.fire(pixelName, emptyMap(), emptyMap())
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
