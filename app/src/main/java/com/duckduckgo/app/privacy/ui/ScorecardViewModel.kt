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

package com.duckduckgo.app.privacy.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import kotlinx.coroutines.launch

class ScorecardViewModel(
    private val userWhitelistDao: UserWhitelistDao,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    data class ViewState(
        val domain: String,
        val beforeGrade: PrivacyGrade,
        val afterGrade: PrivacyGrade,
        val httpsStatus: HttpsStatus,
        val trackerCount: Int,
        val majorNetworkCount: Int,
        val allTrackersBlocked: Boolean,
        val practices: PrivacyPractices.Summary,
        val privacyOn: Boolean,
        val showIsMemberOfMajorNetwork: Boolean,
        val showEnhancedGrade: Boolean
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    private var site: Site? = null

    init {
        resetViewState()
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
            majorNetworkCount = 0,
            allTrackersBlocked = true,
            practices = UNKNOWN,
            privacyOn = true,
            showIsMemberOfMajorNetwork = false,
            showEnhancedGrade = false
        )
    }

    private fun updateSite(site: Site) {
        val domain = site.domain ?: ""
        val grades = site.calculateGrades()
        val grade = grades.grade
        val improvedGrade = grades.improvedGrade

        viewModelScope.launch(dispatchers.io()) {
            viewState.postValue(
                viewState.value?.copy(
                    domain = domain,
                    beforeGrade = grade,
                    afterGrade = improvedGrade,
                    trackerCount = site.trackerCount,
                    majorNetworkCount = site.majorNetworkCount,
                    httpsStatus = site.https,
                    allTrackersBlocked = site.allTrackersBlocked,
                    practices = site.privacyPractices.summary,
                    privacyOn = !userWhitelistDao.contains(domain),
                    showIsMemberOfMajorNetwork = site.entity?.isMajor ?: false,
                    showEnhancedGrade = grade != improvedGrade
                )
            )
        }
    }
}