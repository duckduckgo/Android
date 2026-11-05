package com.duckduckgo.app.browser.omnibar

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.app.browser.AddressDisplayFormatter
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationManager
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.browser.menu.BrowserMenuHighlight
import com.duckduckgo.app.browser.menu.BrowserViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.LaunchNativeInput
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.EnabledState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.Search
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.browser.omnibar.model.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.model.StateChange
import com.duckduckgo.app.browser.progressbar.ProgressBarUpgradeFeature
import com.duckduckgo.app.browser.urldisplay.UrlDisplayRepository
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
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels
import com.duckduckgo.serp.logos.api.SerpLogo
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.reflect.KClass

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

    private val highlightFlow = MutableStateFlow(false)

    private val browserMenuHighlight: BrowserMenuHighlight = mock {
        on { shouldShowHighlightForMode(any()) } doReturn highlightFlow
    }

    private val duckChat: DuckChat = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val duckChatInputModeState: DuckChatInputModeState = mock()
    private val inputModeCapabilityFlow = MutableStateFlow(NativeInputState.InputMode.SEARCH_AND_DUCK_AI)
    private val duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow = MutableStateFlow(true)
    private val duckAiShowOmnibarShortcutInAllStatesFlow = MutableStateFlow(true)
    private val nativeChatInputEnabledFlow = MutableStateFlow(false)
    private val inputScreenUserSettingFlow = MutableStateFlow(false)
    private val activeVoiceSessionsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val isFullUrlEnabledFlow = MutableStateFlow(true)
    private val settingsDataStore: SettingsDataStore = mock()
    private val urlDisplayRepository: UrlDisplayRepository = mock()
    private val mockAddressDisplayFormatter: AddressDisplayFormatter by lazy {
        mock {
            on { getShortUrl(any()) } doAnswer { invocation ->
                val url = invocation.getArgument<String>(0)
                url.toUri().baseHost ?: url
            }
        }
    }
    private val softwareRenderingModeEnabledFlow = MutableStateFlow(false)
    private val addressBarTrackersAnimationManager: AddressBarTrackersAnimationManager = mock {
        on { softwareRenderingModeEnabled } doReturn softwareRenderingModeEnabledFlow
    }
    private val fakeProgressBarUpgradeFeature = FakeFeatureToggleFactory.create(ProgressBarUpgradeFeature::class.java)
    private val fakeAppBrandDesignUpdateToggles = FakeFeatureToggleFactory.create(AppBrandDesignUpdateToggles::class.java)

    private lateinit var fakeStandardizedLeadingIconToggle: StandardizedLeadingIconFeatureToggle
    private lateinit var fakeOmnibarPreFillKillSwitch: OmnibarPreFillKillSwitch
    private lateinit var testee: OmnibarLayoutViewModel

    private val EMPTY_URL = ""
    private val SERP_URL = "https://duckduckgo.com/?q=test&atb=v395-1-wb&ia=web"
    private val DUCK_AI_URL = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    private val DUCK_PLAYER_URL = "duck://player/21bPE0BJdOA"
    private val RANDOM_URL = "https://as.com"
    private val QUERY = "query"

    @Before
    fun before() {
        whenever(tabRepository.flowTabs).thenReturn(flowOf(emptyList()))
        whenever(tabRepository.flowSelectedTab).thenReturn(selectedTabFlow)
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(any(), any(), any(), any())).thenReturn(true)
        whenever(voiceSearchAvailability.observeVoiceSearchAvailability()).thenReturn(emptyFlow())
        whenever(duckPlayer.isDuckPlayerUri(DUCK_PLAYER_URL)).thenReturn(true)
        whenever(duckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow)
        whenever(duckAiFeatureState.showOmnibarShortcutInAllStates).thenReturn(duckAiShowOmnibarShortcutInAllStatesFlow)
        whenever(urlDisplayRepository.isFullUrlEnabled).then { isFullUrlEnabledFlow }
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(nativeChatInputEnabledFlow)
        fakeAppBrandDesignUpdateToggles.addressBar().setRawStoredState(State(enable = false))
        whenever(duckChatInputModeState.inputModeCapability).thenReturn(inputModeCapabilityFlow)
        whenever(duckChat.activeVoiceChatSessions).thenReturn(activeVoiceSessionsFlow)
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(inputScreenUserSettingFlow)
        fakeStandardizedLeadingIconToggle = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "standardizedLeadingIcon",
        ).build().create(StandardizedLeadingIconFeatureToggle::class.java)

        fakeOmnibarPreFillKillSwitch = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "omnibarPreFillKillSwitch",
        ).build().create(OmnibarPreFillKillSwitch::class.java)
        fakeOmnibarPreFillKillSwitch.self().setRawStoredState(State(enable = true))

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
            browserMenuHighlight = browserMenuHighlight,
            duckChat = duckChat,
            duckAiFeatureState = duckAiFeatureState,
            duckChatInputModeState = duckChatInputModeState,
            addressDisplayFormatter = mockAddressDisplayFormatter,
            settingsDataStore = settingsDataStore,
            urlDisplayRepository = urlDisplayRepository,
            addressBarTrackersAnimationManager = addressBarTrackersAnimationManager,
            standardizedLeadingIconToggle = fakeStandardizedLeadingIconToggle,
            omnibarPreFillKillSwitch = fakeOmnibarPreFillKillSwitch,
            progressBarUpgradeFeature = fakeProgressBarUpgradeFeature,
            appBrandDesignUpdateToggles = fakeAppBrandDesignUpdateToggles,
        )
    }

    @Test
    fun whenAddressBarRebrandToggleChangesThenViewStateIsUpdated() = runTest {
        testee.viewState.test {
            assertFalse(awaitItem().isAddressBarRebrandEnabled)

            fakeAppBrandDesignUpdateToggles.addressBar().setRawStoredState(State(enable = true))

            assertTrue(awaitItem().isAddressBarRebrandEnabled)
        }
    }

    @Test
    fun whenAddressBarRebrandEnabledBeforeViewModelCreatedThenInitialViewStateIsEnabled() = runTest {
        fakeAppBrandDesignUpdateToggles.addressBar().setRawStoredState(State(enable = true))

        initializeViewModel()

        assertTrue(testee.viewState.value.isAddressBarRebrandEnabled)
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
    fun whenViewModeChangedToCustomTabThenViewStateCorrect() = runTest {
        val expectedToolbarColor = 100
        val expectedTitle = "example"
        val expectedDomain = ""
        val expectedShowDuckPlayerIcon = false
        testee.onViewModeChanged(
            ViewMode.CustomTab(
                toolbarColor = expectedToolbarColor,
                title = expectedTitle,
                showDuckPlayerIcon = expectedShowDuckPlayerIcon,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showVoiceSearch)
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)

            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedToolbarColor, customTabMode.toolbarColor)
            assertEquals(expectedTitle, customTabMode.title)
            assertEquals(expectedDomain, customTabMode.domain) // Empty string because no URL is set
            assertEquals(expectedShowDuckPlayerIcon, customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun whenVoiceSearchAvailabilityEmitsWhileInCustomTabThenVoiceSearchStaysHidden() = runTest {
        val voiceSearchAvailabilityFlow = MutableStateFlow(false)
        whenever(voiceSearchAvailability.observeVoiceSearchAvailability()).thenReturn(voiceSearchAvailabilityFlow)
        initializeViewModel()

        testee.onViewModeChanged(ViewMode.CustomTab(100, "example", "example.com", showDuckPlayerIcon = false))

        // User toggles "Private Voice Search" on while a custom tab is active.
        voiceSearchAvailabilityFlow.value = true

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            assertFalse(viewState.showVoiceSearch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when custom tab title updates, update view mode state`() = runTest {
        val expectedTitle = "newTitle"
        val initialUrl = "https://example.com/page"
        val expectedDomain = "example.com"
        val decoration = ChangeCustomTabTitle(
            title = expectedTitle,
        )

        testee.onViewModeChanged(ViewMode.CustomTab(100, "example", "example.com", showDuckPlayerIcon = false))
        // Set URL in state so domain can be extracted
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = initialUrl,
                ),
            ),
        )
        testee.onCustomTabTitleUpdate(decoration)

        testee.viewState.test {
            val viewState = expectMostRecentItem()

            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(100, customTabMode.toolbarColor)
            assertEquals(expectedTitle, customTabMode.title)
            assertEquals(expectedDomain, customTabMode.domain)
            assertFalse(customTabMode.showDuckPlayerIcon)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when custom tab view mode changed and URL loaded then domain extracted from URL`() = runTest {
        val testUrl = "https://example.com/path/to/page"
        val expectedDomain = "example.com"

        testee.onViewModeChanged(ViewMode.CustomTab(100, "example"))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = testUrl,
                ),
            ),
        )

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedDomain, customTabMode.domain)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when custom tab URL changes to different domain then domain is dynamically updated`() = runTest {
        val initialUrl = "https://example.com/page"
        val newUrl = "https://different.com/other"
        val expectedNewDomain = "different.com"

        testee.onViewModeChanged(ViewMode.CustomTab(100, "title", "example.com", showDuckPlayerIcon = false))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = initialUrl,
                ),
            ),
        )

        testee.viewState.test {
            skipItems(1)
            testee.onExternalStateChange(
                StateChange.LoadingStateChange(
                    LoadingViewState(
                        isLoading = false,
                        trackersAnimationEnabled = false,
                        progress = 100,
                        url = newUrl,
                    ),
                ),
            )
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedNewDomain, customTabMode.domain)
            assertFalse(customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun `when custom tab URL changes to DuckPlayer URL and DuckPlayer enabled then showDuckPlayerIcon is true`() = runTest {
        val initialUrl = "https://example.com/page"
        val duckPlayerUrl = DUCK_PLAYER_URL
        val expectedDomain = "duck://player" // DuckPlayer URLs extract as "duck://player" (without the path)

        whenever(duckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.ENABLED)

        testee.onViewModeChanged(ViewMode.CustomTab(100, "title", "example.com", showDuckPlayerIcon = false))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = initialUrl,
                ),
            ),
        )

        testee.viewState.test {
            skipItems(1)
            testee.onExternalStateChange(
                StateChange.LoadingStateChange(
                    LoadingViewState(
                        isLoading = false,
                        trackersAnimationEnabled = false,
                        progress = 100,
                        url = duckPlayerUrl,
                    ),
                ),
            )
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedDomain, customTabMode.domain)
            assertTrue(customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun `when custom tab URL is blank and omnibarText is about blank then domain displays about blank`() = runTest {
        val expectedDomain = "about:blank"

        testee.onViewModeChanged(ViewMode.CustomTab(100, "title", "", showDuckPlayerIcon = false))
        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "about:blank",
                    queryOrFullUrl = "",
                    navigationChange = false,
                    forceExpand = false,
                ),
            ),
        )
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = "",
                ),
            ),
        )

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertEquals(expectedDomain, customTabMode.domain)
            assertFalse(customTabMode.showDuckPlayerIcon)
        }
    }

    @Test
    fun whenViewModeChangedToErrorThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Error)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.Globe)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.Error)
        }
    }

    @Test
    fun whenViewModeChangedToSSLWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.SSLWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.Globe)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.SSLWarning)
        }
    }

    @Test
    fun whenViewModeChangedToMaliciousSiteWarningThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.MaliciousSiteWarning)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.Globe)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.MaliciousSiteWarning)
        }
    }

    @Test
    fun whenViewModeChangedToNewTabThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.Search)
            assertFalse(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.NewTab)
        }
    }

    @Test
    fun whenViewModeChangedToBrowserThenViewStateCorrect() = runTest {
        testee.onViewModeChanged(ViewMode.Browser(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.Search)
            assertTrue(viewState.scrollingEnabled)
            assertTrue(viewState.viewMode is ViewMode.Browser)
        }
    }

    @Test
    fun whenViewModeChangedToPdfThenLeadingIconIsPrivacyShield() = runTest {
        testee.onViewModeChanged(ViewMode.Pdf(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.PrivacyShield)
            assertTrue(viewState.viewMode is ViewMode.Pdf)
        }
    }

    @Test
    fun whenViewModeChangedToPdfBeforeUrlPropagatedThenLeadingIconIsPrivacyShield() = runTest {
        // No prior loading state means _viewState.value.url is empty — the shield must still appear.
        testee.onViewModeChanged(ViewMode.Pdf(RANDOM_URL))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.PrivacyShield)
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
        val clearQuery = true
        val deleteLastCharacter = false
        testee.onInputStateChanged(query, clearQuery, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == query)
            assertFalse(viewState.updateOmnibarText)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showBrowserMenu)
        }
    }

    @Test
    fun whenInputStateChangedAndQueryNotEmptyThenViewStateCorrect() = runTest {
        val query = "query"
        val clearQuery = true
        val deleteLastCharacter = false
        testee.onInputStateChanged(query, clearQuery, deleteLastCharacter)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.omnibarText == query)
            assertFalse(viewState.updateOmnibarText)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showBrowserMenu)
        }
    }

    @Test
    fun whenLoadingStateChangesInEmptyUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(EMPTY_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == EMPTY_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.Search)
        }
    }

    @Test
    fun whenLoadingStateChangesInUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == RANDOM_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.PrivacyShield)
        }
    }

    @Test
    fun whenLoadingStateChangesInSERPUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(SERP_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == SERP_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.Dax)
        }
    }

    @Test
    fun whenLoadingStateChangesInDuckPlayerUrlThenViewStateCorrect() = runTest {
        givenSiteLoaded(DUCK_PLAYER_URL)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.url == DUCK_PLAYER_URL)
            assertTrue(viewState.leadingIconState == LeadingIconState.DuckPlayer)
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
    fun whenTrackersAnimationStartedAndOmnibarNotFocusedThenCommandAndViewStateCorrect() = runTest {
        val trackers = givenSomeTrackers()
        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState == LeadingIconState.PrivacyShield)
        }

        testee.commands().test {
            awaitItem().assertCommand(Command.StartTrackersAnimation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSoftwareRenderingModeEnabledThenStartTrackersAnimationCommandForwardsFlag() = runTest {
        softwareRenderingModeEnabledFlow.value = true
        initializeViewModel()

        testee.viewState.test {
            awaitItem()
        }

        val trackers = givenSomeTrackers()
        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.StartTrackersAnimation)
            assertTrue((command as Command.StartTrackersAnimation).useSoftwareRenderingMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSoftwareRenderingModeDisabledThenStartTrackersAnimationCommandForwardsFlag() = runTest {
        softwareRenderingModeEnabledFlow.value = false
        initializeViewModel()

        testee.viewState.test {
            awaitItem()
        }

        val trackers = givenSomeTrackers()
        testee.onAnimationStarted(Decoration.LaunchTrackersAnimation(trackers))

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.StartTrackersAnimation)
            assertFalse((command as Command.StartTrackersAnimation).useSoftwareRenderingMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when highlight flow emits true, then viewState shows highlight`() = runTest {
        highlightFlow.value = true

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showBrowserMenuHighlight)
        }
    }

    @Test
    fun `when highlight flow emits false, then viewState does not show highlight`() = runTest {
        highlightFlow.value = false

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showBrowserMenuHighlight)
        }
    }

    @Test
    fun `when onViewModeChanged NewTab, then highlight is queried for NewTab mode`() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)

        testee.viewState.test {
            awaitItem()
            verify(browserMenuHighlight).shouldShowHighlightForMode(eq(BrowserViewMode.NewTab))
        }
    }

    @Test
    fun `when onViewModeChanged CustomTab, then highlight is queried for CustomTab mode`() = runTest {
        testee.onViewModeChanged(ViewMode.CustomTab(0, null, null, false))

        testee.viewState.test {
            awaitItem()
            verify(browserMenuHighlight).shouldShowHighlightForMode(eq(BrowserViewMode.CustomTab))
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
    fun whenChatEntryPointDisabledThenShowChatMenuFalse() = runTest {
        duckAiShowOmnibarShortcutInAllStatesFlow.value = false
        duckAiShowOmnibarShortcutOnNtpAndOnFocusFlow.value = false

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showChatMenu)
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
    fun whenInputStateChangedWithEmptyQueryThenShowChatMenuTrue() = runTest {
        testee.onInputStateChanged("", clearQuery = true, deleteLastCharacter = false)
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
    }

    @Test
    fun whenInputStateChangedWithNonEmptyQueryThenShowChatMenuTrue() = runTest {
        testee.onInputStateChanged(QUERY, clearQuery = true, deleteLastCharacter = false)
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showChatMenu)
        }
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
    fun whenSearchOnlyThenShowContextualSheetIconFalse() = runTest {
        inputModeCapabilityFlow.value = NativeInputState.InputMode.SEARCH_ONLY

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showContextualSheetIcon)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and no URL then input screen launched with draft query`() = runTest {
        testee.onInputStateChanged(query = "draft", clearQuery = false, deleteLastCharacter = false)

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            assertEquals("draft", (command as LaunchNativeInput).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and DDG URL then input screen launched with search query`() = runTest {
        givenSiteLoaded(SERP_URL)
        val omnibarViewState = OmnibarViewState(omnibarText = "test", queryOrFullUrl = "test")
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            assertEquals("test", (command as LaunchNativeInput).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked and random URL then input screen launched with full URL`() = runTest {
        isFullUrlEnabledFlow.emit(false)
        initializeViewModel()
        givenSiteLoaded(RANDOM_URL)
        val omnibarViewState = OmnibarViewState(omnibarText = RANDOM_URL.toUri().baseHost!!, queryOrFullUrl = RANDOM_URL)
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            assertEquals(RANDOM_URL, (command as LaunchNativeInput).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when input text click catcher clicked before a submitted URL is loaded then input screen shows submitted URL`() = runTest {
        isFullUrlEnabledFlow.emit(false)
        initializeViewModel()
        givenSiteLoaded(RANDOM_URL)
        val submitted = OmnibarViewState(omnibarText = "example.com", queryOrFullUrl = "example.com")
        testee.onExternalStateChange(StateChange.OmnibarStateChange(submitted))

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            assertEquals("example.com", (command as LaunchNativeInput).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when pre-fill kill switch disabled then a submitted URL still pre-fills the loaded URL`() = runTest {
        isFullUrlEnabledFlow.emit(false)
        initializeViewModel()
        fakeOmnibarPreFillKillSwitch.self().setRawStoredState(State(enable = false))
        givenSiteLoaded(RANDOM_URL)
        val submitted = OmnibarViewState(omnibarText = "example.com", queryOrFullUrl = "example.com")
        testee.onExternalStateChange(StateChange.OmnibarStateChange(submitted))

        testee.onTextInputClickCatcherClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            assertEquals(RANDOM_URL, (command as LaunchNativeInput).query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedAndFullUrlEnabledThenAddressDisplayFormatterNotCalled() = runTest {
        val url = "https://example.com/test.html"
        val formattedUrl = "https://example.com/test.html"
        val omnibarViewState = OmnibarViewState(omnibarText = url, queryOrFullUrl = url)
        isFullUrlEnabledFlow.emit(true)

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(formattedUrl, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
            verifyNoInteractions(mockAddressDisplayFormatter)
        }
    }

    @Test
    fun whenExternalOmnibarStateChangedWithForceRenderAndNotDDGUrlAndFullUrlEnabledThenOmnibarTextIsFullUrl() = runTest {
        val omnibarViewState = OmnibarViewState(omnibarText = RANDOM_URL, queryOrFullUrl = RANDOM_URL)
        isFullUrlEnabledFlow.emit(true)

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
        isFullUrlEnabledFlow.emit(false)

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
        isFullUrlEnabledFlow.emit(true)

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
        isFullUrlEnabledFlow.emit(false)

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

    @Test
    fun `when set draft and current state is NTP, then draft text applied`() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)
        val expected = "test"
        testee.setDraftTextIfNtpOrSerp(expected)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(expected, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun `when set draft and current state is SERP, then draft text applied`() = runTest {
        givenSiteLoaded(SERP_URL)
        val expected = "test"
        testee.setDraftTextIfNtpOrSerp(expected)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(expected, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun `when set empty draft and current state is SERP, then omnibar text not overwritten`() = runTest {
        givenSiteLoaded(SERP_URL)
        val originalQuery = "cats"
        testee.onInputStateChanged(query = originalQuery, clearQuery = false, deleteLastCharacter = false)

        testee.setDraftTextIfNtpOrSerp("")

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(originalQuery, viewState.omnibarText)
        }
    }

    @Test
    fun `when set empty draft and current state is NTP, then omnibar text not overwritten`() = runTest {
        testee.onViewModeChanged(ViewMode.NewTab)
        testee.onInputStateChanged(query = "partial", clearQuery = false, deleteLastCharacter = false)

        testee.setDraftTextIfNtpOrSerp("")

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("partial", viewState.omnibarText)
        }
    }

    @Test
    fun `when set draft and current state is a web page, then draft text not applied`() = runTest {
        val omnibarState = OmnibarViewState(
            navigationChange = false,
            omnibarText = RANDOM_URL,
            forceExpand = false,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    trackersAnimationEnabled = true,
                    progress = 100,
                    url = RANDOM_URL,
                ),
            ),
        )

        val expected = RANDOM_URL
        testee.setDraftTextIfNtpOrSerp("some draft text")

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(expected, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun `when omnibar type is SPLIT then show buttons is false`() = runTest {
        whenever(settingsDataStore.omnibarType).thenReturn(OmnibarType.SPLIT)
        initializeViewModel()

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showTabsMenu)
            assertFalse(viewState.showFireIcon)
            assertFalse(viewState.showBrowserMenu)
        }
    }

    @Test
    fun `when omnibar type is SINGLE_TOP then show buttons is true`() = runTest {
        whenever(settingsDataStore.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)
        initializeViewModel()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
        }
    }

    @Test
    fun `when omnibar type is SINGLE_BOTTOM then show buttons is true`() = runTest {
        whenever(settingsDataStore.omnibarType).thenReturn(OmnibarType.SINGLE_BOTTOM)
        initializeViewModel()

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showTabsMenu)
            assertTrue(viewState.showFireIcon)
            assertTrue(viewState.showBrowserMenu)
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

    private fun givenDuckAILoaded() {
        testee.onViewModeChanged(ViewMode.DuckAI)
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = true,
                    trackersAnimationEnabled = true,
                    progress = 100,
                    url = DUCK_AI_URL,
                ),
            ),
        )
    }

    @Test
    fun whenAdBlockingAnimationStartedAndNotFocusedThenStartAdBlockingAnimationCommandSent() = runTest {
        testee.onAnimationStarted(Decoration.LaunchAdBlockingAnimation(icon = 1, text = 2))

        testee.commands().test {
            expectMostRecentItem().assertCommand(Command.StartAdBlockingAnimation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAdBlockingAnimationStartedInCustomTabThenSuppressedCommandSent() = runTest {
        // The badge is not shown in custom tabs; instead it signals suppression so the browser
        // releases its exclusivity claim and the tracker/cookie animations can still run.
        testee.onViewModeChanged(ViewMode.CustomTab(100, "example", "example.com", showDuckPlayerIcon = false))

        testee.onAnimationStarted(Decoration.LaunchAdBlockingAnimation(icon = 1, text = 2))

        testee.commands().test {
            expectMostRecentItem().assertCommand(Command.AdBlockingAnimationSuppressed::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun givenSomeTrackers(): List<Entity> {
        val network = TestingEntity("Network", "Network", 1.0)
        val majorNetwork = TestingEntity("MajorNetwork", "MajorNetwork", Entity.MAJOR_NETWORK_PREVALENCE + 1)
        return listOf(network, majorNetwork)
    }

    private fun Command.assertCommand(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }

    @Test
    fun whenOmnibarNotFocusedAndBlankPageFromOtherTabThenPersistOmbinarText() = runTest {
        initializeViewModel()
        val omnibarState = OmnibarViewState(
            navigationChange = false,
            omnibarText = "about:blank",
            forceExpand = false,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("about:blank", viewState.omnibarText)
        }
    }

    @Test
    fun whenExternalStateChangeWithEasterEggLogoThenLeadingIconStateUpdated() = runTest {
        initializeViewModel()

        val logoUrl = "https://example.com/logo.png"
        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = logoUrl, isFavourite = false),
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(logoUrl, easterEggState.logoUrl)
        }
    }

    @Test
    fun whenExternalStateChangeWithNormalLogoThenLeadingIconStateIsNotEasterEgg() = runTest {
        initializeViewModel()

        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.Normal,
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            assertTrue(viewState.leadingIconState == Search)
        }
    }

    @Test
    fun whenExternalStateChangeWithNullLogoThenLeadingIconStateIsNotEasterEgg() = runTest {
        initializeViewModel()

        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = null,
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            assertTrue(viewState.leadingIconState == Search)
        }
    }

    @Test
    fun whenNavigationChangeThenLeadingIconStateNotUpdatedWithLogo() = runTest {
        initializeViewModel()

        val initialLeadingIconState = testee.viewState.value.leadingIconState

        val logoUrl = "https://example.com/logo.png"
        val omnibarViewState = OmnibarViewState(
            navigationChange = true,
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = logoUrl, isFavourite = false),
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            // Navigation and text updates work
            assertTrue(viewState.expanded)
            assertTrue(viewState.expandedAnimated)
            assertTrue(viewState.omnibarText == QUERY)
            assertTrue(viewState.updateOmnibarText)
            // BUT leadingIconState logic is completely bypassed - it doesn't get updated at all
            assertEquals(initialLeadingIconState, viewState.leadingIconState)
            assertFalse(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
        }
    }

    @Test
    fun whenForceRenderThenFollowsEnabledPath() = runTest {
        initializeViewModel()

        val logoUrl = "https://example.com/logo.png"
        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            queryOrFullUrl = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = logoUrl, isFavourite = false),
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState, forceRender = true))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(logoUrl, easterEggState.logoUrl)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenLogoClickedAndLeadingIconIsEasterEggLogoThenEasterEggLogoClickedCommandSent() = runTest {
        val logoUrl = "https://example.com/logo.png"
        initializeViewModel()

        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = logoUrl, isFavourite = false),
        )

        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.onLogoClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.EasterEggLogoClicked)
            assertEquals(logoUrl, (command as Command.EasterEggLogoClicked).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoClickedAndLeadingIconIsNotEasterEggLogoThenNoCommandSent() = runTest {
        testee.onLogoClicked()

        testee.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenInputStateChangedAndClearingQueryThenHasQueryChangedTruePassedToVoiceSearchAvailability() = runTest {
        var capturedHasQueryChanged: Boolean? = null
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(any(), any(), any(), any())).thenAnswer { invocation ->
            capturedHasQueryChanged = invocation.getArgument(2)
            true
        }

        // First set a non-empty query
        testee.onInputStateChanged("initial", clearQuery = false, deleteLastCharacter = false)
        // Then clear the query which should set hasQueryChanged = true because updatedQuery retains previous value
        testee.onInputStateChanged("", clearQuery = true, deleteLastCharacter = false)

        testee.viewState.test {
            awaitItem()
            assertTrue(capturedHasQueryChanged == true)
        }
    }

    @Test
    fun `when custom tab URL changes to simulated YouTube NoCookie and DuckPlayer enabled then showDuckPlayerIcon is true`() = runTest {
        val youtubeNoCookieUrl = "http://youtube-nocookie.com/videoID=1234"
        val duckPlayerUrl = "duck://player/1234"

        whenever(duckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(true)
        whenever(duckPlayer.createDuckPlayerUriFromYoutubeNoCookie(any())).thenReturn(duckPlayerUrl)
        whenever(duckPlayer.isDuckPlayerUri(duckPlayerUrl)).thenReturn(true)
        whenever(duckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.ENABLED)

        testee.onViewModeChanged(ViewMode.CustomTab(100, "title", "example.com", showDuckPlayerIcon = false))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = duckPlayerUrl,
                ),
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertTrue(customTabMode.showDuckPlayerIcon)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when custom tab URL is DuckPlayer URL and DuckPlayer disabled then showDuckPlayerIcon is false`() = runTest {
        val duckPlayerUrl = DUCK_PLAYER_URL

        whenever(duckPlayer.isDuckPlayerUri(duckPlayerUrl)).thenReturn(true)
        whenever(duckPlayer.getDuckPlayerState()).thenReturn(DuckPlayer.DuckPlayerState.DISABLED)

        testee.onViewModeChanged(ViewMode.CustomTab(100, "title", "example.com", showDuckPlayerIcon = false))
        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(
                    isLoading = false,
                    trackersAnimationEnabled = false,
                    progress = 100,
                    url = duckPlayerUrl,
                ),
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.CustomTab)
            val customTabMode = viewState.viewMode as ViewMode.CustomTab
            assertFalse(customTabMode.showDuckPlayerIcon)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDuckAiHeaderPressedThenNativeInputShown() = runTest {
        testee.onDuckAiHeaderClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LaunchNativeInput)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDuckAILoadedAndVoiceSessionActiveOnSelectedTabThenSidebarHiddenButHeaderShown() = runTest {
        selectedTabFlow.value = TabEntity(tabId = "tab1", position = 0)
        activeVoiceSessionsFlow.value = setOf("tab1")
        initializeViewModel()

        givenDuckAILoaded()

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showDuckAISidebar)
            assertTrue(viewState.showDuckAIHeader)
        }
    }

    @Test
    fun whenDuckAILoadedAndVoiceSessionActiveOnDifferentTabThenSidebarShown() = runTest {
        selectedTabFlow.value = TabEntity(tabId = "tab1", position = 0)
        activeVoiceSessionsFlow.value = setOf("other-tab")
        initializeViewModel()

        givenDuckAILoaded()

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showDuckAISidebar)
            assertTrue(viewState.showDuckAIHeader)
        }
    }

    @Test
    fun whenDuckAILoadedAndVoiceSessionStartsOnSelectedTabThenSidebarHides() = runTest {
        selectedTabFlow.value = TabEntity(tabId = "tab1", position = 0)
        initializeViewModel()
        givenDuckAILoaded()

        activeVoiceSessionsFlow.value = setOf("tab1")

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showDuckAISidebar)
            assertTrue(viewState.showDuckAIHeader)
        }
    }

    @Test
    fun whenDuckAILoadedAndVoiceSessionEndsOnSelectedTabThenSidebarReappears() = runTest {
        selectedTabFlow.value = TabEntity(tabId = "tab1", position = 0)
        activeVoiceSessionsFlow.value = setOf("tab1")
        initializeViewModel()
        givenDuckAILoaded()

        activeVoiceSessionsFlow.value = emptySet()

        testee.viewState.test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showDuckAISidebar)
            assertTrue(viewState.showDuckAIHeader)
        }
    }

    @Test
    fun whenOmnibarTextIsAboutBlankAndForceRenderThenOmnibarTextNotUpdated() = runTest {
        initializeViewModel()

        givenSiteLoaded(RANDOM_URL)

        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "about:blank",
                    queryOrFullUrl = RANDOM_URL,
                    navigationChange = false,
                    forceExpand = false,
                ),
                forceRender = true,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("about:blank", viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarTextIsAboutBlankAndForceRenderWithEasterEggThenOmnibarTextNotUpdated() = runTest {
        initializeViewModel()

        givenSiteLoaded(RANDOM_URL)

        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "about:blank",
                    queryOrFullUrl = RANDOM_URL,
                    navigationChange = false,
                    forceExpand = false,
                ),
                forceRender = true,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("about:blank", viewState.omnibarText)
        }
    }

    @Test
    fun whenOmnibarTextIsNotAboutBlankAndForceRenderThenOmnibarTextUpdated() = runTest {
        initializeViewModel()

        givenSiteLoaded(RANDOM_URL)

        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "",
                    queryOrFullUrl = RANDOM_URL,
                    navigationChange = false,
                    forceExpand = false,
                ),
                forceRender = true,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(RANDOM_URL, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarTextIsNotAboutBlankAndForceRenderWithEasterEggThenOmnibarTextUpdated() = runTest {
        initializeViewModel()

        givenSiteLoaded(RANDOM_URL)

        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "",
                    queryOrFullUrl = RANDOM_URL,
                    navigationChange = false,
                    forceExpand = false,
                ),
                forceRender = true,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(RANDOM_URL, viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    @Test
    fun whenOmnibarTextIsAboutBlankWithShortUrlAndForceRenderThenOmnibarTextNotUpdated() = runTest {
        isFullUrlEnabledFlow.value = false
        initializeViewModel()

        givenSiteLoaded(RANDOM_URL)

        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = "about:blank",
                    queryOrFullUrl = RANDOM_URL,
                    navigationChange = false,
                    forceExpand = false,
                ),
                forceRender = true,
            ),
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals("about:blank", viewState.omnibarText)
            assertTrue(viewState.updateOmnibarText)
        }
    }

    // Favourite Logo Tests

    @Test
    fun whenNavigatingFromFavouriteSerpLogoToNonSerpUrlThenPrivacyShieldShown() = runTest {
        val favouriteLogoUrl = "https://example.com/favourite-logo.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)
        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = QUERY,
                    serpLogo = SerpLogo.EasterEgg(logoUrl = favouriteLogoUrl, isFavourite = true),
                ),
            ),
        )

        val serpViewState = testee.viewState.first { it.leadingIconState is LeadingIconState.EasterEggLogo }
        assertTrue((serpViewState.leadingIconState as LeadingIconState.EasterEggLogo).isFavourite)

        givenSiteLoaded(RANDOM_URL)

        val nonSerpViewState = testee.viewState.first { it.url == RANDOM_URL }
        assertTrue(nonSerpViewState.leadingIconState is LeadingIconState.PrivacyShield)
    }

    @Test
    fun whenFavouriteLogoReceivedThenFavouriteLogoUsedDirectly() = runTest {
        val favouriteLogoUrl = "https://example.com/favourite-logo.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)

        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = favouriteLogoUrl, isFavourite = true),
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(favouriteLogoUrl, easterEggState.logoUrl)
            assertTrue(easterEggState.isFavourite)
        }
    }

    @Test
    fun whenSerpEasterEggReceivedThenEasterEggShown() = runTest {
        val serpEasterEggUrl = "https://example.com/serp-easter-egg.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)

        // SERP returns an Easter Egg logo
        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = serpEasterEggUrl, isFavourite = false),
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(serpEasterEggUrl, easterEggState.logoUrl)
        }
    }

    @Test
    fun whenEasterEggLogoSetAndViewModeChangesToBrowserThenEasterEggPreserved() = runTest {
        val serpEasterEggUrl = "https://example.com/serp-easter-egg.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)

        // SERP returns an Easter Egg logo
        val omnibarViewState = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = serpEasterEggUrl, isFavourite = false),
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewState))

        // Verify Easter Egg is shown
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
        }

        // Now view mode changes to Browser
        testee.onViewModeChanged(ViewMode.Browser(null))

        // Easter Egg should still be preserved
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(serpEasterEggUrl, easterEggState.logoUrl)
        }
    }

    @Test
    fun whenSerpLoadingStateChangesThenFavouriteLogoIsPreserved() = runTest {
        val favouriteLogoUrl = "https://example.com/favourite-logo.png"
        initializeViewModel()
        givenSiteLoaded(SERP_URL)
        testee.onExternalStateChange(
            StateChange.OmnibarStateChange(
                OmnibarViewState(
                    omnibarText = QUERY,
                    serpLogo = SerpLogo.EasterEgg(logoUrl = favouriteLogoUrl, isFavourite = true),
                ),
            ),
        )

        testee.onExternalStateChange(
            StateChange.LoadingStateChange(
                LoadingViewState(isLoading = true, url = SERP_URL),
            ),
        )

        val viewState = testee.viewState.first { it.leadingIconState is LeadingIconState.EasterEggLogo }
        val easterEggLogo = viewState.leadingIconState as LeadingIconState.EasterEggLogo
        assertEquals(favouriteLogoUrl, easterEggLogo.logoUrl)
        assertTrue(easterEggLogo.isFavourite)
    }

    @Test
    fun whenSerpLogoIsNullThenExistingEasterEggPreserved() = runTest {
        val serpEasterEggUrl = "https://example.com/serp-easter-egg.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)

        // First, set an Easter Egg logo
        val omnibarViewStateWithLogo = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = serpEasterEggUrl, isFavourite = false),
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewStateWithLogo))

        // Verify Easter Egg is shown
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
        }

        // Now send an update with serpLogo = null (simulating pending extraction)
        val omnibarViewStateWithNullLogo = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = null,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewStateWithNullLogo))

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                "Expected EasterEggLogo but got ${viewState.leadingIconState}",
                viewState.leadingIconState is LeadingIconState.EasterEggLogo,
            )
            val easterEggState = viewState.leadingIconState as LeadingIconState.EasterEggLogo
            assertEquals(serpEasterEggUrl, easterEggState.logoUrl)
        }
    }

    @Test
    fun whenSerpLogoIsNormalThenEasterEggCleared() = runTest {
        val serpEasterEggUrl = "https://example.com/serp-easter-egg.png"
        initializeViewModel()

        givenSiteLoaded(SERP_URL)

        // First, set an Easter Egg logo
        val omnibarViewStateWithLogo = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.EasterEgg(logoUrl = serpEasterEggUrl, isFavourite = false),
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewStateWithLogo))

        // Verify Easter Egg is shown
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.EasterEggLogo)
        }

        // Now send an update with serpLogo = Normal (explicitly no Easter Egg)
        val omnibarViewStateWithNormalLogo = OmnibarViewState(
            omnibarText = QUERY,
            serpLogo = SerpLogo.Normal,
        )
        testee.onExternalStateChange(StateChange.OmnibarStateChange(omnibarViewStateWithNormalLogo))

        // Easter Egg should be cleared, showing Dax
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.leadingIconState is LeadingIconState.Dax)
        }
    }

    @Test
    fun whenCustomTabUrlLongClickedWithValidUrlThenCopyUrlToClipboardCommandSent() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.onCustomTabUrlLongClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.CopyUrlToClipboard)
            assertEquals(RANDOM_URL, (command as Command.CopyUrlToClipboard).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCustomTabUrlLongClickedWithValidUrlThenCopyUrlPixelFired() = runTest {
        givenSiteLoaded(RANDOM_URL)

        testee.onCustomTabUrlLongClicked()

        verify(pixel).fire(CustomTabPixelNames.CUSTOM_TABS_COPY_URL)
    }

    @Test
    fun whenCustomTabUrlLongClickedWithEmptyUrlThenNoCommandSent() = runTest {
        givenSiteLoaded(EMPTY_URL)

        testee.onCustomTabUrlLongClicked()

        testee.commands().test {
            expectNoEvents()
        }
    }

    @Test
    fun whenCustomTabUrlLongClickedWithEmptyUrlThenCopyUrlPixelNotFired() = runTest {
        givenSiteLoaded(EMPTY_URL)

        testee.onCustomTabUrlLongClicked()

        verify(pixel, never()).fire(CustomTabPixelNames.CUSTOM_TABS_COPY_URL)
    }

    @Test
    fun whenCustomTabUrlLongClickedThenCommandContainsCurrentViewStateUrl() = runTest {
        val expectedUrl = "https://example.com/test"
        givenSiteLoaded(expectedUrl)

        testee.onCustomTabUrlLongClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.CopyUrlToClipboard)
            assertEquals(expectedUrl, (command as Command.CopyUrlToClipboard).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSetLockedTrueAndFireButtonNotHighlightedThenEnabledStateIsNone() = runTest {
        testee.setLocked(true)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(EnabledState.NONE, viewState.enabledState)
        }
    }

    @Test
    fun whenSetLockedTrueAndFireButtonHighlightedThenEnabledStateIsFireButtonOnly() = runTest {
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = true, privacyShield = false))
        testee.setLocked(true)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(EnabledState.FIRE_BUTTON_ONLY, viewState.enabledState)
        }
    }

    @Test
    fun whenSetLockedFalseThenEnabledStateIsAll() = runTest {
        testee.setLocked(true)
        testee.setLocked(false)

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(EnabledState.ALL, viewState.enabledState)
        }
    }

    @Test
    fun whenFireIconPressedAndLockedThenHighlightPreserved() = runTest {
        testee.onHighlightItem(Decoration.HighlightOmnibarItem(fireButton = true, privacyShield = false))
        testee.setLocked(true)
        testee.onFireIconPressed(true)

        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(
                viewState.highlightFireButton == HighlightableButton.Visible(
                    enabled = true,
                    highlighted = true,
                ),
            )
            assertFalse(viewState.scrollingEnabled)
            assertEquals(EnabledState.FIRE_BUTTON_ONLY, viewState.enabledState)
        }
    }

    @Test
    fun whenFireIconPressedAndNotLockedForOnboardingThenHighlightCleared() = runTest {
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
    }

    @Test
    fun whenUpgradeAndAnimationConfigEnabledThenAnimationUpdateFlagIsTrue() = runTest {
        fakeProgressBarUpgradeFeature.behaviourUpdate().setRawStoredState(Toggle.State(enable = true))
        fakeProgressBarUpgradeFeature.animationConfigUpdate().setRawStoredState(Toggle.State(enable = true))
        initializeViewModel()

        testee.viewState.test {
            assertTrue(awaitItem().isAnimationUpdateEnabled)
        }
    }

    @Test
    fun whenUpgradeEnabledButAnimationConfigDisabledThenAnimationUpdateFlagIsFalse() = runTest {
        fakeProgressBarUpgradeFeature.behaviourUpdate().setRawStoredState(Toggle.State(enable = true))
        fakeProgressBarUpgradeFeature.animationConfigUpdate().setRawStoredState(Toggle.State(enable = false))
        initializeViewModel()

        testee.viewState.test {
            assertFalse(awaitItem().isAnimationUpdateEnabled)
        }
    }

    @Test
    fun whenUpgradeDisabledButAnimationConfigEnabledThenAnimationUpdateFlagIsFalse() = runTest {
        fakeProgressBarUpgradeFeature.behaviourUpdate().setRawStoredState(Toggle.State(enable = false))
        fakeProgressBarUpgradeFeature.animationConfigUpdate().setRawStoredState(Toggle.State(enable = true))
        initializeViewModel()

        testee.viewState.test {
            assertFalse(awaitItem().isAnimationUpdateEnabled)
        }
    }
}
