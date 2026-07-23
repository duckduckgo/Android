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
    IDLE, FAST_START, TRACKING, INDETERMINATE, RESUMING, COMPLETING, DONE
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

    var stallDetectionEnabled: Boolean = false

    private var realProgress: Float = 0f
    private var velocity: Float = 0f
    private var phaseStartTime: Long = 0L
    private var creepProgress: Float = 0f
    private var lastForwardProgressTime: Long = 0L

    private var completionFrom: Float = 0f
    private var pendingCompletion: Boolean = false
    private var resumeTarget: Float = 0f

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
        pendingCompletion = false

        phaseStartTime = timeProvider.elapsedRealtime()
        lastForwardProgressTime = timeProvider.elapsedRealtime()
    }

    fun onProgressUpdate(progress: Float) {
        if (phase == Phase.IDLE || phase == Phase.DONE) return
        if (progress <= 0f) return
        if (progress <= realProgress) return // ignore backward/equal progress
        realProgress = progress
        lastForwardProgressTime = timeProvider.elapsedRealtime()
    }

    fun triggerCompletion() {
        if (phase == Phase.COMPLETING || phase == Phase.DONE || phase == Phase.IDLE) return
        if (phase == Phase.INDETERMINATE) {
            // Defer until the sweep finishes its cycle; onSweepFinished() then completes from empty.
            pendingCompletion = true
            return
        }
        phase = Phase.COMPLETING
        completionFrom = displayProgress
        phaseStartTime = timeProvider.elapsedRealtime()
    }

    /**
     * Hands back from the finished indeterminate sweep. Completes if the page finished during the
     * sweep; otherwise runs a constant-time catch-up fill from 0 up to the progress shown before the
     * stall (never the lower raw value), and only then resumes determinate tracking.
     */
    fun onSweepFinished() {
        if (phase != Phase.INDETERMINATE) return
        if (pendingCompletion) {
            pendingCompletion = false
            displayProgress = 0f
            completionFrom = 0f
            phase = Phase.COMPLETING
        } else {
            resumeTarget = maxOf(displayProgress, realProgress).coerceAtMost(95f)
            displayProgress = 0f
            velocity = 0f
            phase = Phase.RESUMING
        }
        phaseStartTime = timeProvider.elapsedRealtime()
        lastForwardProgressTime = timeProvider.elapsedRealtime()
    }

    fun reset() {
        phase = Phase.IDLE
        displayProgress = 0f
        realProgress = 0f
        velocity = 0f
        creepProgress = 0f
        pendingCompletion = false
    }

    fun tick(dtSeconds: Float): FrameState {
        // Cap dt to avoid big jumps when resuming from background
        val dt = dtSeconds.coerceAtMost(0.1f)
        when (phase) {
            Phase.IDLE -> {} // no-op
            Phase.FAST_START -> tickFastStart()
            Phase.TRACKING -> tickTracking(dt)
            Phase.INDETERMINATE -> {} // hold displayProgress frozen; the view renders the sweep
            Phase.RESUMING -> tickResuming()
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
        if (stallDetectionEnabled &&
            timeProvider.elapsedRealtime() - lastForwardProgressTime >= config.stallTimeoutMs
        ) {
            phase = Phase.INDETERMINATE
            velocity = 0f
            return
        }

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

    private fun tickResuming() {
        val elapsed = timeProvider.elapsedRealtime() - phaseStartTime
        val t = (elapsed.toFloat() / config.resumeDuration).coerceIn(0f, 1f)
        // Constant-time cosmetic catch-up (ease-out), independent of the spring.
        val inv = 1f - t
        val eased = 1f - inv * inv * inv
        displayProgress = resumeTarget * eased

        if (t >= 1f) {
            displayProgress = resumeTarget
            creepProgress = resumeTarget
            velocity = 0f
            phase = Phase.TRACKING
        }
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
