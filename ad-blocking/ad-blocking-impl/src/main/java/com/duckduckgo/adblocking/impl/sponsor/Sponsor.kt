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

package com.duckduckgo.adblocking.impl.sponsor

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

enum class SponsorSkipMode {
    MANUAL,
    AUTO,
}

data class SponsorConfig(
    val enabled: Boolean,
    val skipMode: SponsorSkipMode,
)

interface Sponsor {
    /** Built from the cached user mode, so it can lag a mode change made just after launch; [getSegments] reads it from storage. */
    fun config(): SponsorConfig

    /**
     * Segments for [videoId], sorted by start. Empty when Sponsor is disabled or turned off by the user, [videoId] is blank,
     * or the lookup fails.
     */
    suspend fun getSegments(videoId: String): List<SponsorSegment>

    fun modeFlow(): Flow<SponsorMode>

    suspend fun setMode(mode: SponsorMode)
}

@ContributesBinding(AppScope::class)
class RealSponsor @Inject constructor(
    private val sponsorFeature: SponsorFeature,
    private val segmentsProvider: SponsorSegmentsProvider,
    private val userPreferences: SponsorUserPreferences,
) : Sponsor {

    override fun config(): SponsorConfig {
        val mode = userPreferences.cachedMode()
        return SponsorConfig(
            enabled = isEnabled() && mode != SponsorMode.OFF,
            skipMode = if (mode == SponsorMode.ALWAYS_SKIP) SponsorSkipMode.AUTO else SponsorSkipMode.MANUAL,
        )
    }

    override suspend fun getSegments(videoId: String): List<SponsorSegment> {
        if (videoId.isBlank() || !isEnabled() || userPreferences.modeFlow().firstOrNull() == SponsorMode.OFF) return emptyList()
        return try {
            segmentsProvider.getSegments(videoId).sortedBy { it.start }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Sponsor: segments lookup failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    override fun modeFlow(): Flow<SponsorMode> = userPreferences.modeFlow()

    override suspend fun setMode(mode: SponsorMode) {
        userPreferences.setMode(mode)
    }

    private fun isEnabled(): Boolean = sponsorFeature.mvpEnabled().isEnabled()
}
