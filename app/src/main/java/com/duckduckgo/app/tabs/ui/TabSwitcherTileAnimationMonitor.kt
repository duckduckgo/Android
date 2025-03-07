/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.store.TabSwitcherPrefsDataStore
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

private const val MINIMUM_TRACKER_COUNT = 10
private const val MINIMUM_TAB_COUNT = 2

class TabSwitcherTileAnimationMonitor @Inject constructor(
    private val dispatchProvider: DispatcherProvider,
    private val tabSwitcherPrefsDataStore: TabSwitcherPrefsDataStore,
    private val tabDataRepository: TabDataRepository,
    private val webTrackersBlockedAppRepository: WebTrackersBlockedAppRepository,
) {

    fun observeAnimationTileVisibility(): Flow<Boolean> = combine(
        tabSwitcherPrefsDataStore.isAnimationTileDismissed(),
        tabSwitcherPrefsDataStore.hasAnimationTileBeenSeen(),
    ) { isAnimationTileDismissed, hasAnimationTileBeenSeen ->
        when {
            isAnimationTileDismissed -> false
            hasAnimationTileBeenSeen -> true
            else -> shouldDisplayAnimationTile()
        }
    }.flowOn(dispatchProvider.io())

    private suspend fun shouldDisplayAnimationTile(): Boolean {
        val openedTabs = tabDataRepository.getOpenTabCount()
        val trackerCountForLast7Days = webTrackersBlockedAppRepository.getTrackerCountForLast7Days()

        return trackerCountForLast7Days >= MINIMUM_TRACKER_COUNT &&
            openedTabs >= MINIMUM_TAB_COUNT
    }
}
