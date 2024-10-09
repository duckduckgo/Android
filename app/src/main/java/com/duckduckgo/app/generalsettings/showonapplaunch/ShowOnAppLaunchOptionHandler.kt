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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.toHttps
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface ShowOnAppLaunchOptionHandler {
    suspend fun handleAppLaunchOption()
}

@ContributesBinding(AppScope::class)
class ShowOnAppLaunchOptionHandlerImpl @Inject constructor(
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val tabRepository: TabRepository,
): ShowOnAppLaunchOptionHandler {

    override suspend fun handleAppLaunchOption() {
        when (val option = showOnAppLaunchOptionDataStore.optionFlow.first()) {
            LastOpenedTab -> Unit
            NewTabPage -> tabRepository.add()
            is SpecificPage -> handleSpecificPageOption(option)
        }
    }

    private suspend fun handleSpecificPageOption(option: SpecificPage) {
        val uri = option.url.toUri()

        if (isTabAlreadyAdded(uri)) {
            tabRepository.select(option.url)
        } else {
            tabRepository.add(option.url)
        }
    }

    private suspend fun isTabAlreadyAdded(uri: Uri): Boolean {
        val tabId = tabRepository.getTabId(uri.toString())
        val httpsTabId = tabRepository.getTabId(uri.toHttps.toString())

        return tabId != null || httpsTabId != null
    }
}
