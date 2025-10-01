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

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.BrowserViewModel.Command.DismissSetAsDefaultBrowserDialog
import com.duckduckgo.app.browser.BrowserViewModel.Command.DoNotAskAgainSetAsDefaultBrowserDialog
import com.duckduckgo.app.browser.BrowserViewModel.Command.LaunchTabSwitcher
import com.duckduckgo.app.browser.BrowserViewModel.Command.OpenDuckChat
import com.duckduckgo.app.browser.BrowserViewModel.Command.ShowUndoDeleteTabsMessage
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command.OpenMessageDialog
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultAppsActivity
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultBrowserDialog
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.tabs.TabManager.TabModel
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchFeature
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchOptionHandler
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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@OptIn(FlowPreview::class)
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
    private val skipUrlConversionOnNewTabFeature: SkipUrlConversionOnNewTabFeature,
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
    private val additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts,
    private val swipingTabsFeature: SwipingTabsFeatureProvider,
    private val duckChat: DuckChat,
) : ViewModel(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = dispatchers.main()

    data class ViewState(
        val hideWebContent: Boolean = true,
        private val isInEditMode: Boolean = false,
        private val isInFullScreenMode: Boolean = false,
    ) {
        val isTabSwipingEnabled: Boolean = !isInEditMode && !isInFullScreenMode
    }

    sealed class Command {
        data class Query(val query: String) : Command()
        data object LaunchPlayStore : Command()
        data object LaunchFeedbackView : Command()
        data object LaunchTabSwitcher : Command()
        data class ShowAppEnjoymentPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppRatingPrompt(val promptCount: PromptCount) : Command()
        data class ShowAppFeedbackPrompt(val promptCount: PromptCount) : Command()
        data class SwitchToTab(val tabId: String) : Command()
        data class OpenInNewTab(val url: String) : Command()
        data class OpenSavedSite(val url: String) : Command()
        data object ShowSetAsDefaultBrowserDialog : Command()
        data object DismissSetAsDefaultBrowserDialog : Command()
        data object DoNotAskAgainSetAsDefaultBrowserDialog : Command()
        data class ShowSystemDefaultBrowserDialog(val intent: Intent) : Command()
        data class ShowSystemDefaultAppsActivity(val intent: Intent) : Command()
        data class ShowUndoDeleteTabsMessage(val tabIds: List<String>) : Command()
        data class OpenDuckChat(val duckChatUrl: String?, val duckChatSessionActive: Boolean, val withTransition: Boolean) : Command()
    }

    var viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().also {
        it.value = ViewState()
    }

    private val currentViewState: ViewState
        get() = viewState.value!!

    var tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    var selectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    val selectedTabFlow: Flow<String> = tabRepository.flowSelectedTab
        .map { tab -> tab?.tabId }
        .filterNotNull()
        .distinctUntilChanged()
        .debounce(100)

    val tabsFlow: Flow<List<TabModel>> = tabRepository.flowTabs
        .map { tabs -> tabs.map { tab -> TabModel(tab.tabId, tab.url, tab.skipHome) } }
        .distinctUntilChanged()

    val selectedTabIndex: Flow<Int> = combine(tabsFlow, selectedTabFlow) { tabs, selectedTab ->
        tabs.indexOfFirst { it.tabId == selectedTab }
    }.filterNot { it == -1 }

    private var dataClearingObserver = Observer<ApplicationClearDataState> { state ->
        when (state) {
            ApplicationClearDataState.INITIALIZING -> {
                logcat(INFO) { "App clear state initializing" }
                viewState.value = currentViewState.copy(hideWebContent = true)
            }
            ApplicationClearDataState.FINISHED -> {
                logcat(INFO) { "App clear state finished" }
                viewState.value = currentViewState.copy(hideWebContent = false)
            }
        }
    }

    private val appEnjoymentObserver = Observer<AppEnjoymentPromptOptions> { promptType ->
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

    private var lastSystemDefaultAppsTrigger: SetAsDefaultActionTrigger = SetAsDefaultActionTrigger.UNKNOWN
    private var lastSystemDefaultBrowserDialogTrigger: SetAsDefaultActionTrigger = SetAsDefaultActionTrigger.UNKNOWN

    init {
        appEnjoymentPromptEmitter.promptType.observeForever(appEnjoymentObserver)
        viewModelScope.launch {
            additionalDefaultBrowserPrompts.commands.collect {
                when (it) {
                    OpenMessageDialog -> {
                        command.value = Command.ShowSetAsDefaultBrowserDialog
                    }

                    is OpenSystemDefaultAppsActivity -> {
                        lastSystemDefaultAppsTrigger = it.trigger
                        command.value = Command.ShowSystemDefaultAppsActivity(it.intent)
                    }

                    is OpenSystemDefaultBrowserDialog -> {
                        lastSystemDefaultBrowserDialogTrigger = it.trigger
                        command.value = Command.ShowSystemDefaultBrowserDialog(it.intent)
                    }
                }
            }
        }
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
        val url = if (skipUrlConversionOnNewTabFeature.self().isEnabled()) {
            query
        } else {
            queryUrlConverter.convertQueryToUrl(query)
        }

        return if (sourceTabId != null) {
            tabRepository.addFromSourceTab(
                url = url,
                skipHome = skipHome,
                sourceTabId = sourceTabId,
            )
        } else {
            tabRepository.add(
                url = url,
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
            logcat(INFO) { "Tabs list is null or empty; adding default tab" }
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
        if (swipingTabsFeature.isEnabled) {
            launch {
                val existingTab = tabRepository.getTabs().firstOrNull { tab -> tab.url == url }
                if (existingTab == null) {
                    command.value = Command.OpenSavedSite(url)
                } else {
                    command.value = Command.SwitchToTab(existingTab.tabId)
                }
            }
        } else {
            command.value = Command.OpenSavedSite(url)
        }
    }

    fun onTabSelected(tabId: String) {
        launch(dispatchers.io()) {
            tabRepository.select(tabId)
        }
    }

    fun handleShowOnAppLaunchOption() {
        if (showOnAppLaunchFeature.self().isEnabled()) {
            viewModelScope.launch {
                showOnAppLaunchOptionHandler.handleAppLaunchOption()
            }
        }
    }

    fun onTabActivated(tabId: String) {
        viewModelScope.launch(dispatchers.io()) {
            tabRepository.updateTabLastAccess(tabId)
        }
    }

    fun onSetDefaultBrowserDialogShown() {
        additionalDefaultBrowserPrompts.onMessageDialogShown()
    }

    fun onSetDefaultBrowserDialogCanceled() {
        command.value = DismissSetAsDefaultBrowserDialog
        additionalDefaultBrowserPrompts.onMessageDialogCanceled()
    }

    fun onSetDefaultBrowserConfirmationButtonClicked() {
        command.value = DismissSetAsDefaultBrowserDialog
        additionalDefaultBrowserPrompts.onMessageDialogConfirmationButtonClicked()
    }

    fun onSetDefaultBrowserDoNotAskAgainButtonClicked() {
        command.value = DoNotAskAgainSetAsDefaultBrowserDialog
        additionalDefaultBrowserPrompts.onMessageDialogDoNotAskAgainButtonClicked()
    }

    fun onSystemDefaultBrowserDialogShown() {
        additionalDefaultBrowserPrompts.onSystemDefaultBrowserDialogShown()
    }

    fun onSystemDefaultBrowserDialogSuccess() {
        additionalDefaultBrowserPrompts.onSystemDefaultBrowserDialogSuccess(lastSystemDefaultBrowserDialogTrigger)
    }

    fun onSystemDefaultBrowserDialogCanceled() {
        additionalDefaultBrowserPrompts.onSystemDefaultBrowserDialogCanceled(lastSystemDefaultBrowserDialogTrigger)
    }

    fun onSystemDefaultAppsActivityClosed() {
        additionalDefaultBrowserPrompts.onSystemDefaultAppsActivityClosed(lastSystemDefaultAppsTrigger)
    }

    fun onTabsSwiped() {
        pixel.fire(AppPixelName.SWIPE_TABS_USED)
        pixel.fire(pixel = AppPixelName.SWIPE_TABS_USED_DAILY, type = Daily())
    }

    fun onOmnibarEditModeChanged(isInEditMode: Boolean) {
        viewState.value = currentViewState.copy(isInEditMode = isInEditMode)
    }

    fun onFullScreenModeChanged(isFullScreen: Boolean) {
        viewState.value = currentViewState.copy(isInFullScreenMode = isFullScreen)
    }

    // user has not tapped the Undo action -> purge the deletable tabs and remove all data
    fun purgeDeletableTabs() {
        viewModelScope.launch {
            tabRepository.purgeDeletableTabs()
        }
    }

    // user has tapped the Undo action -> restore the closed tabs
    fun undoDeletableTabs(tabIds: List<String>) {
        viewModelScope.launch {
            tabRepository.undoDeletable(tabIds, moveActiveTabToEnd = true)
            command.value = LaunchTabSwitcher
        }
    }

    fun onTabsDeletedInTabSwitcher(tabIds: List<String>) {
        command.value = ShowUndoDeleteTabsMessage(tabIds)
    }

    fun openDuckChat(duckChatUrl: String?, duckChatSessionActive: Boolean, withTransition: Boolean) {
        logcat(INFO) { "Duck.ai openDuckChat duckChatSessionActive $duckChatSessionActive" }
        command.value = OpenDuckChat(duckChatUrl, duckChatSessionActive, withTransition)
    }
}

/**
 * Feature flag to skip converting the query to a URL when opening a new tab
 * This is for fixing https://app.asana.com/0/1208134428464537/1207998553475892/f
 *
 * In case of unexpected side-effects, the old behaviour can be reverted by disabling this remote feature flag
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidSkipUrlConversionOnNewTab",
)
interface SkipUrlConversionOnNewTabFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
