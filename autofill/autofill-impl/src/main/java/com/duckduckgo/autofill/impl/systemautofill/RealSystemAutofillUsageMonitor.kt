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

package com.duckduckgo.autofill.impl.systemautofill

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.systemautofill.SystemAutofillUsageMonitor
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSystemAutofillUsageMonitor @Inject constructor(
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val context: Context,
) : SystemAutofillUsageMonitor {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override fun onSystemAutofillUsed() {
        appCoroutineScope.launch(dispatchers.io()) {
            tryToFireDailyPixel(AutofillPixelNames.SYSTEM_AUTOFILL_USED.pixelName)
        }
    }

    private fun tryToFireDailyPixel(pixelName: String) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixelName.appendTimestampSuffix(), null)

        if (canFirePixelToday(timestamp, now)) {
            pixel.fire(pixelName).also { preferences.edit { putString(pixelName.appendTimestampSuffix(), now) } }
        }
    }

    private fun canFirePixelToday(
        timestamp: String?,
        now: String,
    ): Boolean {
        return timestamp == null || now > timestamp
    }

    private fun String.appendTimestampSuffix(): String = "${this}_timestamp"

    /**
     * returns today's date in YYYY-MM-dd format
     */
    private fun getUtcIsoLocalDate(): String {
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.autofill.impl.systemautofill.RealSystemAutofillUsageMonitor"
    }
}
