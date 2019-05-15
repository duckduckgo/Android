/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint.*
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.TrackerBlockerOptInPage
import com.duckduckgo.app.onboarding.ui.page.UnifiedSummaryPage
import com.duckduckgo.app.statistics.VariantManager

class OnboardingPageManager(
    private val variantManager: VariantManager,
    private val onboardingPageBuilder: OnboardingPageBuilder,
    private val defaultWebBrowserCapability: DefaultBrowserDetector
) {

    private val pages = mutableListOf<OnboardingPageBlueprint>()
    private var isFreshAppInstall = false

    fun pageCount() = pages.size

    fun buildPageBlueprints(isFreshAppInstall: Boolean) {
        this.isFreshAppInstall = isFreshAppInstall

        if (shouldShowTrackerBlockingOptIn(isFreshAppInstall)) {
            pages.add(TrackerBlockingOptInBlueprint())
        }

        if (shouldShowSummaryPage()) {
            pages.add(SummaryPageBlueprint(isFreshAppInstall))
        }

        if (shouldShowDefaultBrowserPage(isFreshAppInstall)) {
            pages.add((DefaultBrowserBlueprint()))
        }

        pages.forEachIndexed { index, pageBlueprint ->
            pageBlueprint.continueButtonTextResourceId = getContinueButtonTextResourceId(index, isFreshAppInstall)
        }
    }

    fun buildPage(position: Int): OnboardingPageFragment? {
        return when (val blueprint = pages.getOrNull(position)) {
            is TrackerBlockingOptInBlueprint -> buildBlockingOptInPage()
            is SummaryPageBlueprint -> buildSummaryPage(blueprint)
            is DefaultBrowserBlueprint -> buildDefaultBrowserPage(blueprint)
            else -> null
        }
    }

    private fun shouldShowSummaryPage(): Boolean {
        // always show summary screen
        return true
    }

    private fun shouldShowDefaultBrowserPage(isFreshAppInstall: Boolean): Boolean {
        return isFreshAppInstall && defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration()
    }

    private fun shouldShowTrackerBlockingOptIn(isFreshAppInstall: Boolean): Boolean {
        return isFreshAppInstall && variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ShowOnboardingTrackerBlockerOptIn)
    }

    @StringRes
    fun getContinueButtonTextResourceId(position: Int, isFreshAppInstall: Boolean): Int {
        if (!isFreshAppInstall) {
            return R.string.onboardingBackButton
        }
        return if (isFinalPage(position)) {
            R.string.onboardingContinueFinalPage
        } else {
            R.string.onboardingContinue
        }
    }

    private fun isFinalPage(position: Int) = position == pageCount() - 1

    private fun buildBlockingOptInPage(): TrackerBlockerOptInPage {
        return onboardingPageBuilder.buildTrackerBlockingOptInPage()
    }

    private fun buildSummaryPage(blueprint: SummaryPageBlueprint): UnifiedSummaryPage {
        val titleTextResourceId =
            if (blueprint.isFreshAppInstall) R.string.unifiedOnboardingTitleFirstVisit else R.string.unifiedOnboardingTitleSubsequentVisits
        return onboardingPageBuilder.buildSummaryPage(blueprint.continueButtonTextResourceId, titleTextResourceId)
    }

    private fun buildDefaultBrowserPage(blueprint: DefaultBrowserBlueprint): DefaultBrowserPage {
        return onboardingPageBuilder.buildDefaultBrowserPage(blueprint.continueButtonTextResourceId)
    }
}
