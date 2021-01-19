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
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class SystemSearchResult(val autocomplete: AutoCompleteResult, val deviceApps: List<DeviceApp>)

class SystemSearchViewModel(
    private var userStageStore: UserStageStore,
    private val autoComplete: AutoComplete,
    private val deviceAppLookup: DeviceAppLookup,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    data class OnboardingViewState(
        val visible: Boolean,
        val expanded: Boolean = false
    )

    data class SystemSearchResultsViewState(
        val autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
        val appResults: List<DeviceApp> = emptyList()
    )

    sealed class Command {
        object ClearInputText : Command()
        object LaunchDuckDuckGo : Command()
        data class LaunchBrowser(val query: String) : Command()
        data class LaunchDeviceApplication(val deviceApp: DeviceApp) : Command()
        data class ShowAppNotFoundMessage(val appName: String) : Command()
        object DismissKeyboard : Command()
        data class EditQuery(val query: String) : Command()
    }

    val onboardingViewState: MutableLiveData<OnboardingViewState> = MutableLiveData()
    val resultsViewState: MutableLiveData<SystemSearchResultsViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val resultsPublishSubject = PublishRelay.create<String>()
    private var results = SystemSearchResult(AutoCompleteResult("", emptyList()), emptyList())
    private var resultsDisposable: Disposable? = null

    private var appsJob: Job? = null

    init {
        resetViewState()
        configureResults()
        refreshAppList()
    }

    private fun currentOnboardingState(): OnboardingViewState = onboardingViewState.value!!
    private fun currentResultsState(): SystemSearchResultsViewState = resultsViewState.value!!

    fun resetViewState() {
        command.value = Command.ClearInputText
        viewModelScope.launch {
            resetOnboardingState()
        }
        resetResultsState()
    }

    private suspend fun resetOnboardingState() {
        val showOnboarding = userStageStore.isNewUser()
        onboardingViewState.value = OnboardingViewState(visible = showOnboarding)
        if (showOnboarding) {
            pixel.fire(INTERSTITIAL_ONBOARDING_SHOWN)
        }
    }

    private fun resetResultsState() {
        results = SystemSearchResult(AutoCompleteResult("", emptyList()), emptyList())
        appsJob?.cancel()
        resultsViewState.value = SystemSearchResultsViewState()
    }

    private fun configureResults() {
        resultsDisposable = resultsPublishSubject
            .debounce(DEBOUNCE_TIME_MS, TimeUnit.MILLISECONDS)
            .switchMap { buildResultsObservable(query = it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    updateResults(result)
                },
                { t: Throwable? -> Timber.w(t, "Failed to get search results") }
            )
    }

    private fun buildResultsObservable(query: String): Observable<SystemSearchResult>? {
        return Observable.zip(
            autoComplete.autoComplete(query),
            Observable.just(deviceAppLookup.query(query)),
            BiFunction<AutoCompleteResult, List<DeviceApp>, SystemSearchResult> { autocompleteResult: AutoCompleteResult, appsResult: List<DeviceApp> ->
                SystemSearchResult(autocompleteResult, appsResult)
            }
        )
    }

    fun userTappedOnboardingToggle() {
        onboardingViewState.value = currentOnboardingState().copy(expanded = !currentOnboardingState().expanded)
        if (currentOnboardingState().expanded) {
            pixel.fire(INTERSTITIAL_ONBOARDING_MORE_PRESSED)
            command.value = Command.DismissKeyboard
        } else {
            pixel.fire(INTERSTITIAL_ONBOARDING_LESS_PRESSED)
        }
    }

    fun userDismissedOnboarding() {
        viewModelScope.launch {
            onboardingViewState.value = currentOnboardingState().copy(visible = false)
            userStageStore.stageCompleted(AppStage.NEW)
            pixel.fire(INTERSTITIAL_ONBOARDING_DISMISSED)
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = Command.EditQuery(query)
    }

    fun userUpdatedQuery(query: String) {
        appsJob?.cancel()

        if (query.isBlank()) {
            inputCleared()
            return
        }

        val trimmedQuery = query.trim()
        resultsPublishSubject.accept(trimmedQuery)
    }

    private fun updateResults(results: SystemSearchResult) {
        this.results = results

        val suggestions = results.autocomplete.suggestions
        val appResults = results.deviceApps
        val hasMultiResults = suggestions.isNotEmpty() && appResults.isNotEmpty()

        val updatedSuggestions = if (hasMultiResults) suggestions.take(RESULTS_MAX_RESULTS_PER_GROUP) else suggestions
        val updatedApps = if (hasMultiResults) appResults.take(RESULTS_MAX_RESULTS_PER_GROUP) else appResults

        resultsViewState.postValue(
            currentResultsState().copy(
                autocompleteResults = AutoCompleteResult(results.autocomplete.query, updatedSuggestions),
                appResults = updatedApps
            )
        )
    }

    private fun inputCleared() {
        resultsPublishSubject.accept("")
        resetResultsState()
    }

    fun userTappedDax() {
        viewModelScope.launch {
            userStageStore.stageCompleted(AppStage.NEW)
            pixel.fire(INTERSTITIAL_LAUNCH_DAX)
            command.value = Command.LaunchDuckDuckGo
        }
    }

    fun userRequestedClear() {
        command.value = Command.ClearInputText
        inputCleared()
    }

    fun userSubmittedQuery(query: String) {
        if (query.isBlank()) {
            return
        }

        viewModelScope.launch {
            userStageStore.stageCompleted(AppStage.NEW)
            command.value = Command.LaunchBrowser(query.trim())
            pixel.fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
        }
    }

    fun userSubmittedAutocompleteResult(query: String) {
        command.value = Command.LaunchBrowser(query)
        pixel.fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    fun userSelectedApp(app: DeviceApp) {
        command.value = Command.LaunchDeviceApplication(app)
        pixel.fire(INTERSTITIAL_LAUNCH_DEVICE_APP)
    }

    fun appNotFound(app: DeviceApp) {
        command.value = Command.ShowAppNotFoundMessage(app.shortName)

        refreshAppList()
    }

    private fun refreshAppList() {
        viewModelScope.launch(dispatchers.io()) {
            deviceAppLookup.refreshAppList()
        }
    }

    override fun onCleared() {
        resultsDisposable?.dispose()
        resultsDisposable = null
        super.onCleared()
    }

    companion object {
        private const val DEBOUNCE_TIME_MS = 200L
        private const val RESULTS_MAX_RESULTS_PER_GROUP = 4
    }
}
