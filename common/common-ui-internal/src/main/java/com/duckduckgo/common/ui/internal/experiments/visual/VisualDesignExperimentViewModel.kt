/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.experiments.visual

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.di.scopes.ViewScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class VisualDesignExperimentViewModel @Inject constructor(
    private val visualDesignExperimentDataStore: VisualDesignExperimentDataStore,
    private val themingDataStore: ThemingDataStore,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val isBrowserThemingFeatureAvailable: Boolean = true,
        val isBrowserThemingFeatureChangeable: Boolean = false,
        val isBrowserThemingFeatureEnabled: Boolean = false,
        val experimentConflictAlertVisible: Boolean = false,
        val selectedTheme: String = "",
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.map {
        it.copy(
            selectedTheme = themingDataStore.theme.toString(),
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, initialValue = ViewState())

    init {
        viewModelScope.launch {
            visualDesignExperimentDataStore.isExperimentEnabled.collect { isExperimentEnabled ->
                _viewState.update {
                    it.copy(isBrowserThemingFeatureEnabled = isExperimentEnabled)
                }
            }
        }
        viewModelScope.launch {
            visualDesignExperimentDataStore.anyConflictingExperimentEnabled.collect { conflicts ->
                _viewState.update {
                    it.copy(
                        isBrowserThemingFeatureChangeable = !conflicts,
                        experimentConflictAlertVisible = conflicts,
                    )
                }
            }
        }
    }

    fun onExperimentalUIModeChanged(checked: Boolean) {
        visualDesignExperimentDataStore.changeRawExperimentFlag(checked)
    }
}
