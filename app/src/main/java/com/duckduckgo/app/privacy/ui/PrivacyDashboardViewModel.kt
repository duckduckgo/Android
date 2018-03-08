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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.support.annotation.VisibleForTesting
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkPercent
import com.duckduckgo.app.privacy.model.*
import com.duckduckgo.app.privacy.store.PrivacySettingsStore

class PrivacyDashboardViewModel(private val settingsStore: PrivacySettingsStore,
                                networkLeaderboardDao: NetworkLeaderboardDao) : ViewModel() {

    data class ViewState(
            val domain: String,
            val beforeGrade: PrivacyGrade,
            val afterGrade: PrivacyGrade,
            val httpsStatus: HttpsStatus,
            val networkCount: Int,
            val allTrackersBlocked: Boolean,
            val practices: TermsOfService.Practices,
            val toggleEnabled: Boolean,
            val showNetworkTrackerSummary: Boolean,
            val networkTrackerSummaryName1: String?,
            val networkTrackerSummaryName2: String?,
            val networkTrackerSummaryName3: String?,
            val networkTrackerSummaryPercent1: Float,
            val networkTrackerSummaryPercent2: Float,
            val networkTrackerSummaryPercent3: Float,
            val shouldReloadPage: Boolean
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    private var site: Site? = null

    private val networkPercentsData: LiveData<Array<NetworkPercent>> = networkLeaderboardDao.networkPercents()
    private val networkPercentsObserver = Observer<Array<NetworkPercent>> { onNetworkPercentsChanged(it!!) }

    private val privacyInitiallyOn = settingsStore.privacyOn

    private val shouldReloadPage: Boolean
        get() = privacyInitiallyOn != settingsStore.privacyOn

    init {
        resetViewState()
        networkPercentsData.observeForever(networkPercentsObserver)
    }

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        networkPercentsData.removeObserver(networkPercentsObserver)
    }

    fun onNetworkPercentsChanged(networkPercents: Array<NetworkPercent>) {

        val enoughNetworksDetected = networkPercents.size >= 3
        val enoughDomainsVisited = enoughNetworksDetected && networkPercents[0].totalDomainsVisited > 10
        val showSummary = enoughDomainsVisited && enoughNetworksDetected

        viewState.value = viewState.value?.copy(
                showNetworkTrackerSummary = enoughNetworksDetected && enoughDomainsVisited,
                networkTrackerSummaryName1 = if (showSummary) networkPercents[0].networkName else null,
                networkTrackerSummaryName2 = if (showSummary) networkPercents[1].networkName else null,
                networkTrackerSummaryName3 = if (showSummary) networkPercents[2].networkName else null,
                networkTrackerSummaryPercent1 = if (showSummary) networkPercents[0].percent else 0.0f,
                networkTrackerSummaryPercent2 = if (showSummary) networkPercents[1].percent else 0.0f,
                networkTrackerSummaryPercent3 = if (showSummary) networkPercents[2].percent else 0.0f
        )
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
                networkCount = 0,
                allTrackersBlocked = true,
                toggleEnabled = settingsStore.privacyOn,
                practices = TermsOfService.Practices.UNKNOWN,
                showNetworkTrackerSummary = false,
                networkTrackerSummaryName1 = null,
                networkTrackerSummaryName2 = null,
                networkTrackerSummaryName3 = null,
                networkTrackerSummaryPercent1 = 0f,
                networkTrackerSummaryPercent2 = 0f,
                networkTrackerSummaryPercent3 = 0f,
                shouldReloadPage = shouldReloadPage
        )
    }

    private fun updateSite(site: Site) {
        viewState.value = viewState.value?.copy(
                domain = site.uri?.host ?: "",
                beforeGrade = site.grade,
                afterGrade = site.improvedGrade,
                httpsStatus = site.https,
                networkCount = site.networkCount,
                allTrackersBlocked = site.allTrackersBlocked,
                practices = site.termsOfService.practices
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

}