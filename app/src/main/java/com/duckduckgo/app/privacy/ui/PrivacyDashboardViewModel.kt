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

package com.duckduckgo.app.privacy.ui

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.*
import com.duckduckgo.app.brokensite.BrokenSiteData
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.Command.LaunchManageWhitelist
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Provider
import javax.inject.Singleton

class PrivacyDashboardViewModel(
    private val userWhitelistDao: UserWhitelistDao,
    networkLeaderboardDao: NetworkLeaderboardDao,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    data class ViewState(
        val domain: String,
        val beforeGrade: PrivacyGrade,
        val afterGrade: PrivacyGrade,
        val httpsStatus: HttpsStatus,
        val trackerCount: Int,
        val allTrackersBlocked: Boolean,
        val practices: PrivacyPractices.Summary,
        val toggleEnabled: Boolean?,
        val shouldShowTrackerNetworkLeaderboard: Boolean,
        val sitesVisited: Int,
        val trackerNetworkEntries: List<NetworkLeaderboardEntry>,
        val shouldReloadPage: Boolean
    )

    sealed class Command {
        object LaunchManageWhitelist : Command()
        class LaunchReportBrokenSite(val data: BrokenSiteData) : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private var site: Site? = null

    private val sitesVisited: LiveData<Int> = networkLeaderboardDao.sitesVisited()
    private val sitesVisitedObserver = Observer<Int> { onSitesVisitedChanged(it) }
    private val trackerNetworkLeaderboard: LiveData<List<NetworkLeaderboardEntry>> = networkLeaderboardDao.trackerNetworkLeaderboard()
    private val trackerNetworkActivityObserver = Observer<List<NetworkLeaderboardEntry>> { onTrackerNetworkEntriesChanged(it) }

    init {
        pixel.fire(PRIVACY_DASHBOARD_OPENED)
        resetViewState()
        sitesVisited.observeForever(sitesVisitedObserver)
        trackerNetworkLeaderboard.observeForever(trackerNetworkActivityObserver)
    }

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        sitesVisited.removeObserver(sitesVisitedObserver)
        trackerNetworkLeaderboard.removeObserver(trackerNetworkActivityObserver)
    }

    fun onSitesVisitedChanged(count: Int?) {
        val siteCount = count ?: 0
        val networkCount = viewState.value?.trackerNetworkEntries?.count() ?: 0
        viewState.value = viewState.value?.copy(
            shouldShowTrackerNetworkLeaderboard = showTrackerNetworkLeaderboard(siteCount, networkCount),
            sitesVisited = siteCount
        )
    }

    fun onTrackerNetworkEntriesChanged(networkLeaderboardEntries: List<NetworkLeaderboardEntry>?) {
        val domainCount = viewState.value?.sitesVisited ?: 0
        val networkEntries = networkLeaderboardEntries ?: emptyList()
        viewState.value = viewState.value?.copy(
            shouldShowTrackerNetworkLeaderboard = showTrackerNetworkLeaderboard(domainCount, networkEntries.count()),
            trackerNetworkEntries = networkEntries
        )
    }

    private fun showTrackerNetworkLeaderboard(siteVisitedCount: Int, networkCount: Int): Boolean {
        return siteVisitedCount > LEADERBOARD_MIN_DOMAINS_EXCLUSIVE && networkCount >= LEADERBOARD_MIN_NETWORKS
    }

    fun onSiteChanged(site: Site?) {
        this.site = site
        if (site == null) {
            resetViewState()
        } else {
            viewModelScope.launch { updateSite(site) }
        }
    }

    private fun resetViewState() {
        viewState.value = ViewState(
            domain = "",
            beforeGrade = PrivacyGrade.UNKNOWN,
            afterGrade = PrivacyGrade.UNKNOWN,
            httpsStatus = HttpsStatus.SECURE,
            trackerCount = 0,
            allTrackersBlocked = true,
            toggleEnabled = null,
            practices = UNKNOWN,
            shouldShowTrackerNetworkLeaderboard = false,
            sitesVisited = 0,
            trackerNetworkEntries = emptyList(),
            shouldReloadPage = false
        )
    }

    private suspend fun updateSite(site: Site) {
        val grades = site.calculateGrades()
        val domain = site.domain ?: ""
        val toggleEnabled = withContext(dispatchers.io()) { !userWhitelistDao.contains(domain) }

        withContext(dispatchers.main()) {
            viewState.value = viewState.value?.copy(
                domain = site.domain ?: "",
                beforeGrade = grades.grade,
                afterGrade = grades.improvedGrade,
                httpsStatus = site.https,
                trackerCount = site.trackerCount,
                allTrackersBlocked = site.allTrackersBlocked,
                toggleEnabled = toggleEnabled,
                practices = site.privacyPractices.summary
            )
        }
    }

    fun onPrivacyToggled(enabled: Boolean) {
        if (viewState.value?.toggleEnabled == null) {
            return
        }

        if (enabled == viewState.value?.toggleEnabled) {
            return
        }

        viewState.value = viewState.value?.copy(
            toggleEnabled = enabled,
            shouldReloadPage = true
        )

        val domain = site?.domain ?: return
        GlobalScope.launch(dispatchers.io()) {
            if (enabled) {
                userWhitelistDao.delete(domain)
                pixel.fire(PRIVACY_DASHBOARD_WHITELIST_REMOVE)
            } else {
                userWhitelistDao.insert(domain)
                pixel.fire(PRIVACY_DASHBOARD_WHITELIST_ADD)
            }
        }
    }

    fun onManageWhitelistSelected() {
        pixel.fire(PRIVACY_DASHBOARD_MANAGE_WHITELIST)
        command.value = LaunchManageWhitelist
    }

    fun onReportBrokenSiteSelected() {
        pixel.fire(PRIVACY_DASHBOARD_REPORT_BROKEN_SITE)
        command.value = LaunchReportBrokenSite(BrokenSiteData.fromSite(site))
    }

    private companion object {
        private const val LEADERBOARD_MIN_NETWORKS = 3
        private const val LEADERBOARD_MIN_DOMAINS_EXCLUSIVE = 30
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class PrivacyDashboardViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun providePrivacyDashboardViewModelFactory(
        userWhitelistDao: Provider<UserWhitelistDao>,
        networkLeaderboardDao: Provider<NetworkLeaderboardDao>,
        pixel: Provider<Pixel>
    ): ViewModelFactoryPlugin {
        return PrivacyDashboardViewModelFactory(userWhitelistDao, networkLeaderboardDao, pixel)
    }
}

private class PrivacyDashboardViewModelFactory(
    private val userWhitelistDao: Provider<UserWhitelistDao>,
    private val networkLeaderboardDao: Provider<NetworkLeaderboardDao>,
    private val pixel: Provider<Pixel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(PrivacyDashboardViewModel::class.java) -> PrivacyDashboardViewModel(userWhitelistDao.get(), networkLeaderboardDao.get(), pixel.get()) as T
                else -> null
            }
        }
    }
}
