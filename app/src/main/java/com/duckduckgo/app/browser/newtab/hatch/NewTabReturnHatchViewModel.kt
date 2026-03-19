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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.tabs.TabManager.TabModel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.logcat

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class NewTabReturnHatchViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val tabTitle: String = "",
        val url: String = "",
        val tabId: String = "",
        val currentTabId: String = "",
        val shouldShow: Boolean = false
    )

    val tabsFlow: Flow<List<TabEntity>> = tabRepository.flowTabs
        .distinctUntilChanged()

    private val _viewState = MutableStateFlow(ViewState())

    val viewState = combine(
        _viewState,
        tabsFlow,
    ) { state, tabs ->
        val lastTab = tabs.first()
        state.copy(
            tabTitle = lastTab.title ?: "Title", url = lastTab.url ?: "url.com",
            tabId = lastTab.tabId,
            currentTabId = lastTab.tabId,
            shouldShow = true,
        )

    }.flowOn(dispatchers.io()).stateIn(viewModelScope, SharingStarted.Eagerly, _viewState.value)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatchers.io()) {
            val lastTab = tabRepository.getSelectedTab()
            val tabs = tabRepository.getTabs().toString()
            logcat { "Hatch: tabs $tabs" }
            if (lastTab != null) {
                if (lastTab.url != null && lastTab.title != null) {
                    _viewState.value = ViewState(
                        tabTitle = lastTab.title!!, url = lastTab.url!!,
                        tabId = lastTab.tabId,
                        currentTabId = lastTab.tabId,
                        shouldShow = true,
                    )
                } else {
                    _viewState.value = ViewState(shouldShow = false)
                }
            } else {
                _viewState.value = ViewState(shouldShow = false)
            }
        }
    }

    fun onHatchPressed() {
        viewModelScope.launch(dispatchers.io()) {
            tabRepository.select(_viewState.value.currentTabId)
        }
    }
}
