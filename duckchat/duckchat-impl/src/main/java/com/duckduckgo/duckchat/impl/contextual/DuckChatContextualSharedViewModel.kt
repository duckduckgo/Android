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

package com.duckduckgo.duckchat.impl.contextual

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.logcat

class DuckChatContextualSharedViewModel() : ViewModel() {

    private val _command = MutableSharedFlow<Command>(extraBufferCapacity = 10)
    val commands = _command.asSharedFlow()

    fun onPageContextReceived(tabId: String, pageContext: String, isStorePageContextEnabled: Boolean = false) {
        _command.tryEmit(Command.PageContextAttached(tabId, pageContext, isStorePageContextEnabled))
    }

    /**
     * Emitted by the host once it has shown the sheet, telling the sheet fragment to reload its chat
     * content (reopen the chat, consume any parked entry prompt). The content-side counterpart of
     * [requestShowSheet]: [Command.ShowSheet] asks the host to show the sheet UI; [Command.ReloadChat]
     * then tells the now-visible sheet to load its chat.
     */
    fun onReloadChatRequested() {
        _command.tryEmit(Command.ReloadChat)
    }

    /**
     * Asks the host to show the contextual sheet UI (un-hide its container and embed/show the sheet
     * fragment) for [tabId]. Used by the New Chat → entry-dialog handoff, which runs while the sheet is
     * hidden: the host must show the container before the sheet can reload, otherwise the chat loads into
     * a hidden container. The host then emits [Command.ReloadChat] so the sheet loads its chat content.
     */
    fun requestShowSheet(tabId: String) {
        _command.tryEmit(Command.ShowSheet(tabId))
    }

    fun requestPageContext() {
        _command.tryEmit(Command.CollectPageContext)
    }

    fun onContextualFireConfirmed() {
        _command.tryEmit(Command.OnContextualFireConfirmed)
    }

    fun onMainBrowserPageFinished(url: String?, isStorePageContextEnabled: Boolean = false) {
        logcat { "Duck.ai: onMainBrowserPageFinished $url" }
        _command.tryEmit(Command.MainBrowserPageFinished(isStorePageContextEnabled))
    }

    sealed class Command {
        data class PageContextAttached(
            val tabId: String,
            val pageContext: String,
            val isStorePageContextEnabled: Boolean = false,
        ) : Command()

        // Host → sheet: the sheet has been shown; (re)load its chat content.
        data object ReloadChat : Command()

        // Sheet → host: show the sheet UI (un-hide the container and embed/show the fragment).
        data class ShowSheet(val tabId: String) : Command()

        data object CollectPageContext : Command()

        data class MainBrowserPageFinished(val isStorePageContextEnabled: Boolean = false) : Command()

        data object OnContextualFireConfirmed : Command()
    }
}
