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

package com.duckduckgo.app.onboarding.di

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.RealDefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.OnboardingFragmentPageBuilder
import com.duckduckgo.app.onboarding.ui.OnboardingPageBuilder
import com.duckduckgo.app.onboarding.ui.OnboardingPageManager
import com.duckduckgo.app.onboarding.ui.OnboardingPageManagerWithTrackerBlocking
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
class OnboardingModule {

    @Provides
    fun onboardingPageManger(
        defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
        onboardingPageBuilder: OnboardingPageBuilder,
        defaultBrowserDetector: DefaultBrowserDetector,
    ): OnboardingPageManager {
        return OnboardingPageManagerWithTrackerBlocking(
            defaultRoleBrowserDialog,
            onboardingPageBuilder,
            defaultBrowserDetector,
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun onboardingPageBuilder(): OnboardingPageBuilder {
        return OnboardingFragmentPageBuilder()
    }

    @Provides
    fun defaultRoleBrowserDialog(
        appInstallStore: AppInstallStore,
        appBuildConfig: AppBuildConfig,
    ): DefaultRoleBrowserDialog = RealDefaultRoleBrowserDialog(appInstallStore, appBuildConfig)
}
