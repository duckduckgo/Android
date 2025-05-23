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
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.DisplayModeSyncableSetting
import com.duckduckgo.savedsites.store.FavoritesDisplayMode
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncStateMonitor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

interface FavoritesDisplayModeSettingsRepository {
    var favoritesDisplayMode: FavoritesDisplayMode
    fun favoritesDisplayModeFlow(): Flow<FavoritesDisplayMode>
    fun getFavoriteFolderFlow(): Flow<String>
    fun getQueryFolder(): String
    fun getInsertFolder(): List<String>
    fun getDeleteFolder(entityId: String): List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealFavoritesDisplayModeSettingsRepository @Inject constructor(
    private val savedSitesSettingsStore: SavedSitesSettingsStore,
    private val syncableSetting: DisplayModeSyncableSetting,
    private val syncStateMonitor: SyncStateMonitor,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : FavoritesDisplayModeSettingsRepository {

    private var syncEnabled: Boolean = false

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            syncStateMonitor.syncState()
                .collect {
                    syncEnabled = when (it) {
                        OFF -> false
                        else -> true
                    }
                }
        }
    }

    override var favoritesDisplayMode: FavoritesDisplayMode
        get() = savedSitesSettingsStore.favoritesDisplayMode
        set(value) {
            if (savedSitesSettingsStore.favoritesDisplayMode == value) return
            savedSitesSettingsStore.favoritesDisplayMode = value
            syncableSetting.onSettingChanged()
        }

    override fun favoritesDisplayModeFlow(): Flow<FavoritesDisplayMode> = savedSitesSettingsStore.favoritesFormFactorModeFlow()

    override fun getFavoriteFolderFlow(): Flow<String> {
        return syncStateMonitor.syncState().combine(savedSitesSettingsStore.favoritesFormFactorModeFlow()) { syncState, displayMode ->
            when (syncState) {
                OFF -> SavedSitesNames.FAVORITES_ROOT
                else -> getFavoriteDisplayModeFolder(displayMode)
            }
        }.distinctUntilChanged()
    }

    override fun getQueryFolder(): String {
        return when (syncEnabled) {
            false -> SavedSitesNames.FAVORITES_ROOT
            true -> return getFavoriteDisplayModeFolder(savedSitesSettingsStore.favoritesDisplayMode)
        }
    }

    private fun getFavoriteDisplayModeFolder(displayMode: FavoritesDisplayMode): String {
        return when (displayMode) {
            FavoritesDisplayMode.NATIVE -> SavedSitesNames.FAVORITES_MOBILE_ROOT
            FavoritesDisplayMode.UNIFIED -> SavedSitesNames.FAVORITES_ROOT
        }
    }

    override fun getInsertFolder(): List<String> {
        return when (syncEnabled) {
            false -> listOf(SavedSitesNames.FAVORITES_ROOT)
            true -> return getInsertDisplayModeFolder(savedSitesSettingsStore.favoritesDisplayMode)
        }
    }

    private fun getInsertDisplayModeFolder(displayMode: FavoritesDisplayMode): List<String> {
        return when (displayMode) {
            FavoritesDisplayMode.NATIVE -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesDisplayMode.UNIFIED -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
        }
    }

    override fun getDeleteFolder(entityId: String): List<String> {
        return when (syncEnabled) {
            false -> listOf(SavedSitesNames.FAVORITES_ROOT)
            true -> getDeleteDisplayModeFolder(entityId, savedSitesSettingsStore.favoritesDisplayMode)
        }
    }

    private fun getDeleteDisplayModeFolder(
        entityId: String,
        displayMode: FavoritesDisplayMode,
    ): List<String> {
        return when (displayMode) {
            FavoritesDisplayMode.NATIVE -> {
                val isDesktopFavorite = savedSitesRelationsDao.relationsByEntityId(entityId)
                    .find { it.folderId == SavedSitesNames.FAVORITES_DESKTOP_ROOT } != null
                logcat(INFO) { "Sync-Bookmarks: Deleting $entityId, isDesktopFavorite: $isDesktopFavorite" }
                if (isDesktopFavorite) {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT)
                } else {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
                }
            }

            FavoritesDisplayMode.UNIFIED -> {
                listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
            }
        }
    }
}
