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

import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.impl.settings.ManageShortcutItem
import com.duckduckgo.newtabpage.impl.settings.NewTabSettingsStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.logcat

interface NewTabShortcutsProvider {
    fun provideActiveShortcuts(): Flow<List<NewTabPageShortcutPlugin>>
    fun provideAllShortcuts(): Flow<List<ManageShortcutItem>>
}

@ContributesBinding(
    scope = AppScope::class,
)
class RealNewTabPageShortcutProvider @Inject constructor(
    private val shortcutPlugins: ActivePluginPoint<NewTabPageShortcutPlugin>,
    private val newTabSettingsStore: NewTabSettingsStore,
) : NewTabShortcutsProvider {
    override fun provideActiveShortcuts(): Flow<List<NewTabPageShortcutPlugin>> = flow {
        // store can be empty the first time we check it, so we make sure the content is initialised
        val plugins = shortcutPlugins.getPlugins()
        if (plugins.isNotEmpty()) {
            if (newTabSettingsStore.shortcutSettings.isEmpty()) {
                val userShortcuts = plugins.map { it.getShortcut().name }
                logcat { "New Tab: User Shortcuts initialised to $userShortcuts" }
                newTabSettingsStore.shortcutSettings = userShortcuts
            }
        }

        val shortcuts = mutableListOf<NewTabPageShortcutPlugin>()

        newTabSettingsStore.shortcutSettings.forEach { userSetting ->
            val shortcutPlugin = plugins.find { it.getShortcut().name == userSetting }
            if (shortcutPlugin != null) {
                shortcuts.add(shortcutPlugin)
            }
        }

        emit(shortcuts)
    }

    override fun provideAllShortcuts(): Flow<List<ManageShortcutItem>> = flow {
        val allShortcuts = mutableListOf<ManageShortcutItem>()
        val shortcutPlugins = shortcutPlugins.getPlugins()
        val userShortcuts = newTabSettingsStore.shortcutSettings

        userShortcuts.forEach { userSetting ->
            val shortcutPlugin = shortcutPlugins.find { it.getShortcut().name == userSetting }
            if (shortcutPlugin != null) {
                allShortcuts.add(ManageShortcutItem(shortcut = shortcutPlugin.getShortcut(), selected = true))
            }
        }

        shortcutPlugins.forEach { plugin ->
            if (allShortcuts.find { it.shortcut == plugin.getShortcut() } == null) {
                allShortcuts.add(ManageShortcutItem(shortcut = plugin.getShortcut(), selected = false))
            }
        }

        logcat { "New Tab Settings: All shortcuts $allShortcuts" }

        emit(allShortcuts)
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
)
private interface NewTabPageShortcutPluginPointTrigger
