package com.duckduckgo.app.browser.omnibar

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
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
import org.mockito.kotlin.times
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
            assertFalse(viewState.showControls)
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
            assertTrue(viewState.showControls)
            assertTrue(viewState.highlightPrivacyShield == HighlightableButton.Gone)
            assertTrue(viewState.showVoiceSearch)
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndDDGUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded("https://duckduckgo.com/?q=test&atb=v395-1-wb&ia=web")
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.DAX)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showControls)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndDuckPlayerUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded("duck://player/21bPE0BJdOA")
        whenever(duckPlayer.isDuckPlayerUri(any())).thenReturn(true)
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.DUCK_PLAYER)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showControls)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndUrlEmptyThenViewStateCorrect() = runTest {
        givenSiteLoaded("")
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showControls)
            assertTrue(viewState.highlightFireButton == HighlightableButton.Visible(highlighted = false))
            assertTrue(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOmnibarNotFocusedAndUrlLoadedThenViewStateCorrect() = runTest {
        givenSiteLoaded("https://as.com")
        testee.onOmnibarFocusChanged(false, "")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.hasFocus)
            assertFalse(viewState.expanded)
            assertTrue(viewState.leadingIconState == LeadingIconState.PRIVACY_SHIELD)
            assertFalse(viewState.showClearButton)
            assertTrue(viewState.showControls)
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
            assertFalse(viewState.showControls)
        }
    }

    @Test
    fun whenViewModeChangedToErrorThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.GLOBE)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Browser("example.com"))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToErrorAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "example.com")
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "example.com")
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "example.com")
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserAndFocusThenViewStateCorrect() = runTest {
        testee.onOmnibarFocusChanged(true, "example.com")
        testee.onViewModeChanged(ViewMode.Browser("example.com"))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.SEARCH)
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

    private fun Command.assertCommand(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
