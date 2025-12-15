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
import app.cash.turbine.test
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger
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
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import junit.framework.TestCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
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

    @Mock private lateinit var mockTabRepository: TabRepository

    @Mock private lateinit var mockOmnibarEntryConverter: OmnibarEntryConverter

    @Mock private lateinit var mockAutomaticDataClearer: DataClearer

    @Mock private lateinit var mockAppEnjoymentUserEventRecorder: AppEnjoymentUserEventRecorder

    @Mock private lateinit var mockAppEnjoymentPromptEmitter: AppEnjoymentPromptEmitter

    @Mock private lateinit var mockPixel: Pixel

    @Mock private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock private lateinit var showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler

    private val additionalDefaultBrowserPromptsCommandsFlow = Channel<AdditionalDefaultBrowserPrompts.Command>(capacity = Channel.CONFLATED)

    @Mock private lateinit var mockAdditionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts

    @Mock private lateinit var mockDuckAIFeatureState: DuckAiFeatureState
    private val mockDuckAiFullScreenMode = MutableStateFlow(false)

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

        whenever(mockAdditionalDefaultBrowserPrompts.commands).thenReturn(additionalDefaultBrowserPromptsCommandsFlow.receiveAsFlow())

        initTestee()

        runTest {
            whenever(mockTabRepository.add()).thenReturn(TAB_ID)
            whenever(mockOmnibarEntryConverter.convertQueryToUrl(any(), any(), any(), any())).then { it.arguments.first() }
            whenever(mockDuckAIFeatureState.showFullScreenMode).thenReturn(mockDuckAiFullScreenMode)
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
        verify(mockTabRepository, never()).addDefaultTab()
    }

    @Test
    fun whenUserSelectedToRateAppThenPlayStoreCommandTriggered() = runTest {
        testee.onUserSelectedToRateApp(PromptCount.first())

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.LaunchPlayStore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSelectedToGiveFeedbackThenFeedbackCommandTriggered() = runTest {
        testee.onUserSelectedToGiveFeedback(PromptCount.first())

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.LaunchFeedbackView)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewStateCreatedThenWebViewContentShouldBeHidden() = runTest {
        initSuspendTestee()

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.hideWebContent)
        }
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

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.SwitchToTab)
            command as Command.SwitchToTab
            TestCase.assertEquals(
                tab.tabId,
                command.tabId,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnBookmarksActivityResultCalledAndSiteNotOpenedThenOpenInNewTabCommandTriggered() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val bookmarkUrl = "https://www.example.com"
        val tab = TabEntity("123", url = "https://cnn.com")

        whenever(mockTabRepository.getTabs()).thenReturn(listOf(tab))

        testee.onBookmarksActivityResult(bookmarkUrl)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.OpenSavedSite)
            command as Command.OpenSavedSite
            TestCase.assertEquals(
                bookmarkUrl,
                command.url,
            )
            cancelAndIgnoreRemainingEvents()
        }
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
    fun `when default browser prompts OpenMessageDialog command, then propagate it to consumers`() = runTest {
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenMessageDialog)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.ShowSetAsDefaultBrowserDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser prompts OpenSystemDefaultBrowserDialog command, then propagate it to consumers`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.ShowSystemDefaultBrowserDialog)
            command as Command.ShowSystemDefaultBrowserDialog
            TestCase.assertEquals(
                intent,
                command.intent,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser prompts OpenSystemDefaultAppsActivity command, then propagate it to consumers`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultAppsActivity(intent, trigger))

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.ShowSystemDefaultAppsActivity)
            command as Command.ShowSystemDefaultAppsActivity
            TestCase.assertEquals(
                intent,
                command.intent,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when onSetDefaultBrowserDialogShown called, then pass that information`() {
        testee.onSetDefaultBrowserDialogShown()

        verify(mockAdditionalDefaultBrowserPrompts).onMessageDialogShown()
    }

    @Test
    fun `when onSetDefaultBrowserDialogCanceled called, then pass that information`() {
        testee.onSetDefaultBrowserDialogCanceled()

        verify(mockAdditionalDefaultBrowserPrompts).onMessageDialogCanceled()
    }

    @Test
    fun `when onSetDefaultBrowserConfirmationButtonClicked called, then pass that information and dismiss dialog`() = runTest {
        testee.onSetDefaultBrowserConfirmationButtonClicked()

        verify(mockAdditionalDefaultBrowserPrompts).onMessageDialogConfirmationButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.DismissSetAsDefaultBrowserDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when onSetDefaultBrowserDoNotAskAgainButtonClicked called, then pass that information and dismiss dialog`() = runTest {
        testee.onSetDefaultBrowserDoNotAskAgainButtonClicked()

        verify(mockAdditionalDefaultBrowserPrompts).onMessageDialogDoNotAskAgainButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.DoNotAskAgainSetAsDefaultBrowserDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when onSystemDefaultBrowserDialogShown called, then pass that information`() {
        testee.onSystemDefaultBrowserDialogShown()

        verify(mockAdditionalDefaultBrowserPrompts).onSystemDefaultBrowserDialogShown()
    }

    @Test
    fun `when onSystemDefaultBrowserDialogSuccess called, then pass that information`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        testee.onSystemDefaultBrowserDialogSuccess()

        verify(mockAdditionalDefaultBrowserPrompts).onSystemDefaultBrowserDialogSuccess(trigger)
    }

    @Test
    fun `when onSystemDefaultBrowserDialogCanceled called, then pass that information`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultBrowserDialog(intent, trigger))

        testee.onSystemDefaultBrowserDialogCanceled()

        verify(mockAdditionalDefaultBrowserPrompts).onSystemDefaultBrowserDialogCanceled(trigger)
    }

    @Test
    fun `when onSystemDefaultAppsActivityClosed called, then pass that information`() = runTest {
        val intent: Intent = mock()
        val trigger: SetAsDefaultActionTrigger = mock()
        additionalDefaultBrowserPromptsCommandsFlow.send(AdditionalDefaultBrowserPrompts.Command.OpenSystemDefaultAppsActivity(intent, trigger))

        testee.onSystemDefaultAppsActivityClosed()

        verify(mockAdditionalDefaultBrowserPrompts).onSystemDefaultAppsActivityClosed(trigger)
    }

    @Test
    fun whenOnTabsDeletedInTabSwitcherCalledThenUndoSnackbarCommandTriggered() = runTest {
        val tabIds = listOf("tab1", "tab2")
        testee.onTabsDeletedInTabSwitcher(tabIds)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.ShowUndoDeleteTabsMessage)
            command as Command.ShowUndoDeleteTabsMessage
            TestCase.assertEquals(
                tabIds,
                command.tabIds,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarIsInEditModeTabSwipingIsDisabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        val isInEditMode = true
        testee.onOmnibarEditModeChanged(isInEditMode)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenOmnibarIsInNotEditModeTabSwipingIsEnabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        val isInEditMode = false
        testee.onOmnibarEditModeChanged(isInEditMode)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenBrowserIsNotInFullscreenModeTabSwipingIsEnabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))

        val isFullScreen = false
        testee.onFullScreenModeChanged(isFullScreen)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenBrowserIsInFullscreenModeTabSwipingIsDisabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        val isFullScreen = true
        testee.onFullScreenModeChanged(isFullScreen)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenOmnibarIsNotInEditModeAndBrowserIsInFullscreenModeTabSwipingIsDisabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        val isFullScreen = true
        testee.onFullScreenModeChanged(isFullScreen)

        val isInEditMode = false
        testee.onOmnibarEditModeChanged(isInEditMode)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenOmnibarIsInEditModeAndBrowserIsNotInFullscreenModeTabSwipingIsDisabled() = runTest {
        initSuspendTestee()

        swipingTabsFeature.self().setRawStoredState(State(enable = true))
        val isFullScreen = false
        testee.onFullScreenModeChanged(isFullScreen)

        val isInEditMode = true
        testee.onOmnibarEditModeChanged(isInEditMode)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.isTabSwipingEnabled)
        }
    }

    @Test
    fun whenOnBookmarksActivityResultCalledThenOpenSavedSiteCommandTriggered() = runTest {
        swipingTabsFeature.self().setRawStoredState(State(enable = false))
        val bookmarkUrl = "https://www.example.com"

        testee.onBookmarksActivityResult(bookmarkUrl)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.OpenSavedSite)
            command as Command.OpenSavedSite
            TestCase.assertEquals(
                bookmarkUrl,
                command.url,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when openDuckChat called and tabs are null then command is sent with 0 tabs`() = runTest {
        doReturn(MutableLiveData(null)).whenever(mockTabRepository).liveTabs
        initTestee()

        testee.openDuckChat(duckChatUrl = "duck://chat", duckChatSessionActive = false, withTransition = false)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.OpenDuckChat)
            command as Command.OpenDuckChat
            assertEquals(0, command.tabs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when openDuckChat called then command is sent with correct tab count`() = runTest {
        val tabs = listOf(TabEntity("1", "", "", position = 0))
        doReturn(MutableLiveData(tabs)).whenever(mockTabRepository).liveTabs
        initTestee()

        testee.openDuckChat(duckChatUrl = "duck://chat", duckChatSessionActive = false, withTransition = false)

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is Command.OpenDuckChat)
            command as Command.OpenDuckChat
            assertEquals(tabs.size, command.tabs)
            cancelAndIgnoreRemainingEvents()
        }
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
            additionalDefaultBrowserPrompts = mockAdditionalDefaultBrowserPrompts,
            swipingTabsFeature = swipingTabsFeatureProvider,
            duckAiFeatureState = mockDuckAIFeatureState,
        )
    }

    private suspend fun initSuspendTestee() {
        whenever(mockTabRepository.add()).thenReturn(TAB_ID)
        whenever(mockOmnibarEntryConverter.convertQueryToUrl(any(), any(), any(), any())).then { it.arguments.first() }
        whenever(mockDuckAIFeatureState.showFullScreenMode).thenReturn(mockDuckAiFullScreenMode)

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
            additionalDefaultBrowserPrompts = mockAdditionalDefaultBrowserPrompts,
            swipingTabsFeature = swipingTabsFeatureProvider,
            duckAiFeatureState = mockDuckAIFeatureState,
        )
    }

    private fun configureSkipUrlConversionInNewTabState(enabled: Boolean) {
        skipUrlConversionOnNewTabFeature.self().setRawStoredState(State(enable = enabled))
    }

    @Test
    fun whenSendPixelEventForLandscapeOrientationThenPixelsAreFired() = runTest {
        testee.sendPixelEventForLandscapeOrientation()

        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_LANDSCAPE_ORIENTATION_USED)
        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_LANDSCAPE_ORIENTATION_USED_DAILY, type = Daily())
    }

    companion object {
        const val TAB_ID = "TAB_ID"
    }
}
