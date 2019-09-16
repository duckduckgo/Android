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

import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.rating.ui.AppEnjoymentDialogFragment
import com.duckduckgo.app.browser.rating.ui.GiveFeedbackDialogFragment
import com.duckduckgo.app.browser.rating.ui.RateAppDialogFragment
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.feedback.ui.initial.InitialFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.brokensite.BrokenSiteNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.mainreason.MainReasonNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.openended.ShareOpenEndedFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.subreason.SubReasonNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.positive.initial.PositiveFeedbackLandingFragment
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.job.AppConfigurationJobService
import com.duckduckgo.app.launch.LaunchActivity
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageExperiment
import com.duckduckgo.app.onboarding.ui.page.UnifiedSummaryPage
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.privacy.ui.PrivacyPracticesActivity
import com.duckduckgo.app.privacy.ui.ScorecardActivity
import com.duckduckgo.app.privacy.ui.TrackerNetworksActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import com.duckduckgo.app.tabs.ui.old.TabSwitcherActivityLegacy
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
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
    abstract fun tabsActivityLegacy(): TabSwitcherActivityLegacy

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
    abstract fun feedbackActivity(): FeedbackActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun brokenSiteActivity(): BrokenSiteActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun userSurveyActivity(): SurveyActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun addWidgetInstructionsActivity(): AddWidgetInstructionsActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun settingsActivity(): SettingsActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun bookmarksActivity(): BookmarksActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun fireActivity(): FireActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun aboutDuckDuckGoActivity(): AboutDuckDuckGoActivity

    /* Fragments */

    @ContributesAndroidInjector
    abstract fun browserTabFragment(): BrowserTabFragment

    @ContributesAndroidInjector
    abstract fun onboardingDefaultBrowserFragment(): DefaultBrowserPage

    @ContributesAndroidInjector
    abstract fun onboardingDefaultBrowserFragmentExperiment(): DefaultBrowserPageExperiment

    @ContributesAndroidInjector
    abstract fun appEnjoymentDialogFragment(): AppEnjoymentDialogFragment

    @ContributesAndroidInjector
    abstract fun giveFeedbackDialogFragment(): GiveFeedbackDialogFragment

    @ContributesAndroidInjector
    abstract fun rateAppDialogFragment(): RateAppDialogFragment

    @ContributesAndroidInjector
    abstract fun initialfFeedbackFragment(): InitialFeedbackFragment

    @ContributesAndroidInjector
    abstract fun positiveFeedbackLandingFragment(): PositiveFeedbackLandingFragment

    @ContributesAndroidInjector
    abstract fun shareOpenEndedPositiveFeedbackFragment(): ShareOpenEndedFeedbackFragment

    @ContributesAndroidInjector
    abstract fun mainReasonNegativeFeedbackFragment(): MainReasonNegativeFeedbackFragment

    @ContributesAndroidInjector
    abstract fun disambiguationNegativeFeedbackFragment(): SubReasonNegativeFeedbackFragment

    @ContributesAndroidInjector
    abstract fun brokenSiteNegativeFeedbackFragment(): BrokenSiteNegativeFeedbackFragment

    @ContributesAndroidInjector
    abstract fun unifiedSummaryPage(): UnifiedSummaryPage

    /* Services */

    @ContributesAndroidInjector
    abstract fun jobService(): AppConfigurationJobService

    @ContributesAndroidInjector
    abstract fun notificationHandlerService(): NotificationHandlerService
}