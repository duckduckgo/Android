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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
@OptIn(ExperimentalTestApi::class)
class DaxOnboardingDialogIntroTest {

    private val completions = mutableListOf<Boolean>()
    private var settleCalls = 0
    private var morphCalls = 0
    private val shell = DaxOnboardingDialogShell(initiallyVisible = false, tail = DaxOnboardingDialogTail.None, tailExtended = false)

    private suspend fun DaxOnboardingDialogIntro.run(
        shell: DaxOnboardingDialogShell = this@DaxOnboardingDialogIntroTest.shell,
        settleShell: suspend () -> Unit = { settleCalls++ },
    ) = runPhase(
        shell = shell,
        hasHeader = { false },
        morphEnteredShell = { morphCalls++ },
        settleShell = settleShell,
        onFinished = { completions += it },
    )

    @Test
    fun nonAnimatedIntroStartsSettledAndFinishesOnceUnanimated() = runTest {
        val intro = DaxOnboardingDialogIntro(animate = false, entryDelay = Duration.ZERO)
        assertTrue(intro.settled)
        assertEquals(1f, intro.contentAlpha.value)

        intro.run()
        intro.run()

        assertEquals(listOf(false), completions)
        assertTrue(intro.finished)
    }

    @Test
    fun skipFromDelaySnapsEverythingAndFinishesOnce() = runTest {
        val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
        assertEquals(IntroPhase.Delay, intro.phase)
        assertEquals(0f, intro.headerAlpha.value)

        intro.requestSkip()
        assertTrue(intro.skipRequested)
        intro.run()
        intro.run()

        assertTrue(intro.settled)
        assertEquals(1f, intro.headerAlpha.value)
        assertEquals(1f, intro.contentAlpha.value)
        assertEquals(1, settleCalls)
        assertEquals(listOf(false), completions)
    }

    @Test
    fun skipAfterSettlingIsIgnored() = runTest {
        val intro = DaxOnboardingDialogIntro(animate = false, entryDelay = Duration.ZERO)
        intro.requestSkip()
        assertFalse(intro.skipRequested)
    }

    @Test
    fun delayPhaseWaitsForEntranceAndEntryDelayThenAdvances() = runTest {
        val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.parse("100ms"))
        val start = testScheduler.currentTime

        intro.run()

        assertEquals(IntroPhase.Entry, intro.phase)
        assertEquals(DaxOnboardingDialogDefaults.EntranceDelay.inWholeMilliseconds + 100, testScheduler.currentTime - start)
    }

    @Test
    fun replacingAVisibleCardStartsItsMorphWithoutAnotherEntranceDelay() = runTest {
        val enteredShell = DaxOnboardingDialogShell(initiallyVisible = true, tail = DaxOnboardingDialogTail.None, tailExtended = false)
        val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.parse("100ms"))
        val start = testScheduler.currentTime

        intro.run(shell = enteredShell)

        assertEquals(IntroPhase.Entry, intro.phase)
        assertEquals(0L, testScheduler.currentTime - start)
    }

    @Test
    fun typingFinishedOnlyAdvancesFromTyping() {
        val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
        intro.onTypingFinished()
        assertEquals(IntroPhase.Delay, intro.phase)
    }

    @Test
    fun contentFadeCallsSettleShellAfterTheFadeAndFinishesOnce() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
            var extended = false
            val settle: suspend () -> Unit = { shell.settle(DaxOnboardingDialogTail.BottomStart, extended, null) }
            repeat(3) { intro.run(settleShell = settle) }
            intro.onTypingFinished()
            assertEquals(IntroPhase.Content, intro.phase)

            val fade = launch { intro.run(settleShell = settle) }
            advanceTimeBy(DaxOnboardingDialogDefaults.ContentFadeDuration.inWholeMilliseconds / 2)
            extended = true
            fade.join()

            assertEquals(1f, shell.tailDepth.value)
            assertEquals(listOf(true), completions)
        }
    }

    @Test
    fun contentFadeHasTheViewDialogsHalfOpacityAtItsMidpoint() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
            repeat(3) { intro.run() }
            intro.onTypingFinished()
            val fade = launch { intro.run() }
            advanceTimeBy(116)

            assertTrue("The View fade is half opaque at its midpoint", intro.contentAlpha.value in 0.4f..0.6f)
            fade.join()
        }
    }

    @Test
    fun entryOnAFreshShellFadesTheCardInWithoutMorphing() = runTest {
        withContext(TestMonotonicFrameClock(this)) {
            val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
            repeat(2) { intro.run() }

            assertEquals(IntroPhase.Header, intro.phase)
            assertTrue(shell.entered)
            assertEquals(1f, shell.cardAlpha.value)
            assertEquals(0, morphCalls)
        }
    }

    @Test
    fun entryOnAnAlreadyEnteredShellMorphsInsteadOfFadingTheCard() = runTest {
        val enteredShell = DaxOnboardingDialogShell(initiallyVisible = true, tail = DaxOnboardingDialogTail.None, tailExtended = false)
        val intro = DaxOnboardingDialogIntro(animate = true, entryDelay = Duration.ZERO)
        repeat(2) { intro.run(shell = enteredShell) }

        assertEquals(IntroPhase.Header, intro.phase)
        assertEquals(1, morphCalls)
    }
}
