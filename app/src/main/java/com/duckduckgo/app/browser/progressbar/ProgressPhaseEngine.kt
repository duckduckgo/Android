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

enum class Phase {
    IDLE, FAST_START, TRACKING, COMPLETING, DONE
}

data class FrameState(
    val displayProgress: Float = 0f,
    val phase: Phase = Phase.IDLE,
    val shouldInvalidate: Boolean = false,
)

interface TimeProvider {
    fun elapsedRealtime(): Long
}

class ProgressPhaseEngine(
    private val config: ProgressBarConfig = ProgressBarConfig(),
    private val timeProvider: TimeProvider,
) {
    var phase: Phase = Phase.IDLE
        private set

    var displayProgress: Float = 0f
        private set

    private var realProgress: Float = 0f
    private var velocity: Float = 0f
    private var phaseStartTime: Long = 0L
    private var creepProgress: Float = 0f

    private var completionFrom: Float = 0f

    val frameState: FrameState
        get() = FrameState(
            displayProgress = displayProgress,
            phase = phase,
            shouldInvalidate = phase != Phase.IDLE,
        )

    fun start() {
        phase = Phase.FAST_START
        displayProgress = 0f
        realProgress = 0f
        velocity = 0f
        creepProgress = 0f

        phaseStartTime = timeProvider.elapsedRealtime()
    }

    fun onProgressUpdate(progress: Float) {
        if (phase == Phase.IDLE || phase == Phase.DONE) return
        if (progress <= 0f) return
        if (progress <= realProgress) return // ignore backward progress
        realProgress = progress
    }

    fun triggerCompletion() {
        if (phase == Phase.COMPLETING || phase == Phase.DONE || phase == Phase.IDLE) return
        phase = Phase.COMPLETING
        completionFrom = displayProgress
        phaseStartTime = timeProvider.elapsedRealtime()
    }

    fun reset() {
        phase = Phase.IDLE
        displayProgress = 0f
        realProgress = 0f
        velocity = 0f
        creepProgress = 0f
    }

    fun tick(dtSeconds: Float): FrameState {
        // Cap dt to avoid big jumps when resuming from background
        val dt = dtSeconds.coerceAtMost(0.1f)
        when (phase) {
            Phase.IDLE -> {} // no-op
            Phase.FAST_START -> tickFastStart()
            Phase.TRACKING -> tickTracking(dt)
            Phase.COMPLETING -> tickCompleting()
            Phase.DONE -> {} // no-op
        }
        return frameState
    }

    private fun tickFastStart() {
        val elapsed = timeProvider.elapsedRealtime() - phaseStartTime
        val t = (elapsed.toFloat() / config.fastStartDuration).coerceIn(0f, 1f)
        // cubic ease-in
        val eased = t * t * t
        displayProgress = eased * config.fastStartTarget

        if (t >= 1f) {
            displayProgress = config.fastStartTarget
            phase = Phase.TRACKING
            velocity = 0f
            creepProgress = config.fastStartTarget
        }
    }

    private fun tickTracking(dt: Float) {
        val floor = config.fastStartTarget

        // Perpetual creep accumulates independently
        if (config.creepVelocity > 0f) {
            creepProgress = (creepProgress + config.creepVelocity * dt * 100f).coerceAtMost(95f)
        }

        val target = realProgress.coerceAtMost(95f)
        val effectiveTarget = maxOf(target, floor, creepProgress)

        // Spring dynamics (semi-implicit Euler)
        val displacement = effectiveTarget - displayProgress
        val springForce = config.springStiffness * displacement
        velocity = (velocity + springForce * dt) * maxOf(0f, 1f - config.dampingRatio * dt)
        displayProgress += velocity * dt

        // Overshoot clamp
        if (displayProgress > effectiveTarget) {
            displayProgress = effectiveTarget
            velocity = 0f
        }

        displayProgress = displayProgress.coerceIn(floor, 95f)
    }

    private fun tickCompleting() {
        val elapsed = timeProvider.elapsedRealtime() - phaseStartTime
        val t = (elapsed.toFloat() / config.endDuration).coerceIn(0f, 1f)
        val eased = t * t * t // cubic ease-in
        displayProgress = completionFrom + (100f - completionFrom) * eased

        if (t >= 1f) {
            displayProgress = 100f
            phase = Phase.DONE
        }
    }
}
