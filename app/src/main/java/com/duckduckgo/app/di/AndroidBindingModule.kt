/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.job.AppConfigurationJobService
import com.duckduckgo.app.launch.LaunchActivity
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.privacy.ui.PrivacyPracticesActivity
import com.duckduckgo.app.privacy.ui.ScorecardActivity
import com.duckduckgo.app.privacy.ui.TrackerNetworksActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.tabs.TabSwitcherActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector


@Module
abstract class AndroidBindingModule {

    /* Activities */

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun launchActivity(): LaunchActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun onboardingActivity(): OnboardingActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun browserActivity(): BrowserActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun tabsActivity(): TabSwitcherActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun privacyDashboardActivity(): PrivacyDashboardActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun scorecardActivity(): ScorecardActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun trackerNetworksActivity(): TrackerNetworksActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun privacyTermsActivity(): PrivacyPracticesActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun settingsActivity(): SettingsActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun bookmarksActivity(): BookmarksActivity

    /* Fragments */

    @ContributesAndroidInjector
    abstract fun browserTabFragment(): BrowserTabFragment

    /* Services */

    @ContributesAndroidInjector
    abstract fun jobService(): AppConfigurationJobService
}