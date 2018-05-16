/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import android.arch.lifecycle.ViewModel
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DefaultBrowserFeature

class OnboardingViewModel(
    private val onboardingStore: OnboardingStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager
) : ViewModel() {

    fun pageCount(): Int {
        return if (shouldShowDefaultBrowserPage()) 3 else 2
    }

    fun onOnboardingDone() {
        onboardingStore.onboardingShown()
    }

    fun getItem(position: Int): OnboardingPageFragment? {
        return when (position) {
            0 -> OnboardingPageFragment.ProtectDataPage()
            1 -> OnboardingPageFragment.NoTracePage()
            2 -> {
                return if (shouldShowDefaultBrowserPage())
                    OnboardingPageFragment.DefaultBrowserPage()
                else null
            }
            else -> null
        }
    }

    private fun shouldShowDefaultBrowserPage(): Boolean {
        val deviceSupported =
            defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration()
        val featureEnabled = variantManager.getVariant().hasFeature(DefaultBrowserFeature.ShowInOnboarding)

        return deviceSupported && featureEnabled
    }
}
