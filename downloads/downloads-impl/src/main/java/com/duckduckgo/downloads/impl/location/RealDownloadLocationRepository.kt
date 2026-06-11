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

package com.duckduckgo.downloads.impl.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.CustomDownloadLocation
import com.duckduckgo.downloads.api.DownloadLocationRepository
import com.duckduckgo.downloads.impl.di.DownloadLocation
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadLocationRepository @Inject constructor(
    @DownloadLocation private val dataStore: DataStore<Preferences>,
) : DownloadLocationRepository {

    override val customLocationFlow: Flow<CustomDownloadLocation?> = dataStore.data.map { preferences ->
        preferences.toCustomLocation()
    }

    override suspend fun getCustomLocation(): CustomDownloadLocation? {
        return dataStore.data.first().toCustomLocation()
    }

    override suspend fun saveCustomLocation(location: CustomDownloadLocation) {
        dataStore.edit { preferences ->
            preferences[TREE_URI_KEY] = location.treeUri
            preferences[DISPLAY_NAME_KEY] = location.displayName
            preferences[PATH_LABEL_KEY] = location.pathLabel
        }
    }

    override suspend fun clearCustomLocation() {
        dataStore.edit { preferences ->
            preferences.remove(TREE_URI_KEY)
            preferences.remove(DISPLAY_NAME_KEY)
            preferences.remove(PATH_LABEL_KEY)
        }
    }

    private fun Preferences.toCustomLocation(): CustomDownloadLocation? {
        val treeUri = this[TREE_URI_KEY] ?: return null
        val displayName = this[DISPLAY_NAME_KEY] ?: return null
        val pathLabel = this[PATH_LABEL_KEY] ?: displayName
        return CustomDownloadLocation(
            treeUri = treeUri,
            displayName = displayName,
            pathLabel = pathLabel,
        )
    }

    companion object {
        private val TREE_URI_KEY = stringPreferencesKey("download_location_tree_uri")
        private val DISPLAY_NAME_KEY = stringPreferencesKey("download_location_display_name")
        private val PATH_LABEL_KEY = stringPreferencesKey("download_location_path_label")
    }
}
