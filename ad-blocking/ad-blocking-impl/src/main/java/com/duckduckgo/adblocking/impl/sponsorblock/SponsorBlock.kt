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

package com.duckduckgo.adblocking.impl.sponsorblock

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

enum class SponsorBlockSkipMode {
    MANUAL,
}

data class SponsorBlockConfig(
    val enabled: Boolean,
    val skipMode: SponsorBlockSkipMode,
)

interface SponsorBlock {
    fun config(): SponsorBlockConfig

    /** Emits the current [config] and then every change to it. */
    fun observeConfig(): Flow<SponsorBlockConfig>

    /**
     * Segments for [videoId], sorted by start. Empty when SponsorBlock is disabled, [videoId] is blank, or the lookup
     * fails.
     */
    suspend fun getSegments(videoId: String): List<SponsorBlockSegment>
}

@ContributesBinding(AppScope::class)
class RealSponsorBlock @Inject constructor(
    private val sponsorBlockFeature: SponsorBlockFeature,
    private val segmentsProvider: SponsorBlockSegmentsProvider,
) : SponsorBlock {

    // TODO: send the user's SponsorBlock settings (on/off, skip mode) once they exist; skipMode is fixed to manual for now.
    override fun config(): SponsorBlockConfig = config(enabled = isEnabled())

    override fun observeConfig(): Flow<SponsorBlockConfig> =
        sponsorBlockFeature.mvpEnabled().enabled().map { config(enabled = it) }.distinctUntilChanged()

    override suspend fun getSegments(videoId: String): List<SponsorBlockSegment> {
        if (videoId.isBlank() || !isEnabled()) return emptyList()
        return try {
            segmentsProvider.getSegments(videoId).sortedBy { it.start }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "SponsorBlock: segments lookup failed: ${e.javaClass.simpleName}" }
            emptyList()
        }
    }

    private fun config(enabled: Boolean) = SponsorBlockConfig(enabled = enabled, skipMode = SponsorBlockSkipMode.MANUAL)

    private fun isEnabled(): Boolean = sponsorBlockFeature.mvpEnabled().isEnabled()
}
