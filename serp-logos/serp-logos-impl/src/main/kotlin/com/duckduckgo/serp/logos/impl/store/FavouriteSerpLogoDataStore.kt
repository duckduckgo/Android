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

package com.duckduckgo.serp.logos.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.serp.logos.impl.di.FavouriteSerpEasterEggLogo
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface FavouriteSerpLogoDataStore {

    val favouriteSerpEasterEggLogoUrlFlow: Flow<String?>

    suspend fun setFavouriteLogo(url: String?)

    suspend fun clearFavouriteLogo()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class FavouriteSerpLogoPrefsDataStore @Inject constructor(
    @FavouriteSerpEasterEggLogo private val store: DataStore<Preferences>,
) : FavouriteSerpLogoDataStore {

    override val favouriteSerpEasterEggLogoUrlFlow: Flow<String?> = store.data.map { prefs ->
        prefs[FAVOURITE_LOGO_URL]
    }

    override suspend fun setFavouriteLogo(url: String?) {
        store.edit { prefs ->
            if (url != null) {
                prefs[FAVOURITE_LOGO_URL] = url
            } else {
                prefs.remove(FAVOURITE_LOGO_URL)
            }
        }
    }

    override suspend fun clearFavouriteLogo() {
        store.edit { prefs -> prefs.remove(FAVOURITE_LOGO_URL) }
    }

    private companion object {
        private val FAVOURITE_LOGO_URL = stringPreferencesKey("FAVOURITE_LOGO_URL")
    }
}
