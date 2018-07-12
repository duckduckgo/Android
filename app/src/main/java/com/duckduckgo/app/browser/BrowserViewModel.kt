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

package com.duckduckgo.app.browser

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.ViewModel
import android.support.annotation.StringRes
import com.duckduckgo.app.browser.BrowserViewModel.Command.DisplayMessage
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity.Companion.RELOAD_RESULT_CODE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val queryUrlConverter: OmnibarEntryConverter
) : ViewModel() {

    data class ViewState(
        val isFullScreen: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false
    )

    sealed class Command {
        object Refresh : Command()
        data class Query(val query: String) : Command()
        data class DisplayMessage(@StringRes val messageId: Int) : Command()
    }

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    var selectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun onNewTabRequested() {
        tabRepository.add()
    }

    fun onOpenInNewTabRequested(query: String) {
        tabRepository.add(queryUrlConverter.convertQueryToUrl(query))
    }

    fun onTabsUpdated(tabs: List<TabEntity>?) {
        if (tabs == null || tabs.isEmpty()) {
            tabRepository.add()
            return
        }
    }

    fun receivedDashboardResult(resultCode: Int) {
        if (resultCode == RELOAD_RESULT_CODE) command.value = Refresh
    }

    fun onClearRequested() {
        tabRepository.deleteAll()
    }

    fun onClearComplete() {
        command.value = DisplayMessage(R.string.fireDataCleared)
    }
}