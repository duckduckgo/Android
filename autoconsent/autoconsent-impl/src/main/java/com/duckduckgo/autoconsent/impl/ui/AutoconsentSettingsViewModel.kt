/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelName.AUTOCONSENT_DISABLED
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelName.AUTOCONSENT_ENABLED
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutoconsentSettingsViewModel @Inject constructor(private val autoconsent: Autoconsent, private val pixel: Pixel) : ViewModel() {

    private val viewStateFlow: MutableStateFlow<ViewState> =
        MutableStateFlow(ViewState(autoconsent.isSettingEnabled()))
    val viewState: StateFlow<ViewState> = viewStateFlow

    data class ViewState(
        val autoconsentEnabled: Boolean,
    )

    fun onUserToggleAutoconsent(enabled: Boolean) {
        viewModelScope.launch {
            val pixelName = if (enabled) AUTOCONSENT_ENABLED else AUTOCONSENT_DISABLED
            pixel.fire(pixelName)
            autoconsent.changeSetting(enabled)
            viewStateFlow.emit(ViewState(autoconsent.isSettingEnabled()))
        }
    }
}
