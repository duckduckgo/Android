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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SyncPixels {
    fun fireStatsPixel()

    fun fireOrphanPresentPixel(feature: String)

    fun firePersisterErrorPixel(feature: String, mergeError: SyncMergeResult.Error)

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
class RealSyncPixels @Inject constructor(
    private val pixel: Pixel,
    private val statsRepository: SyncStatsRepository,
) : SyncPixels {
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

    override fun firePersisterErrorPixel(feature: String, mergeError: SyncMergeResult.Error) {
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
}

enum class SyncPixelName(override val pixelName: String) : Pixel.PixelName {
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
