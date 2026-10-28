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

import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal enum class IntroPhase { Delay, Entry, Header, Typing, Content, Settled }

/** Card and tail animation that outlives a content key, so the bubble stays painted while new content introduces itself. */
@Stable
internal class DaxOnboardingDialogShell(
    initiallyVisible: Boolean,
    tail: DaxOnboardingDialogTail,
    tailExtended: Boolean,
) {
    var entered by mutableStateOf(initiallyVisible)
        private set
    val cardAlpha = Animatable(if (initiallyVisible) 1f else 0f)
    val dismissAlpha = Animatable(if (initiallyVisible) 1f else 0f)
    val tailDepth = Animatable(if (tailExtended) 1f else 0f)
    val tailPlacement = Animatable(tailPlacementFor(tail).toOffset(), Offset.VectorConverter)
    val cardSize = Animatable(IntSize.Zero, IntSize.VectorConverter)

    suspend fun enter() {
        if (entered) return
        cardAlpha.animateTo(1f, fade(DaxOnboardingDialogDefaults.ContainerFadeDuration))
        entered = true
    }

    suspend fun settle(tail: DaxOnboardingDialogTail, tailExtended: Boolean, measuredSize: IntSize?) {
        cardAlpha.snapTo(1f)
        dismissAlpha.snapTo(1f)
        entered = true
        tailPlacement.snapTo(tailPlacementFor(tail).toOffset())
        tailDepth.snapTo(if (tailExtended) 1f else 0f)
        measuredSize?.let { cardSize.snapTo(it) }
    }
}

/** One content key's introduction; the caller relaunches [runPhase] whenever [phase] or [skipRequested] changes. */
@Stable
internal class DaxOnboardingDialogIntro(
    val animate: Boolean,
    private val entryDelay: Duration,
) {
    var phase by mutableStateOf(if (animate) IntroPhase.Delay else IntroPhase.Settled)
        private set
    var skipRequested by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    val settled: Boolean get() = phase == IntroPhase.Settled

    val headerAlpha = Animatable(if (animate) 0f else 1f)
    val contentAlpha = Animatable(if (animate) 0f else 1f)

    val requestSkip: () -> Unit = { if (phase != IntroPhase.Settled) skipRequested = true }

    fun onTypingFinished() {
        if (phase == IntroPhase.Typing) phase = IntroPhase.Content
    }

    suspend fun runPhase(
        shell: DaxOnboardingDialogShell,
        hasHeader: () -> Boolean,
        morphEnteredShell: suspend () -> Unit,
        settleShell: suspend () -> Unit,
        onFinished: (animated: Boolean) -> Unit,
        hasTitle: () -> Boolean = { true },
    ) {
        if (finished) return
        if (skipRequested || !animate) {
            finish(animated = false, settleShell, onFinished)
            return
        }
        when (phase) {
            IntroPhase.Delay -> {
                if (!shell.entered) delay((DaxOnboardingDialogDefaults.EntranceDelay + entryDelay).inWholeMilliseconds)
                phase = IntroPhase.Entry
            }
            IntroPhase.Entry -> {
                if (!shell.entered) shell.enter() else morphEnteredShell()
                phase = IntroPhase.Header
            }
            IntroPhase.Header -> {
                if (hasHeader()) {
                    headerAlpha.animateTo(1f, fade(DaxOnboardingDialogDefaults.HeaderFadeDuration))
                } else {
                    headerAlpha.snapTo(1f)
                }
                phase = IntroPhase.Typing
            }
            IntroPhase.Typing -> {
                snapshotFlow { hasTitle() }.first { !it }
                onTypingFinished()
            }
            IntroPhase.Content -> {
                coroutineScope {
                    val spec = fade(DaxOnboardingDialogDefaults.ContentFadeDuration)
                    launch { contentAlpha.animateTo(1f, spec) }
                    launch { shell.dismissAlpha.animateTo(1f, spec) }
                }
                finish(animated = true, settleShell, onFinished)
            }
            IntroPhase.Settled -> Unit
        }
    }

    private suspend fun finish(animated: Boolean, settleShell: suspend () -> Unit, onFinished: (Boolean) -> Unit) {
        settleShell()
        headerAlpha.snapTo(1f)
        contentAlpha.snapTo(1f)
        phase = IntroPhase.Settled
        if (!finished) {
            finished = true
            onFinished(animated)
        }
    }
}

private val FadeEasing = Easing(AccelerateDecelerateInterpolator()::getInterpolation)

private fun fade(duration: Duration): TweenSpec<Float> = tween(duration.inWholeMilliseconds.toInt(), easing = FadeEasing)
