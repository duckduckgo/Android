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

import com.duckduckgo.di.scopes.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.impl.FavoritesDisplayModeSettingsRepository
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.NATIVE
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.UNIFIED
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface SavedSitesFormFactorSyncMigration {
    fun onFormFactorFavouritesEnabled()
    fun onFormFactorFavouritesDisabled()
}

@ContributesBinding(AppScope::class)
class RealSavedSitesFormFactorSyncMigration @Inject constructor(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val favoritesFormFactorSettings: FavoritesDisplayModeSettingsRepository,
) : SavedSitesFormFactorSyncMigration {
    override fun onFormFactorFavouritesEnabled() {
        logcat { "Sync-Bookmarks: syncEnabled creating FFS folders" }
        savedSitesEntitiesDao.createFormFactorFavoriteFolders()
        savedSitesRelationsDao.cloneFolder(
            SavedSitesNames.FAVORITES_ROOT,
            SavedSitesNames.FAVORITES_MOBILE_ROOT,
        )
    }

    override fun onFormFactorFavouritesDisabled() {
        logcat { "Sync-Bookmarks: syncDisabled removing FFS folders and migrating favorites" }
        when (favoritesFormFactorSettings.favoritesDisplayMode) {
            NATIVE -> {
                savedSitesRelationsDao.migrateNativeFavoritesAsNewRoot()
            }
            UNIFIED -> {
                savedSitesRelationsDao.migrateUnifiedFavoritesAsNewRoot()
            }
        }
        savedSitesEntitiesDao.removeFormFactorFavoriteFolders()
        favoritesFormFactorSettings.favoritesDisplayMode = NATIVE
        logcat { "Sync-Bookmarks: favoriteFormFactor changed to NATIVE" }
    }
}
