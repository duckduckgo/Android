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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(AppScope::class)
class ScorecardViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val userWhitelistDao: UserWhitelistDao,
    private val contentBlocking: ContentBlocking,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    data class ViewState(
        val domain: String = "",
        val beforeGrade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        val afterGrade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        val httpsStatus: HttpsStatus = HttpsStatus.SECURE,
        val trackerCount: Int = 0,
        val majorNetworkCount: Int = 0,
        val allTrackersBlocked: Boolean = true,
        val practices: PrivacyPractices.Summary = UNKNOWN,
        val privacyOn: Boolean = true,
        val showIsMemberOfMajorNetwork: Boolean = false,
        val showEnhancedGrade: Boolean = false,
        val isSiteInTempAllowedList: Boolean = false
    )

    fun scoreCard(tabId: String): StateFlow<ViewState> = flow {
        tabRepository.retrieveSiteData(tabId).asFlow().collect { site ->
            val domain = site.domain ?: ""
            val isWhitelisted = withContext(dispatchers.io()) { userWhitelistDao.contains(domain) }
            val isSiteAContentBlockingException = withContext(dispatchers.io()) { contentBlocking.isAnException(domain) }
            emit(updatedState(site, domain, isWhitelisted, isSiteAContentBlockingException))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    private fun updatedState(
        site: Site,
        domain: String,
        isWhitelisted: Boolean,
        isSiteAContentBlockingException: Boolean
    ): ViewState {
        val grades = site.calculateGrades()
        val grade = grades.grade
        val improvedGrade = grades.improvedGrade

        return ViewState(
            domain = domain,
            beforeGrade = grade,
            afterGrade = improvedGrade,
            trackerCount = site.trackerCount,
            majorNetworkCount = site.majorNetworkCount,
            httpsStatus = site.https,
            allTrackersBlocked = site.allTrackersBlocked,
            practices = site.privacyPractices.summary,
            privacyOn = !isWhitelisted && !isSiteAContentBlockingException,
            showIsMemberOfMajorNetwork = site.entity?.isMajor ?: false,
            showEnhancedGrade = grade != improvedGrade,
            isSiteInTempAllowedList = isSiteAContentBlockingException
        )
    }
}
