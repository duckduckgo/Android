package com.duckduckgo.app.browser.customtabs

import app.cash.turbine.test
import com.duckduckgo.app.browser.customtabs.CustomTabViewModel.Companion.CUSTOM_TAB_NAME_PREFIX
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.customtabs.api.CustomTabDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class CustomTabViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockCustomTabDetector: CustomTabDetector = mock()
    private val mockPixel: Pixel = mock()

    private lateinit var testee: CustomTabViewModel

    @Before
    fun before() {
        testee = CustomTabViewModel(mockCustomTabDetector, mockPixel, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenCustomTabCreatedThenPixelFired() = runTest {
        testee.onCustomTabCreated("url", 100)

        testee.viewState.test {
            val state = awaitItem()
            assertEquals(100, state.toolbarColor)
            assertEquals("url", state.url)
            assertNotNull(state.tabId)
            assertTrue(state.tabId.startsWith(CUSTOM_TAB_NAME_PREFIX))
            verify(mockPixel).fire(CustomTabPixelNames.CUSTOM_TABS_OPENED)
        }
    }

    @Test
    fun whenCustomTabShownThenCustomTabDetectorSetToTrue() {
        testee.onShowCustomTab()

        verify(mockCustomTabDetector).setCustomTab(true)
    }

    @Test
    fun whenCustomTabClosedThenCustomTabDetectorSetToFalse() {
        testee.onCloseCustomTab()

        verify(mockCustomTabDetector).setCustomTab(false)
    }
}
