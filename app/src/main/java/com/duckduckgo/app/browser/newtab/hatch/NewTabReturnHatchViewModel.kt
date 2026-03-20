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

package com.duckduckgo.app.browser.newtab.hatch

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class NewTabReturnHatchViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val tabTitle: String = "",
        val url: String = "",
        val tabId: String = "",
        val currentTabId: String = "",
        val shouldShow: Boolean = false,
    )

    val viewState = tabRepository.flowLastAccessedTab
        .map { lastTab ->
            val featureEnabled = androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()
            if (featureEnabled && lastTab != null) {
                ViewState(
                    tabTitle = lastTab.title.orEmpty(),
                    url = lastTab.url.orEmpty(),
                    tabId = lastTab.tabId,
                    currentTabId = lastTab.tabId,
                    shouldShow = true,
                )
            } else {
                ViewState(shouldShow = false)
            }
        }
        .flowOn(dispatchers.io())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ViewState())

    fun onHatchPressed() {
        viewModelScope.launch(dispatchers.io()) {
            tabRepository.select(viewState.value.currentTabId)
        }
    }
}
