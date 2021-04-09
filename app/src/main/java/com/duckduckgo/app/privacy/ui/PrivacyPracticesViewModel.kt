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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

class PrivacyPracticesViewModel : ViewModel() {

    data class ViewState(
        val domain: String,
        val practices: PrivacyPractices.Summary,
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
            practices = UNKNOWN,
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
            domain = site.domain ?: "",
            practices = site.privacyPractices.summary,
            goodTerms = site.privacyPractices.goodReasons,
            badTerms = site.privacyPractices.badReasons
        )
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class PrivacyPracticesViewModelFactory @Inject constructor() : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(PrivacyPracticesViewModel::class.java) -> (PrivacyPracticesViewModel() as T)
                else -> null
            }
        }
    }
}
