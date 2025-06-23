package com.duckduckgo.app.browser

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErrorNavigationUserPreferenceFeatureStateTest {

    @Test
    fun whenStackContainsBackItemsAndErrorNavigationStateWithClientSideHitAndMainFrame_canGoBack_returnsFalse() {
        val testee = createErrorNavigationState(clientSideHit = true, isMainframe = true)

        assertFalse(testee.canGoBack)
    }

    @Test
    fun whenStackContainsBackItemsAndErrorNavigationStateWithClientSideHitAndMainFrameFalse_canGoBack_returnsFalse() {
        val testee = createErrorNavigationState(clientSideHit = true, isMainframe = false)

        assertTrue(testee.canGoBack)
    }

    @Test
    fun whenStackContainsBackItemsAndErrorNavigationStateWithClientSideHitFalseAndMainFrame_canGoBack_returnsTrue() {
        val testee = createErrorNavigationState(clientSideHit = false, isMainframe = true)

        assertTrue(testee.canGoBack)
    }

    @Test
    fun whenStackContainsBackItemsAndErrorNavigationStateWithClientSideHitFalseAndMainFrameFalse_canGoBack_returnsTrue() {
        val testee = createErrorNavigationState(clientSideHit = false, isMainframe = false)

        assertTrue(testee.canGoBack)
    }

    private fun createErrorNavigationState(clientSideHit: Boolean, isMainframe: Boolean): ErrorNavigationState {
        val testBackForwardList = TestBackForwardList().apply {
            addPageToHistory("example.com".toHistoryItem())
            addPageToHistory("example2.com".toHistoryItem())
        }

        return ErrorNavigationState(
            testBackForwardList,
            "https://malicious.com".toUri(),
            maliciousSiteTitle = null,
            clientSideHit = clientSideHit,
            isMainframe = isMainframe,
        )
    }
}
