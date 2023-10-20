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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.sync.api.*
import com.squareup.anvil.annotations.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface FavoritesAccessor {
    fun getFavoriteFolderFlow(): Flow<String>
    fun getFavoriteFolder(): String
    fun getInsertFavoriteFolder(): List<String>
    fun getDeleteFavoriteFolder(entityId: String): List<String>
    fun changeViewMode()
}

@ContributesBinding(AppScope::class)
class FavoritesAccessorImpl @Inject constructor(
    private val deviceSyncState: DeviceSyncState,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val savedSitesSettings: SavedSitesSettingsRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : FavoritesAccessor {

    private var viewMode = FavoritesViewMode.NATIVE

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            savedSitesSettings.viewModeFlow().collect {
                viewMode = it
            }
        }
    }

    override fun getFavoriteFolderFlow(): Flow<String> {
        return savedSitesSettings.viewModeFlow().map { getFavoriteFolderFor(it) }
    }

    override fun getFavoriteFolder(): String {
        return getFavoriteFolderFor(viewMode)
    }

    override fun getInsertFavoriteFolder(): List<String> {
        if (!deviceSyncState.isUserSignedInOnDevice()) return listOf(SavedSitesNames.FAVORITES_ROOT)
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesViewMode.UNIFIED -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesViewMode.DESKTOP -> TODO()
        }
    }

    override fun getDeleteFavoriteFolder(
        entityId: String,
    ): List<String> {
        if (!deviceSyncState.isUserSignedInOnDevice()) return listOf(SavedSitesNames.FAVORITES_ROOT)
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> {
                val isDesktopFavorite = savedSitesRelationsDao.relationsByEntityId(entityId)
                    .find { it.folderId == SavedSitesNames.FAVORITES_DESKTOP_ROOT } != null
                if (isDesktopFavorite) {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT)
                } else {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
                }
            }

            FavoritesViewMode.UNIFIED -> {
                return listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
            }

            FavoritesViewMode.DESKTOP -> TODO()
        }
    }

    private fun getFavoriteFolderFor(viewMode: FavoritesViewMode): String {
        if (!deviceSyncState.isUserSignedInOnDevice()) return SavedSitesNames.FAVORITES_ROOT

        return when (viewMode) {
            FavoritesViewMode.NATIVE -> SavedSitesNames.FAVORITES_MOBILE_ROOT
            FavoritesViewMode.UNIFIED -> SavedSitesNames.FAVORITES_ROOT
            FavoritesViewMode.DESKTOP -> SavedSitesNames.FAVORITES_DESKTOP_ROOT
        }
    }

    override fun changeViewMode() {
        when (viewMode) {
            FavoritesViewMode.UNIFIED -> {
                savedSitesSettings.favoritesDisplayMode = FavoritesViewMode.NATIVE
            }
            FavoritesViewMode.NATIVE -> {
                savedSitesSettings.favoritesDisplayMode = FavoritesViewMode.DESKTOP
            }
            else -> {
                savedSitesSettings.favoritesDisplayMode = FavoritesViewMode.UNIFIED
            }
        }
    }
}
