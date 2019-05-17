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

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel

class OnboardingViewModel(
    private val onboardingStore: OnboardingStore,
    private val privacySettingsStore: PrivacySettingsStore,
    private val pageLayoutManager: OnboardingPageManager,
    private val variantManager: VariantManager,
    private val pixel: Pixel
) : ViewModel() {

    fun initializePages() {
        pageLayoutManager.buildPageBlueprints()
    }

    fun pageCount(): Int {
        return pageLayoutManager.pageCount()
    }

    fun getItem(position: Int): OnboardingPageFragment? {
        return pageLayoutManager.buildPage(position)
    }

    fun onOnboardingDone() {
        onboardingStore.onboardingShown()
        fireTrackerBlockingFinalStatePixel()

    }

    private fun fireTrackerBlockingFinalStatePixel() {
        if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.TrackerBlockingOnboardingOptIn)) {
            val pixelName = if (privacySettingsStore.privacyOn) {
                Pixel.PixelName.ONBOARDING_TRACKER_BLOCKING_FINAL_ONBOARDING_STATE_ENABLED
            } else {
                Pixel.PixelName.ONBOARDING_TRACKER_BLOCKING_FINAL_ONBOARDING_STATE_DISABLED
            }
            pixel.fire(pixelName)
        }
    }
}
