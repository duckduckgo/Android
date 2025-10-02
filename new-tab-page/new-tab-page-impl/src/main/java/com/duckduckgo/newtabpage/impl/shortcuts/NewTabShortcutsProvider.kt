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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.logcat
import javax.inject.Inject

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
        val allPlugins = shortcutPlugins.getPlugins()
        val enabledPlugins = allPlugins.filter { it.isUserEnabled() }
        if (allPlugins.isNotEmpty()) {
            if (newTabSettingsStore.shortcutSettings.isEmpty()) {
                // find the plugins enabled by default and add them in the desired order
                // https://app.asana.com/0/1174433894299346/1207522943839271/f
                val userShortcuts = enabledPlugins.map { it.getShortcut().name() }
                logcat { "New Tab Shortcuts: initialised to $userShortcuts" }
                newTabSettingsStore.shortcutSettings = userShortcuts
            }
        }

        val shortcuts = mutableListOf<NewTabPageShortcutPlugin>()

        newTabSettingsStore.shortcutSettings.forEach { userSetting ->
            val shortcutPlugin = enabledPlugins.find { it.getShortcut().name() == userSetting }
            if (shortcutPlugin != null) {
                shortcuts.add(shortcutPlugin)
            }
        }

        logcat { "New Tab Shortcuts: ${shortcuts.map { it.getShortcut().name() }}" }

        emit(shortcuts)
    }

    override fun provideAllShortcuts(): Flow<List<ManageShortcutItem>> = flow {
        val allShortcuts = mutableListOf<ManageShortcutItem>()
        val enabledPlugins = shortcutPlugins.getPlugins().filter { it.isUserEnabled() }
        logcat { "New Tab Shortcuts: enabled ${enabledPlugins.map { it.getShortcut().name() }}" }
        val disabledPlugins = shortcutPlugins.getPlugins().filterNot { it.isUserEnabled() }
        logcat { "New Tab Shortcuts: disabled ${disabledPlugins.map { it.getShortcut().name() }}" }

        val userShortcuts = newTabSettingsStore.shortcutSettings

        userShortcuts.forEach { userSetting ->
            val shortcutPlugin = enabledPlugins.find { it.getShortcut().name() == userSetting }
            if (shortcutPlugin != null) {
                allShortcuts.add(ManageShortcutItem(plugin = shortcutPlugin, selected = true))
            }
        }

        disabledPlugins.forEach { disabledPlugin ->
            allShortcuts.add(ManageShortcutItem(plugin = disabledPlugin, selected = false))
        }

        logcat { "New Tab Shortcuts: all ${allShortcuts.map { it.plugin.getShortcut().name() + " enabled:" + it.selected }}" }

        emit(allShortcuts)
    }
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
)
private interface NewTabPageShortcutPluginPointTrigger
