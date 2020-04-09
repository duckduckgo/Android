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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
        val toggleEnabled: Boolean,
        val shouldShowTrackerNetworkLeaderboard: Boolean,
        val sitesVisited: Int,
        val trackerNetworkEntries: List<NetworkLeaderboardEntry>,
        val shouldReloadPage: Boolean
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
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
            updateSite(site)
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
            toggleEnabled = true,
            practices = UNKNOWN,
            shouldShowTrackerNetworkLeaderboard = false,
            sitesVisited = 0,
            trackerNetworkEntries = emptyList(),
            shouldReloadPage = false
        )
    }

    private fun updateSite(site: Site) {
        val grades = site.calculateGrades()

        GlobalScope.launch(dispatchers.io()) {
            viewState.postValue(viewState.value?.copy(
                domain = site.domain ?: "",
                beforeGrade = grades.grade,
                afterGrade = grades.improvedGrade,
                httpsStatus = site.https,
                trackerCount = site.trackerCount,
                allTrackersBlocked = site.allTrackersBlocked,
                toggleEnabled = site.domain?.let { !userWhitelistDao.contains(it) } ?: true,
                practices = site.privacyPractices.summary
            ))
        }
    }

    fun onPrivacyToggled(enabled: Boolean) {
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
                pixel.fire(TRACKER_BLOCKER_DASHBOARD_TURNED_ON)
            } else {
                userWhitelistDao.insert(domain)
                pixel.fire(TRACKER_BLOCKER_DASHBOARD_TURNED_OFF)
            }
        }
    }

    private companion object {
        private const val LEADERBOARD_MIN_NETWORKS = 3
        private const val LEADERBOARD_MIN_DOMAINS_EXCLUSIVE = 30
    }
}



