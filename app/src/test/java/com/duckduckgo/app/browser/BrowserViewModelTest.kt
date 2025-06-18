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

import android.annotation.SuppressLint
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.fire.DataClearer
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchFeature
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchOptionHandler
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions
import com.duckduckgo.app.global.rating.AppEnjoymentUserEventRecorder
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.tabs.SwipingTabsFeature
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class BrowserViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule var coroutinesTestRule = CoroutineTestRule()

    @Mock private lateinit var mockCommandObserver: Observer<Command>

    private val commandCaptor = argumentCaptor<Command>()

    @Mock private lateinit var mockTabRepository: TabRepository

    @Mock private lateinit var mockOmnibarEntryConverter: OmnibarEntryConverter

    @Mock private lateinit var mockAutomaticDataClearer: DataClearer

    @Mock private lateinit var mockAppEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder

    @Mock private lateinit var mockAppEnjoymentPromptEmitter: AppEnjoymentPromptEmitter

    @Mock private lateinit var mockPixel: Pixel

    @Mock private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock private lateinit var showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler

    private val defaultBrowserPromptsExperimentCommandsFlow = Channel<DefaultBrowserPromptsExperiment.Command>(capacity = Channel.CONFLATED)

    @Mock private lateinit var mockDefaultBrowserPromptsExperiment: DefaultBrowserPromptsExperiment

    private val fakeShowOnAppLaunchFeatureToggle = FakeFeatureToggleFactory.create(ShowOnAppLaunchFeature::class.java)

    private lateinit var testee: BrowserViewModel

    private val skipUrlConversionOnNewTabFeature = FakeFeatureToggleFactory.create(SkipUrlConversionOnNewTabFeature::class.java)

    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)

    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        doReturn(MutableLiveData<AppEnjoymentPromptOptions>()).whenever(mockAppEnjoymentPromptEmitter).promptType

        configureSkipUrlConversionInNewTabState(enabled = true)
        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))

        whenever(mockDefaultBrowserPromptsExperiment.commands).thenReturn(defaultBrowserPromptsExperimentCommandsFlow.receiveAsFlow())

        initTestee()

        testee.command.observeForever(mockCommandObserver)

        runTest {
            whenever(mockTabRepository.add()).thenReturn(TAB_ID)
            whenever(mockOmnibarEntryConverter.convertQueryToUrl(any(), any(), any())).then { it.arguments.first() }
        }
    }

    @After
    fun after() {
        if (this::testee.isInitialized) {
            testee.command.removeObserver(mockCommandObserver)
        }
    }

    @Test
    fun whenNewTabRequestedThenTabAddedToRepository() = runTest {
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onNewTabRequested()
        verify(mockTabRepository).add()
    }

    @Test
    fun whenNewTabRequestedFromSourceTabThenTabAddedToRepositoryWithSourceTabId() = runTest {
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onNewTabRequested("sourceTabId")
        verify(mockTabRepository).addFromSourceTab(sourceTabId = "sourceTabId")
    }

    @Test
    fun whenOpenInNewTabRequestedThenTabAddedToRepository() = runTest {
        val url = "http://example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onOpenInNewTabRequested(url, null, false)
        verify(mockTabRepository).add(url = url, skipHome = false)
    }

    @Test
    fun whenOpenInNewTabRequestedWithSourceTabIdThenTabAddedToRepositoryWithSourceTabId() = runTest {
        val url = "http://example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        whenever(mockTabRepository.liveSelectedTab).doReturn(MutableLiveData())
        testee.onOpenInNewTabRequested(url, sourceTabId = "tabId", skipHome = false)
        verify(mockTabRepository).addFromSourceTab(url = url, skipHome = false, sourceTabId = "tabId")
    }

    @Test
    fun whenTabsUpdatedAndNoTabsThenDefaultTabAddedToRepository() = runTest {
        testee.onTabsUpdated(listOf())
        verify(mockTabRepository).addDefaultTab()
    }

    @Test
    fun whenTabsUpdatedWithTabsThenNewTabNotLaunched() = runTest {
        testee.onTabsUpdated(listOf(TabEntity("123")))
        verify(mockCommandObserver, never()).onChanged(any())
    }

    @Test
    fun whenUserSelectedToRateAppThenPlayStoreCommandTriggered() {
        testee.onUserSelectedToRateApp(PromptCount.first())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchPlayStore, commandCaptor.lastValue)
    }

    @Test
    fun whenUserSelectedToGiveFeedbackThenFeedbackCommandTriggered() {
        testee.onUserSelectedToGiveFeedback(PromptCount.first())
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchFeedbackView, commandCaptor.lastValue)
    }

    @Test
    fun whenViewStateCreatedThenWebViewContentShouldBeHidden() {
        assertTrue(testee.viewState.value!!.hideWebContent)
    }

    @Test
    fun whenOpenShortcutThenSelectByUrlOrNewTab() = runTest {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenShortcut(url)
        verify(mockTabRepository).selectByUrlOrNewTab(url)
    }

    @Test
    fun whenOpenShortcutThenFirePixel() {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenShortcut(url)
        verify(mockPixel).fire(AppPixelName.SHORTCUT_OPENED)
    }

    @Test
    fun whenTabsSwipedThenFireSwipingUsedPixels() = runTest {
        testee.onTabsSwiped()
        verify(mockPixel).fire(AppPixelName.SWIPE_TABS_USED)
        verify(mockPixel).fire(AppPixelName.SWIPE_TABS_USED_DAILY, type = Daily())
    }

    @Test
    fun whenOpenFavoriteThenSelectByUrlOrNewTab() = runTest {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenFavoriteFromWidget(url)
        verify(mockTabRepository).selectByUrlOrNewTab(url)
    }

    @Test
    fun whenOpenFavoriteFromWidgetThenFirePixel() = runTest {
        val url = "example.com"
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(url)).thenReturn(url)
        testee.onOpenFavoriteFromWidget(url)
        verify(mockPixel).fire(AppPixelName.APP_FAVORITES_ITEM_WIDGET_LAUNCH)
    }

    @Test
    fun whenOpenFromThirdPartyAndNotDefaultBrowserThenFirePixel() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.launchFromThirdParty()
        verify(mockPixel).fire(
            AppPixelName.APP_THIRD_PARTY_LAUNCH,
            mapOf(PixelParameter.DEFAULT_BROWSER to "false"),
        )
    }

    @Test
    fun whenOpenFromThirdPartyAndDefaultBrowserThenFirePixel() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.launchFromThirdParty()
        verify(mockPixel).fire(
            AppPixelName.APP_THIRD_PARTY_LAUNCH,
            mapOf(PixelParameter.DEFAULT_BROWSER to "true"),
        )
    }

    @Test
    fun whenOnLaunchedFromNotificationCalledWithPixelNameThePixelFired() {
        val pixelName = "pixel_name"
        testee.onLaunchedFromNotification(pixelName)

        verify(mockPixel).fire(pixelName)
    }

    @Test
    fun whenOnBookmarksActivityResultCalledAndSiteAlreadyOpenedThenSwitchToTabCommandTriggered() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val bookmarkUrl = "https://www.example.com"
        val tab = TabEntity("123", url = bookmarkUrl)

        whenever(mockTabRepository.getTabs()).thenReturn(listOf(tab))

        testee.onBookmarksActivityResult(bookmarkUrl)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.SwitchToTab(tab.tabId), commandCaptor.lastValue)
    }

    @Test
    fun whenOnBookmarksActivityResultCalledAndSiteNotOpenedThenOpenInNewTabCommandTriggered() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val bookmarkUrl = "https://www.example.com"
        val tab = TabEntity("123", url = "https://cnn.com")

        whenever(mockTabRepository.getTabs()).thenReturn(listOf(tab))

        testee.onBookmarksActivityResult(bookmarkUrl)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.OpenInNewTab(bookmarkUrl), commandCaptor.lastValue)
    }

    @Test
    fun whenOpenInNewTabWithSkipUrlConversionEnabledThenQueryNotConverted() = runTest {
        configureSkipUrlConversionInNewTabState(enabled = true)
        testee.onOpenInNewTabRequested(query = "query", sourceTabId = null, skipHome = false)
        verify(mockOmnibarEntryConverter, never()).convertQueryToUrl("query")
    }

    @Test
    fun whenOpenInNewTabWithSkipUrlConversionDisabledThenQueryConverted() = runTest {
        configureSkipUrlConversionInNewTabState(enabled = false)
        testee.onOpenInNewTabRequested(query = "query", sourceTabId = null, skipHome = false)
        verify(mockOmnibarEntryConverter).convertQueryToUrl("query")
    }

    @Test
    fun whenOnTabSelectedCalledWithTabIdThenSelectTabWithTheSameId() = runTest {
        val tabId = "tabId"

        testee.onTabSelected(tabId)

        verify(mockTabRepository).select(tabId)
    }

    @Test
    fun whenHandleShowOnAppLaunchCalledThenNoTabIsAddedByDefault() = runTest {
        testee.handleShowOnAppLaunchOption()

        verify(mockTabRepository, never()).add()
        verify(mockTabRepository, never()).addFromSourceTab(url = any(), skipHome = any(), sourceTabId = any())
        verify(mockTabRepository, never()).addDefaultTab()
    }

    @Test
    fun whenShowOnAppLaunchFeatureToggleIsOnThenShowOnAppLaunchHandled() = runTest {
        fakeShowOnAppLaunchFeatureToggle.self().setRawStoredState(State(enable = true))

        testee.handleShowOnAppLaunchOption()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun `when default browser prompts experiment OpenMessageDialog command, then propagate it to consumers`() = runTest {
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenMessageDialog)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowSetAsDefaultBrowserDialog, commandCaptor.lastValue)
    }

    @Test
    fun `when default browser prompts experiment OpenSystemDefaultBrowserDialog command, then propagate it to consumers`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowSystemDefaultBrowserDialog(intent), commandCaptor.lastValue)
    }

    @Test
    fun `when default browser prompts experiment OpenSystemDefaultAppsActivity command, then propagate it to consumers`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenSystemDefaultAppsActivity(intent, trigger))

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowSystemDefaultAppsActivity(intent), commandCaptor.lastValue)
    }

    @Test
    fun `when onSetDefaultBrowserDialogShown called, then pass that information to the experiment`() {
        testee.onSetDefaultBrowserDialogShown()

        verify(mockDefaultBrowserPromptsExperiment).onMessageDialogShown()
    }

    @Test
    fun `when onSetDefaultBrowserDialogCanceled called, then pass that information to the experiment`() {
        testee.onSetDefaultBrowserDialogCanceled()

        verify(mockDefaultBrowserPromptsExperiment).onMessageDialogCanceled()
    }

    @Test
    fun `when onSetDefaultBrowserConfirmationButtonClicked called, then pass that information to the experiment and dismiss dialog`() {
        testee.onSetDefaultBrowserConfirmationButtonClicked()

        verify(mockDefaultBrowserPromptsExperiment).onMessageDialogConfirmationButtonClicked()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.DismissSetAsDefaultBrowserDialog, commandCaptor.lastValue)
    }

    @Test
    fun `when onSetDefaultBrowserNotNowButtonClicked called, then pass that information to the experiment and dismiss dialog`() {
        testee.onSetDefaultBrowserNotNowButtonClicked()

        verify(mockDefaultBrowserPromptsExperiment).onMessageDialogNotNowButtonClicked()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.DismissSetAsDefaultBrowserDialog, commandCaptor.lastValue)
    }

    @Test
    fun `when onSystemDefaultBrowserDialogShown called, then pass that information to the experiment`() {
        testee.onSystemDefaultBrowserDialogShown()

        verify(mockDefaultBrowserPromptsExperiment).onSystemDefaultBrowserDialogShown()
    }

    @Test
    fun `when onSystemDefaultBrowserDialogSuccess called, then pass that information to the experiment`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        testee.onSystemDefaultBrowserDialogSuccess()

        verify(mockDefaultBrowserPromptsExperiment).onSystemDefaultBrowserDialogSuccess(trigger)
    }

    @Test
    fun `when onSystemDefaultBrowserDialogCanceled called, then pass that information to the experiment`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        testee.onSystemDefaultBrowserDialogCanceled()

        verify(mockDefaultBrowserPromptsExperiment).onSystemDefaultBrowserDialogCanceled(trigger)
    }

    @Test
    fun `when onSystemDefaultAppsActivityClosed called, then pass that information to the experiment`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        defaultBrowserPromptsExperimentCommandsFlow.send(DefaultBrowserPromptsExperiment.Command.OpenSystemDefaultAppsActivity(intent, trigger))

        testee.onSystemDefaultAppsActivityClosed()

        verify(mockDefaultBrowserPromptsExperiment).onSystemDefaultAppsActivityClosed(trigger)
    }

    @Test
    fun whenOnTabsDeletedInTabSwitcherCalledThenUndoSnackbarCommandTriggered() {
        val tabIds = listOf("tab1", "tab2")
        testee.onTabsDeletedInTabSwitcher(tabIds)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowUndoDeleteTabsMessage(tabIds), commandCaptor.lastValue)
    }

    @Test
    fun whenOmnibarIsInEditModeTabSwipingIsDisabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isInEditMode = true
        testee.onOmnibarEditModeChanged(isInEditMode)
        assertEquals(!isInEditMode, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenOmnibarIsInNotEditModeTabSwipingIsEnabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isInEditMode = false
        testee.onOmnibarEditModeChanged(isInEditMode)
        assertEquals(!isInEditMode, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenBrowserIsNotInFullscreenModeTabSwipingIsEnabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isFullScreen = false
        testee.onFullScreenModeChanged(isFullScreen)
        assertEquals(true, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenBrowserIsInFullscreenModeTabSwipingIsDisabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isFullScreen = true
        testee.onFullScreenModeChanged(isFullScreen)
        assertEquals(false, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenOmnibarIsNotInEditModeAndBrowserIsInFullscreenModeTabSwipingIsDisabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isFullScreen = true
        testee.onFullScreenModeChanged(isFullScreen)

        val isInEditMode = false
        testee.onOmnibarEditModeChanged(isInEditMode)

        assertEquals(false, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenOmnibarIsInEditModeAndBrowserIsNotInFullscreenModeTabSwipingIsDisabled() {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isFullScreen = false
        testee.onFullScreenModeChanged(isFullScreen)

        val isInEditMode = true
        testee.onOmnibarEditModeChanged(isInEditMode)

        assertEquals(false, testee.viewState.value!!.isTabSwipingEnabled)
    }

    @Test
    fun whenOnBookmarksActivityResultCalledThenOpenSavedSiteCommandTriggered() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        val bookmarkUrl = "https://www.example.com"

        testee.onBookmarksActivityResult(bookmarkUrl)

        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.OpenSavedSite(bookmarkUrl), commandCaptor.lastValue)
    }

    private fun initTestee() {
        testee = BrowserViewModel(
            tabRepository = mockTabRepository,
            queryUrlConverter = mockOmnibarEntryConverter,
            dataClearer = mockAutomaticDataClearer,
            appEnjoymentPromptEmitter = mockAppEnjoymentPromptEmitter,
            appEnjoymentUserEventRecorder = mockAppEnjoymentUserEventRecorder,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            pixel = mockPixel,
            skipUrlConversionOnNewTabFeature = skipUrlConversionOnNewTabFeature,
            showOnAppLaunchFeature = fakeShowOnAppLaunchFeatureToggle,
            showOnAppLaunchOptionHandler = showOnAppLaunchOptionHandler,
            defaultBrowserPromptsExperiment = mockDefaultBrowserPromptsExperiment,
            swipingTabsFeature = swipingTabsFeatureProvider,
        )
    }

    private fun configureSkipUrlConversionInNewTabState(enabled: Boolean) {
        skipUrlConversionOnNewTabFeature.self().setRawStoredState(State(enable = enabled))
    }

    companion object {
        const val TAB_ID = "TAB_ID"
    }
}
