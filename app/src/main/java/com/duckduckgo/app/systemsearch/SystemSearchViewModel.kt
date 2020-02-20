/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteResult
import com.duckduckgo.app.global.SingleLiveEvent
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SystemSearchViewModel(
    private val autoCompleteApi: AutoCompleteApi,
    private val deviceAppsLookup: DeviceAppsLookup
) : ViewModel() {

    data class SystemSearchViewState(
        val queryText: String = "",
        val autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
        val appResults: List<DeviceApp> = emptyList()
    )

    sealed class Command {
        data class LaunchBrowser(val query: String) : Command()
        data class LaunchApplication(val intent: Intent) : Command()
    }

    val viewState: MutableLiveData<SystemSearchViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private var appResults: List<DeviceApp> = emptyList()
    private var autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList())

    init {
        resetState()
        configureAutoComplete()
    }

    private fun currentViewState(): SystemSearchViewState = viewState.value!!

    fun resetState() {
        viewState.value = SystemSearchViewState()
        autocompleteResults = AutoCompleteResult("", emptyList())
        appResults = emptyList()
    }

    private fun configureAutoComplete() {
        autoCompletePublishSubject
            .debounce(DEBOUNCE_TIME_MS, TimeUnit.MILLISECONDS)
            .switchMap { autoCompleteApi.autoComplete(it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                updateAutocompleteResult(result)
            }, { t: Throwable? -> Timber.w(t, "Failed to get search results") })
    }

    fun userUpdatedQuery(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery == currentViewState().queryText) {
            return
        }

        if (trimmedQuery.isBlank()) {
            userClearedQuery()
            return
        }

        viewState.value = currentViewState().copy(queryText = trimmedQuery)
        autoCompletePublishSubject.accept(trimmedQuery)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                updateAppResults(deviceAppsLookup.query(query))
            }
        }
    }

    private fun updateAppResults(results: List<DeviceApp>) {
        appResults = results
        refreshViewStateResults()
    }

    private fun updateAutocompleteResult(results: AutoCompleteResult) {
        autocompleteResults = results
        refreshViewStateResults()
    }

    private fun refreshViewStateResults() {
        val hasMultiResults = autocompleteResults.suggestions.isNotEmpty() && appResults.isNotEmpty()
        val fullSuggestions = autocompleteResults.suggestions
        val updatedSuggestions = if (hasMultiResults) fullSuggestions.take(RESULTS_MAX_RESULTS_PER_GROUP) else fullSuggestions
        val updatedApps = if (hasMultiResults) appResults.take(RESULTS_MAX_RESULTS_PER_GROUP) else appResults

        viewState.postValue(
            currentViewState().copy(
                autocompleteResults = AutoCompleteResult(autocompleteResults.query, updatedSuggestions),
                appResults = updatedApps
            )
        )
    }

    fun userClearedQuery() {
        autoCompletePublishSubject.accept("")
        resetState()
    }

    fun userSubmittedQuery(query: String) {
        command.value = Command.LaunchBrowser(query)
    }

    fun userSelectedApp(app: DeviceApp) {
        command.value = Command.LaunchApplication(app.launchIntent)
    }

    companion object {
        private const val DEBOUNCE_TIME_MS = 200L
        private const val RESULTS_MAX_RESULTS_PER_GROUP = 4
    }
}
