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

package com.duckduckgo.app.launch

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface TestScenarioSeeder {
    suspend fun seedIfNeeded(
        isMaestroExtra: String?,
        scenarioKey: String?,
        omnibarPosition: String?,
        nativeInputToggle: String?,
    )

    companion object {
        const val EXTRA_IS_MAESTRO = "isMaestro"
        const val EXTRA_TEST_SCENARIO = "testScenario"
        const val EXTRA_OMNIBAR_POSITION = "omnibarPosition"
        const val EXTRA_NATIVE_INPUT_TOGGLE = "nativeInputToggle"
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTestScenarioSeeder @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val settingsDataStore: SettingsDataStore,
    private val duckChatDataStore: DuckChatDataStore,
    private val dispatchers: DispatcherProvider,
) : TestScenarioSeeder {

    override suspend fun seedIfNeeded(
        isMaestroExtra: String?,
        scenarioKey: String?,
        omnibarPosition: String?,
        nativeInputToggle: String?,
    ) {
        if (isMaestroExtra != "true") return
        withContext(dispatchers.io()) {
            scenarioKey?.let { TestScenario.fromKey(it)?.seed(savedSitesRepository) }
            omnibarPosition?.let {
                when (it.lowercase()) {
                    "top" -> settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
                    "bottom" -> settingsDataStore.omnibarType = OmnibarType.SINGLE_BOTTOM
                    "split" -> settingsDataStore.omnibarType = OmnibarType.SPLIT
                }
            }
            nativeInputToggle?.let {
                duckChatDataStore.setDuckChatUserEnabled(it == "true")
            }
        }
    }
}
