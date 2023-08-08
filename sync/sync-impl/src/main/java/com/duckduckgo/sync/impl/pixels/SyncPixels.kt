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
import com.duckduckgo.sync.impl.pixels.SyncPixelValues.Feature
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SyncPixels {
    fun fireStatsPixel()

    fun fireMergeConflictPixel(feature: Feature)

    fun fireOrphanPresentPixel(feature: Feature)
    fun fireEncryptFailurePixel()

    fun fireDecryptFailurePixel()

    fun fireCountLimitPixel(feature: Feature)
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

    override fun fireMergeConflictPixel(feature: Feature) {
        pixel.fire(
            SyncPixelName.SYNC_MERGE_CONFLICT,
            mapOf(
                SyncPixelParameters.FEATURE to feature.toString(),
            ),
        )
    }

    override fun fireOrphanPresentPixel(feature: Feature) {
        pixel.fire(
            SyncPixelName.SYNC_ORPHAN_PRESENT,
            mapOf(
                SyncPixelParameters.FEATURE to feature.toString(),
            ),
        )
    }

    override fun fireEncryptFailurePixel() {
        pixel.fire(SyncPixelName.SYNC_ENCRYPT_FAILURE)
    }

    override fun fireDecryptFailurePixel() {
        pixel.fire(SyncPixelName.SYNC_DECRYPT_FAILURE)
    }

    override fun fireCountLimitPixel(feature: Feature) {
        pixel.fire(
            SyncPixelName.SYNC_COUNT_LIMIT,
            mapOf(
                SyncPixelParameters.FEATURE to feature.toString(),
            ),
        )
    }
}

enum class SyncPixelName(override val pixelName: String) : Pixel.PixelName {
    SYNC_SUCCESS_RATE("m_sync_daily_success_rate"),
    SYNC_DAILY_ATTEMPTS("m_sync_daily_attempts"),
    SYNC_MERGE_CONFLICT("m_sync_merge_conflict"),
    SYNC_ORPHAN_PRESENT("m_sync_orphan_present"),
    SYNC_ENCRYPT_FAILURE("m_sync_encrypt_failure"),
    SYNC_DECRYPT_FAILURE("m_sync_decrypt_failure"),
    SYNC_COUNT_LIMIT("m_sync_count_limit"),
}

object SyncPixelParameters {
    const val ATTEMPTS = "attempts"
    const val RATE = "rate"
    const val FEATURE = "feature"
}

object SyncPixelValues {
    sealed class Feature {
        object Bookmarks : Feature()
        object Autofill : Feature()
        object Settings : Feature()
    }
}
