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

package com.duckduckgo.app.browser.nativeinput

import androidx.core.net.toUri
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.isNewDuckChatUrl
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * One-shot carried from a Duck.ai launch to the tab it creates: set when that tab should open with
 * its native input focused and the keyboard up.
 *
 * App-scoped rather than held on BrowserActivity because the launch can start outside it — the tab
 * switcher's Duck.ai button adds the tab from its own Activity — and because an Activity recreation
 * between arming and consuming would otherwise drop it.
 */
@SingleInstanceIn(AppScope::class)
class DuckAiNewChatFocusStore @Inject constructor(
    private val duckChat: DuckChat,
) {
    private val armed = AtomicBoolean(false)

    /**
     * Arms the one-shot when [url] opens a new, empty Duck.ai chat, and disarms it otherwise. Safe to
     * call for any new-tab url: the latest launch wins, so a stale arming can't outlive it.
     */
    fun arm(url: String) {
        armed.set(url.toUri().isNewDuckChatUrl(duckChat))
    }

    /** Returns whether a new chat is pending focus, clearing the one-shot. */
    fun consume(): Boolean = armed.getAndSet(false)
}
