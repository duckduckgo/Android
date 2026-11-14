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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * State for one content key of the dialog.
 *
 * [shell] outlives the content key so the card can morph between steps. Everything else is created per content key.
 */
@Stable
internal class DaxOnboardingDialogStateHolder(
    val shell: DaxOnboardingDialogShell,
    val intro: DaxOnboardingDialogIntro,
    private val scope: CoroutineScope,
    private val inputs: State<DaxOnboardingDialogInputs>,
) {
    val cardSizeTracker = CardSizeTracker()
    val replacingEnteredShell = shell.entered
    val scrollState = ScrollState(0)
    val focusedContentRequester = BringIntoViewRequester()
    var focusedChild by mutableStateOf<LayoutCoordinates?>(null)
    var scrollingContent by mutableStateOf<LayoutCoordinates?>(null)

    val motionScale: Float get() = inputs.value.motionScale
    val motionEnabled: Boolean get() = motionScale != 0f

    val holdingForMorph: () -> Boolean = { replacingEnteredShell && intro.animate }

    val bubbleShape: () -> DaxSpeechBubbleShape = {
        DaxSpeechBubbleShape(
            cornerRadius = DaxOnboardingDialogDefaults.CornerRadius,
            tailPosition = tailPositionFor(inputs.value.tail, renderedTailPlacement()),
            depth = renderedTailDepth(),
            mirrorFraction = renderedTailMirror(),
        )
    }
    val shellAlpha: () -> Float = { if (tailAtRest()) 1f else shell.cardAlpha.value }
    val dismissAlpha: () -> Float = { if (tailAtRest()) 1f else shell.dismissAlpha.value }

    fun onContentSizeChanged(size: IntSize) {
        inputs.value.state.updateContentHeight(intro.requestSkip, size.height)
        updateCardSize(size)
    }

    fun updateCardSize(size: IntSize) {
        val latest = inputs.value
        cardSizeTracker.naturalSize = size
        val maximum = latest.state.availableHeightPx?.let { (it - latest.dismissOffsetPx).coerceAtLeast(0) } ?: cardSizeTracker.maxHeight
        val target = IntSize(size.width, size.height.coerceAtMost(maximum))
        cardSizeTracker.lastSize = target
        val awaitingMorph = replacingEnteredShell && intro.animate && !cardSizeTracker.hasMeasured && !intro.settled
        if (awaitingMorph) return
        scope.launch {
            if (cardSizeTracker.hasMeasured && intro.settled && latest.animate && latest.motionScale != 0f) {
                if (!shell.cardSize.isRunning || shell.cardSize.targetValue.height != target.height) {
                    shell.cardSize.animateTo(target, boundsTween(latest.boundsAnimationDuration))
                }
            } else {
                cardSizeTracker.hasMeasured = true
                shell.cardSize.snapTo(target)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun morphEnteredShell(boundsAnimationDuration: Duration) {
        if (!replacingEnteredShell) return
        val sizes = snapshotFlow { cardSizeTracker.lastSize }.filterNotNull()
        if (cardSizeTracker.bounded) {
            // A host can raise the available height after the replacement is first measured, so the morph retargets.
            sizes.mapLatest { shell.cardSize.animateTo(it, boundsTween(boundsAnimationDuration)) }.firstOrNull()
        } else {
            sizes.firstOrNull()?.let { shell.cardSize.snapTo(it) }
        }
        cardSizeTracker.hasMeasured = true
    }

    private fun tailAtRest() = !intro.animate && !intro.finished

    private fun renderedTailPlacement() = if (tailAtRest()) tailPlacementFor(inputs.value.tail).toOffset() else shell.tailPlacement.value

    private fun renderedTailDepth() = if (tailAtRest()) { if (inputs.value.tailExtended) 1f else 0f } else shell.tailDepth.value

    private fun renderedTailMirror() = if (tailAtRest()) tailPlacementFor(inputs.value.tail).mirrorFraction else shell.tailMirror.value
}

/** The caller's latest parameters, read by the holder during composition and when a layout, draw or effect callback runs. */
internal data class DaxOnboardingDialogInputs(
    val state: DaxOnboardingDialogStateImpl,
    val animate: Boolean,
    val tail: DaxOnboardingDialogTail,
    val tailExtended: Boolean,
    val boundsAnimationDuration: Duration,
    val dismissOffsetPx: Int,
    val motionScale: Float,
)

internal class CardSizeTracker {
    var naturalSize: IntSize? = null
    var lastSize by mutableStateOf<IntSize?>(null)
    var hasMeasured = false
    var maxHeight = Int.MAX_VALUE
    val bounded: Boolean get() = maxHeight != Int.MAX_VALUE
}

@Composable
internal fun rememberDaxOnboardingDialogStateHolder(
    contentKey: String,
    state: DaxOnboardingDialogStateImpl,
    animate: Boolean,
    entryDelay: Duration,
    tail: DaxOnboardingDialogTail,
    tailExtended: Boolean,
    boundsAnimationDuration: Duration,
): DaxOnboardingDialogStateHolder {
    val inInspectionMode = LocalInspectionMode.current
    val compositionScope = rememberCoroutineScope()
    val motionScale = compositionScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val intro = remember(contentKey) { DaxOnboardingDialogIntro(animate && motionScale != 0f && !inInspectionMode, entryDelay) }
    val shell = remember { DaxOnboardingDialogShell(initiallyVisible = !intro.animate, tail = tail, tailExtended = tailExtended) }
    val dismissOffsetPx = with(LocalDensity.current) { DaxOnboardingDialogDefaults.DismissOffset.roundToPx() }
    val inputs = rememberUpdatedState(
        DaxOnboardingDialogInputs(state, animate, tail, tailExtended, boundsAnimationDuration, dismissOffsetPx, motionScale),
    )
    return remember(intro) { DaxOnboardingDialogStateHolder(shell, intro, compositionScope, inputs) }
}

@Composable
internal fun DaxOnboardingDialogEffects(
    holder: DaxOnboardingDialogStateHolder,
    contentKey: String,
    state: DaxOnboardingDialogStateImpl,
    animate: Boolean,
    tail: DaxOnboardingDialogTail,
    tailExtended: Boolean,
    boundsAnimationDuration: Duration,
    hasHeader: Boolean,
    hasTitle: Boolean,
    onIntroFinished: (contentKey: String) -> Unit,
) {
    val intro = holder.intro
    val shell = holder.shell
    val motionScale = holder.motionScale
    val settled = intro.settled
    val requestSkip = intro.requestSkip
    val latestOnIntroFinished by rememberUpdatedState(onIntroFinished)
    val latestTail by rememberUpdatedState(tail)
    val latestTailExtended by rememberUpdatedState(tailExtended)
    val latestHasHeader by rememberUpdatedState(hasHeader)
    val latestHasTitle by rememberUpdatedState(hasTitle)

    DisposableEffect(state, contentKey) {
        state.attach(requestSkip, !settled)
        onDispose { state.detach(requestSkip) }
    }
    SideEffect { state.updateAnimating(requestSkip, !settled) }

    LaunchedEffect(intro, intro.phase, intro.skipRequested) {
        intro.runPhase(
            shell = shell,
            hasHeader = { latestHasHeader },
            morphEnteredShell = { holder.morphEnteredShell(boundsAnimationDuration) },
            settleShell = { shell.settle(latestTail, latestTailExtended, holder.cardSizeTracker.lastSize) },
            hasTitle = { latestHasTitle },
            onFinished = {
                state.updateAnimating(requestSkip, false)
                latestOnIntroFinished(contentKey)
            },
        )
    }

    LaunchedEffect(holder.cardSizeTracker, state.availableHeightPx) {
        holder.cardSizeTracker.naturalSize?.let { holder.updateCardSize(it) }
    }

    val animateTailPosition = if (settled) animate else intro.animate
    LaunchedEffect(contentKey, tail, animateTailPosition, motionScale) {
        val target = tailPlacementFor(tail)
        if (animateTailPosition && motionScale != 0f && shell.tailPlacement.value.toPlacement().present && target.present) {
            val duration = DaxOnboardingDialogDefaults.TailPositionDuration.inWholeMilliseconds.toInt()
            coroutineScope {
                launch { shell.tailPlacement.animateTo(target.toOffset(), tween(duration, easing = FastOutSlowInEasing)) }
                launch { shell.tailMirror.animateTo(target.mirrorFraction, tween(duration, easing = FastOutSlowInEasing)) }
            }
        } else {
            shell.tailPlacement.snapTo(target.toOffset())
            shell.tailMirror.snapTo(target.mirrorFraction)
        }
    }
    // The tail is already at depth when content introduces itself; only a settled change animates.
    LaunchedEffect(contentKey, tailExtended, animate, settled) {
        val target = if (tailExtended) 1f else 0f
        if (settled && animate && motionScale != 0f) {
            shell.tailDepth.animateTo(
                target,
                tween(DaxOnboardingDialogDefaults.ContentFadeDuration.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing),
            )
        } else {
            shell.tailDepth.snapTo(target)
        }
    }

    LaunchedEffect(contentKey, holder.scrollState) {
        snapshotFlow { Triple(holder.scrollState.viewportSize, holder.focusedChild, holder.scrollingContent) }
            .collectLatest { (viewportHeight, child, parent) ->
                if (viewportHeight > 0 && child?.isAttached == true && parent?.isAttached == true) {
                    holder.focusedContentRequester.bringIntoView(parent.localBoundingBoxOf(child, clipBounds = false))
                }
            }
    }
}

private fun Offset.toPlacement(): DaxSpeechBubbleTailPlacement = DaxSpeechBubbleTailPlacement(
    edgeFraction = x,
    offsetDp = y,
    present = y > 0f,
)

private fun tailPositionFor(tail: DaxOnboardingDialogTail, placement: Offset): DaxSpeechBubbleTailPosition = when (tail) {
    DaxOnboardingDialogTail.None -> DaxSpeechBubbleTailPosition.None
    DaxOnboardingDialogTail.BottomStart,
    DaxOnboardingDialogTail.BottomStartMirrored,
    DaxOnboardingDialogTail.BottomStartWide,
    DaxOnboardingDialogTail.BottomEnd,
    -> DaxSpeechBubbleTailPosition.Interpolated(placement.x, placement.y)
}

private val BoundsAnimationEasing = Easing(AccelerateDecelerateInterpolator()::getInterpolation)

private fun boundsTween(duration: Duration) = tween<IntSize>(duration.inWholeMilliseconds.toInt(), easing = BoundsAnimationEasing)
