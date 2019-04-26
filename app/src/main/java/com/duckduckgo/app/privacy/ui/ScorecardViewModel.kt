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
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.privacy.store.PrivacySettingsStore

class ScorecardViewModel(private val settingsStore: PrivacySettingsStore) : ViewModel() {

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
            privacyOn = settingsStore.privacyOn,
            showIsMemberOfMajorNetwork = false,
            showEnhancedGrade = false
        )
    }

    private fun updateSite(site: Site) {
        val grades = site.calculateGrades()
        val grade = grades.grade
        val improvedGrade = grades.improvedGrade

        viewState.value = viewState.value?.copy(
            domain = site.uri?.host ?: "",
            beforeGrade = grade,
            afterGrade = improvedGrade,
            trackerCount = site.trackerCount,
            majorNetworkCount = site.majorNetworkCount,
            httpsStatus = site.https,
            allTrackersBlocked = site.allTrackersBlocked,
            practices = site.privacyPractices.summary,
            showIsMemberOfMajorNetwork = site.memberNetwork?.isMajor ?: false,
            showEnhancedGrade = grade != improvedGrade
        )
    }
}