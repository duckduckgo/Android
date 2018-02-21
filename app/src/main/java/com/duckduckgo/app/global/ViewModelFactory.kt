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

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel
import com.duckduckgo.app.browser.BrowserViewModel
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.LongPressHandler
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.launch.LaunchViewModel
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsSharedPreferences
import com.duckduckgo.app.privacymonitor.store.TermsOfServiceStore
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardViewModel
import com.duckduckgo.app.privacymonitor.ui.PrivacyPracticesViewModel
import com.duckduckgo.app.privacymonitor.ui.ScorecardViewModel
import com.duckduckgo.app.privacymonitor.ui.TrackerNetworksViewModel
import com.duckduckgo.app.settings.SettingsViewModel
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import javax.inject.Inject


@Suppress("UNCHECKED_CAST")
class ViewModelFactory @Inject constructor(
        private val statisticsUpdater: StatisticsUpdater,
        private val onboaringStore: OnboardingStore,
        private val queryUrlConverter: QueryUrlConverter,
        private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
        private val privacyMonitorRepository: PrivacyMonitorRepository,
        private val privacySettingsStore: PrivacySettingsSharedPreferences,
        private val termsOfServiceStore: TermsOfServiceStore,
        private val trackerNetworks: TrackerNetworks,
        private val stringResolver: StringResolver,
        private val appConfigurationDao: AppConfigurationDao,
        private val networkLeaderboardDao: NetworkLeaderboardDao,
        private val bookmarksDao: BookmarksDao,
        private val autoCompleteApi: AutoCompleteApi,
        private val appSettingsPreferencesStore: SettingsDataStore,
        private val webViewLongPressHandler: LongPressHandler
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>) =
            with(modelClass) {
                when {
                    isAssignableFrom(LaunchViewModel::class.java) -> LaunchViewModel(onboaringStore)
                    isAssignableFrom(OnboardingViewModel::class.java) -> OnboardingViewModel(onboaringStore)
                    isAssignableFrom(BrowserViewModel::class.java) -> browserViewModel()
                    isAssignableFrom(PrivacyDashboardViewModel::class.java) -> PrivacyDashboardViewModel(privacySettingsStore, networkLeaderboardDao)
                    isAssignableFrom(ScorecardViewModel::class.java) -> ScorecardViewModel(privacySettingsStore)
                    isAssignableFrom(TrackerNetworksViewModel::class.java) -> TrackerNetworksViewModel()
                    isAssignableFrom(PrivacyPracticesViewModel::class.java) -> PrivacyPracticesViewModel()
                    isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(stringResolver, appSettingsPreferencesStore)
                    isAssignableFrom(BookmarksViewModel::class.java) -> BookmarksViewModel(bookmarksDao)
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T

    private fun browserViewModel(): ViewModel = BrowserViewModel(
            statisticsUpdater = statisticsUpdater,
            queryUrlConverter = queryUrlConverter,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
            termsOfServiceStore = termsOfServiceStore,
            trackerNetworks = trackerNetworks,
            privacyMonitorRepository = privacyMonitorRepository,
            networkLeaderboardDao = networkLeaderboardDao,
            bookmarksDao = bookmarksDao,
            appSettingsPreferencesStore = appSettingsPreferencesStore,
            appConfigurationDao = appConfigurationDao,
            longPressHandler = webViewLongPressHandler,
            autoCompleteApi = autoCompleteApi)
}
