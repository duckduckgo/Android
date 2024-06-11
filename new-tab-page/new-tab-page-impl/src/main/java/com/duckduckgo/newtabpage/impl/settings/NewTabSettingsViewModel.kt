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
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.impl.shortcuts.NewTabShortcutsProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    private val newTabSettingsStore: NewTabSettingsStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val activeSectionsPlugins: MutableList<NewTabPageSectionSettingsPlugin> = mutableListOf()

    private val _viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> =
        _viewState.onStart {
            configureViews()
        }.flowOn(dispatcherProvider.io())

    data class ViewState(
        val sections: List<NewTabPageSectionSettingsPlugin> = emptyList(),
        val shortcuts: List<ManageShortcutItem> = emptyList(),
    )

    private fun configureViews() {
        shortcutsProvider.provideShortcuts()
            .combine(sectionSettingsProvider.provideSections(), ::Pair)
            .flowOn(dispatcherProvider.io())
            .onEach { (shortcuts, sections) ->
                if (sections != activeSectionsPlugins) {
                    activeSectionsPlugins.clear()
                    activeSectionsPlugins.addAll(sections)
                    withContext(dispatcherProvider.main()) {
                        _viewState.update {
                            ViewState(
                                sections = sections,
                                shortcuts = shortcuts.map { ManageShortcutItem(it.getShortcut(), false) },
                            )
                        }
                    }
                }
            }
            .flowOn(dispatcherProvider.main())
            .launchIn(viewModelScope)

        // viewModelScope.launch(dispatcherProvider.io()) {
        //     shortcutsProvider.provideShortcuts().combine(shortcuts ->
        //
        //     )
        //     sectionSettingsProvider.provideSections().collect { sections ->
        //         if (sections != activeSectionsPlugins) {
        //             activeSectionsPlugins.clear()
        //             activeSectionsPlugins.addAll(sections)
        //             withContext(dispatcherProvider.main()) {
        //                 _viewState.update { ViewState(sections = sections) }
        //             }
        //         }
        //     }
        // }
    }

    fun onSectionsSwapped(
        firstTag: String,
        newSecondPosition: Int,
        secondTag: String,
        newFirstPosition: Int,
    ) {
        logcat { "New Tab: $firstTag to $newFirstPosition $secondTag to $newSecondPosition" }
        viewModelScope.launch(dispatcherProvider.io()) {
            val settings = newTabSettingsStore.sectionSettings.toMutableList()
            logcat { "New Tab: Sections $settings" }
            settings.swap(newFirstPosition, newSecondPosition)
            newTabSettingsStore.sectionSettings = settings
            logcat { "New Tab: Sections updated to $settings" }
        }
    }

    fun onShortcutSelected(shortcut: NewTabShortcut) {
    }
}

fun <T> MutableList<T>.swap(
    idx1: Int,
    idx2: Int,
): MutableList<T> = apply {
    val t = this[idx1]
    this[idx1] = this[idx2]
    this[idx2] = t
}
