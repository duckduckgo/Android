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

package com.duckduckgo.newtabpage.impl.settings

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutsProvider
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutsSectionSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ActivityScope::class)
class NewTabSettingsViewModel @Inject constructor(
    private val sectionSettingsProvider: NewTabPageSectionSettingsProvider,
    private val shortcutsProvider: NewTabShortcutsProvider,
    private val shortcutSetting: NewTabShortcutsSectionSetting,
    private val newTabSettingsStore: NewTabSettingsStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> =
        _viewState.onStart {
            renderViews()
        }.flowOn(dispatcherProvider.io())

    data class ViewState(
        val sections: List<NewTabPageSectionSettingsPlugin> = emptyList(),
        val shortcuts: List<ManageShortcutItem> = emptyList(),
        val shortcutsManagementEnabled: Boolean = false,
    )

    private fun renderViews() {
        shortcutsProvider.provideAllShortcuts()
            .combine(sectionSettingsProvider.provideSections()) { shortcuts, sections ->
                SettingsSections(sections = sections, shortcuts = shortcuts)
            }
            .combine(shortcutSetting.isEnabledFlow()) { settings, enabled ->
                ViewState(sections = settings.sections, shortcuts = settings.shortcuts, shortcutsManagementEnabled = enabled)
            }
            .flowOn(dispatcherProvider.io())
            .onEach { viewState ->
                withContext(dispatcherProvider.main()) {
                    _viewState.update {
                        viewState
                    }
                }
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onSectionsSwapped(
        firstTag: String,
        newSecondPosition: Int,
        secondTag: String,
        newFirstPosition: Int,
    ) {
        logcat { "New Tab Settings: $firstTag to $newFirstPosition $secondTag to $newSecondPosition" }
        viewModelScope.launch(dispatcherProvider.io()) {
            val settings = newTabSettingsStore.sectionSettings.toMutableList()
            logcat { "New Tab: Sections $settings" }
            settings.swap(newFirstPosition, newSecondPosition)
            newTabSettingsStore.sectionSettings = settings
            logcat { "New Tab: Sections updated to $settings" }
        }
        renderViews()
    }

    fun onShortcutSelected(shortcutItem: ManageShortcutItem) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val shortcuts = newTabSettingsStore.shortcutSettings.toMutableList()
            if (shortcutItem.selected) {
                logcat { "New Tab Settings: removing shortcut $shortcutItem" }
                shortcuts.remove(shortcutItem.plugin.getShortcut().name)
            } else {
                logcat { "New Tab Settings: adding shortcut $shortcutItem" }
                shortcuts.add(shortcutItem.plugin.getShortcut().name)
            }
            shortcutItem.plugin.toggle()

            newTabSettingsStore.shortcutSettings = shortcuts
            logcat { "New Tab: Shortcuts updated to $shortcuts" }
        }
        renderViews()
    }
}

private data class SettingsSections(
    val sections: List<NewTabPageSectionSettingsPlugin> = emptyList(),
    val shortcuts: List<ManageShortcutItem> = emptyList(),
)

fun <T> MutableList<T>.swap(
    idx1: Int,
    idx2: Int,
): MutableList<T> = apply {
    val t = this[idx1]
    this[idx1] = this[idx2]
    this[idx2] = t
}
