/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboarding.di

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.RealDefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.ComposeOnboardingDialogsStore
import com.duckduckgo.app.onboarding.ui.OnboardingFragmentPageBuilder
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder
import com.duckduckgo.app.onboarding.ui.OnboardingPageManager
import com.duckduckgo.app.onboarding.ui.OnboardingPageManagerWithTrackerBlocking
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboarding.ui.page.welcome.ComposeWelcomePageFragment
import com.duckduckgo.app.onboarding.ui.page.welcome.WelcomePageFragment
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Proof of concept, internal builds only: the same bindings as [OnboardingModule], except that the welcome page
 * becomes [ComposeWelcomePageFragment] once after the Compose Samples screen asks for it.
 */
@BindingContainer
@ContributesTo(
    scope = AppScope::class,
    replaces = [OnboardingModule::class],
)
class InternalOnboardingModule {

    @Provides
    fun onboardingPageManger(
        defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
        onboardingPageBuilder: OnboardingPageBuilder,
        defaultBrowserDetector: DefaultBrowserDetector,
        composeOnboardingDialogsStore: ComposeOnboardingDialogsStore,
    ): OnboardingPageManager {
        return ComposeSampleOnboardingPageManager(
            OnboardingPageManagerWithTrackerBlocking(
                defaultRoleBrowserDialog,
                onboardingPageBuilder,
                defaultBrowserDetector,
            ),
            composeOnboardingDialogsStore,
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun onboardingPageBuilder(): OnboardingPageBuilder {
        return OnboardingFragmentPageBuilder()
    }

    @Provides
    fun defaultRoleBrowserDialog(
        appInstallStore: AppInstallStore,
        appBuildConfig: AppBuildConfig,
    ): DefaultRoleBrowserDialog = RealDefaultRoleBrowserDialog(appInstallStore, appBuildConfig)
}

class ComposeSampleOnboardingPageManager(
    private val delegate: OnboardingPageManager,
    private val composeOnboardingDialogsStore: ComposeOnboardingDialogsStore,
) : OnboardingPageManager by delegate {

    override fun buildPage(position: Int): OnboardingPageFragment? {
        val page = delegate.buildPage(position)
        return if (page is WelcomePageFragment && composeOnboardingDialogsStore.consumeUseComposeDialogs()) {
            ComposeWelcomePageFragment()
        } else {
            page
        }
    }
}
