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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.TermsOfService

class PrivacyPracticesViewModel : ViewModel() {

    data class ViewState(
            val domain: String,
            val practices: TermsOfService.Practices,
            val goodTerms: List<String>,
            val badTerms: List<String>
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    init {
        resetViewState()
    }

    private fun resetViewState() {
        viewState.value = ViewState(
                domain = "",
                practices = TermsOfService.Practices.UNKNOWN,
                goodTerms = ArrayList(),
                badTerms = ArrayList()
        )
    }

    fun onSiteChanged(site: Site?) {
        if (site == null) {
            resetViewState()
            return
        }
        viewState.value = viewState.value?.copy(
                domain = site.uri?.host ?: "",
                practices = site.termsOfService.practices,
                goodTerms = site.termsOfService.goodPrivacyTerms,
                badTerms = site.termsOfService.badPrivacyTerms
        )
    }
}