/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import kotlinx.coroutines.launch

class TabSwitcherViewModel(
    private val tabRepository: TabRepository,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val faviconManager: FaviconManager
) : ViewModel() {

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data class DisplayMessage(@StringRes val messageId: Int) : Command()
        object Close : Command()
    }

    suspend fun onNewTabRequested() {
        tabRepository.add()
        command.value = Command.Close
    }

    suspend fun onTabSelected(tab: TabEntity) {
        tabRepository.select(tab.tabId)
        command.value = Command.Close
    }

    suspend fun onTabDeleted(tab: TabEntity) {
        tabRepository.delete(tab)
        webViewSessionStorage.deleteSession(tab.tabId)
    }

    fun onClearComplete() {
        command.value = Command.DisplayMessage(R.string.fireDataCleared)
        command.value = Command.Close
    }

    fun loadFavicon(tabId: String, url: String, view: ImageView) {
        viewModelScope.launch {
            faviconManager.loadToViewFromTemp(tabId, url, view)
        }
    }
}
