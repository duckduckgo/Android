/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.impl.sync.DisplayModeSyncableSetting
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.flow.Flow

interface SavedSitesSettingsRepository {
    var favoritesDisplayMode: FavoritesViewMode
    fun viewModeFlow(): Flow<FavoritesViewMode>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSavedSitesSettingsRepository @Inject constructor(
    private val savedSitesSettingsStore: SavedSitesSettingsStore,
    private val syncableSetting: DisplayModeSyncableSetting,
) : SavedSitesSettingsRepository {

    override var favoritesDisplayMode: FavoritesViewMode
        get() = savedSitesSettingsStore.favoritesDisplayMode
        set(value) {
            if (savedSitesSettingsStore.favoritesDisplayMode == value) return
            savedSitesSettingsStore.favoritesDisplayMode = value
            syncableSetting.onSettingChanged()
        }

    override fun viewModeFlow(): Flow<FavoritesViewMode> = savedSitesSettingsStore.viewModeFlow()
}
