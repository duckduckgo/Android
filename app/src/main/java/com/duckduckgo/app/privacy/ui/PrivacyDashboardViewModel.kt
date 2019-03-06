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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao.NetworkTally
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.PRIVACY_DASHBOARD_OPENED

class PrivacyDashboardViewModel(
    private val settingsStore: PrivacySettingsStore,
    networkLeaderboardDao: NetworkLeaderboardDao,
    pixel: Pixel
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
        val showTrackerNetworkLeaderboard: Boolean,
        val domainsVisited: Int,
        val trackerNetworkTally: List<NetworkTally>,
        val shouldReloadPage: Boolean
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    private var site: Site? = null

    private val domainsVisited: LiveData<Int> = networkLeaderboardDao.domainsVisitedCount()
    private val domainsVisitedObserver = Observer<Int> { onDomainsVisitedChanged(it) }
    private val trackerNetworkTally: LiveData<List<NetworkTally>> = networkLeaderboardDao.trackerNetworkTally()
    private val trackerNetworkActivityObserver = Observer<List<NetworkTally>> { onTrackerNetworkTallyChanged(it) }

    private val privacyInitiallyOn = settingsStore.privacyOn

    private val shouldReloadPage: Boolean
        get() = privacyInitiallyOn != settingsStore.privacyOn

    init {
        pixel.fire(PRIVACY_DASHBOARD_OPENED)
        resetViewState()
        domainsVisited.observeForever(domainsVisitedObserver)
        trackerNetworkTally.observeForever(trackerNetworkActivityObserver)
    }

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        domainsVisited.removeObserver(domainsVisitedObserver)
        trackerNetworkTally.removeObserver(trackerNetworkActivityObserver)
    }

    fun onDomainsVisitedChanged(count: Int?) {
        val domainCount = count ?: 0
        val networkCount = viewState.value?.trackerNetworkTally?.count() ?: 0
        viewState.value = viewState.value?.copy(
            showTrackerNetworkLeaderboard = showTrackerNetworkLeaderboard(domainCount, networkCount),
            domainsVisited = domainCount
        )
    }

    fun onTrackerNetworkTallyChanged(tally: List<NetworkTally>?) {
        val domainCount = viewState.value?.domainsVisited ?: 0
        val networkTally = tally ?: emptyList()
        viewState.value = viewState.value?.copy(
            showTrackerNetworkLeaderboard = showTrackerNetworkLeaderboard(domainCount, networkTally.count()),
            trackerNetworkTally = networkTally
        )
    }

    private fun showTrackerNetworkLeaderboard(domainCount: Int, networkCount: Int): Boolean {
        return domainCount > LEADERNOARD_MIN_DOMAINS_EXCLUSIVE && networkCount >= LEADERBOARD_MIN_NETWORKS
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
            toggleEnabled = settingsStore.privacyOn,
            practices = UNKNOWN,
            showTrackerNetworkLeaderboard = false,
            domainsVisited = 0,
            trackerNetworkTally = emptyList(),
            shouldReloadPage = shouldReloadPage
        )
    }

    private fun updateSite(site: Site) {
        viewState.value = viewState.value?.copy(
            domain = site.uri?.host ?: "",
            beforeGrade = site.grade,
            afterGrade = site.improvedGrade,
            httpsStatus = site.https,
            trackerCount = site.trackerCount,
            allTrackersBlocked = site.allTrackersBlocked,
            practices = site.privacyPractices.summary
        )
    }

    fun onPrivacyToggled(enabled: Boolean) {
        if (enabled != viewState.value?.toggleEnabled) {
            settingsStore.privacyOn = enabled
            viewState.value = viewState.value?.copy(
                toggleEnabled = enabled,
                shouldReloadPage = shouldReloadPage
            )
        }
    }

    private companion object {
        private const val LEADERBOARD_MIN_NETWORKS = 3
        private const val LEADERNOARD_MIN_DOMAINS_EXCLUSIVE = 30
    }
}



