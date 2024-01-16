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
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_DAILY_PIXEL
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface SyncPixels {

    /**
     * Fired once per day, for all users with sync enabled
     */
    fun fireDailyPixel()

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

    fun fireStatsPixel()

    fun fireOrphanPresentPixel(feature: String)

    fun firePersisterErrorPixel(
        feature: String,
        mergeError: SyncMergeResult.Error,
    )

    fun fireEncryptFailurePixel()

    fun fireDecryptFailurePixel()

    fun fireCountLimitPixel(feature: String)

    fun fireSyncAttemptErrorPixel(
        feature: String,
        result: Error,
    )

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
        tryToFireDailyPixel(SYNC_DAILY_PIXEL)
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

    override fun fireStatsPixel() {
        val dailyStats = statsRepository.getDailyStats()
        pixel.fire(
            SyncPixelName.SYNC_SUCCESS_RATE,
            mapOf(
                SyncPixelParameters.RATE to dailyStats.successRate.toString(),
            ),
        )
        pixel.fire(
            SyncPixelName.SYNC_DAILY_ATTEMPTS,
            mapOf(
                SyncPixelParameters.ATTEMPTS to dailyStats.attempts.toString(),
            ),
        )
    }

    override fun fireOrphanPresentPixel(feature: String) {
        pixel.fire(
            SyncPixelName.SYNC_ORPHAN_PRESENT,
            mapOf(
                SyncPixelParameters.FEATURE to feature,
            ),
        )
    }

    override fun firePersisterErrorPixel(
        feature: String,
        mergeError: SyncMergeResult.Error,
    ) {
        pixel.fire(
            SyncPixelName.SYNC_PERSISTER_FAILURE,
            mapOf(
                SyncPixelParameters.FEATURE to feature,
                SyncPixelParameters.ERROR_CODE to mergeError.code.toString(),
                SyncPixelParameters.ERROR_REASON to mergeError.reason,
            ),
        )
    }

    override fun fireEncryptFailurePixel() {
        // pixel.fire(SyncPixelName.SYNC_ENCRYPT_FAILURE)
    }

    override fun fireDecryptFailurePixel() {
        // pixel.fire(SyncPixelName.SYNC_DECRYPT_FAILURE)
    }

    override fun fireCountLimitPixel(feature: String) {
        pixel.fire(
            SyncPixelName.SYNC_COUNT_LIMIT,
            mapOf(
                SyncPixelParameters.FEATURE to feature,
            ),
        )
    }

    override fun fireSyncAttemptErrorPixel(
        feature: String,
        result: Error,
    ) {
        pixel.fire(
            SyncPixelName.SYNC_ATTEMPT_FAILURE,
            mapOf(
                SyncPixelParameters.FEATURE to feature,
                SyncPixelParameters.ERROR_CODE to result.code.toString(),
                SyncPixelParameters.ERROR_REASON to result.reason,
            ),
        )
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
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
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
    SYNC_DAILY_PIXEL("m_sync_daily"),
    SYNC_LOGIN("m_sync_login"),
    SYNC_SIGNUP_DIRECT("m_sync_signup_direct"),
    SYNC_SIGNUP_CONNECT("m_sync_signup_connect"),

    SYNC_SUCCESS_RATE("m_sync_daily_success_rate"),
    SYNC_DAILY_ATTEMPTS("m_sync_daily_attempts"),
    SYNC_ORPHAN_PRESENT("m_sync_orphan_present"),
    SYNC_ENCRYPT_FAILURE("m_sync_encrypt_failure"),
    SYNC_DECRYPT_FAILURE("m_sync_decrypt_failure"),
    SYNC_COUNT_LIMIT("m_sync_count_limit"),
    SYNC_ATTEMPT_FAILURE("m_sync_attempt_failure"),
    SYNC_ACCOUNT_FAILURE("m_sync_account_failure"),
    SYNC_PERSISTER_FAILURE("m_sync_persister_failure"),
}

object SyncPixelParameters {
    const val ATTEMPTS = "attempts"
    const val RATE = "rate"
    const val FEATURE = "feature"
    const val ERROR_CODE = "code"
    const val ERROR_REASON = "reason"
}
