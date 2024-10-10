package com.duckduckgo.app.browser.omnibar

import app.cash.turbine.test
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import java.lang.String
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OmnibarLayoutViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val tabRepository: TabRepository = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
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

        verify(voiceSearchPixelLogger.log())
    }

    @Test
    fun whenViewModelAttachedAndVoiceSearchNotSupportedThenPixelLogged() = runTest {
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(false)

        testee.onAttachedToWindow()

        verify(voiceSearchPixelLogger.log(), times(0))
    }

    @Test
    fun whenOmnibarFocusedThenCancelTrackersAnimationCommandSent() = runTest {
        testee.onOmnibarFocusChanged(true, "query")

        testee.commands().test {
            awaitItem().assertCommand(Command.CancelTrackersAnimation::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommand(expectedType: KClass<out Command>) {
        assertTrue(String.format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
