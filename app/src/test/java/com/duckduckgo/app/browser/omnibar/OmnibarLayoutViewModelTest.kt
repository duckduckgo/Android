package com.duckduckgo.app.browser.omnibar

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.AddressDisplayFormatter
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.LaunchInputScreen
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OmnibarLayoutViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val tabRepository: TabRepository = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger = mock()
    private val duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl()
    private val duckPlayer: DuckPlayer = mock()
    private val pixel: Pixel = mock()
    private val userBrowserProperties: UserBrowserProperties = mock()

    private val mockExperimentalThemingDataStore: ExperimentalThemingDataStore = mock()
    private val disabledVisualExperimentNavBarStateFlow = MutableStateFlow(false)
    private val enabledVisualExperimentNavBarStateFlow = MutableStateFlow(true)

    private val defaultBrowserPromptsExperimentHighlightOverflowMenuFlow = MutableStateFlow(false)
    private val defaultBrowserPromptsExperiment: DefaultBrowserPromptsExperiment = mock()

    private val mockSenseOfProtectionExperiment: SenseOfProtectionExperiment = mock()
    private val duckChat: DuckChat = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow = MutableStateFlow(true)
    private val duckAiShowOmnibarShortcutInAllStatesFlow = MutableStateFlow(true)
    private val duckAiShowInputScreenFlow = MutableStateFlow(false)
    private val settingsDataStore: SettingsDataStore = mock()
    private val mockAddressDisplayFormatter: AddressDisplayFormatter by lazy {
        mock {
            on { getShortUrl(any()) } doAnswer { invocation ->
                val url = invocation.getArgument<String>(0)
                url.toUri().baseHost ?: url
            }
        }
    }

    private lateinit var testee: OmnibarLayoutViewModel

    private val EMPTY_URL = ""
    private val SERP_URL = "https://duckduckgo.com/?q=test&atb=v395-1-wb&ia=web"
    private val DUCK_PLAYER_URL = "duck://player/21bPE0BJdOA"
    private val RANDOM_URL = "https://as.com"
    private val QUERY = "query"

    @Before
    fun before() {
        whenever(defaultBrowserPromptsExperiment.highlightPopupMenu).thenReturn(defaultBrowserPromptsExperimentHighlightOverflowMenuFlow)
        whenever(tabRepository.flowTabs).thenReturn(flowOf(emptyList()))
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(any(), any(), any(), any())).thenReturn(true)
        whenever(duckPlayer.isDuckPlayerUri(DUCK_PLAYER_URL)).thenReturn(true)
        whenever(mockExperimentalThemingDataStore.isSingleOmnibarEnabled).thenReturn(disabledVisualExperimentNavBarStateFlow)
        whenever(duckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow)
        whenever(duckAiFeatureState.showOmnibarShortcutInAllStates).thenReturn(duckAiShowOmnibarShortcutInAllStatesFlow)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        whenever(duckAiFeatureState.showInputScreen).thenReturn(duckAiShowInputScreenFlow)

        initializeViewModel()
    }

    @Test
    fun whenViewModelAttachedAndNoTabsOpenThenTabsRetrieved() = runTest {
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.tabCount == 0)
        }
    }

    @Test
    fun whenViewModelAttachedAndTabsOpenedThenTabsRetrieved() = runTest {
        whenever(tabRepository.flowTabs).thenReturn(flowOf(listOf(TabEntity(tabId = "0", position = 0))))

        initializeViewModel()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.tabCount == 1)
            assertTrue(viewState.shouldUpdateTabsCount)
        }
    }

    private fun initializeViewModel() {
        testee = OmnibarLayoutViewModel(
            tabRepository = tabRepository,
            voiceSearchAvailability = voiceSearchAvailability,
            voiceSearchPixelLogger = voiceSearchPixelLogger,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
            duckPlayer = duckPlayer,
            pixel = pixel,
            userBrowserProperties = userBrowserProperties,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            defaultBrowserPromptsExperiment = defaultBrowserPromptsExperiment,
            experimentalThemingDataStore = mockExperimentalThemingDataStore,
            senseOfProtectionExperiment = mockSenseOfProtectionExperiment,
            duckChat = duckChat,
            duckAiFeatureState = duckAiFeatureState,
            addressDisplayFormatter = mockAddressDisplayFormatter,
            settingsDataStore = settingsDataStore,
        )
    }

    @Test
    fun whenViewModelAttachedAndVoiceSearchSupportedThenPixelLogged() = runTest {
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(true)

        initializeViewModel()

        verify(voiceSearchPixelLogger).log()
    }

    @Test
    fun whenViewModelAttachedAndVoiceSearchNotSupportedThenPixelLogged() = runTest {
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(false)

        verifyNoInteractions(voiceSearchPixelLogger)
    }

    @Test
    fun whenOmnibarFocusedThenCancelTrackersAnimationCommandSent() = runTest {
        testee.onOmnibarFocusChanged(true, "query")

        testee.commands().test {
            awaitItem().assertCommand(Command.CancelAnimations::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSingleOmnibarEnabledThenExperimentalThemingViewStateSet() = runTest {
        whenever(mockExperimentalThemingDataStore.isSingleOmnibarEnabled).thenReturn(enabledVisualExperimentNavBarStateFlow)

        initializeViewModel()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.isExperimentalThemingEnabled)
        }
    }

    @Test
    fun whenOmnibarFocusedAndQueryNotBlankThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "query")

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.hasFocus)
            assertTrue(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.showClearButton)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertFalse(viewState.showBrowserMenu)
            assertTrue(viewState.highlightPrivacyShield == HighlightableButton.Gone)
            assertTrue(viewState.showVoiceSearch)
        }
    }

    @Test
    fun whenOmnibarFocusedAndQueryBlankThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "")

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.hasFocus)
            assertTrue(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertTrue(viewState.highlightPrivacyShield == HighlightableButton.Gone)
            assertTrue(viewState.showVoiceSearch)
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndDDGUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(SERP_URL)
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.DAX)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.MoveCaretToFront::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndDuckPlayerUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(DUCK_PLAYER_URL)
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.DUCK_PLAYER)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.MoveCaretToFront::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndUrlEmptyThenViewStateCorrect() = runTest {
        givenSiteLoaded(EMPTY_URL)
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.MoveCaretToFront::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndUrlLoadedThenViewStateCorrect() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.PRIVACY_SHIELD)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.MoveCaretToFront::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewModeChangedToCustomTabThenViewStateCorrect() = runTest {
        val expectedToolbarColor = 100
        val expectedTitle = "example"
        val expectedDomain = "example.com"
        val expectedShowDuckPlayerIcon = false
        testee.onViewModeChanged(
            ViewMode.CustomTab(
                toolbarColor = expectedToolbarColor,
                title = expectedTitle,
                domain = expectedDomain,
                showDuckPlayerIcon = expectedShowDuckPlayerIcon,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showClearButton)
            assertFalse(viewState.showVoiceSearch)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)

            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedToolbarColor, customTabMode.toolbarColor)
            assertEquals(expectedTitle, customTabMode.title)
            assertEquals(expectedDomain, customTabMode.domain)
            assertEquals(expectedShowDuckPlayerIcon, customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun `when custom tab title updates, update view mode state`() = runTest {
        val expectedTitle = "newTitle"
        val expectedDomain = "newDomain"
        val expectedShowDuckPlayerIcon = true
        val decoration = ChangeCustomTabTitle(
            title = expectedTitle,
            domain = expectedDomain,
            showDuckPlayerIcon = expectedShowDuckPlayerIcon,
        )

        testee.onViewModeChanged(ViewMode.CustomTab(100, "example", "example.com", showDuckPlayerIcon = false))
        testee.viewState.test {
            // skipping initial update
            skipItems(1)
            testee.onCustomTabTitleUpdate(decoration)
            val viewState = awaitItem()
            assertFalse(viewState.showClearButton)
            assertFalse(viewState.showVoiceSearch)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)

            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(100, customTabMode.toolbarColor)
            assertEquals(expectedTitle, customTabMode.title)
            assertEquals(expectedDomain, customTabMode.domain)
            assertEquals(expectedShowDuckPlayerIcon, customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun whenViewModeChangedToErrorThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.Error)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.SSLWarning)
        }
    }

    @Test
    fun whenViewModeChangedToMaliciousSiteWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.MaliciousSiteWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.MaliciousSiteWarning)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertFalse(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.NewTab)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Browser(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.Browser)
        }
    }

    @Test
    fun whenViewModeChangedToErrorAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.viewMode is ViewMode.Error)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.viewMode is ViewMode.SSLWarning)
        }
    }

    @Test
    fun whenViewModeChangedToMaliciousSiteWarningAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.MaliciousSiteWarning)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.viewMode is ViewMode.MaliciousSiteWarning)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.viewMode is ViewMode.NewTab)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.Browser(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.viewMode is ViewMode.Browser)
        }
    }

    @Test
    fun whenPrivacyShieldChangedToProtectedThenViewStateCorrect() = runTest {
        val privacyShield = PROTECTED
        testee.onPrivacyShieldChanged(privacyShield)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.privacyShield == privacyShield)
        }
    }

    @Test
    fun whenPrivacyShieldChangedToUnProtectedThenViewStateCorrect() = runTest {
        val privacyShield = UNPROTECTED
        testee.onPrivacyShieldChanged(privacyShield)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.privacyShield == privacyShield)
        }
    }

    @Test
    fun whenPrivacyShieldChangedToWarningThenViewStateCorrect() = runTest {
        val privacyShield = PrivacyShield.MALICIOUS
        testee.onPrivacyShieldChanged(privacyShield)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.privacyShield == privacyShield)
        }
    }

    @Test
    fun whenPrivacyShieldChangedToUnknownThenViewStateCorrect() = runTest {
        val privacyShield = PrivacyShield.UNKNOWN
        testee.onPrivacyShieldChanged(privacyShield)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.privacyShield == privacyShield)
        }
    }

    @Test
    fun whenClearTextButtonPressedThenViewStateCorrect() = runTest {
        testee.onClearTextButtonPressed()
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText.isEmpty())
            assertTrue(viewState.updateOmnibarText)
            assertTrue(viewState.expanded)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
            assertFalse(viewState.showClearButton)
        }
    }

    @Test
    fun whenClearTextButtonPressedAndUrlEmptyThenPixelSent() = runTest {
        givenSiteLoaded("")
        testee.onClearTextButtonPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_ENTRY_CLEARED)
    }

    @Test
    fun whenClearTextButtonPressedAndSERPUrlThenPixelSent() = runTest {
        givenSiteLoaded(SERP_URL)
        testee.onClearTextButtonPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_SERP_ENTRY_CLEARED)
    }

    @Test
    fun whenClearTextButtonPressedAndUrlThenPixelSent() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onClearTextButtonPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_WEBSITE_ENTRY_CLEARED)
    }

    @Test
    fun whenUserTouchedTextInputWithWrongActionThenPixelNotSent() = runTest {
        testee.onUserTouchedOmnibarTextInput(MotionEvent.ACTION_DOWN)
        verifyNoInteractions(pixel)
    }

    @Test
    fun whenUserTouchedTextInputAndUrlEmptyThenPixelSent() = runTest {
        givenSiteLoaded("")
        testee.onUserTouchedOmnibarTextInput(MotionEvent.ACTION_UP)
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CLICKED)
    }

    @Test
    fun whenUserTouchedTextInputAndSERPUrlThenPixelSent() = runTest {
        givenSiteLoaded(SERP_URL)
        testee.onUserTouchedOmnibarTextInput(MotionEvent.ACTION_UP)
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_SERP_CLICKED)
    }

    @Test
    fun whenUserTouchedTextInputAndUrlThenPixelSent() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onUserTouchedOmnibarTextInput(MotionEvent.ACTION_UP)
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_WEBSITE_CLICKED)
    }

    @Test
    fun whenBackKeyPressedAndUrlEmptyThenPixelSent() = runTest {
        givenSiteLoaded("")
        testee.onBackKeyPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_NEW_TAB_PAGE_CANCELLED)
    }

    @Test
    fun whenBackKeyPressedAndSERPUrlThenPixelSent() = runTest {
        givenSiteLoaded(SERP_URL)
        testee.onBackKeyPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_SERP_CANCELLED)
    }

    @Test
    fun whenBackKeyPressedAndUrlThenPixelSent() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onBackKeyPressed()
        verify(pixel).fire(AppPixelName.ADDRESS_BAR_WEBSITE_CANCELLED)
    }

    @Test
    fun whenEnterPressedAndUrlEmptyThenPixelSent() = runTest {
        givenSiteLoaded("")
        testee.onEnterKeyPressed()
        verify(pixel).fire(AppPixelName.KEYBOARD_GO_NEW_TAB_CLICKED)
    }

    @Test
    fun whenEnterPressedAndSERPUrlThenPixelSent() = runTest {
        givenSiteLoaded(SERP_URL)
        testee.onEnterKeyPressed()
        verify(pixel).fire(AppPixelName.KEYBOARD_GO_SERP_CLICKED)
    }

    @Test
    fun whenEnterPressedAndUrlThenPixelSent() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onEnterKeyPressed()
        verify(pixel).fire(AppPixelName.KEYBOARD_GO_WEBSITE_CLICKED)
    }

    @Test
    fun whenPrivacyShieldItemHighlightedThenViewStateCorrect() = runTest {
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = false, privacyShield = true))
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightPrivacyShield == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = true,
                ),
            )

            assertTrue(
                viewState.highlightFireButton == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )

            assertFalse(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenFireButtonItemHighlightedThenViewStateCorrect() = runTest {
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = true, privacyShield = false))
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightPrivacyShield == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )

            assertTrue(
                viewState.highlightFireButton == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = true,
                ),
            )

            assertFalse(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenFireButtonItemHighlightedRemovedThenViewStateCorrect() = runTest {
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = false, privacyShield = false))
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightPrivacyShield == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )

            assertTrue(
                viewState.highlightFireButton == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )

            assertTrue(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenFireIconPressedAndFireIconHighlightedThenViewStateCorrectAndPixelSent() = runTest {
        val animationPlaying = true
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = true, privacyShield = false))
        testee.onFireIconPressed(true)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightFireButton == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )
            assertTrue(viewState.scrollingEnabled)
        }

        verify(pixel).fire(
            AppPixelName.MENU_ACTION_FIRE_PRESSED.pixelName,
            mapOf(FIRE_BUTTON_STATE to animationPlaying.toString()),
        )
    }

    @Test
    fun whenPrivacyShieldIconPressedAndFireIconHighlightedThenViewStateCorrectAndPixelSent() = runTest {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(1)
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = false, privacyShield = true))
        testee.onPrivacyShieldButtonPressed()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightPrivacyShield == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = false,
                ),
            )
            assertTrue(viewState.scrollingEnabled)
        }

        verify(pixel).fire(
            pixel = PrivacyDashboardPixels.PRIVACY_DASHBOARD_FIRST_TIME_OPENED,
            parameters = mapOf(
                "daysSinceInstall" to userBrowserProperties.daysSinceInstalled().toString(),
                "from_onboarding" to "true",
            ),
            type = Unique(),
        )
    }

    @Test
    fun whenInputStateChangedAndQueryEmptyThenViewStateCorrect() = runTest {
        val query = ""
        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = false
        testee.onInputStateChanged(query, hasFocus, clearQuery, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == query)
            assertFalse(viewState.updateOmnibarText)
            assertTrue(viewState.hasFocus)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showBrowserMenu)
            assertFalse(viewState.showClearButton)
        }
    }

    @Test
    fun whenInputStateChangedAndQueryNotEmptyThenViewStateCorrect() = runTest {
        val query = "query"
        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = false
        testee.onInputStateChanged(query, hasFocus, clearQuery, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == query)
            assertFalse(viewState.updateOmnibarText)
            assertTrue(viewState.hasFocus)
            assertFalse(viewState.showFireIcon)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showBrowserMenu)
            assertTrue(viewState.showClearButton)
        }
    }

    @Test
    fun whenLoadingStateChangesInEmptyUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(EMPTY_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == EMPTY_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenLoadingStateChangesInUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == RANDOM_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.PRIVACY_SHIELD)
        }
    }

    @Test
    fun whenLoadingStateChangesInSERPUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(SERP_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == SERP_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.DAX)
        }
    }

    @Test
    fun whenLoadingStateChangesInDuckPlayerUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(DUCK_PLAYER_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == DUCK_PLAYER_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.DUCK_PLAYER)
        }
    }

    @Test
    fun whenOmnibarStateChangesAsNavigationalChangeThenViewStateUpdates() = runTest {
        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    navigationChange = true,
                    omnibarText = QUERY,
                ),
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.expanded)
            assertTrue(viewState.expandedAnimated)
            assertTrue(viewState.omnibarText == QUERY)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarStateChangesThenViewStateUpdates() = runTest {
        val omnibarState = OmnibarViewState(
            navigationChange = false,
            omnibarText = QUERY,
            forceExpand = false,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.expanded == omnibarState.forceExpand)
            assertTrue(viewState.expandedAnimated == omnibarState.forceExpand)
            assertTrue(viewState.omnibarText == QUERY)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarFocusedAndLoadingStateChangesThenViewStateCorrect() = runTest {
        val omnibarState = OmnibarViewState(
            navigationChange = false,
            omnibarText = QUERY,
            forceExpand = false,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))
        testee.onOmnibarFocusChanged(true, QUERY)
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    trackersAnimationEnabled = true,
                    progress = 100,
                    url = SERP_URL,
                ),
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == SEARCH)
            assertTrue(viewState.expandedAnimated == omnibarState.forceExpand)
            assertTrue(viewState.omnibarText == QUERY)
            assertFalse(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarFocusedThenOmnibarTextRemainsDoesNotChangeIfBlank() = runTest {
        val omnibarViewState = OmnibarViewState(omnibarText = "")
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))
        testee.onOmnibarFocusChanged(true, "old input text")

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("", viewState.omnibarText)
        }
    }

    @Test
    fun whenTrackersAnimationStartedAndOmnibarNotFocusedThenCommandAndViewStateCorrect() = runTest {
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInModifiedControlCohortAndExperimentEnabled()).thenReturn(false)
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInAVariantAndExperimentEnabled()).thenReturn(false)
        testee.onOmnibarFocusChanged(false, SERP_URL)
        val trackers = givenSomeTrackers()
        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.PRIVACY_SHIELD)
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.StartTrackersAnimation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTrackersAnimationStartedAndOmnibarFocusedThenCommandAndViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, SERP_URL)
        val trackers = givenSomeTrackers()

        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenTrackersAnimationStartedAndOmnibarFocusedAndSelfAndVariant1EnabledThenStartExperimentVariant1AnimationCommandSent() = runTest {
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInModifiedControlCohortAndExperimentEnabled()).thenReturn(true)
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInVariant2CohortAndExperimentEnabled()).thenReturn(false)
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInVariant1CohortAndExperimentEnabled()).thenReturn(false)

        testee.onOmnibarFocusChanged(false, SERP_URL)
        val trackers = givenSomeTrackers()

        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.commands().test {
            awaitItem().assertCommand(Command.StartExperimentVariant1Animation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenTrackersAnimationStartedAndOmnibarFocusedAndSelfAndVariant1DisabledThenStartExperimentVariant2Or3AnimationCommandSent() = runTest {
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInAVariantAndExperimentEnabled()).thenReturn(true)
        whenever(mockSenseOfProtectionExperiment.isUserEnrolledInModifiedControlCohortAndExperimentEnabled()).thenReturn(false)

        testee.onOmnibarFocusChanged(false, SERP_URL)
        val trackers = givenSomeTrackers()

        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.commands().test {
            awaitItem().assertCommand(Command.StartExperimentVariant2OrVariant3Animation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarFocusedAndAnimationPlayingThenAnimationsCanceled() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.onOmnibarFocusChanged(true, RANDOM_URL)

        testee.commands().test {
            awaitItem().assertCommand(Command.CancelAnimations::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarTextClearedAndBackPressedThenUrlIsShown() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onClearTextButtonPressed()
        testee.onBackKeyPressed()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == RANDOM_URL)
        }
    }

    @Test
    fun whenHidingKeyboardAfterClearingInputWhileInSiteThenURLisShown() = runTest {
        givenSiteLoaded(RANDOM_URL)
        testee.onClearTextButtonPressed()
        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = false
        testee.onInputStateChanged("", hasFocus, clearQuery, deleteLastCharacter)
        testee.onOmnibarFocusChanged(false, "")
        testee.onInputStateChanged(RANDOM_URL, false, false, false)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == RANDOM_URL)
        }
    }

    @Test
    fun whenHidingKeyboardAfterClearingInputWhileInSERPThenURLisShown() = runTest {
        givenSiteLoaded(SERP_URL)

        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = false

        testee.onClearTextButtonPressed()
        testee.onInputStateChanged("", hasFocus, clearQuery, deleteLastCharacter)
        testee.onOmnibarFocusChanged(false, "")
        testee.onInputStateChanged(SERP_URL, false, false, false)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == SERP_URL)
        }
    }

    @Test
    fun whenClosingKeyboardAfterDeletingLastCharacterFromOmnibaWhileInSERPThenURLisShown() = runTest {
        givenSiteLoaded(SERP_URL)

        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = true

        testee.onClearTextButtonPressed()
        testee.onInputStateChanged("", hasFocus, clearQuery, deleteLastCharacter)
        testee.onOmnibarFocusChanged(false, "")
        testee.onInputStateChanged(SERP_URL, false, false, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == SERP_URL)
        }
    }

    @Test
    fun whenClosingKeyboardAfterDeletingLastCharacterFromOmnibaWhileInSitehenURLisShown() = runTest {
        givenSiteLoaded(RANDOM_URL)

        val hasFocus = true
        val clearQuery = true
        val deleteLastCharacter = true

        testee.onClearTextButtonPressed()
        testee.onInputStateChanged("", hasFocus, clearQuery, deleteLastCharacter)
        testee.onOmnibarFocusChanged(false, "")
        testee.onInputStateChanged(RANDOM_URL, false, false, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == RANDOM_URL)
        }
    }

    @Test
    fun `when default browser experiment updates browser menu highlight, then update the view state`() = runTest {
        defaultBrowserPromptsExperimentHighlightOverflowMenuFlow.value = true

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showBrowserMenuHighlight)
        }
    }

    @Test
    fun whenViewModelAttachedThenShowChatMenuTrue() = runTest {
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenViewModeIsNewTabThenShowChatMenuTrue() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenViewModeIsNewTabAndChatEntryPointDisabledThenShowChatMenuFalse() = runTest {
        duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow.value = false
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
        }
    }

    @Test
    fun whenShowDuckAiInAllStatesThenShowChatMenuTrue() = runTest {
        duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow.value = false
        duckAiShowOmnibarShortcutInAllStatesFlow.value = true

        testee.viewState.test {
            var viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
            testee.onViewModeChanged(ViewMode.NewTab)
            viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenShowDuckAiInAllStatesAndCustomTabThenShowChatMenuFalse() = runTest {
        duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow.value = false
        duckAiShowOmnibarShortcutInAllStatesFlow.value = true
        testee.onViewModeChanged(ViewMode.CustomTab(0, "example", "example.com", false))

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
        }
    }

    @Test
    fun whenNavigationBarExperimentEnabledThenShowChatMenuTrue() = runTest {
        disabledVisualExperimentNavBarStateFlow.value = true

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenNavigationBarExperimentEnabledAndChatEntryPointDisabledThenShowChatMenuFalse() = runTest {
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow.value = false
        disabledVisualExperimentNavBarStateFlow.value = true

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
        }
    }

    @Test
    fun whenOmnibarNotFocusedThenShowChatMenuTrue() = runTest {
        testee.onOmnibarFocusChanged(false, QUERY)
        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenViewModeChangedToCustomTabThenShowChatMenuFalse() = runTest {
        testee.onViewModeChanged(ViewMode.CustomTab(0, "example", "example.com", false))
        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
        }
    }

    @Test
    fun whenClearTextButtonPressedThenShowChatMenuTrue() = runTest {
        testee.onClearTextButtonPressed()
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenInputStateChangedWithEmptyQueryThenShowChatMenuTrue() = runTest {
        testee.onInputStateChanged("", hasFocus = true, clearQuery = true, deleteLastCharacter = false)
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenInputStateChangedWithNonEmptyQueryThenShowChatMenuTrue() = runTest {
        testee.onInputStateChanged(QUERY, hasFocus = true, clearQuery = true, deleteLastCharacter = false)
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun `when DuckChat Button pressed and omnibar has focus then source is focused`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = "query")

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "focused"))
    }

    @Test
    fun `when DuckChat Button pressed and omnibar has focus with experiment enabled then source is focused`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        whenever(mockExperimentalThemingDataStore.isSingleOmnibarEnabled).thenReturn(enabledVisualExperimentNavBarStateFlow)
        initializeViewModel()
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = "query")

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "focused"))
    }

    @Test
    fun `when DuckChat Button pressed, omnibar not focused, and viewMode is NewTab then source is ntp`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "ntp"))
    }

    @Test
    fun `when DuckChat Button pressed, omnibar not focused, viewMode is Browser, and URL is SERP then source is serp`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        givenSiteLoaded(SERP_URL)

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "serp"))
    }

    @Test
    fun `when DuckChat Button pressed, omnibar not focused, viewMode is Browser, and URL is website then source is website`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        givenSiteLoaded(RANDOM_URL)

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "website"))
    }

    @Test
    fun `when DuckChat Button pressed and source is unknown, then send a pixel anyway`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        testee.onViewModeChanged(ViewMode.Error)

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "0", "source" to "unknown"))
    }

    @Test
    fun `when DuckChat Button pressed with experiment enabled and was used before then correct pixel sent`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(true)
        whenever(mockExperimentalThemingDataStore.isSingleOmnibarEnabled).thenReturn(enabledVisualExperimentNavBarStateFlow)
        initializeViewModel()
        testee.onViewModeChanged(ViewMode.NewTab)
        testee.onOmnibarFocusChanged(false, "")

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "1", "source" to "ntp"))
    }

    @Test
    fun `when DuckChat Button pressed with experiment disabled and was used before then correct pixel sent`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(true)
        givenSiteLoaded(RANDOM_URL)
        testee.onOmnibarFocusChanged(false, "")

        testee.onDuckChatButtonPressed()

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_BUTTON_OPEN, mapOf("was_used_before" to "1", "source" to "website"))
    }

    @Test
    fun whenDuckAIPoCEnabledThenShowClickCatcherTrue() = runTest {
        duckAiShowInputScreenFlow.value = true

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showTextInputClickCatcher)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDuckAIPoCDisabledThenShowClickCatcherFalse() = runTest {
        duckAiShowInputScreenFlow.value = false

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showTextInputClickCatcher)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and no URL then input screen launched with draft query`() = runTest {
        testee.onInputStateChanged(query = "draft", hasFocus = false, clearQuery = false, deleteLastCharacter = false)

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchInputScreen)
            assertEquals("draft", (command as LaunchInputScreen).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and DDG URL then input screen launched with search query`() = runTest {
        givenSiteLoaded(SERP_URL)
        val omnibarViewState = OmnibarViewState(omnibarText = "test", queryOrFullUrl = "test", isEditing = false)
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchInputScreen)
            assertEquals("test", (command as LaunchInputScreen).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and random URL then input screen launched with full URL`() = runTest {
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        initializeViewModel()
        val omnibarViewState = OmnibarViewState(omnibarText = "test", queryOrFullUrl = "test", isEditing = false)
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))
        givenSiteLoaded(RANDOM_URL)

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchInputScreen)
            assertEquals(RANDOM_URL, (command as LaunchInputScreen).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarLosesFocusAndFullUrlEnabledThenAddressDisplayDisplaysFullUrl() = runTest {
        val query = "query"
        val url = "https://example.com/test.html"
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        initializeViewModel()
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = query) // Initial focus
        testee.onExternalStateChange(StateChange.LoadingStateChange(LoadingViewState(url = url))) // Set URL

        testee.onOmnibarFocusChanged(hasFocus = false, inputFieldText = query)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(url, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenOmnibarLosesFocusAndFullUrlDisabledThenAddressDisplayTheShortUrl() = runTest {
        val query = "query"
        val url = "https://example.com/test.html"
        val formattedUrl = "example.com"
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        initializeViewModel()
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = query) // Initial focus
        testee.onExternalStateChange(StateChange.LoadingStateChange(LoadingViewState(url = url))) // Set URL

        testee.onOmnibarFocusChanged(hasFocus = false, inputFieldText = query)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(formattedUrl, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verify(mockAddressDisplayFormatter).getShortUrl(viewState.url)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedAndFullUrlEnabledThenAddressDisplayFormatterNotCalled() = runTest {
        val url = "https://example.com/test.html"
        val formattedUrl = "https://example.com/test.html"
        val omnibarViewState = OmnibarViewState(omnibarText = url, queryOrFullUrl = url, isEditing = false)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        initializeViewModel()

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(formattedUrl, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenOmnibarGainsFocusAndFullUrlDisabledThenOmnibarTextIsFullUrl() = runTest {
        val url = "https://example.com/test.html"
        val shortUrl = "example.com"
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)
        initializeViewModel()

        // Set initial state: site loaded, omnibar not focused (showing short url)
        givenSiteLoaded(url)
        testee.onOmnibarFocusChanged(hasFocus = false, inputFieldText = "")

        // omnibar gains focus
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = shortUrl)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(url, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarGainsFocusAndFullUrlEnabledThenOmnibarTextIsFullUrl() = runTest {
        val url = "https://example.com/test.html"
        val shortUrl = "example.com"
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)
        initializeViewModel()

        // Set initial state: site loaded, omnibar not focused (showing short url)
        givenSiteLoaded(url)
        testee.onOmnibarFocusChanged(hasFocus = false, inputFieldText = "")

        // omnibar gains focus
        testee.onOmnibarFocusChanged(hasFocus = true, inputFieldText = shortUrl)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(url, viewState.omnibarText)
            assertFalse(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedWithForceRenderAndNotDDGUrlAndFullUrlEnabledThenOmnibarTextIsFullUrl() = runTest {
        val omnibarViewState = OmnibarViewState(omnibarText = RANDOM_URL, queryOrFullUrl = RANDOM_URL)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState, forceRender = true))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(RANDOM_URL, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedWithForceRenderAndNotDDGUrlAndFullUrlDisabledThenOmnibarTextIsShortUrl() = runTest {
        val shortUrl = RANDOM_URL.toUri().baseHost!!
        val omnibarViewState = OmnibarViewState(omnibarText = RANDOM_URL, queryOrFullUrl = RANDOM_URL)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState, forceRender = true))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(shortUrl, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verify(mockAddressDisplayFormatter).getShortUrl(RANDOM_URL)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedWithForceRenderAndNotDDGUrlAndFullUrlEndabledThenOmnibarTextIsFullUrl() = runTest {
        val shortUrl = RANDOM_URL.toUri().baseHost!!
        val omnibarViewState = OmnibarViewState(omnibarText = shortUrl, queryOrFullUrl = RANDOM_URL)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(true)

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState, forceRender = true))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(RANDOM_URL, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedWithoutForceRenderThenOmnibarTextIsFromState() = runTest {
        val omnibarViewState = OmnibarViewState(omnibarText = RANDOM_URL, queryOrFullUrl = RANDOM_URL)
        whenever(settingsDataStore.isFullUrlEnabled).thenReturn(false)

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState, forceRender = false))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(RANDOM_URL, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenShowDuckAiInAllStatesDisabledAndOtherConditionsAreNotMetThenButtonIsNotVisible() = runTest {
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        initializeViewModel()
        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
        }
    }

    @Test
    fun whenShowDuckAiInAllStatesDisabledButInNewTabThenShowChatMenuIsTrue() = runTest {
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        initializeViewModel()

        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenShowDuckAiInAllStatesDisabledButHasFocusAndTextThenShowChatMenuIsTrue() = runTest {
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        initializeViewModel()

        testee.onInputStateChanged(QUERY, hasFocus = true, clearQuery = false, deleteLastCharacter = false)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFindInPageDismissedThenShowFindInPageIsFalse() = runTest {
        // First set showFindInPage to true
        testee.onFindInPageRequested()

        // Then dismiss it
        testee.onFindInPageDismissed()

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showFindInPage)
        }
    }

    @Test
    fun whenFindInPageRequestedThenShowFindInPageIsTrue() = runTest {
        // First set showFindInPage to true
        testee.onFindInPageRequested()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showFindInPage)
        }
    }

    private fun givenSiteLoaded(loadedUrl: String) {
        testee.onViewModeChanged(ViewMode.Browser(loadedUrl))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    trackersAnimationEnabled = true,
                    progress = 100,
                    url = loadedUrl,
                ),
            ),
        )
    }

    private fun givenSomeTrackers(): List<Entity> {
        val network = TestingEntity("Network", "Network", 1.0)
        val majorNetwork = TestingEntity("MajorNetwork", "MajorNetwork", Entity.MAJOR_NETWORK_PREVALENCE + 1)
        return listOf(network, majorNetwork)
    }

    private fun Command.assertCommand(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
