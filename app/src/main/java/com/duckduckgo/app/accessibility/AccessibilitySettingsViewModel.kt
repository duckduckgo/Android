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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchAvailabilityConfigProvider
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_OFF
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_ON
import com.duckduckgo.voice.store.VoiceSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AccessibilitySettingsViewModel @Inject constructor(
    private val accessibilitySettings: AccessibilitySettingsDataStore,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val configProvider: VoiceSearchAvailabilityConfigProvider,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val overrideSystemFontSize: Boolean = false,
        val appFontSize: Float = 100f,
        val forceZoom: Boolean = false,
        val showVoiceSearch: Boolean = false,
        val voiceSearchEnabled: Boolean = false,
    )

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): StateFlow<ViewState> = viewState

    fun start() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    overrideSystemFontSize = accessibilitySettings.overrideSystemFontSize,
                    appFontSize = accessibilitySettings.appFontSize,
                    forceZoom = accessibilitySettings.forceZoom,
                    showVoiceSearch = voiceSearchAvailability.isVoiceSearchSupported,
                    voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable,
                ),
            )
        }
    }

    fun onForceZoomChanged(checked: Boolean) {
        Timber.v("AccessibilityActSettings: onForceZoomChanged $checked")
        accessibilitySettings.forceZoom = checked
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    forceZoom = accessibilitySettings.forceZoom,
                ),
            )
        }
    }

    fun onSystemFontSizeChanged(checked: Boolean) {
        Timber.v("AccessibilityActSettings: onOverrideSystemFontSizeChanged $checked")
        accessibilitySettings.overrideSystemFontSize = checked
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    overrideSystemFontSize = accessibilitySettings.overrideSystemFontSize,
                ),
            )
        }
    }

    fun onFontSizeChanged(newValue: Float) {
        Timber.v("AccessibilityActSettings: onFontSizeChanged $newValue")
        accessibilitySettings.appFontSize = newValue
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    appFontSize = accessibilitySettings.appFontSize,
                ),
            )
        }
    }

    fun onVoiceSearchChanged(checked: Boolean) {
        voiceSearchRepository.setVoiceSearchUserEnabled(checked)
        if (checked) {
            voiceSearchRepository.resetVoiceSearchDismissed()
            pixel.fire(
                VOICE_SEARCH_ON,
                mapOf(
                    Pixel.PixelParameter.LOCALE to configProvider.get().languageTag,
                    Pixel.PixelParameter.MANUFACTURER to configProvider.get().deviceManufacturer,
                    Pixel.PixelParameter.MODEL to configProvider.get().deviceModel,
                ),
            )
        } else {
            pixel.fire(
                VOICE_SEARCH_OFF,
                mapOf(
                    Pixel.PixelParameter.LOCALE to configProvider.get().languageTag,
                    Pixel.PixelParameter.MANUFACTURER to configProvider.get().deviceManufacturer,
                    Pixel.PixelParameter.MODEL to configProvider.get().deviceModel,
                ),
            )
        }
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable,
                ),
            )
        }
    }

    private fun currentViewState() = viewState.value
}
