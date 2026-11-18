/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.segmentskip

import com.duckduckgo.common.utils.sha256
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

enum class SegmentSkipSkipMode {
    MANUAL,
    AUTO,
}

data class SegmentSkipConfig(
    val enabled: Boolean,
    val skipMode: SegmentSkipSkipMode,
)

interface SegmentSkip {
    /** Built from the cached user mode, so it can lag a mode change made just after launch; [getSegments] reads it from storage. */
    fun config(): SegmentSkipConfig

    /**
     * Segments for [videoId], sorted by start. Empty when SegmentSkip is disabled or turned off by the user, [videoId] is blank,
     * or the lookup fails.
     */
    suspend fun getSegments(videoId: String): List<SegmentSkipSegment>

    fun modeFlow(): Flow<SegmentSkipMode>

    suspend fun setMode(mode: SegmentSkipMode)
}

@ContributesBinding(AppScope::class)
class RealSegmentSkip @Inject constructor(
    private val segmentSkipFeature: SegmentSkipFeature,
    private val segmentsProvider: SegmentSkipSegmentsProvider,
    private val hashPrefixGate: SegmentSkipHashPrefixGate,
    private val userPreferences: SegmentSkipUserPreferences,
) : SegmentSkip {

    override fun config(): SegmentSkipConfig {
        val mode = userPreferences.cachedMode()
        return SegmentSkipConfig(
            enabled = isEnabled() && mode != SegmentSkipMode.OFF,
            skipMode = if (mode == SegmentSkipMode.ALWAYS_SKIP) SegmentSkipSkipMode.AUTO else SegmentSkipSkipMode.MANUAL,
        )
    }

    override suspend fun getSegments(videoId: String): List<SegmentSkipSegment> {
        if (videoId.isBlank() || !isEnabled() || userPreferences.modeFlow().firstOrNull() == SegmentSkipMode.OFF) return emptyList()
        return try {
            val hash = videoId.sha256
            if (!hashPrefixGate.hasSegments(hash.take(DATASET_PREFIX_LENGTH))) return emptyList()
            segmentsProvider.getSegments(videoId, hash.take(HASH_PREFIX_LENGTH)).sortedBy { it.start }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "SegmentSkip: segments lookup failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override fun modeFlow(): Flow<SegmentSkipMode> = userPreferences.modeFlow()

    override suspend fun setMode(mode: SegmentSkipMode) {
        userPreferences.setMode(mode)
    }

    private fun isEnabled(): Boolean = segmentSkipFeature.mvpEnabled().isEnabled()

    private companion object {
        const val HASH_PREFIX_LENGTH = 4
        const val DATASET_PREFIX_LENGTH = 8
    }
}
