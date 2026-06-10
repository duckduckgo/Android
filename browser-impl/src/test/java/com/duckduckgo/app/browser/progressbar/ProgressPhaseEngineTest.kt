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

package com.duckduckgo.app.browser.progressbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTimeProvider(var now: Long = 0L) : TimeProvider {
    override fun elapsedRealtime() = now
    fun advance(ms: Long) { now += ms }
}

class ProgressPhaseEngineTest {

    private lateinit var time: FakeTimeProvider
    private lateinit var engine: ProgressPhaseEngine
    private val config = ProgressBarConfig()

    @Before
    fun setUp() {
        time = FakeTimeProvider()
        engine = ProgressPhaseEngine(config, time)
    }

    // --- Phase transitions ---

    @Test
    fun `initial phase is IDLE`() {
        assertEquals(Phase.IDLE, engine.phase)
    }

    @Test
    fun `start transitions to FAST_START`() {
        engine.start()
        assertEquals(Phase.FAST_START, engine.phase)
    }

    @Test
    fun `fast start transitions to TRACKING after duration`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        assertEquals(Phase.TRACKING, engine.phase)
    }

    @Test
    fun `triggerCompletion transitions TRACKING to COMPLETING`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.triggerCompletion()
        assertEquals(Phase.COMPLETING, engine.phase)
    }

    @Test
    fun `completing transitions to DONE after endSpeed`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.triggerCompletion()
        time.advance(301)
        engine.tick(0.301f)
        assertEquals(Phase.DONE, engine.phase)
    }

    @Test
    fun `reset from any state returns to IDLE`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.reset()
        assertEquals(Phase.IDLE, engine.phase)
        assertEquals(0f, engine.displayProgress)
    }

    @Test
    fun `triggerCompletion during FAST_START skips to COMPLETING`() {
        engine.start()
        time.advance(100)
        engine.tick(0.1f)
        engine.triggerCompletion()
        assertEquals(Phase.COMPLETING, engine.phase)
    }

    @Test
    fun `triggerCompletion while IDLE is no-op`() {
        engine.triggerCompletion()
        assertEquals(Phase.IDLE, engine.phase)
    }

    // --- Fast-start ---

    @Test
    fun `fast start reaches target at end of duration`() {
        engine.start()
        time.advance(600)
        engine.tick(0.6f)
        assertEquals(config.fastStartTarget, engine.displayProgress, 0.5f)
    }

    @Test
    fun `progress updates during fast start are stored but not applied`() {
        engine.start()
        engine.onProgressUpdate(50f)
        time.advance(100)
        engine.tick(0.1f)
        assertTrue(engine.displayProgress < config.fastStartTarget)
    }

    // --- Tracking ---

    @Test
    fun `spring converges toward realProgress`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(60f)
        // The spring is heavily overdamped (damping=8.5), so convergence is gradual.
        // Run enough frames (~10 seconds) for it to settle near the target.
        repeat(625) {
            time.advance(16)
            engine.tick(0.016f)
        }
        // With overdamped spring + creep, should be well past initial 20% and approaching 60%
        assertTrue("displayProgress should be > 50, was ${engine.displayProgress}", engine.displayProgress > 50f)
        assertTrue("displayProgress should be <= 60, was ${engine.displayProgress}", engine.displayProgress <= 60f)
    }

    @Test
    fun `display progress never exceeds 95 during tracking`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(99f)
        repeat(200) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertTrue(engine.displayProgress <= 95f)
    }

    @Test
    fun `backward progress is ignored`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(60f)
        repeat(50) {
            time.advance(16)
            engine.tick(0.016f)
        }
        val progressBefore = engine.displayProgress
        engine.onProgressUpdate(30f)
        repeat(50) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertTrue(engine.displayProgress >= progressBefore - 1f)
    }

    // --- Perpetual creep ---

    @Test
    fun `creep advances when spring settles`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(25f)
        repeat(200) {
            time.advance(16)
            engine.tick(0.016f)
        }
        val settled = engine.displayProgress
        repeat(100) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertTrue(engine.displayProgress > settled)
    }

    @Test
    fun `creep stops at 95 ceiling`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(94f)
        repeat(1000) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertTrue(engine.displayProgress <= 95f)
    }

    // --- Completion ---

    @Test
    fun `completion reaches 100 percent`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(80f)
        repeat(50) {
            time.advance(16)
            engine.tick(0.016f)
        }
        engine.triggerCompletion()
        time.advance(301)
        engine.tick(0.301f)
        assertEquals(100f, engine.displayProgress)
        assertEquals(Phase.DONE, engine.phase)
    }

    // --- Edge cases ---

    @Test
    fun `large dt is capped at 0_1 seconds`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        time.advance(5000)
        engine.tick(5.0f)
        assertTrue(engine.displayProgress <= 95f)
        assertTrue(engine.displayProgress >= 0f)
    }

    @Test
    fun `onProgressUpdate while IDLE is no-op`() {
        engine.onProgressUpdate(50f)
        assertEquals(Phase.IDLE, engine.phase)
        assertEquals(0f, engine.displayProgress)
    }

    @Test
    fun `onProgressUpdate with zero is ignored`() {
        engine.start()
        engine.onProgressUpdate(0f)
        time.advance(601)
        engine.tick(0.601f)
        assertEquals(Phase.TRACKING, engine.phase)
    }

    @Test
    fun `rapid start reset cycles do not corrupt state`() {
        repeat(10) {
            engine.start()
            engine.onProgressUpdate(50f)
            time.advance(100)
            engine.tick(0.1f)
            engine.reset()
        }
        assertEquals(Phase.IDLE, engine.phase)
        assertEquals(0f, engine.displayProgress)
    }
}
