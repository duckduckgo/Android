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

package com.duckduckgo.app.browser.tabs.transition

import android.graphics.Bitmap
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.RIGHT_OF_CENTER
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class RealPendingNewTabTransitionsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val timeProvider = FakeTimeProvider()
    private val testee = RealPendingNewTabTransitions(timeProvider, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    private val motion = NewTabTransitionConfig.DEFAULT.motionFor(RIGHT_OF_CENTER)

    @Test
    fun consumeReturnsRegisteredTransitionOnce() {
        val transition = transition()
        testee.register("a", transition)

        assertSame(transition, testee.consume("a"))
        assertNull(testee.consume("a"))
    }

    @Test
    fun peekReturnsRegisteredTransitionWithoutConsumingIt() {
        val transition = transition()
        testee.register("a", transition)

        assertSame(transition, testee.peek("a"))
        assertNull(testee.peek("b"))
        assertSame(transition, testee.consume("a"))
    }

    @Test
    fun consumeReturnsNullForOtherTab() {
        testee.register("a", transition())

        assertNull(testee.consume("b"))
    }

    @Test
    fun registerReplacesPreviousPending() {
        testee.register("a", transition())
        val second = transition()
        testee.register("b", second)

        assertNull(testee.consume("a"))
        assertSame(second, testee.consume("b"))
    }

    @Test
    fun expiredEntryIsNotReturned() {
        testee.register("a", transition(fromTabManager = true))

        timeProvider.elapsed += 3_001

        assertNull(testee.tabManagerTabId())
        assertNull(testee.consume("a"))
    }

    @Test
    fun expiredEntryIsDroppedWithoutALookup() {
        testee.register("a", transition())

        advanceTime(3_001)

        // The clock still reads 0, so only the scheduled drop can explain the miss.
        assertNull(testee.consume("a"))
    }

    @Test
    fun earlierExpiryDoesNotDropANewerEntry() {
        testee.register("a", transition())
        advanceTime(2_000)
        val newer = transition()
        testee.register("b", newer)

        advanceTime(1_500)

        assertSame(newer, testee.consume("b"))
    }

    @Test
    fun tabManagerTabIdOnlyWhenFromTabManager() {
        testee.register("a", transition(fromTabManager = false))
        assertNull(testee.tabManagerTabId())

        val fromTabManager = transition(fromTabManager = true)
        testee.register("b", fromTabManager)

        assertEquals("b", testee.tabManagerTabId())
        assertSame(fromTabManager, testee.consume("b"))
    }

    @Test
    fun clearDropsPending() {
        testee.register("a", transition())

        testee.clear()

        assertNull(testee.consume("a"))
    }

    @Test
    fun readyOrNullIsNullWhilePending() {
        assertNull(CompletableDeferred<Bitmap?>().readyOrNull())
    }

    @Test
    fun readyOrNullIsNullWhenCaptureFailed() {
        val failed = CompletableDeferred<Bitmap?>().apply { complete(null) }

        assertTrue(failed.isCompleted)
        assertNull(failed.readyOrNull())
    }

    @Test
    fun readyOrNullIsPictureWhenCaptured() {
        val picture: Bitmap = mock()

        assertSame(picture, CompletableDeferred<Bitmap?>(picture).readyOrNull())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun advanceTime(millis: Long) {
        coroutineRule.testScope.advanceTimeBy(millis.milliseconds)
        coroutineRule.testScope.runCurrent()
    }

    private fun transition(
        picture: Bitmap = mock(),
        fromTabManager: Boolean = false,
    ) = PendingNewTabTransition(CompletableDeferred(picture), motion, fromTabManager)

    private class FakeTimeProvider : CurrentTimeProvider {
        var elapsed = 0L

        override fun elapsedRealtime(): Long = elapsed

        override fun currentTimeMillis(): Long = elapsed

        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
}
