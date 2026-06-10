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

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeShowOnAppLaunchOptionDataStore(defaultOption: ShowOnAppLaunchOption? = null) : ShowOnAppLaunchOptionDataStore {

    override var showOnAppLaunchTabId: String? = null
        private set

    private var optionSelected = defaultOption != null

    private var currentOptionStateFlow = MutableStateFlow(defaultOption ?: LastOpenedTab)

    private var currentSpecificPageUrl = MutableStateFlow("https://duckduckgo.com")

    override val optionFlow: Flow<ShowOnAppLaunchOption> = currentOptionStateFlow.asStateFlow()

    override val specificPageUrlFlow: Flow<String> = currentSpecificPageUrl.asStateFlow()

    override suspend fun hasOptionSelected(): Boolean = optionSelected

    override suspend fun setShowOnAppLaunchOption(showOnAppLaunchOption: ShowOnAppLaunchOption) {
        optionSelected = true
        currentOptionStateFlow.value = showOnAppLaunchOption
    }

    override suspend fun setSpecificPageUrl(url: String) {
        currentSpecificPageUrl.value = url
    }

    var resolvedPageUrl: String? = null
        private set

    override suspend fun setResolvedPageUrl(url: String) {
        resolvedPageUrl = url
    }

    override fun setShowOnAppLaunchTabId(tabId: String) {
        showOnAppLaunchTabId = tabId
    }
}
