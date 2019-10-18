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
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint.DefaultBrowserBlueprint
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint.SummaryPageBlueprint
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageExperiment
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.UnifiedSummaryPage
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager

interface OnboardingPageManager {
    fun pageCount(): Int
    fun buildPageBlueprints()
    fun buildPage(position: Int): OnboardingPageFragment?
    fun getContinueButtonTextResourceId(position: Int): Int
}

class OnboardingPageManagerWithTrackerBlocking(
    private val onboardingPageBuilder: OnboardingPageBuilder,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager
) : OnboardingPageManager {

    private val pages = mutableListOf<OnboardingPageBlueprint>()

    override fun pageCount() = pages.size

    override fun buildPageBlueprints() {
        pages.clear()

        pages.add(SummaryPageBlueprint())

        if (shouldShowDefaultBrowserPage()) {
            pages.add((DefaultBrowserBlueprint()))
        }

        pages.forEachIndexed { index, pageBlueprint ->
            pageBlueprint.continueButtonTextResourceId = getContinueButtonTextResourceId(index)
        }
    }

    override fun buildPage(position: Int): OnboardingPageFragment? {
        return when (val blueprint = pages.getOrNull(position)) {
            is SummaryPageBlueprint -> buildSummaryPage(blueprint)
            is DefaultBrowserBlueprint -> {
                if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.OnboardingExperiment)) {
                    buildDefaultBrowserPageExperiment(blueprint)
                } else {
                    buildDefaultBrowserPage(blueprint)
                }
            }
            else -> null
        }
    }

    @StringRes
    override fun getContinueButtonTextResourceId(position: Int): Int {
        return if (isFinalPage(position)) {
            R.string.onboardingContinueFinalPage
        } else {
            R.string.onboardingContinue
        }
    }

    private fun shouldShowDefaultBrowserPage(): Boolean {
        return defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration()
    }

    private fun isFinalPage(position: Int) = position == pageCount() - 1


    private fun buildSummaryPage(blueprint: SummaryPageBlueprint): UnifiedSummaryPage {
        return onboardingPageBuilder.buildSummaryPage(blueprint.continueButtonTextResourceId)
    }

    private fun buildDefaultBrowserPage(blueprint: DefaultBrowserBlueprint): DefaultBrowserPage {
        return onboardingPageBuilder.buildDefaultBrowserPage(blueprint.continueButtonTextResourceId)
    }

    private fun buildDefaultBrowserPageExperiment(blueprint: DefaultBrowserBlueprint): DefaultBrowserPageExperiment {
        return onboardingPageBuilder.buildDefaultBrowserPageExperiment(blueprint.continueButtonTextResourceId)
    }
}
