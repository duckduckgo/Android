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

import android.os.Bundle
import androidx.annotation.StringRes
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageExperiment
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.UnifiedSummaryPage

interface OnboardingPageBuilder {
    fun buildSummaryPage(@StringRes continueButtonTextResourceId: Int?): UnifiedSummaryPage
    fun buildDefaultBrowserPage(@StringRes continueButtonTextResourceId: Int?): DefaultBrowserPage
    fun buildDefaultBrowserPageExperiment(@StringRes continueButtonTextResourceId: Int?): DefaultBrowserPageExperiment

    sealed class OnboardingPageBlueprint(@StringRes open var continueButtonTextResourceId: Int) {

        data class SummaryPageBlueprint(override var continueButtonTextResourceId: Int = 0) :
            OnboardingPageBlueprint(continueButtonTextResourceId)

        data class DefaultBrowserBlueprint(override var continueButtonTextResourceId: Int = 0) :
            OnboardingPageBlueprint(continueButtonTextResourceId)
    }
}

class OnboardingFragmentPageBuilder : OnboardingPageBuilder {

    override fun buildSummaryPage(@StringRes continueButtonTextResourceId: Int?): UnifiedSummaryPage {
        val bundle = Bundle()

        if (continueButtonTextResourceId != null) {
            bundle.putInt(OnboardingPageFragment.CONTINUE_BUTTON_TEXT_RESOURCE_ID_EXTRA, continueButtonTextResourceId)
        }

        val fragment = UnifiedSummaryPage()
        fragment.arguments = bundle
        return fragment
    }

    override fun buildDefaultBrowserPage(@StringRes continueButtonTextResourceId: Int?): DefaultBrowserPage {
        val bundle = Bundle()

        if (continueButtonTextResourceId != null) {
            bundle.putInt(OnboardingPageFragment.CONTINUE_BUTTON_TEXT_RESOURCE_ID_EXTRA, continueButtonTextResourceId)
        }

        val fragment = DefaultBrowserPage()
        fragment.arguments = bundle
        return fragment
    }

    override fun buildDefaultBrowserPageExperiment(continueButtonTextResourceId: Int?): DefaultBrowserPageExperiment {
        val bundle = Bundle()

        if (continueButtonTextResourceId != null) {
            bundle.putInt(OnboardingPageFragment.CONTINUE_BUTTON_TEXT_RESOURCE_ID_EXTRA, continueButtonTextResourceId)
        }

        val fragment = DefaultBrowserPageExperiment()
        fragment.arguments = bundle
        return fragment
    }
}