/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import javax.inject.Inject

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
    priority = 2,
)
class AIChatNewTabShortcutPlugin @Inject constructor(
    private val browserNav: BrowserNav,
) : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return NewTabShortcut.Chat
    }

    override fun onClick(context: Context) {
        context.startActivity(browserNav.openInCurrentTab(context, AI_CHAT_URL))
    }

    companion object {
        private const val AI_CHAT_URL = "https://duckduckgo.com/chat"
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
    priority = 5,
)
class WhatsNewShortcutPluginPlugin @Inject constructor(
    private val browserNav: BrowserNav,
) : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return NewTabShortcut.WhatsNew
    }

    override fun onClick(context: Context) {
        context.startActivity(browserNav.openInCurrentTab(context, WHATS_NEW_URL))
    }

    companion object {
        private const val WHATS_NEW_URL = "https://duckduckgo.com/updates"
    }
}
