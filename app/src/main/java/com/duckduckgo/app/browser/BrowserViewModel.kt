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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.global.ApplicationClearDataState
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions
import com.duckduckgo.app.global.rating.AppEnjoymentUserEventRecorder
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.APP_ENJOYMENT_DIALOG_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.APP_ENJOYMENT_DIALOG_USER_CANCELLED
import com.duckduckgo.app.pixels.AppPixelName.APP_ENJOYMENT_DIALOG_USER_ENJOYING
import com.duckduckgo.app.pixels.AppPixelName.APP_ENJOYMENT_DIALOG_USER_NOT_ENJOYING
import com.duckduckgo.app.pixels.AppPixelName.APP_FEEDBACK_DIALOG_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.APP_FEEDBACK_DIALOG_USER_CANCELLED
import com.duckduckgo.app.pixels.AppPixelName.APP_FEEDBACK_DIALOG_USER_DECLINED_FEEDBACK
import com.duckduckgo.app.pixels.AppPixelName.APP_FEEDBACK_DIALOG_USER_GAVE_FEEDBACK
import com.duckduckgo.app.pixels.AppPixelName.APP_RATING_DIALOG_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.APP_RATING_DIALOG_USER_CANCELLED
import com.duckduckgo.app.pixels.AppPixelName.APP_RATING_DIALOG_USER_DECLINED_RATING
import com.duckduckgo.app.pixels.AppPixelName.APP_RATING_DIALOG_USER_GAVE_RATING
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class BrowserViewModel @Inject constructor(
    private val tabRepository: TabRepository,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val dataClearer: DataClearer,
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val appEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel(),
    CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = dispatchers.main()

    data class ViewState(
        val hideWebContent: Boolean = true,
    )

    sealed class Command {
        data class Query(val query: String) : Command()
        object LaunchPlayStore : Command()
        object LaunchFeedbackView : Command()
        data class ShowAppEnjoymentPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppRatingPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppFeedbackPrompt(val promptCount: PromptCount) : Command()
        data class OpenSavedSite(val url: String) : Command()
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
                else -> {}
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
        skipHome: Boolean = false,
    ): String {
        return if (sourceTabId != null) {
            tabRepository.addFromSourceTab(
                url = queryUrlConverter.convertQueryToUrl(query),
                skipHome = skipHome,
                sourceTabId = sourceTabId,
            )
        } else {
            tabRepository.add(
                url = queryUrlConverter.convertQueryToUrl(query),
                skipHome = skipHome,
            )
        }
    }

    suspend fun onOpenFavoriteFromWidget(query: String) {
        pixel.fire(AppPixelName.APP_FAVORITES_ITEM_WIDGET_LAUNCH)
        tabRepository.selectByUrlOrNewTab(queryUrlConverter.convertQueryToUrl(query))
    }

    fun launchFromThirdParty() {
        pixel.fire(
            AppPixelName.APP_THIRD_PARTY_LAUNCH,
            mapOf(PixelParameter.DEFAULT_BROWSER to defaultBrowserDetector.isDefaultBrowser().toString()),
        )
    }

    suspend fun onTabsUpdated(tabs: List<TabEntity>?) {
        if (tabs.isNullOrEmpty()) {
            Timber.i("Tabs list is null or empty; adding default tab")
            tabRepository.addDefaultTab()
            return
        }
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

    private fun firePixelWithPromptCount(name: Pixel.PixelName, promptCount: PromptCount) {
        val formattedPixelName = String.format(name.pixelName, promptCount.value)
        pixel.fire(formattedPixelName)
    }

    fun onAppEnjoymentDialogShown(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_SHOWN, promptCount)
    }

    fun onAppRatingDialogShown(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_RATING_DIALOG_SHOWN, promptCount)
    }

    fun onGiveFeedbackDialogShown(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_FEEDBACK_DIALOG_SHOWN, promptCount)
    }

    fun onUserSelectedAppIsEnjoyed(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_ENJOYING, promptCount)
        appEnjoymentUserEventRecorder.onUserEnjoyingApp(promptCount)
    }

    fun onUserSelectedAppIsNotEnjoyed(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_NOT_ENJOYING, promptCount)
        appEnjoymentUserEventRecorder.onUserNotEnjoyingApp(promptCount)
    }

    fun onUserSelectedToRateApp(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_RATING_DIALOG_USER_GAVE_RATING, promptCount)
        command.value = Command.LaunchPlayStore

        launch { appEnjoymentUserEventRecorder.onUserSelectedToRateApp(promptCount) }
    }

    fun onUserDeclinedToRateApp(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_RATING_DIALOG_USER_DECLINED_RATING, promptCount)
        launch { appEnjoymentUserEventRecorder.userDeclinedToRateApp(promptCount) }
    }

    fun onUserCancelledRateAppDialog(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_RATING_DIALOG_USER_CANCELLED, promptCount)
        launch { appEnjoymentUserEventRecorder.userDeclinedToRateApp(promptCount) }
    }

    fun onUserSelectedToGiveFeedback(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_GAVE_FEEDBACK, promptCount)
        command.value = Command.LaunchFeedbackView

        launch { appEnjoymentUserEventRecorder.onUserSelectedToGiveFeedback(promptCount) }
    }

    fun onUserDeclinedToGiveFeedback(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_DECLINED_FEEDBACK, promptCount)
        launch { appEnjoymentUserEventRecorder.onUserDeclinedToGiveFeedback(promptCount) }
    }

    fun onUserCancelledGiveFeedbackDialog(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_FEEDBACK_DIALOG_USER_CANCELLED, promptCount)
        launch { appEnjoymentUserEventRecorder.onUserDeclinedToGiveFeedback(promptCount) }
    }

    fun onUserCancelledAppEnjoymentDialog(promptCount: PromptCount) {
        firePixelWithPromptCount(APP_ENJOYMENT_DIALOG_USER_CANCELLED, promptCount)
        launch { appEnjoymentUserEventRecorder.onUserDeclinedToSayIfEnjoyingApp(promptCount) }
    }

    fun onOpenShortcut(url: String) {
        launch(dispatchers.io()) {
            tabRepository.selectByUrlOrNewTab(queryUrlConverter.convertQueryToUrl(url))
            pixel.fire(AppPixelName.SHORTCUT_OPENED)
        }
    }

    fun onLaunchedFromNotification(pixelName: String) {
        pixel.fire(pixelName)
    }

    fun onBookmarksActivityResult(url: String) {
        command.value = Command.OpenSavedSite(url)
    }
}
