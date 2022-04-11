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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@ContributesViewModel(AppScope::class)
class PrivacyPracticesViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel() {

    data class ViewState(
        val domain: String = "",
        val practices: PrivacyPractices.Summary = UNKNOWN,
        val goodTerms: List<String> = emptyList(),
        val badTerms: List<String> = emptyList()
    )

    fun privacyPractices(tabId: String): StateFlow<ViewState> = flow {
        tabRepository.retrieveSiteData(tabId).asFlow().collect { site ->
            emit(
                ViewState(
                    domain = site.domain ?: "",
                    practices = site.privacyPractices.summary,
                    goodTerms = site.privacyPractices.goodReasons,
                    badTerms = site.privacyPractices.badReasons
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())
}
