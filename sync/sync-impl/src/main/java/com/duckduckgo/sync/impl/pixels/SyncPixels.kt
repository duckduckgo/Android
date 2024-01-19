/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.sync.impl.pixels

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_DAILY_PIXEL
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

interface SyncPixels {

    /**
     * Fired once per day, for all users with sync enabled
     */
    fun fireDailyPixel()

    /**
     * Fired after a sync operation has found timestamp conflict
     */
    fun fireTimestampConflictPixel(feature: String)

    /**
     * Fired when adding new device to existing account
     */
    fun fireLoginPixel()

    /**
     * Fired when user sets up a sync account from connect flow
     */
    fun fireSignupConnectPixel()

    /**
     * Fired when user sets up a sync account directly.
     */
    fun fireSignupDirectPixel()

    fun fireSyncAccountErrorPixel(
        result: Error,
    )
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSyncPixels @Inject constructor(
    private val pixel: Pixel,
    private val statsRepository: SyncStatsRepository,
    private val sharedPrefsProvider: SharedPrefsProvider,
) : SyncPixels {

    private val preferences: SharedPreferences by lazy {
        sharedPrefsProvider.getSharedPrefs(SYNC_PIXELS_PREF_FILE)
    }

    override fun fireDailyPixel() {
        val dailyStats = statsRepository.getYesterdayDailyStats()
        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats).plus(dailyStats.operationErrorStats)
        tryToFireDailyPixel(SYNC_DAILY_PIXEL, payload)
    }

    override fun fireTimestampConflictPixel(feature: String) {
        pixel.fire(
            String.format(Locale.US, SyncPixelName.SYNC_TIMESTAMP_RESOLUTION_TRIGGERED.pixelName, feature),
        )
    }

    override fun fireLoginPixel() {
        pixel.fire(SyncPixelName.SYNC_LOGIN)
    }

    override fun fireSignupConnectPixel() {
        pixel.fire(SyncPixelName.SYNC_SIGNUP_CONNECT)
    }

    override fun fireSignupDirectPixel() {
        pixel.fire(SyncPixelName.SYNC_SIGNUP_DIRECT)
    }

    override fun fireSyncAccountErrorPixel(result: Error) {
        pixel.fire(
            SyncPixelName.SYNC_ACCOUNT_FAILURE,
            mapOf(
                SyncPixelParameters.ERROR_CODE to result.code.toString(),
                SyncPixelParameters.ERROR_REASON to result.reason,
            ),
        )
    }

    private fun tryToFireDailyPixel(
        pixel: SyncPixelName,
        payload: Map<String, String> = emptyMap(),
    ) {
        val now = getUtcIsoLocalDate()
        val timestamp = preferences.getString(pixel.name.appendTimestampSuffix(), null)

        // check if pixel was already sent in the current day
        if (timestamp == null || now > timestamp) {
            this.pixel.fire(pixel, payload)
                .also { preferences.edit { putString(pixel.name.appendTimestampSuffix(), now) } }
        }
    }

    private fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun String.appendTimestampSuffix(): String {
        return "${this}_timestamp"
    }

    companion object {
        private const val SYNC_PIXELS_PREF_FILE = "com.duckduckgo.sync.pixels.v1"
    }
}

// https://app.asana.com/0/72649045549333/1205649300615861
enum class SyncPixelName(override val pixelName: String) : Pixel.PixelName {
    SYNC_DAILY_PIXEL("m_sync_success_rate_daily"),
    SYNC_TIMESTAMP_RESOLUTION_TRIGGERED("m_sync_%s_local_timestamp_resolution_triggered"),
    SYNC_LOGIN("m_sync_login"),
    SYNC_SIGNUP_DIRECT("m_sync_signup_direct"),
    SYNC_SIGNUP_CONNECT("m_sync_signup_connect"),
    SYNC_ACCOUNT_FAILURE("m_sync_account_failure"),
    SYNC_ENCRYPT_FAILURE("m_sync_encrypt_failure"),
    SYNC_DECRYPT_FAILURE("m_sync_decrypt_failure"),
}

object SyncPixelParameters {
    const val COUNT = "sync_count"
    const val DATE = "date"
    const val OBJECT_LIMIT_EXCEEDED_COUNT = "%s_object_limit_exceeded_count"
    const val REQUEST_SIZE_LIMIT_EXCEEDED_COUNT = "%s_request_size_limit_exceeded_count"
    const val VALIDATION_ERROR_COUNT = "%s_validation_error"
    const val TOO_MANY_REQUESTS = "%s_too_many_requests_count"
    const val DATA_ENCRYPT_ERROR = "encrypt_error_count"
    const val DATA_DECRYPT_ERROR = "decrypt_error_count"
    const val ERROR_CODE = "code"
    const val ERROR_REASON = "reason"
}
