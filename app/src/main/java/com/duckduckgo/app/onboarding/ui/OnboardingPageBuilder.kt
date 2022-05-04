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

import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.WelcomePage
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnIntroPage
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPermissionPage

interface OnboardingPageBuilder {
    fun buildWelcomePage(): WelcomePage
    fun buildDefaultBrowserPage(): DefaultBrowserPage

    fun buildVpnIntro(): VpnIntroPage

    fun buildVpnPermission(): VpnPermissionPage

    sealed class OnboardingPageBlueprint {
        object DefaultBrowserBlueprint : OnboardingPageBlueprint()
        object WelcomeBlueprint : OnboardingPageBlueprint()
        object VpnIntroBlueprint : OnboardingPageBlueprint()
        object VpnPermissionBlueprint : OnboardingPageBlueprint()
    }
}

class OnboardingFragmentPageBuilder : OnboardingPageBuilder {

    override fun buildWelcomePage() = WelcomePage()
    override fun buildDefaultBrowserPage() = DefaultBrowserPage()
    override fun buildVpnIntro() = VpnIntroPage()
    override fun buildVpnPermission() = VpnPermissionPage()
}
