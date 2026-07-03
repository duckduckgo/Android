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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.CookiePopUpPreference
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel.SETTINGS_AUTOCONSENT_OFF
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel.SETTINGS_AUTOCONSENT_ON
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel.SETTINGS_AUTOCONSENT_SHOWN
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutoconsentSettingsViewModel @Inject constructor(
    private val autoconsent: Autoconsent,
    private val pixel: Pixel,
    private val autoconsentFeature: AutoconsentFeature,
) : ViewModel() {
    data class ViewState(
        val selectedPreference: CookiePopUpPreference,
        val autoconsentEnabled: Boolean,
    ) {
        val autoManageEnabled: Boolean
            get() = selectedPreference != CookiePopUpPreference.off

        val popUpsWithoutOptOutsEnabled: Boolean
            get() = selectedPreference == CookiePopUpPreference.max
    }

    sealed class Command {
        data class LaunchLearnMoreWebPage(
            val url: String = LEARN_MORE_URL,
            @StringRes val titleId: Int = R.string.autoconsentTitle,
        ) : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private val viewStateFlow: MutableStateFlow<ViewState> = MutableStateFlow(buildViewState())
    val viewState: StateFlow<ViewState> = viewStateFlow

    init {
        pixel.fire(SETTINGS_AUTOCONSENT_SHOWN)
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onAutoManageCookiePopUpsToggled(enabled: Boolean) {
        if (!isCookiePopUpPreferenceSettingEnabled()) return
        val preference = if (enabled) {
            CookiePopUpPreference.`default`
        } else {
            CookiePopUpPreference.off
        }
        updateCookiePopUpPreference(preference)
    }

    fun onPopUpsWithoutOptOutsToggled(enabled: Boolean) {
        if (!isCookiePopUpPreferenceSettingEnabled()) return
        val preference = if (enabled) {
            CookiePopUpPreference.max
        } else {
            CookiePopUpPreference.`default`
        }
        updateCookiePopUpPreference(preference)
    }

    private fun updateCookiePopUpPreference(preference: CookiePopUpPreference) {
        viewModelScope.launch {
            pixel.fire(
                if (preference == CookiePopUpPreference.off) {
                    SETTINGS_AUTOCONSENT_OFF
                } else {
                    SETTINGS_AUTOCONSENT_ON
                },
            )
            autoconsent.changeCookiePopUpPreference(preference)
            viewStateFlow.emit(buildViewState())
        }
    }

    fun onUserToggleAutoconsent(enabled: Boolean) {
        if (isCookiePopUpPreferenceSettingEnabled()) return
        viewModelScope.launch {
            pixel.fire(
                if (enabled) {
                    SETTINGS_AUTOCONSENT_ON
                } else {
                    SETTINGS_AUTOCONSENT_OFF
                },
            )
            autoconsent.changeSetting(enabled)
            viewStateFlow.emit(buildViewState())
        }
    }

    fun onLearnMoreSelected() {
        viewModelScope.launch { command.send(Command.LaunchLearnMoreWebPage()) }
    }

    companion object {
        const val LEARN_MORE_URL = "https://help.duckduckgo.com/duckduckgo-help-pages/privacy/web-tracking-protections/#cookie-pop-up-management"
    }

    private fun buildViewState(): ViewState {
        if (!isCookiePopUpPreferenceSettingEnabled()) {
            val enabled = autoconsent.isSettingEnabled()
            return ViewState(
                selectedPreference = if (enabled) CookiePopUpPreference.`default` else CookiePopUpPreference.off,
                autoconsentEnabled = enabled,
            )
        }
        return ViewState(
            selectedPreference = autoconsent.getCookiePopUpPreference(),
            autoconsentEnabled = autoconsent.isSettingEnabled(),
        )
    }

    private fun isCookiePopUpPreferenceSettingEnabled(): Boolean {
        return autoconsentFeature.cookiePopUpPreferenceSetting().isEnabled()
    }
}
