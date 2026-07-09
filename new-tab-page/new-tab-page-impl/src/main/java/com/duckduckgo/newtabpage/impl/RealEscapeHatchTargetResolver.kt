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

package com.duckduckgo.newtabpage.impl

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.EscapeHatchTarget
import com.duckduckgo.newtabpage.api.EscapeHatchTargetResolver
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDateTime
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealEscapeHatchTargetResolver @Inject constructor(
    private val browserModeStateHolder: BrowserModeStateHolder,
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
    private val fireModeAvailability: FireModeAvailability,
) : EscapeHatchTargetResolver {

    override suspend fun resolve(): EscapeHatchTarget? {
        // No hatch in Fire mode — also defends against a stale process-global isAfterIdleReturn that
        // was set in Regular before the user switched to Fire.
        if (browserModeStateHolder.currentMode.value == BrowserMode.FIRE) return null

        val candidateModes = if (fireModeAvailability.isAvailable()) {
            listOf(BrowserMode.REGULAR, BrowserMode.FIRE)
        } else {
            listOf(BrowserMode.REGULAR)
        }
        val candidates = candidateModes.mapNotNull { mode ->
            tabRepositoryProvider.forMode(mode).getLastAccessedTab()?.let { it to mode }
        }
        val (tab, mode) = candidates.maxByOrNull { (candidate, _) -> candidate.lastAccessTime ?: LocalDateTime.MIN }
            ?: return null
        return EscapeHatchTarget(tab.tabId, mode)
    }
}
