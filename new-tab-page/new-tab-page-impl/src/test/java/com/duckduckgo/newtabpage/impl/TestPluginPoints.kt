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
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

val enabledShortcutPlugins = object : ActivePluginPoint<NewTabPageShortcutPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageShortcutPlugin> {
        return listOf(
            FakeShortcutPlugin(FakeShortcut("bookmarks")),
            FakeShortcutPlugin(FakeShortcut("chat")),
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
        return listOf(FakeShortcutPlugin(FakeShortcut("bookmarks")), FakeShortcutPlugin(FakeShortcut("chat")))
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

class FakeShortcut(val name: String) : NewTabShortcut {
    override fun name(): String {
        return name
    }

    override fun titleResource(): Int {
        return 10
    }

    override fun iconResource(): Int {
        return 10
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

var allSectionSettings: List<String> = listOf(
    NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name,
    NewTabPageSection.APP_TRACKING_PROTECTION.name,
    NewTabPageSection.FAVOURITES.name,
    NewTabPageSection.SHORTCUTS.name,
)
var allShortcutSettings: List<String> = listOf(
    FakeShortcut("bookmarks").name,
    FakeShortcut("passwords").name,
    FakeShortcut("chat").name,
    FakeShortcut("downloads").name,
    FakeShortcut("settings").name,
)

val activeSectionSettingsPlugins = object : PluginPoint<NewTabPageSectionSettingsPlugin> {
    override fun getPlugins(): Collection<NewTabPageSectionSettingsPlugin> {
        return listOf(
            FakeActiveSectionSettingPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
            FakeActiveSectionSettingPlugin(NewTabPageSection.FAVOURITES.name, true),
            FakeActiveSectionSettingPlugin(NewTabPageSection.SHORTCUTS.name, true),
        )
    }
}

val disabledSectionSettingsPlugins = object : PluginPoint<NewTabPageSectionSettingsPlugin> {
    override fun getPlugins(): Collection<NewTabPageSectionSettingsPlugin> {
        return listOf(
            FakeActiveSectionSettingPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, false),
            FakeActiveSectionSettingPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, false),
            FakeActiveSectionSettingPlugin(NewTabPageSection.FAVOURITES.name, false),
            FakeActiveSectionSettingPlugin(NewTabPageSection.SHORTCUTS.name, false),
        )
    }
}

private class FakeActiveSectionSettingPlugin(
    val section: String,
    val isEnabled: Boolean,
) : NewTabPageSectionSettingsPlugin {
    override val name: String
        get() = section

    override fun getView(context: Context): View? {
        return null
    }

    override suspend fun isActive(): Boolean {
        return isEnabled
    }
}

val enabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
        return listOf(
            FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, true),
        )
    }
}

val favoriteDisabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
        return listOf(
            FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, false),
            FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, true),
        )
    }
}

val enabledIndonesiaSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
        return listOf(
            FakeEnabledSectionPlugin(NewTabPageSection.INDONESIA_MESSAGE.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, true),
            FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, true),
        )
    }
}

val disabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
    override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
        return listOf(
            FakeEnabledSectionPlugin(NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name, false),
            FakeEnabledSectionPlugin(NewTabPageSection.APP_TRACKING_PROTECTION.name, false),
            FakeEnabledSectionPlugin(NewTabPageSection.FAVOURITES.name, false),
            FakeEnabledSectionPlugin(NewTabPageSection.SHORTCUTS.name, false),
        )
    }
}

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

class FakeEnabledSectionPlugin(
    val section: String,
    val isUserEnabled: Boolean,
) : NewTabPageSectionPlugin {
    override val name: String
        get() = section

    override fun getView(context: Context): View? {
        return null
    }

    override suspend fun isUserEnabled(): Boolean {
        return isUserEnabled
    }
}

class FakeShortcutDataStore(enabled: Boolean = false) : NewTabShortcutDataStore {
    private var fakeEnabledSetting: Boolean = enabled

    override val isEnabled: Flow<Boolean>
        get() = flowOf(fakeEnabledSetting)

    override suspend fun setIsEnabled(enabled: Boolean) {
        fakeEnabledSetting = enabled
    }

    override suspend fun isEnabled(): Boolean {
        return fakeEnabledSetting
    }
}
