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

package com.duckduckgo.common.ui.compose.onboarding

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DaxTypingTextTimingTest {
    @Test
    fun aDelayedFrameCatchesUpWithoutAddingAnotherDelayForEachMissedGrapheme() = runTest {
        val clock = BroadcastFrameClock()
        val revealed = mutableListOf<Int>()
        launch(clock) { revealGraphemes(listOf(1, 3, 8, 9, 12), revealed::add) }
        runCurrent()
        clock.sendFrame(0)
        runCurrent()
        clock.sendFrame(60_000_000)
        runCurrent()

        assertEquals(9, revealed.lastOrNull())
        assertEquals("Only presentation time advanced during the frame gap", 0L, testScheduler.currentTime)
        clock.sendFrame(100_000_000)
        runCurrent()
        assertEquals(12, revealed.last())
        advanceTimeBy(20)
        runCurrent()
        clock.sendFrame(500_000_000)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1, 9, 12), revealed)
    }

    @Test
    fun firstFrameRevealsTheFirstWholeGraphemeAndCancellationDoesNotFinishTheTyping() = runTest {
        val clock = BroadcastFrameClock()
        val revealed = mutableListOf<Int>()
        val typing = launch(clock) { revealGraphemes(listOf(2, 5, 6), revealed::add) }
        runCurrent()
        clock.sendFrame(0)
        runCurrent()
        assertEquals(listOf(2), revealed)

        typing.cancelAndJoin()
        clock.sendFrame(500_000_000)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(2), revealed)
    }
}
