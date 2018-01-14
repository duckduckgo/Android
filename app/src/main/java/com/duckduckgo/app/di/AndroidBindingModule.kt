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
import com.duckduckgo.app.job.AppConfigurationJobService
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity
import com.duckduckgo.app.privacymonitor.ui.PrivacyPracticesActivity
import com.duckduckgo.app.privacymonitor.ui.ScorecardActivity
import com.duckduckgo.app.privacymonitor.ui.TrackerNetworksActivity
import com.duckduckgo.app.settings.SettingsActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector


@Module
abstract class AndroidBindingModule {

    /* Activities */

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun browserActivity(): BrowserActivity

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

    /* Services */

    @ContributesAndroidInjector
    abstract fun jobService(): AppConfigurationJobService
}