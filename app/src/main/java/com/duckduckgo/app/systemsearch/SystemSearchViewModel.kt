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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SystemSearchViewModel(
    private val autoComplete: AutoComplete,
    private val deviceAppLookup: DeviceAppLookup,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    data class SystemSearchViewState(
        val queryText: String = "",
        val autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
        val appResults: List<DeviceApp> = emptyList()
    )

    sealed class Command {
        object LaunchDuckDuckGo : Command()
        data class LaunchBrowser(val query: String) : Command()
        data class LaunchDeviceApplication(val deviceApp: DeviceApp) : Command()
        data class ShowAppNotFoundMessage(val appName: String) : Command()
    }

    val viewState: MutableLiveData<SystemSearchViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private var autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList())

    private var appsJob: Job? = null
    private var appResults: List<DeviceApp> = emptyList()

    init {
        resetState()
        configureAutoComplete()
    }

    private fun currentViewState(): SystemSearchViewState = viewState.value!!

    fun resetState() {
        autocompleteResults = AutoCompleteResult("", emptyList())
        appsJob?.cancel()
        appResults = emptyList()
        viewState.value = SystemSearchViewState()
    }

    private fun configureAutoComplete() {
        autoCompletePublishSubject
            .debounce(DEBOUNCE_TIME_MS, TimeUnit.MILLISECONDS)
            .switchMap { autoComplete.autoComplete(it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                updateAutocompleteResult(result)
            }, { t: Throwable? -> Timber.w(t, "Failed to get search results") })
    }

    fun userUpdatedQuery(query: String) {

        appsJob?.cancel()

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

        appsJob = viewModelScope.launch(dispatchers.io()) {
            updateAppResults(deviceAppLookup.query(query))
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

    fun userTappedDax() {
        command.value = Command.LaunchDuckDuckGo
    }

    fun userClearedQuery() {
        autoCompletePublishSubject.accept("")
        resetState()
    }

    fun userSubmittedQuery(query: String) {
        command.value = Command.LaunchBrowser(query)
    }

    fun userSubmittedAutocompleteResult(query: String) {
        command.value = Command.LaunchBrowser(query)
    }

    fun userSelectedApp(app: DeviceApp) {
        command.value = Command.LaunchDeviceApplication(app)
    }

    fun appNotFound(app: DeviceApp) {
        command.value = Command.ShowAppNotFoundMessage(app.shortName)
        deviceAppLookup.refreshAppList()
    }

    companion object {
        private const val DEBOUNCE_TIME_MS = 200L
        private const val RESULTS_MAX_RESULTS_PER_GROUP = 4
    }
}
