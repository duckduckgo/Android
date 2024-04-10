package com.duckduckgo.app.browser.customtabs

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import app.cash.turbine.test
import com.duckduckgo.app.global.intentText
import com.duckduckgo.autofill.api.emailprotection.EmailProtectionLinkVerifier
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntentDispatcherViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockCustomTabDetector: CustomTabDetector = mock()
    private val mockIntent: Intent = mock()
    private val emailProtectionLinkVerifier: EmailProtectionLinkVerifier = mock()

    private lateinit var testee: IntentDispatcherViewModel

    @Before
    fun before() {
        testee = IntentDispatcherViewModel(
            customTabDetector = mockCustomTabDetector,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            emailProtectionLinkVerifier = emailProtectionLinkVerifier,
        )
    }

    @Test
    fun whenIntentReceivedWithSessionThenCustomTabIsRequested() = runTest {
        val text = "url"
        val toolbarColor = 100
        configureHasSession(true)
        whenever(mockIntent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0)).thenReturn(toolbarColor)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(toolbarColor, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithoutSessionThenCustomTabIsNotRequested() = runTest {
        val text = "url"
        configureHasSession(false)
        whenever(mockIntent.intentText).thenReturn(text)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
            assertEquals(text, state.intentText)
            assertEquals(DEFAULT_COLOR, state.toolbarColor)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsEmailProtectionVerificationThenCustomTabIsNotRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(true)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.customTabRequested)
        }
    }

    @Test
    fun whenIntentReceivedWithSessionAndLinkIsNotEmailProtectionVerificationThenCustomTabIsRequested() = runTest {
        configureHasSession(true)
        configureIsEmailProtectionLink(false)

        testee.onIntentReceived(mockIntent, DEFAULT_COLOR)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.customTabRequested)
        }
    }

    private fun configureHasSession(returnValue: Boolean) {
        whenever(mockIntent.hasExtra(CustomTabsIntent.EXTRA_SESSION)).thenReturn(returnValue)
    }

    private fun configureIsEmailProtectionLink(returnValue: Boolean) {
        whenever(emailProtectionLinkVerifier.shouldDelegateToInContextView(anyOrNull(), any())).thenReturn(returnValue)
    }

    private companion object {
        private const val DEFAULT_COLOR = 0
    }
}
