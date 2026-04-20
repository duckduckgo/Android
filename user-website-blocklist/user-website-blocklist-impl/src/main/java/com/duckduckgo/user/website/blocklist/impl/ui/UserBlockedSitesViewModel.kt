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

package com.duckduckgo.user.website.blocklist.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.user.website.blocklist.api.UserWebsiteBlocklist
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class UserBlockedSitesViewModel @Inject constructor(
    private val blocklist: UserWebsiteBlocklist,
) : ViewModel() {

    data class Item(
        val domain: String,
        val addedAt: Long,
    )

    data class ViewState(
        val items: List<Item> = emptyList(),
    )

    private val _state = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            blocklist.blockedDomainsWithTimestamps().collect { list ->
                _state.value = ViewState(items = list.map { Item(it.domain, it.addedAt) })
            }
        }
    }

    fun onUnblockClicked(domain: String) {
        viewModelScope.launch { blocklist.unblock(domain) }
    }

    fun onClearAllConfirmed() {
        viewModelScope.launch { blocklist.clearAll() }
    }
}
