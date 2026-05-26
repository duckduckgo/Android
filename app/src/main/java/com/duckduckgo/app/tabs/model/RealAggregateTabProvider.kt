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

package com.duckduckgo.app.tabs.model

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAggregateTabProvider @Inject constructor(
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository>,
    private val fireModeAvailability: FireModeAvailability,
) : AggregateTabProvider {

    override fun observe(modes: Set<BrowserMode>): Flow<List<TabEntity>> {
        val effectiveModes = if (fireModeAvailability.isAvailable()) modes else modes - BrowserMode.FIRE
        val flows = effectiveModes.map { tabRepositoryProvider.forMode(it).flowTabs }
        return when {
            flows.isEmpty() -> flowOf(emptyList())
            flows.size == 1 -> flows.single()
            else -> combine(flows) { lists -> lists.flatMap { it } }
        }
    }
}
