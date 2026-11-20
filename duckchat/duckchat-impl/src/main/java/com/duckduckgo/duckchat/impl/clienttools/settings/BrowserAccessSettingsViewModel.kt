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

package com.duckduckgo.duckchat.impl.clienttools.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.clienttools.ClientTool
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermission
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermissionMode
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermissionStore
import com.duckduckgo.duckchat.impl.clienttools.ClientToolSettingsSection
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class BrowserAccessSettingsViewModel @Inject constructor(
    private val tools: PluginPoint<ClientTool>,
    private val permissionStore: ClientToolPermissionStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val seeTools: List<ToolRow> = emptyList(),
        val doTools: List<ToolRow> = emptyList(),
        val isResetAllEnabled: Boolean = false,
    )

    data class ToolRow(
        val toolName: String,
        @StringRes val name: Int,
        @StringRes val description: Int,
        @DrawableRes val icon: Int,
        val choice: BrowserAccessChoice,
        val options: List<BrowserAccessChoice>,
    )

    sealed interface Command {
        data class ShowChoices(val row: ToolRow) : Command

        data object ConfirmResetAll : Command
    }

    private val enabledTools = flow {
        emit(tools.getPlugins().filter { it.isEnabled() }.sortedBy { displayPosition(it.name) })
    }.flowOn(dispatcherProvider.io())

    val viewState: StateFlow<ViewState> = combine(enabledTools, permissionStore.observe()) { tools, choices ->
        val rows = tools.map { tool -> tool to toRow(tool, choices[tool.name]) }
        ViewState(
            seeTools = rows.filter { (tool, _) -> tool.settingsListing.section == ClientToolSettingsSection.SEE }.map { it.second },
            doTools = rows.filter { (tool, _) -> tool.settingsListing.section == ClientToolSettingsSection.DO }.map { it.second },
            isResetAllEnabled = rows.any { (tool, row) -> row.choice != defaultChoice(tool.permissionMode) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands: Flow<Command> = commandChannel.receiveAsFlow()

    fun onToolClicked(toolName: String) {
        val row = (viewState.value.seeTools + viewState.value.doTools).firstOrNull { it.toolName == toolName } ?: return
        viewModelScope.launch { commandChannel.send(Command.ShowChoices(row)) }
    }

    fun onChoiceSelected(
        toolName: String,
        choice: BrowserAccessChoice,
    ) {
        val tool = tools.getPlugins().firstOrNull { it.name == toolName } ?: return
        viewModelScope.launch {
            if (choice == defaultChoice(tool.permissionMode)) {
                permissionStore.clear(toolName)
            } else {
                permissionStore.set(toolName, choice.toStored())
            }
        }
    }

    fun onResetAllClicked() {
        viewModelScope.launch { commandChannel.send(Command.ConfirmResetAll) }
    }

    fun onResetAllConfirmed() {
        viewModelScope.launch { permissionStore.clearAll() }
    }

    private fun toRow(
        tool: ClientTool,
        stored: ClientToolPermission?,
    ) = ToolRow(
        toolName = tool.name,
        name = tool.settingsListing.name,
        description = tool.settingsListing.description,
        icon = tool.settingsListing.icon,
        choice = currentChoice(tool.permissionMode, stored),
        options = options(tool.permissionMode),
    )

    private fun displayPosition(toolName: String): Int = TOOL_ORDER.indexOf(toolName).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private companion object {
        val TOOL_ORDER = listOf(
            "listOpenTabs",
            "readTabContent",
            "findInPage",
            "searchHistory",
            "searchBookmarks",
            "searchDownloads",
            "openTab",
            "switchToTab",
            "bookmarkTab",
            "closeTabs",
        )
    }
}

enum class BrowserAccessChoice(@StringRes val label: Int) {
    ALWAYS_ALLOW(R.string.duckAiBrowserAccessAlwaysAllow),
    ALWAYS_ASK(R.string.duckAiBrowserAccessAlwaysAsk),
    NEVER_ALLOW(R.string.duckAiBrowserAccessNeverAllow),
}

/** The choices the permission gate honours for [mode]; the gate ignores Always Allow on an ask-every-time tool. */
internal fun options(mode: ClientToolPermissionMode): List<BrowserAccessChoice> = when (mode) {
    ClientToolPermissionMode.Auto -> listOf(BrowserAccessChoice.ALWAYS_ALLOW, BrowserAccessChoice.NEVER_ALLOW)
    is ClientToolPermissionMode.Ask -> BrowserAccessChoice.entries
    is ClientToolPermissionMode.AskEveryTime -> listOf(BrowserAccessChoice.ALWAYS_ASK, BrowserAccessChoice.NEVER_ALLOW)
}

/** The choice that stores nothing, so picking it is the per-tool reset. */
internal fun defaultChoice(mode: ClientToolPermissionMode): BrowserAccessChoice = when (mode) {
    ClientToolPermissionMode.Auto -> BrowserAccessChoice.ALWAYS_ALLOW
    is ClientToolPermissionMode.Ask, is ClientToolPermissionMode.AskEveryTime -> BrowserAccessChoice.ALWAYS_ASK
}

internal fun currentChoice(
    mode: ClientToolPermissionMode,
    stored: ClientToolPermission?,
): BrowserAccessChoice = when {
    stored == ClientToolPermission.NEVER_ALLOW -> BrowserAccessChoice.NEVER_ALLOW
    stored == ClientToolPermission.ALWAYS_ALLOW && BrowserAccessChoice.ALWAYS_ALLOW in options(mode) -> BrowserAccessChoice.ALWAYS_ALLOW
    else -> defaultChoice(mode)
}

private fun BrowserAccessChoice.toStored(): ClientToolPermission = when (this) {
    BrowserAccessChoice.ALWAYS_ALLOW -> ClientToolPermission.ALWAYS_ALLOW
    BrowserAccessChoice.NEVER_ALLOW -> ClientToolPermission.NEVER_ALLOW
    BrowserAccessChoice.ALWAYS_ASK -> error("Always Ask is the default for every mode that offers it, so it clears instead of storing")
}
