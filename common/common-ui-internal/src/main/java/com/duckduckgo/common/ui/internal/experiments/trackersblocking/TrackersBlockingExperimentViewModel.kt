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

package com.duckduckgo.common.ui.internal.experiments.trackersblocking

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class TrackersBlockingExperimentViewModel@Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val visualDesignExperimentDataStore: VisualDesignExperimentDataStore,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val variant1: Boolean = false,
        val variant2: Boolean = false,
        val variant3: Boolean = false,
    )

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { updateCurrentState() }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant1ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            updateCurrentState()
        }
    }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant2ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            updateCurrentState()
        }
    }

    @SuppressLint("DenyListedApi")
    fun onTrackersBlockingVariant3ExperimentalUIModeChanged(checked: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            updateCurrentState()
        }
    }

    private fun updateCurrentState() {
        viewModelScope.launch {
            viewState.update { currentViewState() }
        }
    }
}
