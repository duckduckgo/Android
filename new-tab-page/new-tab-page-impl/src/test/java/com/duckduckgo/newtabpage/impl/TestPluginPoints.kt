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

package com.duckduckgo.newtabpage.impl

import android.content.Context
import android.view.View
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.api.NewTabShortcut.Bookmarks
import com.duckduckgo.newtabpage.api.NewTabShortcut.Chat
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore

val enabledShortcutPlugins = object : ActivePluginPoint<NewTabPageShortcutPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageShortcutPlugin> {
        return listOf(
            FakeShortcutPlugin(Bookmarks),
            FakeShortcutPlugin(Chat),
        )
    }
}

val disabledShortcutPlugins = object : ActivePluginPoint<NewTabPageShortcutPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageShortcutPlugin> {
        return emptyList()
    }
}

class FakeShortcutPluginPoint : PluginPoint<NewTabPageShortcutPlugin> {
    override fun getPlugins(): List<NewTabPageShortcutPlugin> {
        return listOf(FakeShortcutPlugin(Chat), FakeShortcutPlugin(Bookmarks))
    }
}

class FakeShortcutPlugin(val fakeShortcut: NewTabShortcut) : NewTabPageShortcutPlugin {

    private var enabled: Boolean = true

    override fun getShortcut(): NewTabShortcut {
        return fakeShortcut
    }

    override fun onClick(
        context: Context,
    ) {
        // no - op
    }

    override suspend fun isUserEnabled(): Boolean {
        return enabled
    }

    override suspend fun setUserEnabled(state: Boolean) {
        enabled = state
    }
}

val enabledSectionsPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
        return listOf(
            FakeSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK),
            FakeSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION),
            FakeSectionPlugin(NewTabPageSection.FAVOURITES),
            FakeSectionPlugin(NewTabPageSection.SHORTCUTS),
        )
    }
}

class FakeSectionPlugin(val section: NewTabPageSection) : NewTabPageSectionPlugin {
    private var enabled: Boolean = true

    override val name: String
        get() = section.name

    override fun getView(context: Context): View? {
        return null
    }

    override suspend fun isUserEnabled(): Boolean {
        return enabled
    }
}

val enabledSectionSettingsPlugins = listOf(
    FakeSectionSettingPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK, true),
    FakeSectionSettingPlugin(NewTabPageSection.APP_TRACKING_PROTECTION, true),
    FakeSectionSettingPlugin(NewTabPageSection.FAVOURITES, true),
    FakeSectionSettingPlugin(NewTabPageSection.SHORTCUTS, true),
)

class FakeSectionSettingPlugin(
    val section: NewTabPageSection,
    val active: Boolean,
) : NewTabPageSectionSettingsPlugin {
    override val name: String
        get() = section.name

    override fun getView(context: Context): View? {
        return null
    }

    override suspend fun isActive(): Boolean {
        return active
    }
}

private var allSectionSettings: List<String> = listOf(
    NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name,
    NewTabPageSection.APP_TRACKING_PROTECTION.name,
    NewTabPageSection.FAVOURITES.name,
    NewTabPageSection.SHORTCUTS.name,
)
private var allShortcutSettings: List<String> = listOf(
    NewTabShortcut.Bookmarks.name,
    NewTabShortcut.Chat.name,
    NewTabShortcut.Passwords.name,
    NewTabShortcut.Downloads.name,
    NewTabShortcut.Settings.name,
)

class FakeSettingStore(
    sections: List<String> = allSectionSettings,
    shortcuts: List<String> = allShortcutSettings,
) : NewTabSettingsStore {
    private var fakeSectionSettings: List<String> = sections
    private var fakeShortcutSettings: List<String> = shortcuts

    override var sectionSettings: List<String>
        get() = fakeSectionSettings
        set(value) {
            fakeSectionSettings = value
        }
    override var shortcutSettings: List<String>
        get() = fakeShortcutSettings
        set(value) {
            fakeShortcutSettings = value
        }
}
