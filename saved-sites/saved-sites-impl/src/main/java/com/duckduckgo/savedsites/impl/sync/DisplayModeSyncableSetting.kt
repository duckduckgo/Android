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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.FavoritesViewMode.NATIVE
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(scope = AppScope::class, boundType = SyncableSetting::class)
class DisplayModeSyncableSetting @Inject constructor(
    private val savedSitesSettingsStore: SavedSitesSettingsStore,
    private val syncSettingsListener: SyncSettingsListener,
) : SyncableSetting {

    private var listener: () -> Unit = {}

    override val key: String = "favorites_display_mode"

    override fun getValue(): String? {
        return savedSitesSettingsStore.favoritesDisplayMode.value
    }

    override fun save(value: String?): Boolean {
        val displayMode = FavoritesViewMode.values().firstOrNull { it.value == value } ?: return false
        Timber.i("Sync-Settings-Display-Mode: save($displayMode)")
        savedSitesSettingsStore.favoritesDisplayMode = displayMode
        listener.invoke()
        return true
    }

    override fun deduplicate(value: String?): Boolean {
        val displayMode = FavoritesViewMode.values().firstOrNull { it.value == value } ?: return false
        Timber.i("Sync-Settings-Display-Mode: deduplicate($displayMode)")
        savedSitesSettingsStore.favoritesDisplayMode = displayMode
        listener.invoke()
        return true
    }

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) {
        this.listener = onDataChanged
    }

    override fun onSettingChanged() {
        Timber.i("Sync-Settings-Display-Mode: notify Setting Changed")
        syncSettingsListener.onSettingChanged(key)
    }

    override fun onSyncDisabled() {
        savedSitesSettingsStore.favoritesDisplayMode = NATIVE
    }
}
