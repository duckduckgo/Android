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

package com.duckduckgo.app.global

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.feedback.api.FeedbackSubmitter
import com.duckduckgo.app.feedback.ui.common.FeedbackViewModel
import com.duckduckgo.app.feedback.ui.initial.InitialFeedbackFragmentViewModel
import com.duckduckgo.app.feedback.ui.negative.brokensite.BrokenSiteNegativeFeedbackViewModel
import com.duckduckgo.app.feedback.ui.negative.openended.ShareOpenEndedNegativeFeedbackViewModel
import com.duckduckgo.app.feedback.ui.positive.initial.PositiveFeedbackLandingViewModel
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesViewModel
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.global.rating.AppEnjoymentUserEventRecorder
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlViewModel
import com.duckduckgo.app.icon.api.IconModifier
import com.duckduckgo.app.icon.ui.ChangeIconViewModel
import com.duckduckgo.app.launch.LaunchViewModel
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.location.ui.LocationPermissionsViewModel
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingPageManager
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageViewModel
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.ui.*
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.settings.SettingsViewModel
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.ui.SurveyViewModel
import com.duckduckgo.app.systemsearch.DeviceAppLookup
import com.duckduckgo.app.systemsearch.SystemSearchViewModel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel
import javax.inject.Inject

@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
    private val statisticsUpdater: StatisticsUpdater,
    private val statisticsStore: StatisticsDataStore,
    private val userStageStore: UserStageStore,
    private val appInstallStore: AppInstallStore,
    private val queryUrlConverter: QueryUrlConverter,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val tabRepository: TabRepository,
    private val siteFactory: SiteFactory,
    private val userWhitelistDao: UserWhitelistDao,
    private val networkLeaderboardDao: NetworkLeaderboardDao,
    private val bookmarksDao: BookmarksDao,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geoLocationPermissions: GeoLocationPermissions,
    private val navigationAwareLoginDetector: NavigationAwareLoginDetector,
    private val surveyDao: SurveyDao,
    private val autoCompleteApi: AutoCompleteApi,
    private val deviceAppLookup: DeviceAppLookup,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val webViewLongPressHandler: LongPressHandler,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val variantManager: VariantManager,
    private val brokenSiteSender: BrokenSiteSender,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val specialUrlDetector: SpecialUrlDetector,
    private val faviconManager: FaviconManager,
    private val addToHomeCapabilityDetector: AddToHomeCapabilityDetector,
    private val pixel: Pixel,
    private val dataClearer: DataClearer,
    private val ctaViewModel: CtaViewModel,
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val searchCountDao: SearchCountDao,
    private val appEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder,
    private val playStoreUtils: PlayStoreUtils,
    private val feedbackSubmitter: FeedbackSubmitter,
    private val onboardingPageManager: OnboardingPageManager,
    private val appInstallationReferrerStateListener: AppInstallationReferrerStateListener,
    private val appIconModifier: IconModifier,
    private val userEventsStore: UserEventsStore,
    private val notificationDao: NotificationDao,
    private val userOurAppDetector: UseOurAppDetector,
    private val dismissedCtaDao: DismissedCtaDao,
    private val fileDownloader: FileDownloader,
    private val dispatcherProvider: DispatcherProvider
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>) =
        with(modelClass) {
            when {
                isAssignableFrom(LaunchViewModel::class.java) -> LaunchViewModel(userStageStore, appInstallationReferrerStateListener)
                isAssignableFrom(SystemSearchViewModel::class.java) -> SystemSearchViewModel(userStageStore, autoCompleteApi, deviceAppLookup, pixel)
                isAssignableFrom(OnboardingViewModel::class.java) -> onboardingViewModel()
                isAssignableFrom(BrowserViewModel::class.java) -> browserViewModel()
                isAssignableFrom(BrowserTabViewModel::class.java) -> browserTabViewModel()
                isAssignableFrom(TabSwitcherViewModel::class.java) -> TabSwitcherViewModel(tabRepository, webViewSessionStorage)
                isAssignableFrom(PrivacyDashboardViewModel::class.java) -> privacyDashboardViewModel()
                isAssignableFrom(ScorecardViewModel::class.java) -> ScorecardViewModel(userWhitelistDao)
                isAssignableFrom(TrackerNetworksViewModel::class.java) -> TrackerNetworksViewModel()
                isAssignableFrom(PrivacyPracticesViewModel::class.java) -> PrivacyPracticesViewModel()
                isAssignableFrom(WhitelistViewModel::class.java) -> WhitelistViewModel(userWhitelistDao)
                isAssignableFrom(FeedbackViewModel::class.java) -> FeedbackViewModel(playStoreUtils, feedbackSubmitter)
                isAssignableFrom(BrokenSiteViewModel::class.java) -> BrokenSiteViewModel(pixel, brokenSiteSender)
                isAssignableFrom(SurveyViewModel::class.java) -> SurveyViewModel(surveyDao, statisticsStore, appInstallStore)
                isAssignableFrom(AddWidgetInstructionsViewModel::class.java) -> AddWidgetInstructionsViewModel()
                isAssignableFrom(SettingsViewModel::class.java) -> settingsViewModel()
                isAssignableFrom(BookmarksViewModel::class.java) -> BookmarksViewModel(bookmarksDao, faviconManager, dispatcherProvider)
                isAssignableFrom(InitialFeedbackFragmentViewModel::class.java) -> InitialFeedbackFragmentViewModel()
                isAssignableFrom(PositiveFeedbackLandingViewModel::class.java) -> PositiveFeedbackLandingViewModel()
                isAssignableFrom(ShareOpenEndedNegativeFeedbackViewModel::class.java) -> ShareOpenEndedNegativeFeedbackViewModel()
                isAssignableFrom(BrokenSiteNegativeFeedbackViewModel::class.java) -> BrokenSiteNegativeFeedbackViewModel()
                isAssignableFrom(DefaultBrowserPageViewModel::class.java) -> defaultBrowserPage()
                isAssignableFrom(ChangeIconViewModel::class.java) -> changeAppIconViewModel()
                isAssignableFrom(FireproofWebsitesViewModel::class.java) -> fireproofWebsiteViewModel()
                isAssignableFrom(LocationPermissionsViewModel::class.java) -> locationPermissionsViewModel()
                isAssignableFrom(GlobalPrivacyControlViewModel::class.java) -> globalPrivacyControlViewModel()

                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T

    private fun defaultBrowserPage() = DefaultBrowserPageViewModel(defaultBrowserDetector, pixel, appInstallStore)

    private fun onboardingViewModel() = OnboardingViewModel(userStageStore, onboardingPageManager, dispatcherProvider)

    private fun settingsViewModel(): SettingsViewModel {
        return SettingsViewModel(
            appSettingsPreferencesStore,
            defaultBrowserDetector,
            variantManager,
            pixel
        )
    }

    private fun privacyDashboardViewModel(): PrivacyDashboardViewModel {
        return PrivacyDashboardViewModel(
            userWhitelistDao,
            networkLeaderboardDao,
            pixel
        )
    }

    private fun browserViewModel(): BrowserViewModel {
        return BrowserViewModel(
            tabRepository = tabRepository,
            queryUrlConverter = queryUrlConverter,
            dataClearer = dataClearer,
            appEnjoymentPromptEmitter = appEnjoymentPromptEmitter,
            appEnjoymentUserEventRecorder = appEnjoymentUserEventRecorder,
            useOurAppDetector = userOurAppDetector,
            pixel = pixel
        )
    }

    private fun browserTabViewModel(): ViewModel = BrowserTabViewModel(
        statisticsUpdater = statisticsUpdater,
        queryUrlConverter = queryUrlConverter,
        duckDuckGoUrlDetector = duckDuckGoUrlDetector,
        siteFactory = siteFactory,
        tabRepository = tabRepository,
        userWhitelistDao = userWhitelistDao,
        networkLeaderboardDao = networkLeaderboardDao,
        bookmarksDao = bookmarksDao,
        fireproofWebsiteRepository = fireproofWebsiteRepository,
        locationPermissionsRepository = locationPermissionsRepository,
        geoLocationPermissions = geoLocationPermissions,
        navigationAwareLoginDetector = navigationAwareLoginDetector,
        autoComplete = autoCompleteApi,
        appSettingsPreferencesStore = appSettingsPreferencesStore,
        longPressHandler = webViewLongPressHandler,
        webViewSessionStorage = webViewSessionStorage,
        specialUrlDetector = specialUrlDetector,
        faviconManager = faviconManager,
        addToHomeCapabilityDetector = addToHomeCapabilityDetector,
        ctaViewModel = ctaViewModel,
        searchCountDao = searchCountDao,
        pixel = pixel,
        userEventsStore = userEventsStore,
        notificationDao = notificationDao,
        useOurAppDetector = userOurAppDetector,
        variantManager = variantManager,
        fileDownloader = fileDownloader
    )

    private fun changeAppIconViewModel() =
        ChangeIconViewModel(settingsDataStore = appSettingsPreferencesStore, appIconModifier = appIconModifier, pixel = pixel)

    private fun fireproofWebsiteViewModel() =
        FireproofWebsitesViewModel(
            fireproofWebsiteRepository = fireproofWebsiteRepository,
            dispatcherProvider = dispatcherProvider,
            pixel = pixel,
            settingsDataStore = appSettingsPreferencesStore
        )

    private fun locationPermissionsViewModel() =
        LocationPermissionsViewModel(
            locationPermissionsRepository = locationPermissionsRepository,
            geoLocationPermissions = geoLocationPermissions,
            dispatcherProvider = dispatcherProvider,
            settingsDataStore = appSettingsPreferencesStore,
            pixel = pixel
        )

    private fun globalPrivacyControlViewModel() =
        GlobalPrivacyControlViewModel(
            settingsDataStore = appSettingsPreferencesStore,
            pixel = pixel
        )
}
