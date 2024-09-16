package com.duckduckgo.app.sync

import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.FavoritesDisplayModeSettingsRepository
import com.duckduckgo.savedsites.store.FavoritesDisplayMode
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.NATIVE
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.UNIFIED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeDisplayModeSettingsRepository() : FavoritesDisplayModeSettingsRepository {

    override var favoritesDisplayMode: FavoritesDisplayMode = NATIVE

    override fun favoritesDisplayModeFlow(): Flow<FavoritesDisplayMode> = MutableStateFlow(NATIVE)

    override fun getFavoriteFolderFlow(): Flow<String> = flowOf(SavedSitesNames.FAVORITES_ROOT)

    override fun getQueryFolder(): String = SavedSitesNames.FAVORITES_ROOT

    override fun getInsertFolder(): List<String> = listOf(SavedSitesNames.FAVORITES_ROOT)

    override fun getDeleteFolder(entityId: String): List<String> = listOf(SavedSitesNames.FAVORITES_ROOT)
}

class FakeFavoritesDisplayModeSettingsRepository(default: FavoritesDisplayMode = NATIVE) : FavoritesDisplayModeSettingsRepository {

    override var favoritesDisplayMode: FavoritesDisplayMode = default

    val favoritesDisplayModeFlow = MutableStateFlow(default)

    val queryFavoritesFolderFlow = MutableStateFlow(getQueryFolder())

    override fun favoritesDisplayModeFlow(): Flow<FavoritesDisplayMode> = favoritesDisplayModeFlow
    override fun getFavoriteFolderFlow(): Flow<String> = queryFavoritesFolderFlow

    override fun getQueryFolder(): String {
        return when (favoritesDisplayMode) {
            NATIVE -> SavedSitesNames.FAVORITES_MOBILE_ROOT
            UNIFIED -> SavedSitesNames.FAVORITES_ROOT
        }
    }

    override fun getInsertFolder(): List<String> = listOf(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_MOBILE_ROOT)

    override fun getDeleteFolder(entityId: String): List<String> = listOf(
        SavedSitesNames.FAVORITES_ROOT,
        SavedSitesNames.FAVORITES_MOBILE_ROOT,
        SavedSitesNames.FAVORITES_DESKTOP_ROOT,
    )
}
