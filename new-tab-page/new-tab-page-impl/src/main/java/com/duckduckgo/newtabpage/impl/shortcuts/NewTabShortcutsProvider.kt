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

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

interface NewTabShortcutsProvider {
    fun provideShortcuts(): List<NewTabPageShortcutPlugin>
}

@ContributesBinding(
    scope = AppScope::class,
)
class RealNewTabPageShortcutProvider @Inject constructor(
    private val newTabPageSections: PluginPoint<NewTabPageShortcutPlugin>,
) : NewTabShortcutsProvider {
    override fun provideShortcuts(): List<NewTabPageShortcutPlugin> {
        return newTabPageSections.getPlugins().map { it }
    }
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
)
private interface NewTabPageShortcutPluginPointTrigger

@ContributesMultibinding(AppScope::class)
class BookmarksNewTabShortcutPlugin @Inject constructor() : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return NewTabShortcut(R.string.newTabPageShortcutBookmarks, R.drawable.ic_bookmarks_open_color_16)
    }
}

@ContributesMultibinding(AppScope::class)
class AIChatNewTabShortcutPlugin @Inject constructor() : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return NewTabShortcut(R.string.newTabPageShortcutBookmarks, R.drawable.ic_placeholder_color_16)
    }
}
