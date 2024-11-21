package com.duckduckgo.app.browser.omnibar

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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

    private lateinit var testee: OmnibarLayoutViewModel

    private val EMPTY_URL = ""
    private val SERP_URL = "https://duckduckgo.com/?q=test&atb=v395-1-wb&ia=web"
    private val DUCK_PLAYER_URL = "duck://player/21bPE0BJdOA"
    private val RANDOM_URL = "https://as.com"
    private val QUERY = "query"

    @Before
    fun before() {
        testee = OmnibarLayoutViewModel(
            tabRepository = tabRepository,
            voiceSearchAvailability = voiceSearchAvailability,
            voiceSearchPixelLogger = voiceSearchPixelLogger,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
            duckPlayer = duckPlayer,
            pixel = pixel,
            userBrowserProperties = userBrowserProperties,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        whenever(tabRepository.flowTabs).thenReturn(flowOf(emptyList()))
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(any(), any(), any(), any())).thenReturn(true)
        whenever(duckPlayer.isDuckPlayerUri(DUCK_PLAYER_URL)).thenReturn(true)
    }

    @Test
    fun whenViewModelAttachedAndNoTabsOpenThenTabsRetrieved() = runTest {
        testee.onAttachedToWindow()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.tabs.isEmpty())
        }
    }

    @Test
    fun whenViewModelAttachedAndTabsOpenedThenTabsRetrieved() = runTest {
        whenever(tabRepository.flowTabs).thenReturn(flowOf(listOf(TabEntity(tabId = "0", position = 0))))

        testee.onAttachedToWindow()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.tabs.size == 1)
            assertTrue(viewState.shouldUpdateTabsCount)
        }
    }

    @Test
    fun whenViewModelAttachedAndVoiceSearchSupportedThenPixelLogged() = runTest {
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(true)

        testee.onAttachedToWindow()

        verify(voiceSearchPixelLogger).log()
    }

    @Test
    fun whenViewModelAttachedAndVoiceSearchNotSupportedThenPixelLogged() = runTest {
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(false)

        testee.onAttachedToWindow()

        verifyNoInteractions(voiceSearchPixelLogger)
    }

    @Test
    fun whenOmnibarFocusedThenCancelTrackersAnimationCommandSent() = runTest {
        testee.onOmnibarFocusChanged(true, "query")

        testee.commands().test {
            awaitItem().assertCommand(Command.CancelTrackersAnimation::class)
            cancelAndIgnoreRemainingEvents()
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
            assertFalse(viewState.shouldMoveCaretToStart)
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
            assertFalse(viewState.shouldMoveCaretToStart)
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
            assertTrue(viewState.shouldMoveCaretToStart)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
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
            assertTrue(viewState.shouldMoveCaretToStart)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
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
            assertTrue(viewState.shouldMoveCaretToStart)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
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
            assertTrue(viewState.shouldMoveCaretToStart)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewModeChangedToCustomTabThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.CustomTab(0, "example.com", false))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            assertFalse(viewState.showClearButton)
            assertFalse(viewState.showVoiceSearch)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
        }
    }

    @Test
    fun whenViewModeChangedToErrorThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
            assertTrue(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
            assertTrue(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertFalse(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Browser(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertTrue(viewState.scrollingEnabled)
        }
    }

    @Test
    fun whenViewModeChangedToErrorAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, RANDOM_URL)
        testee.onViewModeChanged(ViewMode.Browser(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
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
        val privacyShield = PrivacyShield.WARNING
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
    fun whenOutlineEnabledThenViewStateCorrect() = runTest {
        testee.onOutlineEnabled(true)
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.hasFocus)
        }
    }

    @Test
    fun whenOutlineDisabledThenViewStateCorrect() = runTest {
        testee.onOutlineEnabled(false)
        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.hasFocus)
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
        testee.onInputStateChanged(query, hasFocus)

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
        testee.onInputStateChanged(query, hasFocus)

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
            shouldMoveCaretToEnd = true,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.expanded == omnibarState.forceExpand)
            assertTrue(viewState.expandedAnimated == omnibarState.forceExpand)
            assertTrue(viewState.omnibarText == QUERY)
            assertTrue(viewState.updateOmnibarText)
            assertTrue(viewState.shouldMoveCaretToEnd == omnibarState.shouldMoveCaretToEnd)
        }
    }

    @Test
    fun whenOmnibarFocusedAndLoadingStateChangesThenViewStateCorrect() = runTest {
        val omnibarState = OmnibarViewState(
            navigationChange = false,
            omnibarText = QUERY,
            forceExpand = false,
            shouldMoveCaretToEnd = true,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))
        testee.onOmnibarFocusChanged(true, QUERY)
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    privacyOn = true,
                    progress = 100,
                    url = SERP_URL,
                ),
            ) {},
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == SEARCH)
            assertTrue(viewState.expandedAnimated == omnibarState.forceExpand)
            assertTrue(viewState.omnibarText == QUERY)
            assertTrue(viewState.updateOmnibarText)
            assertTrue(viewState.shouldMoveCaretToEnd == omnibarState.shouldMoveCaretToEnd)
        }
    }

    @Test
    fun whenTrackersAnimationStartedAndOmnibarNotFocusedThenCommandAndViewStateCorrect() = runTest {
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

    @Test
    fun whenOmnibarFocusedAndAnimationPlayingThenAnimationsCanceled() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.onOmnibarFocusChanged(true, RANDOM_URL)

        testee.commands().test {
            awaitItem().assertCommand(Command.CancelTrackersAnimation::class)
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

    private fun givenSiteLoaded(loadedUrl: String) {
        testee.onViewModeChanged(ViewMode.Browser(loadedUrl))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    privacyOn = true,
                    progress = 100,
                    url = loadedUrl,
                ),
            ) {},
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
