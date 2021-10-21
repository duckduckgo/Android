/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.accessibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class AccessibilitySettingsViewModel constructor(
    private val accessibilitySettings: AccessibilitySettingsDataStore
) : ViewModel() {

    data class ViewState(
        val overrideSystemFontSize: Boolean = false,
        val fontSize: Float = 100f,
        val forceZoom: Boolean = false
    )

    private val viewState = MutableStateFlow(ViewState())

    fun start() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    overrideSystemFontSize = accessibilitySettings.overrideSystemFontSize,
                    fontSize = accessibilitySettings.fontSize,
                    forceZoom = accessibilitySettings.forceZoom
                )
            )
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onForceZoomChanged(checked: Boolean) {
        Timber.i("Accessibility: onForceZoomChanged $checked")
        accessibilitySettings.forceZoom = checked
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    forceZoom = accessibilitySettings.forceZoom
                )
            )
        }
    }

    fun onOverrideSystemFontSizeChanged(checked: Boolean) {
        Timber.i("Accessibility: onOverrideSystemFontSizeChanged $checked")
        accessibilitySettings.overrideSystemFontSize = checked
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    overrideSystemFontSize = checked
                )
            )
        }
    }

    fun onFontSizeChanged(newValue: Float) {
        Timber.i("Accessibility: onFontSizeChanged $newValue")
        accessibilitySettings.fontSize = newValue
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    fontSize = accessibilitySettings.fontSize
                )
            )
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class AccessibilitySettingsViewModelFactory @Inject constructor(
    private val accessibilitySettings: Provider<AccessibilitySettingsDataStore>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AccessibilitySettingsViewModel::class.java) -> (AccessibilitySettingsViewModel(accessibilitySettings.get()) as T)
                else -> null
            }
        }
    }
}
