/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORDS_SEARCH_INPUT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_PASSWORD_SELECTED
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.searching.CredentialListFilter
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.LogPriority.*
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class AutofillProviderCredentialsListViewModel @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val credentialListFilter: CredentialListFilter,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private var searchQueryFilter = MutableStateFlow("")

    private var combineJob: Job? = null

    private var hasPreviouslySearched = false

    fun onViewCreated() {
        if (combineJob != null) return
        combineJob = viewModelScope.launch(dispatchers.io()) {
            val allCredentials = autofillStore.getAllCredentials().distinctUntilChanged()
            val combined = allCredentials.combine(searchQueryFilter) { credentials, filter ->
                credentialListFilter.filter(credentials, filter)
            }
            combined.collect { credentials ->
                _viewState.value = _viewState.value.copy(
                    logins = credentials,
                )
            }
        }
    }

    fun onSearchQueryChanged(searchText: String) {
        if (!hasPreviouslySearched) {
            pixel.fire(AUTOFILL_SERVICE_PASSWORDS_SEARCH_INPUT)
            hasPreviouslySearched = true
        }
        logcat(VERBOSE) { "Search query changed: $searchText" }
        searchQueryFilter.value = searchText
        _viewState.value = _viewState.value.copy(credentialSearchQuery = searchText)
    }

    fun onCredentialSelected(credentials: LoginCredentials) {
        pixel.fire(AUTOFILL_SERVICE_PASSWORD_SELECTED)
        credentials.updateLastUsedTimestamp()
    }

    private fun LoginCredentials.updateLastUsedTimestamp() {
        appCoroutineScope.launch(dispatchers.io()) {
            val updated = this@updateLastUsedTimestamp.copy(lastUsedMillis = System.currentTimeMillis())
            autofillStore.updateCredentials(updated, refreshLastUpdatedTimestamp = false)
        }
    }

    data class ViewState(
        val logins: List<LoginCredentials>? = null,
        val credentialSearchQuery: String = "",
    )
}
