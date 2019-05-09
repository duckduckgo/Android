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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.onboarding.store.OnboardingStore

class OnboardingViewModel(
    private val onboardingStore: OnboardingStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector
) : ViewModel() {

    fun pageCount(isFreshAppInstall: Boolean): Int {

        // always show first welcome screen
        var count = 1

        if (shouldShowDefaultBrowserPage(isFreshAppInstall)) {
            count++
        }

        return count
    }

    fun onOnboardingDone() {
        onboardingStore.onboardingShown()
    }

    fun getItem(position: Int, isFreshAppInstall: Boolean): OnboardingPageFragment? {
        val continueButtonTextResourceId = getContinueButtonTextResourceId(position, isFreshAppInstall)
        return when (position) {
            0 -> {
                val titleTextResourceId =
                    if (isFreshAppInstall) R.string.unifiedOnboardingTitleFirstVisit else R.string.unifiedOnboardingTitleSubsequentVisits
                OnboardingPageFragment.UnifiedWelcomePage.instance(continueButtonTextResourceId, titleTextResourceId)
            }
            1 -> {
                return if (shouldShowDefaultBrowserPage(isFreshAppInstall)) {
                    OnboardingPageFragment.DefaultBrowserPage.instance(continueButtonTextResourceId)
                } else null
            }
            else -> null
        }
    }

    @StringRes
    fun getContinueButtonTextResourceId(position: Int, isFreshAppInstall: Boolean): Int {
        if (!isFreshAppInstall) {
            return R.string.onboardingBackButton
        }
        return if (isFinalPage(position, isFreshAppInstall)) {
            R.string.onboardingContinueFinalPage
        } else {
            R.string.onboardingContinue
        }
    }

    private fun isFinalPage(position: Int, isFreshAppInstall: Boolean) = position == pageCount(isFreshAppInstall) - 1

    private fun shouldShowDefaultBrowserPage(isFreshAppInstall: Boolean): Boolean {
        return isFreshAppInstall && defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration()
    }
}
