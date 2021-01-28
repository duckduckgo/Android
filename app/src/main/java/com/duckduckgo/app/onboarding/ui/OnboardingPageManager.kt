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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint.DefaultBrowserBlueprint
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder.OnboardingPageBlueprint.WelcomeBlueprint
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.WelcomePage

interface OnboardingPageManager {
    fun pageCount(): Int
    fun buildPageBlueprints()
    fun buildPage(position: Int): OnboardingPageFragment?
}

class OnboardingPageManagerWithTrackerBlocking(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val onboardingPageBuilder: OnboardingPageBuilder,
    private val defaultWebBrowserCapability: DefaultBrowserDetector
) : OnboardingPageManager {

    private val pages = mutableListOf<OnboardingPageBlueprint>()

    override fun pageCount() = pages.size

    override fun buildPageBlueprints() {
        pages.clear()

        pages.add(WelcomeBlueprint)

        if (shouldShowDefaultBrowserPage()) {
            pages.add((DefaultBrowserBlueprint))
        }
    }

    override fun buildPage(position: Int): OnboardingPageFragment? {
        return when (pages.getOrNull(position)) {
            is WelcomeBlueprint -> buildWelcomePage()
            is DefaultBrowserBlueprint -> buildDefaultBrowserPage()
            else -> null
        }
    }

    private fun shouldShowDefaultBrowserPage(): Boolean {
        return defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration() &&
            !defaultWebBrowserCapability.isDefaultBrowser() &&
            !defaultRoleBrowserDialog.shouldShowDialog()
    }

    private fun buildDefaultBrowserPage(): DefaultBrowserPage {
        return onboardingPageBuilder.buildDefaultBrowserPage()
    }

    private fun buildWelcomePage(): WelcomePage {
        return onboardingPageBuilder.buildWelcomePage()
    }
}
