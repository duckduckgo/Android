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

package com.duckduckgo.app.autocomplete.api

import com.duckduckgo.app.autocomplete.AutocompleteTabsFeature
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.DeviceAppLookup
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoCompleteFactory
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class AutoCompleteFactoryImpl @Inject constructor(
    private val autoCompleteService: AutoCompleteService,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val autoCompleteScorer: AutoCompleteScorer,
    private val autoCompleteRepository: AutoCompleteRepository,
    private val tabRepository: TabRepository,
    private val userStageStore: UserStageStore,
    private val autocompleteTabsFeature: AutocompleteTabsFeature,
    private val duckChat: DuckChat,
    private val history: NavigationHistory,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    private val deviceAppLookup: DeviceAppLookup,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : AutoCompleteFactory {

    override fun create(config: AutoComplete.Config): AutoComplete {
        return AutoCompleteApi(
            autoCompleteService = autoCompleteService,
            savedSitesRepository = savedSitesRepository,
            navigationHistory = navigationHistory,
            autoCompleteScorer = autoCompleteScorer,
            autoCompleteRepository = autoCompleteRepository,
            tabRepository = tabRepository,
            userStageStore = userStageStore,
            autocompleteTabsFeature = autocompleteTabsFeature,
            duckChat = duckChat,
            history = history,
            dispatchers = dispatchers,
            pixel = pixel,
            deviceAppLookup = deviceAppLookup,
            coroutineScope = coroutineScope,
            config = config,
        )
    }
}
