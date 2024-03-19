package com.duckduckgo.app.browser.customtabs

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import app.cash.turbine.test
import com.duckduckgo.app.global.intentText
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntentDispatcherViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockCustomTabDetector: CustomTabDetector = mock()
    private val mockIntent: Intent = mock()

    private lateinit var testee: IntentDispatcherViewModel

    @Before
    fun before() {
        testee = IntentDispatcherViewModel(mockCustomTabDetector, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenIntentReceivedWithSessionThenCustomTabIsRequested() = runTest {
        val hasSession = true
        val text = "url"
        val toolbarColor = 100
        val defaultColor = 0
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)).thenReturn(hasSession)
        whenever(mockIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)).thenReturn(toolbarColor)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, defaultColor)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(toolbarColor, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithoutSessionThenCustomTabIsNotRequested() = runTest {
        val hasSession = false
        val text = "url"
        val defaultColor = 0
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)).thenReturn(hasSession)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, defaultColor)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(defaultColor, state.toolbarColor)
        }
    }
}
