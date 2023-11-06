package com.duckduckgo.app.sync

import com.duckduckgo.savedsites.impl.RealSavedSitesSettingsRepository.ViewMode
import com.duckduckgo.savedsites.impl.RealSavedSitesSettingsRepository.ViewMode.DEFAULT
import com.duckduckgo.savedsites.impl.SavedSitesSettingsRepository
import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.FavoritesViewMode.NATIVE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSavedSitesSettingsRepository() : SavedSitesSettingsRepository {
    val viewModeFlow = MutableStateFlow<ViewMode>(DEFAULT)

    override var favoritesDisplayMode: FavoritesViewMode = NATIVE
    override fun viewModeFlow(): Flow<ViewMode> = viewModeFlow

    suspend fun setViewMode(favoritesViewMode: FavoritesViewMode) {
        favoritesDisplayMode = favoritesViewMode
        viewModeFlow.value = ViewMode.FormFactorViewMode(favoritesViewMode)
    }
}
