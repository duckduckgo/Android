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
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject

class DeviceShieldOnboardingViewModel(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore
) : ViewModel() {

    data class OnboardingPage(val imageHeader: Int, val title: Int, val text: Int)

    val pages = listOf(
        OnboardingPage(
            R.raw.device_shield_tracker_count,
            R.string.atp_OnboardingLastPageOneTitle, R.string.atp_OnboardingLatsPageOneSubtitle
        ),
        OnboardingPage(
            R.raw.device_shield_tracking_apps,
            R.string.atp_OnboardingLastPageTwoTitle, R.string.atp_OnboardingLastPageTwoSubTitle
        ),
        OnboardingPage(
            R.drawable.device_shield_onboarding_page_three_header,
            R.string.atp_OnboardingLastPageThreeTitle, R.string.atp_OnboardingLastPageThreeSubTitle
        )
    )

    fun onStart() {
        deviceShieldOnboardingStore.onboardingDidShow()
    }

    fun onClose() {
        deviceShieldOnboardingStore.onboardingDidNotShow()
    }

    fun onDeviceShieldEnabled() {
        Timber.i("App Tracking Protection, is now enabled")
        deviceShieldPixels.enableFromOnboarding()
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
