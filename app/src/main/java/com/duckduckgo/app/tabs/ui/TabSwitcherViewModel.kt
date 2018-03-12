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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabEntity

class TabSwitcherViewModel(private val tabRepository: TabRepository) : ViewModel() {

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        data class DisplayMessage(@StringRes val messageId: Int) : Command()
        object Close : Command()
    }

    fun onNewTabRequested() {
        tabRepository.addNew()
        command.value = Command.Close
    }

    fun onTabSelected(tab: TabEntity) {
        tabRepository.select(tab.tabId)
        command.value = Command.Close
    }

    fun onTabDeleted(tab: TabEntity) {
        tabRepository.delete(tab)
    }

    fun onClearRequested() {
        tabRepository.deleteAll()
        command.value = Command.Close
    }

    fun onClearComplete() {
        command.value = Command.DisplayMessage(R.string.fireDataCleared)
    }
}
