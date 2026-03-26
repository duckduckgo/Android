/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore.Companion.DEFAULT_SPECIFIC_PAGE_URL
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ShowOnAppLaunchOptionDataStore {
    val optionFlow: Flow<ShowOnAppLaunchOption>
    val specificPageUrlFlow: Flow<String>
    val showOnAppLaunchTabId: String?

    fun setShowOnAppLaunchTabId(tabId: String)
    suspend fun setShowOnAppLaunchOption(showOnAppLaunchOption: ShowOnAppLaunchOption)
    suspend fun setSpecificPageUrl(url: String)
    suspend fun setResolvedPageUrl(url: String)

    companion object {
        const val DEFAULT_SPECIFIC_PAGE_URL = "https://duckduckgo.com/"
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class ShowOnAppLaunchOptionPrefsDataStore @Inject constructor(
    @ShowOnAppLaunch private val store: DataStore<Preferences>,
) : ShowOnAppLaunchOptionDataStore {

    override var showOnAppLaunchTabId: String? = null
        private set

    override val optionFlow: Flow<ShowOnAppLaunchOption> = store.data.map { preferences ->
        preferences[intPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_OPTION)]?.let { optionId ->
            when (val option = ShowOnAppLaunchOption.mapToOption(optionId)) {
                LastOpenedTab,
                NewTabPage,
                -> option
                is SpecificPage -> {
                    val url = preferences[stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_URL)]!!
                    val resolvedUrl = preferences[stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_RESOLVED_URL)]
                    SpecificPage(url, resolvedUrl)
                }
            }
        } ?: LastOpenedTab
    }

    override val specificPageUrlFlow: Flow<String> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_URL)] ?: DEFAULT_SPECIFIC_PAGE_URL
    }

    override suspend fun setShowOnAppLaunchOption(showOnAppLaunchOption: ShowOnAppLaunchOption) {
        store.edit { preferences ->
            preferences[intPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_OPTION)] = showOnAppLaunchOption.id

            if (showOnAppLaunchOption is SpecificPage) {
                preferences.setShowOnAppLaunch(showOnAppLaunchOption.url)
                preferences.remove(stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_RESOLVED_URL))
                showOnAppLaunchTabId = null
            }
        }
    }

    override fun setShowOnAppLaunchTabId(tabId: String) {
        showOnAppLaunchTabId = tabId
    }

    override suspend fun setSpecificPageUrl(url: String) {
        store.edit { preferences ->
            preferences.setShowOnAppLaunch(url)
        }
    }

    override suspend fun setResolvedPageUrl(url: String) {
        store.edit { preferences ->
            preferences.setShowOnAppLaunchResolvedUrl(url)
        }
    }

    private fun MutablePreferences.setShowOnAppLaunch(url: String) {
        set(stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_URL), url)
    }

    private fun MutablePreferences.setShowOnAppLaunchResolvedUrl(url: String) {
        set(stringPreferencesKey(KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_RESOLVED_URL), url)
    }

    companion object {
        private const val KEY_SHOW_ON_APP_LAUNCH_OPTION = "SHOW_ON_APP_LAUNCH_OPTION"
        private const val KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_URL = "SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_URL"
        private const val KEY_SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_RESOLVED_URL = "SHOW_ON_APP_LAUNCH_SPECIFIC_PAGE_RESOLVED_URL"
    }
}
