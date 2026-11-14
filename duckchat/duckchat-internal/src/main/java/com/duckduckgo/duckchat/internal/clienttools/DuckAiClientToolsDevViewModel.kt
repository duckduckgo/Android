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

package com.duckduckgo.duckchat.internal.clienttools

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.clienttools.AiChatBrowserToolsFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckAiClientToolsDevViewModel @Inject constructor(
    private val browserToolsFeature: AiChatBrowserToolsFeature,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    enum class FlagGroup { GENERAL, TOOLS }

    enum class BrowserToolsFlag(
        val flagName: String,
        val group: FlagGroup,
        val toggle: (AiChatBrowserToolsFeature) -> Toggle,
    ) {
        BROWSER_TOOLS("aiChatBrowserTools", FlagGroup.GENERAL, { it.self() }),
        SKIP_PERMISSION_PROMPT("devSkipPermissionPrompt", FlagGroup.GENERAL, { it.devSkipPermissionPrompt() }),
        LIST_OPEN_TABS("listOpenTabs", FlagGroup.TOOLS, { it.listOpenTabs() }),
    }

    data class FlagRow(
        val flag: BrowserToolsFlag,
        val enabled: Boolean,
    )

    data class ViewState(val flags: List<FlagRow> = emptyList())

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    init {
        refresh()
    }

    fun onFlagToggled(
        flag: BrowserToolsFlag,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            withContext(dispatchers.io()) { setEnabled(flag.toggle(browserToolsFeature), enabled) }
            refresh()
        }
    }

    @SuppressLint("DenyListedApi")
    private fun setEnabled(
        toggle: Toggle,
        enabled: Boolean,
    ) {
        toggle.setRawStoredState(Toggle.State(remoteEnableState = enabled, enable = enabled))
    }

    private fun refresh() {
        viewModelScope.launch {
            val flags = withContext(dispatchers.io()) {
                BrowserToolsFlag.entries.map { FlagRow(it, it.toggle(browserToolsFeature).isEnabled()) }
            }
            _viewState.value = ViewState(flags)
        }
    }
}
