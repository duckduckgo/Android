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

package com.duckduckgo.app.browser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.browser.rating.ui.AppEnjoymentDialogFragment
import com.duckduckgo.app.browser.rating.ui.GiveFeedbackDialogFragment
import com.duckduckgo.app.browser.rating.ui.RateAppDialogFragment
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.events.db.AppUserEventsStore
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions
import com.duckduckgo.app.global.rating.AppEnjoymentUserEventRecorder
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity.Companion.RELOAD_RESULT_CODE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val dataClearer: DataClearer,
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val appEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val pixel: Pixel
) : AppEnjoymentDialogFragment.Listener,
    RateAppDialogFragment.Listener,
    GiveFeedbackDialogFragment.Listener,
    ViewModel(),
    CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    data class ViewState(
        val hideWebContent: Boolean = true
    )

    sealed class Command {
        object Refresh : Command()
        data class Query(val query: String) : Command()
        object LaunchPlayStore : Command()
        object LaunchFeedbackView : Command()
        data class ShowAppEnjoymentPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppRatingPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppFeedbackPrompt(val promptCount: PromptCount) : Command()
    }

    var viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().also {
        it.value = ViewState()
    }

    private val currentViewState: ViewState
        get() = viewState.value!!

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    var selectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private var dataClearingObserver = Observer<ApplicationClearDataState> {
        it?.let { state ->
            when (state) {
                ApplicationClearDataState.INITIALIZING -> {
                    Timber.i("App clear state initializing")
                    viewState.value = currentViewState.copy(hideWebContent = true)
                }
                ApplicationClearDataState.FINISHED -> {
                    Timber.i("App clear state finished")
                    viewState.value = currentViewState.copy(hideWebContent = false)
                }
            }
        }
    }

    private val appEnjoymentObserver = Observer<AppEnjoymentPromptOptions> {
        it?.let { promptType ->
            when (promptType) {
                is AppEnjoymentPromptOptions.ShowEnjoymentPrompt -> {
                    command.value = Command.ShowAppEnjoymentPrompt(promptType.promptCount)
                }
                is AppEnjoymentPromptOptions.ShowRatingPrompt -> {
                    command.value = Command.ShowAppRatingPrompt(promptType.promptCount)
                }
                is AppEnjoymentPromptOptions.ShowFeedbackPrompt -> {
                    command.value = Command.ShowAppFeedbackPrompt(promptType.promptCount)
                }
            }
        }
    }

    init {
        appEnjoymentPromptEmitter.promptType.observeForever(appEnjoymentObserver)
    }

    suspend fun onNewTabRequested(sourceTabId: String? = null): String {
        return if (sourceTabId != null) {
            tabRepository.addFromSourceTab(sourceTabId = sourceTabId)
        } else {
            tabRepository.add()
        }
    }

    suspend fun onOpenInNewTabRequested(
        query: String,
        sourceTabId: String? = null,
        skipHome: Boolean = false
    ): String {
        return if (sourceTabId != null) {
            tabRepository.addFromSourceTab(
                url = queryUrlConverter.convertQueryToUrl(query),
                skipHome = skipHome,
                sourceTabId = sourceTabId
            )
        } else {
            tabRepository.add(
                url = queryUrlConverter.convertQueryToUrl(query),
                skipHome = skipHome
            )
        }
    }

    suspend fun onOpenFavoriteFromWidget(query: String) {
        pixel.fire(AppPixelName.APP_FAVORITES_ITEM_WIDGET_LAUNCH)
        tabRepository.selectByUrlOrNewTab(queryUrlConverter.convertQueryToUrl(query))
    }

    suspend fun onTabsUpdated(tabs: List<TabEntity>?) {
        if (tabs.isNullOrEmpty()) {
            Timber.i("Tabs list is null or empty; adding default tab")
            tabRepository.addDefaultTab()
            return
        }
    }

    fun receivedDashboardResult(resultCode: Int) {
        if (resultCode == RELOAD_RESULT_CODE) command.value = Refresh
    }

    /**
     * To ensure the best UX, we might not want to show anything to the user while the clear is taking place.
     * This method will await until the ApplicationClearDataState.FINISHED event is received before observing for other changes
     * The effect of this delay is that we won't show old tabs if they are in the process of deleting them.
     */
    fun awaitClearDataFinishedNotification() {
        dataClearer.dataClearerState.observeForever(dataClearingObserver)
    }

    override fun onCleared() {
        super.onCleared()
        dataClearer.dataClearerState.removeObserver(dataClearingObserver)
        appEnjoymentPromptEmitter.promptType.removeObserver(appEnjoymentObserver)
    }

    override fun onUserSelectedAppIsEnjoyed(promptCount: PromptCount) {
        appEnjoymentUserEventRecorder.onUserEnjoyingApp(promptCount)
    }

    override fun onUserSelectedAppIsNotEnjoyed(promptCount: PromptCount) {
        appEnjoymentUserEventRecorder.onUserNotEnjoyingApp(promptCount)
    }

    override fun onUserSelectedToRateApp(promptCount: PromptCount) {
        command.value = Command.LaunchPlayStore

        launch { appEnjoymentUserEventRecorder.onUserSelectedToRateApp(promptCount) }
    }

    override fun onUserDeclinedToRateApp(promptCount: PromptCount) {
        launch { appEnjoymentUserEventRecorder.userDeclinedToRateApp(promptCount) }
    }

    override fun onUserSelectedToGiveFeedback(promptCount: PromptCount) {
        command.value = Command.LaunchFeedbackView

        launch { appEnjoymentUserEventRecorder.onUserSelectedToGiveFeedback(promptCount) }
    }

    override fun onUserDeclinedToGiveFeedback(promptCount: PromptCount) {
        launch { appEnjoymentUserEventRecorder.onUserDeclinedToGiveFeedback(promptCount) }
    }

    override fun onUserCancelledAppEnjoymentDialog(promptCount: PromptCount) {
        launch { appEnjoymentUserEventRecorder.onUserDeclinedToSayIfEnjoyingApp(promptCount) }
    }

    override fun onUserCancelledRateAppDialog(promptCount: PromptCount) {
        onUserDeclinedToRateApp(promptCount)
    }

    override fun onUserCancelledGiveFeedbackDialog(promptCount: PromptCount) {
        onUserDeclinedToGiveFeedback(promptCount)
    }

    fun onOpenShortcut(url: String) {
        launch(dispatchers.io()) {
            tabRepository.selectByUrlOrNewTab(queryUrlConverter.convertQueryToUrl(url))
            pixel.fire(AppPixelName.SHORTCUT_OPENED)
        }
    }
}

@ContributesMultibinding(AppScope::class)
class BrowserViewModelFactory @Inject constructor(
    val tabRepository: Provider<TabRepository>,
    val queryUrlConverter: Provider<QueryUrlConverter>,
    val dataClearer: Provider<DataClearer>,
    val appEnjoymentPromptEmitter: Provider<AppEnjoymentPromptEmitter>,
    val appEnjoymentUserEventRecorder: Provider<AppEnjoymentUserEventRecorder>,
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    val pixel: Provider<Pixel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BrowserViewModel::class.java) -> BrowserViewModel(
                    tabRepository.get(),
                    queryUrlConverter.get(),
                    dataClearer.get(),
                    appEnjoymentPromptEmitter.get(),
                    appEnjoymentUserEventRecorder.get(),
                    dispatchers,
                    pixel.get()
                ) as T
                else -> null
            }
        }
    }
}
