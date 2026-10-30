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

package com.duckduckgo.adblocking.impl.duckplayer

import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

interface DuckPlayerSettingsPlacementProvider {
    /**
     * Emits where (or whether) the Duck Player settings entry should be shown and re-emits when it
     * changes (e.g. after a remote privacy config download). This is the single source of truth for
     * the entry's placement.
     */
    fun placementFlow(): Flow<DuckPlayerSettingsPlacement>
}

/**
 * Where the Duck Player settings entry is rendered.
 */
sealed interface DuckPlayerSettingsPlacement {
    data object InAdBlocking : DuckPlayerSettingsPlacement
    data object Standalone : DuckPlayerSettingsPlacement
    data object Hidden : DuckPlayerSettingsPlacement
}

@ContributesBinding(AppScope::class)
class RealDuckPlayerSettingsPlacementProvider @Inject constructor(
    private val duckPlayer: DuckPlayerInternal,
    private val duckPlayerFeature: DuckPlayerFeature,
    private val statusChecker: AdBlockingStatusChecker,
) : DuckPlayerSettingsPlacementProvider {

    override fun placementFlow(): Flow<DuckPlayerSettingsPlacement> =
        combine(
            duckPlayer.observeDuckPlayerState(),
            duckPlayerFeature.standaloneSettings().enabled(),
            statusChecker.observeSettingsVisible(),
        ) { duckPlayerState, standaloneSettings, adBlockingSettingsVisible ->
            when {
                duckPlayerState == DISABLED -> DuckPlayerSettingsPlacement.Hidden
                standaloneSettings -> DuckPlayerSettingsPlacement.Standalone
                !adBlockingSettingsVisible -> DuckPlayerSettingsPlacement.Standalone
                else -> DuckPlayerSettingsPlacement.InAdBlocking
            }
        }
}
