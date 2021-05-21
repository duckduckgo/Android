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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject

class DeviceShieldOnboardingViewModel(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore
) : ViewModel() {

    fun onStart() {
        deviceShieldOnboardingStore.onboardingDidShow()
    }

    fun onClose() {
        deviceShieldOnboardingStore.onboardingDidNotShow()
    }

    fun onDeviceShieldSettingChanged(enabled: Boolean) {
        Timber.i("Device Shield, is now enabled: $enabled")

        // TODO: This is only needed if we need pixels for this, it wasn't discussed but my guess is we need them
        // if needed, then we have to create a deviceShieldPixels.enableFromOnboarding()
        if (enabled) {
            deviceShieldPixels.enableFromSettings()
        } else {
            deviceShieldPixels.disableFromSettings()
        }

    }
}

@ContributesMultibinding(AppObjectGraph::class)
class DeviceShieldOnboardingViewModelFactory @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(DeviceShieldOnboardingViewModel::class.java) -> (DeviceShieldOnboardingViewModel(deviceShieldPixels, deviceShieldOnboardingStore) as T)
                else -> null
            }
        }
    }
}
