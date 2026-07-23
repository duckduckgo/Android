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

    // --- Indeterminate fallback ---

    @Test
    fun `does not enter INDETERMINATE when stall detection disabled`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        time.advance(config.stallTimeoutMs + 1000)
        engine.tick(0.016f)
        assertEquals(Phase.TRACKING, engine.phase)
    }

    @Test
    fun `enters INDETERMINATE after stall timeout with no forward progress`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        time.advance(config.stallTimeoutMs + 1)
        engine.tick(0.016f)
        assertEquals(Phase.INDETERMINATE, engine.phase)
    }

    @Test
    fun `forward progress before timeout keeps TRACKING and resets the stall timer`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        time.advance(config.stallTimeoutMs - 500)
        engine.tick(0.016f)
        assertEquals(Phase.TRACKING, engine.phase)
        engine.onProgressUpdate(60f) // forward → resets timer
        time.advance(600)
        engine.tick(0.016f)
        assertEquals(Phase.TRACKING, engine.phase)
    }

    @Test
    fun `becoming visible resets the stall timer after time spent in the background`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)

        time.advance(config.stallTimeoutMs + 1000)
        engine.onBecameVisible()
        engine.tick(0.016f)

        assertEquals(Phase.TRACKING, engine.phase)
        time.advance(config.stallTimeoutMs - 1)
        engine.tick(0.016f)
        assertEquals(Phase.TRACKING, engine.phase)
        time.advance(1)
        engine.tick(0.016f)
        assertEquals(Phase.INDETERMINATE, engine.phase)
    }

    @Test
    fun `equal progress does not reset the stall timer`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        time.advance(config.stallTimeoutMs - 100)
        engine.tick(0.016f)
        engine.onProgressUpdate(50f) // equal → ignored, timer NOT reset
        time.advance(200)
        engine.tick(0.016f)
        assertEquals(Phase.INDETERMINATE, engine.phase)
    }

    @Test
    fun `forward progress stays INDETERMINATE with the fill frozen`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        repeat(100) {
            time.advance(16)
            engine.tick(0.016f)
        }
        time.advance(config.stallTimeoutMs + 1)
        engine.tick(0.016f)
        assertEquals(Phase.INDETERMINATE, engine.phase)
        val frozen = engine.displayProgress
        engine.onProgressUpdate(60f)
        repeat(100) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertEquals(Phase.INDETERMINATE, engine.phase)
        assertEquals(frozen, engine.displayProgress, 0.001f)
    }

    @Test
    fun `displayProgress is frozen during INDETERMINATE`() {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        repeat(100) {
            time.advance(16)
            engine.tick(0.016f)
        }
        time.advance(config.stallTimeoutMs + 1)
        engine.tick(0.016f)
        assertEquals(Phase.INDETERMINATE, engine.phase)
        val frozen = engine.displayProgress
        repeat(100) {
            time.advance(16)
            engine.tick(0.016f)
        }
        assertEquals(frozen, engine.displayProgress, 0.001f)
    }

    // --- Indeterminate hand-back (onSweepFinished) ---

    @Test
    fun `onSweepFinished while not INDETERMINATE is a no-op`() {
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(50f)
        engine.onSweepFinished()
        assertEquals(Phase.TRACKING, engine.phase)
    }

    @Test
    fun `onSweepFinished starts the catch-up fill from zero`() {
        enterIndeterminate(real = 50f)
        engine.onSweepFinished()
        assertEquals(Phase.RESUMING, engine.phase)
        assertEquals(0f, engine.displayProgress, 0.001f)
    }

    @Test
    fun `resume catch-up is animated, not an instant jump`() {
        enterIndeterminate(real = 60f)
        engine.onSweepFinished()
        time.advance(config.resumeDurationMs / 2)
        engine.tick(0.1f)
        assertEquals(Phase.RESUMING, engine.phase)
        assertTrue("mid catch-up should be partway, was ${engine.displayProgress}", engine.displayProgress > 0f)
    }

    @Test
    fun `resume catch-up climbs to the displayed value, not the lower real progress`() {
        enterIndeterminate(real = 10f)
        val frozen = engine.displayProgress
        assertTrue("displayed ($frozen) should exceed the real progress", frozen > 15f)
        engine.onProgressUpdate(15f) // real resumes but stays below the displayed value
        engine.onSweepFinished()
        time.advance(config.resumeDurationMs + 1)
        engine.tick(0.4f)
        assertEquals(Phase.TRACKING, engine.phase)
        assertEquals(frozen, engine.displayProgress, 0.001f)
    }

    @Test
    fun `resume catch-up climbs to real progress when it overtook the displayed value`() {
        enterIndeterminate(real = 10f)
        val frozen = engine.displayProgress
        engine.onProgressUpdate(frozen + 20f) // real jumped past the displayed value
        engine.onSweepFinished()
        time.advance(config.resumeDurationMs + 1)
        engine.tick(0.4f)
        assertEquals(Phase.TRACKING, engine.phase)
        assertEquals(frozen + 20f, engine.displayProgress, 0.001f)
    }

    @Test
    fun `resume catch-up completes within the constant resume duration regardless of distance`() {
        enterIndeterminate(real = 10f)
        engine.onProgressUpdate(80f) // far target
        engine.onSweepFinished()
        assertEquals(Phase.RESUMING, engine.phase)
        time.advance(config.resumeDurationMs + 1)
        engine.tick(0.4f)
        assertEquals(Phase.TRACKING, engine.phase)
        assertEquals(80f, engine.displayProgress, 0.001f)
    }

    @Test
    fun `triggerCompletion during INDETERMINATE defers until the sweep finishes`() {
        enterIndeterminate(real = 50f)
        engine.triggerCompletion()
        assertEquals(Phase.INDETERMINATE, engine.phase)
    }

    @Test
    fun `onSweepFinished completes to 100 when completion was requested during the sweep`() {
        enterIndeterminate(real = 50f)
        engine.triggerCompletion()
        engine.onSweepFinished()
        assertEquals(Phase.COMPLETING, engine.phase)
        time.advance(301)
        engine.tick(0.301f)
        assertEquals(100f, engine.displayProgress, 0.001f)
        assertEquals(Phase.DONE, engine.phase)
    }

    @Test
    fun `completion supersedes a resume requested during the sweep`() {
        enterIndeterminate(real = 50f)
        engine.onProgressUpdate(60f) // resume requested
        engine.triggerCompletion() // then the page finishes — completion wins
        engine.onSweepFinished()
        assertEquals(Phase.COMPLETING, engine.phase)
        time.advance(301)
        engine.tick(0.301f)
        assertEquals(100f, engine.displayProgress, 0.001f)
    }

    private fun enterIndeterminate(real: Float) {
        engine.stallDetectionEnabled = true
        engine.start()
        time.advance(601)
        engine.tick(0.601f)
        engine.onProgressUpdate(real)
        repeat(400) {
            time.advance(16)
            engine.tick(0.016f)
        }
    }
}
