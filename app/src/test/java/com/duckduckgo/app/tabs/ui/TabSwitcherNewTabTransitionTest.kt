/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.graphics.Bitmap
import com.duckduckgo.app.browser.tabs.transition.NewTabMotion
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionConfig
import com.duckduckgo.app.browser.tabs.transition.RealPendingNewTabTransitions
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TabSwitcherNewTabTransitionTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val timeProvider: CurrentTimeProvider = mock<CurrentTimeProvider>().also { whenever(it.elapsedRealtime()).thenReturn(0L) }
    private val pendingTransitions = RealPendingNewTabTransitions(timeProvider, coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    private val transitionScope = TestScope(coroutineRule.testDispatcher + Job())
    private val captures = mutableListOf<CompletableDeferred<Bitmap?>>()
    private val finishes = mutableListOf<Boolean>()
    private val motion = NewTabTransitionConfig.DEFAULT.let { it.motionFor(it.tabManagerOrigin) }

    private val testee = newTabTransition(transitionScope)

    @Test
    fun completedCaptureRegistersItsTabThenFinishesWithoutAnimation() {
        val request = testee.onNewTabTap()
        close(request, "new")

        captures.last().complete(mock())

        assertEquals("new", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun failedCaptureFinishesNormallyWithoutRegistering() {
        val request = testee.onNewTabTap()
        close(request, "new")

        captures.last().complete(null)

        assertNull(pendingTransitions.tabManagerTabId())
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun requestWithoutCaptureFinishesNormally() {
        testee.enabled = false
        val request = testee.onNewTabTap()

        close(request, "new")

        assertTrue(captures.isEmpty())
        assertNull(pendingTransitions.tabManagerTabId())
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun duckAiButtonStartsARequestOnlyWhenItOpensAChatStraightAway() {
        assertNull(testee.onDuckAiButtonTap(chatsMenuEnabled = true))
        assertTrue(captures.isEmpty())

        val request = testee.onDuckAiButtonTap(chatsMenuEnabled = false)
        assertEquals(1, captures.size)

        close(request!!, "chat")
        captures.last().complete(mock())
        assertEquals("chat", pendingTransitions.tabManagerTabId())
    }

    @Test
    fun laterCloseDropsATransitionStillWaitingForItsPicture() {
        val request = testee.onNewTabTap()
        close(request, "new")

        testee.onClose()
        captures.last().complete(mock())

        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())
    }

    @Test
    fun laterCloseDropsTheRegistrationOfACompletedTransition() {
        val request = testee.onNewTabTap()
        close(request, "new")
        captures.last().complete(mock())

        testee.onClose()

        assertNull(pendingTransitions.tabManagerTabId())
    }

    @Test
    fun newerTapDropsAnOlderWaitBeforeItsOwnCommandArrives() {
        val first = testee.onNewTabTap()
        close(first, "first")

        val second = testee.onNewTabTap()
        captures[0].complete(mock())
        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())

        close(second, "second")
        captures[1].complete(mock())
        assertEquals("second", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun earlierTapsCommandCannotUseTheLatestTapsPicture() {
        val first = testee.onNewTabTap()
        val second = testee.onNewTabTap()

        close(first, "first")
        captures[1].complete(mock())
        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())

        close(second, "second")
        assertEquals("second", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun earlierTapsCommandArrivingLastLeavesTheLatestTransitionAlone() {
        val first = testee.onNewTabTap()
        val second = testee.onNewTabTap()
        close(second, "second")

        close(first, "first")
        captures[1].complete(mock())
        captures[0].complete(mock())

        assertEquals("second", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun closeToNewTabAfterAnotherCloseIsIgnored() {
        val request = testee.onNewTabTap()
        testee.onClose()

        close(request, "new")
        captures.last().complete(mock())

        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())
    }

    @Test
    fun newRequestAfterACompletedTransitionIsIgnored() {
        val first = testee.onNewTabTap()
        close(first, "first")
        captures.last().complete(mock())

        val second = testee.onNewTabTap()
        close(second, "second")
        captures.last().complete(mock())

        assertEquals("first", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun earlierTabManagersCommandCannotUseThisTabManagersRequest() {
        val earlier = newTabTransition(TestScope(coroutineRule.testDispatcher + Job()))
        val earlierRequest = earlier.onNewTabTap()
        val request = testee.onNewTabTap()

        close(earlierRequest, "earlier")
        captures.last().complete(mock())
        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())

        close(request, "new")
        assertEquals("new", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun earlierTabManagersCommandBeforeAnyTapFinishesWithoutAnimation() {
        val earlier = newTabTransition(TestScope(coroutineRule.testDispatcher + Job()))
        val earlierRequest = earlier.onNewTabTap()
        captures.last().complete(mock())

        close(earlierRequest, "earlier")

        assertNull(pendingTransitions.tabManagerTabId())
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun earlierTabManagersCommandAfterAnotherCloseIsIgnored() {
        val earlier = newTabTransition(TestScope(coroutineRule.testDispatcher + Job()))
        val earlierRequest = earlier.onNewTabTap()
        testee.onClose()

        close(earlierRequest, "earlier")

        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())
    }

    @Test
    fun closeWithoutTransitionFinishesAtOnceWithoutRegistering() {
        val request = testee.onNewTabTap()

        close(request, "reused", motion = null)

        assertNull(pendingTransitions.tabManagerTabId())
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun earlierTapsCloseWithoutTransitionLeavesTheLatestRequestAlone() {
        val first = testee.onNewTabTap()
        val second = testee.onNewTabTap()

        close(first, "reused", motion = null)
        assertTrue(finishes.isEmpty())

        close(second, "new")
        captures[1].complete(mock())
        assertEquals("new", pendingTransitions.tabManagerTabId())
        assertEquals(listOf(true), finishes)
    }

    @Test
    fun earlierTabManagersCloseWithoutTransitionCannotCloseThisTabManagersRequest() {
        val earlier = newTabTransition(TestScope(coroutineRule.testDispatcher + Job()))
        val earlierRequest = earlier.onNewTabTap()
        testee.onNewTabTap()

        close(earlierRequest, "reused", motion = null)

        assertTrue(finishes.isEmpty())
    }

    @Test
    fun destroyedWhileWaitingRegistersNothing() {
        val request = testee.onNewTabTap()
        close(request, "new")

        transitionScope.cancel()
        captures.last().complete(mock())

        assertNull(pendingTransitions.tabManagerTabId())
        assertTrue(finishes.isEmpty())
    }

    @Test
    fun failedCaptureAfterSupersessionDoesNothing() {
        val request = testee.onNewTabTap()
        close(request, "new")
        testee.onClose()

        captures.last().complete(null)

        assertTrue(finishes.isEmpty())
    }

    private fun newTabTransition(scope: TestScope) = TabSwitcherNewTabTransition(scope, pendingTransitions) {
        CompletableDeferred<Bitmap?>().also { captures += it }
    }.apply { enabled = true }

    private fun close(
        request: Long,
        tabId: String,
        motion: NewTabMotion? = this.motion,
    ) = testee.closeToNewTab(request, tabId, motion) { animated -> finishes += animated }
}
