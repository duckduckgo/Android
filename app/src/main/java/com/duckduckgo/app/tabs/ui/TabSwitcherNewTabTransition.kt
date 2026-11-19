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

package com.duckduckgo.app.tabs.ui

import android.graphics.Bitmap
import androidx.annotation.MainThread
import com.duckduckgo.app.browser.tabs.transition.NewTabMotion
import com.duckduckgo.app.browser.tabs.transition.PendingNewTabTransition
import com.duckduckgo.app.browser.tabs.transition.PendingNewTabTransitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Connects the tab manager to the tab transition engine.
 *
 * - Each tap starts a request. Its id goes along with the tab operation and comes back in the close command.
 * - Only the latest tap can hand over, because the tab operations can finish in any order.
 * - Closing any other way cancels it.
 */
@MainThread
class TabSwitcherNewTabTransition(
    private val scope: CoroutineScope,
    private val pendingTransitions: PendingNewTabTransitions,
    private val capturePicture: () -> Deferred<Bitmap?>,
) {

    var enabled = false

    private var latestRequest: Long? = null
    private var picture: Deferred<Bitmap?>? = null
    private var waiting: Job? = null
    private var registeredTabId: String? = null
    private var closed = false

    fun onNewTabTap(): Long {
        waiting?.cancel()
        waiting = null
        picture = if (enabled && !closed) capturePicture() else null
        return (++lastRequestId).also { latestRequest = it }
    }

    /** With the chats menu on, the button only opens the menu, so the request starts when a chat is picked. */
    fun onDuckAiButtonTap(chatsMenuEnabled: Boolean): Long? = if (chatsMenuEnabled) null else onNewTabTap()

    /** Calls [finish] with `animated = true` once the picture is handed over, or `false` right away when there's nothing to animate. */
    fun closeToNewTab(
        requestId: Long,
        tabId: String,
        motion: NewTabMotion?,
        finish: (animated: Boolean) -> Unit,
    ) {
        if (closed) return
        if (requestId != latestRequest) {
            // An older tap's command. With no tap of its own, this tab manager was recreated after that tap: just close.
            if (latestRequest == null) close(finish, animated = false)
            return
        }
        val picture = picture
        if (motion == null || picture == null) {
            close(finish, animated = false)
            return
        }
        waiting = scope.launch {
            // The browser shows the picture as soon as the tab manager closes, so it has to be ready first.
            if (picture.await() == null) {
                close(finish, animated = false)
                return@launch
            }
            pendingTransitions.register(tabId, PendingNewTabTransition(picture, motion, fromTabManager = true))
            registeredTabId = tabId
            close(finish, animated = true)
        }
    }

    /** The tab manager is closing another way (a tab picked, an undo, a mode switch), even if a new tab was already handed over. */
    fun onClose() {
        waiting?.cancel()
        waiting = null
        registeredTabId?.let { pendingTransitions.consume(it) }
        registeredTabId = null
        closed = true
    }

    private fun close(
        finish: (animated: Boolean) -> Unit,
        animated: Boolean,
    ) {
        closed = true
        finish(animated)
    }

    private companion object {
        // Shared by all tab managers, so a recreated one never matches an earlier one's request.
        var lastRequestId = 0L
    }
}
