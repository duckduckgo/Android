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

import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.duckduckgo.common.ui.compose.button.DaxBrandButton
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxIconButtonColors
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration

internal data class DialogActionLayout(
    val headerToActions: Dp,
    val contentToPrimary: Dp,
    val primaryToSecondary: Dp,
)

internal fun dialogActionLayout(
    hasContent: Boolean,
    hasPrimaryCta: Boolean,
    hasSecondaryCta: Boolean,
): DialogActionLayout = DialogActionLayout(
    headerToActions = if (hasContent || hasPrimaryCta || hasSecondaryCta) {
        DaxOnboardingDialogDefaults.HeaderContentSpacing
    } else {
        0.dp
    },
    contentToPrimary = if (hasContent && hasPrimaryCta) {
        DaxOnboardingDialogDefaults.ContentToPrimaryCtaSpacing
    } else {
        0.dp
    },
    primaryToSecondary = if (hasPrimaryCta && hasSecondaryCta) {
        DaxOnboardingDialogDefaults.CtaSpacing
    } else {
        0.dp
    },
)

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun DaxOnboardingDialogCore(
    contentKey: String,
    title: AnnotatedString,
    titleStyle: DuckDuckGoTextStyle,
    state: DaxOnboardingDialogState,
    animate: Boolean,
    entryDelay: Duration,
    textAlign: DaxOnboardingDialogTextAlign,
    headerImage: Painter?,
    boundsAnimationDuration: Duration,
    tail: DaxOnboardingDialogTail,
    tailExtended: Boolean,
    primaryCta: DaxOnboardingDialogCta?,
    secondaryCta: DaxOnboardingDialogCta?,
    onIntroFinished: (contentKey: String) -> Unit,
    dismiss: DaxOnboardingDialogDismiss?,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    body: (@Composable () -> Unit)? = null,
    landscapeLayout: Boolean = false,
) {
    val titleLineHeightPx = with(LocalDensity.current) { titleStyle.asTextStyle.lineHeight.toPx() }
    val titleLineSpacingExtraPx = with(LocalDensity.current) {
        DaxOnboardingDialogDefaults.ViewTitleLineSpacingExtraMagnitude.toPx()
    }
    val inInspectionMode = LocalInspectionMode.current
    val compositionScope = rememberCoroutineScope()
    val motionScale = compositionScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val intro = remember(contentKey) { DaxOnboardingDialogIntro(animate && motionScale != 0f && !inInspectionMode, entryDelay) }
    val shell = remember { DaxOnboardingDialogShell(initiallyVisible = !intro.animate, tail = tail, tailExtended = tailExtended) }
    val sampledAnimate = intro.animate
    val settled = intro.settled
    val latestOnIntroFinished by rememberUpdatedState(onIntroFinished)
    val latestTail by rememberUpdatedState(tail)
    val latestTailExtended by rememberUpdatedState(tailExtended)
    val latestHeaderImage by rememberUpdatedState(headerImage)
    val contentScope = remember(contentKey) { DaxOnboardingDialogContentScope { intro.settled } }
    val requestSkip = intro.requestSkip
    val latestAnimating by rememberUpdatedState(!settled)
    val latestRequestSkip by rememberUpdatedState(requestSkip)
    val finishAnimationLabel = stringResource(R.string.onboardingDialogFinishAnimation)
    val introGestureGate = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            var consumeGesture = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!consumeGesture && event.changes.any { it.pressed && !it.previousPressed } && latestAnimating) {
                    consumeGesture = true
                    latestRequestSkip()
                }
                if (consumeGesture) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) consumeGesture = false
            } while (true)
        }
    }

    val cardSizeTracker = remember(contentKey) { CardSizeTracker() }
    val replacingEnteredShell = remember(contentKey) { shell.entered }
    val latestTitle by rememberUpdatedState(title)

    DisposableEffect(state, contentKey) {
        state.attach(requestSkip, !settled)
        onDispose { state.detach(requestSkip) }
    }
    SideEffect { state.updateAnimating(requestSkip, !settled) }

    LaunchedEffect(intro, intro.phase, intro.skipRequested) {
        intro.runPhase(
            shell = shell,
            hasHeader = { latestHeaderImage != null },
            morphEnteredShell = {
                if (replacingEnteredShell) {
                    val target = snapshotFlow { cardSizeTracker.lastSize }.filterNotNull().first()
                    if (cardSizeTracker.bounded) {
                        shell.cardSize.animateTo(target, boundsTween(boundsAnimationDuration))
                    } else {
                        shell.cardSize.snapTo(target)
                    }
                    cardSizeTracker.lastSize?.takeIf { it != target }?.let { shell.cardSize.snapTo(it) }
                    cardSizeTracker.hasMeasured = true
                }
            },
            settleShell = { shell.settle(latestTail, latestTailExtended, cardSizeTracker.lastSize) },
            hasTitle = { latestTitle.text.isNotEmpty() },
            onFinished = { animated ->
                state.updateAnimating(requestSkip, false)
                contentScope.notifyContentReady(animated)
                contentScope.deliverContentReady()
                latestOnIntroFinished(contentKey)
            },
        )
    }

    val animateTailPosition = if (settled) animate else sampledAnimate
    LaunchedEffect(contentKey, tail, animateTailPosition, motionScale) {
        val target = tailPlacementFor(tail)
        if (animateTailPosition && motionScale != 0f && shell.tailPlacement.value.toPlacement().present && target.present) {
            shell.tailPlacement.animateTo(
                target.toOffset(),
                tween(DaxOnboardingDialogDefaults.TailPositionDuration.inWholeMilliseconds.toInt(), easing = FastOutSlowInEasing),
            )
        } else {
            shell.tailPlacement.snapTo(target.toOffset())
        }
    }
    // Like the View dialogs, the tail is already at depth when content introduces itself; only a settled change animates.
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
    LaunchedEffect(contentScope, contentScope.readyVersion) { contentScope.deliverContentReady() }

    // Orientation changes move the slot between layouts; it must keep state while contentScope stays delivered.
    val contentSlot = remember(contentScope) {
        movableContentOf<@Composable DaxOnboardingDialogContentScope.() -> Unit> { slot ->
            DisposableEffect(Unit) { onDispose { contentScope.clearReadyCallback() } }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                contentScope.installColumnScope(this)
                with(contentScope) { slot() }
            }
        }
    }
    // Title and body are called from different layout branches by orientation; a stable identity keeps typing progress.
    val dialogTextSlot = remember(contentKey) { movableContentOf<@Composable () -> Unit> { it() } }
    val scrollState = remember(contentKey) { ScrollState(0) }
    val tailAtRest = { !sampledAnimate && !intro.finished }
    val renderedTailPlacement = { if (tailAtRest()) tailPlacementFor(tail).toOffset() else shell.tailPlacement.value }
    val renderedTailDepth = { if (tailAtRest()) { if (tailExtended) 1f else 0f } else shell.tailDepth.value }
    val bubbleShape = {
        DaxSpeechBubbleShape(
            cornerRadius = DaxOnboardingDialogDefaults.CornerRadius,
            tailPosition = tailPositionFor(tail, renderedTailPlacement()),
            depth = renderedTailDepth(),
        )
    }
    val shellAlpha = { if (tailAtRest()) 1f else shell.cardAlpha.value }
    val colors = DaxOnboardingDialogDefaults.colors
    val onboardingIconsPrimary = LocalOnboardingColors.current.iconsPrimary
    val alignment = if (textAlign == DaxOnboardingDialogTextAlign.Start) TextAlign.Start else TextAlign.Center
    val titleMode = when {
        intro.phase == IntroPhase.Typing -> TitleRenderMode.Typing
        intro.phase == IntroPhase.Content || settled -> TitleRenderMode.Full
        else -> TitleRenderMode.Hidden
    }
    // Not `settled`: onContentReady requests focus before recomposition updates it.
    val contentGate = Modifier
        .focusProperties { if (intro.phase != IntroPhase.Settled) onEnter = { cancelFocusChange() } }
        .focusGroup()
        .then(if (settled) Modifier else Modifier.clearAndSetSemantics { })

    Box(
        modifier = modifier.then(introGestureGate).semantics {
            if (!settled) {
                customActions = listOf(
                    CustomAccessibilityAction(finishAnimationLabel) {
                        requestSkip()
                        true
                    },
                )
            }
        }.padding(top = DaxOnboardingDialogDefaults.DismissOffset, end = DaxOnboardingDialogDefaults.DismissOffset),
    ) {
        Box(Modifier.fillMaxWidth()) {
            val cardModifier = Modifier
                .groupAlphaWithOverflow(CardShadowOverflow, shellAlpha)
                .shadowAndClip(bubbleShape, colors.shadow)
                .bubbleSurface(bubbleShape, colors.surface, colors.stroke, DaxOnboardingDialogDefaults.StrokeWidth)
                .then(
                    if (inInspectionMode) {
                        Modifier
                    } else {
                        Modifier.animatedCardSize(shell.cardSize, cardSizeTracker, { intro.settled }, { replacingEnteredShell && sampledAnimate })
                    },
                )
                .verticalScroll(scrollState, enabled = settled)

            Box(modifier = cardModifier.fillMaxWidth()) {
                key(contentKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                val target = IntSize(size.width, size.height.coerceAtMost(cardSizeTracker.maxHeight))
                                cardSizeTracker.lastSize = target
                                val awaitingMorph = replacingEnteredShell && sampledAnimate && !cardSizeTracker.hasMeasured && !intro.settled
                                if (awaitingMorph) return@onSizeChanged
                                compositionScope.launch {
                                    if (cardSizeTracker.hasMeasured && intro.settled && animate && motionScale != 0f) {
                                        if (!shell.cardSize.isRunning || shell.cardSize.targetValue.height != target.height) {
                                            shell.cardSize.animateTo(target, boundsTween(boundsAnimationDuration))
                                        }
                                    } else {
                                        cardSizeTracker.hasMeasured = true
                                        shell.cardSize.snapTo(target)
                                    }
                                }
                            }
                            .padding(
                                top = DaxOnboardingDialogDefaults.TopPadding,
                                bottom = DaxOnboardingDialogDefaults.BottomPadding,
                            ),
                    ) {
                        @Composable
                        fun DialogText() {
                            if (headerImage != null) {
                                Image(
                                    painter = headerImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                        .requiredSize(DaxOnboardingDialogDefaults.HeaderImageSize)
                                        .drawAlpha { intro.headerAlpha.value },
                                )
                                Spacer(Modifier.size(DaxOnboardingDialogDefaults.HeaderTitleSpacing))
                            }

                            if (title.text.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DaxOnboardingDialogDefaults.TextHorizontalPadding),
                                    horizontalAlignment = if (textAlign == DaxOnboardingDialogTextAlign.Center) {
                                        Alignment.CenterHorizontally
                                    } else {
                                        Alignment.Start
                                    },
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                // 1 line stays below 1.5 line heights; 2+ lines exceed it after View spacing.
                                                val correction = if (placeable.height > titleLineHeightPx * 1.5f) {
                                                    titleLineSpacingExtraPx.roundToInt()
                                                } else {
                                                    0
                                                }
                                                layout(
                                                    placeable.width,
                                                    (placeable.height + correction).coerceAtMost(constraints.maxHeight),
                                                ) { placeable.place(0, 0) }
                                            }
                                            .then(if (titleMode == TitleRenderMode.Hidden) Modifier.clearAndSetSemantics { } else Modifier)
                                            .alpha(
                                                when (titleMode) {
                                                    TitleRenderMode.Hidden -> 0f
                                                    TitleRenderMode.Typing,
                                                    TitleRenderMode.Full,
                                                    -> 1f
                                                },
                                            ),
                                    ) {
                                        when (titleMode) {
                                            TitleRenderMode.Typing -> DaxTypingText(
                                                text = title,
                                                modifier = Modifier.fillMaxWidth(),
                                                style = titleStyle,
                                                color = colors.content,
                                                textAlign = alignment,
                                                animate = sampledAnimate,
                                                onFinished = intro::onTypingFinished,
                                            )

                                            TitleRenderMode.Hidden,
                                            TitleRenderMode.Full,
                                            -> DaxText(
                                                text = title,
                                                modifier = Modifier.fillMaxWidth(),
                                                style = titleStyle,
                                                color = colors.content,
                                                textAlign = alignment,
                                            )
                                        }
                                    }

                                    if (body != null) {
                                        Spacer(
                                            Modifier.size(
                                                if (landscapeLayout) {
                                                    DaxOnboardingDialogDefaults.LandscapeTitleBodySpacing
                                                } else {
                                                    DaxOnboardingDialogDefaults.TitleBodySpacing
                                                },
                                            ),
                                        )
                                        Box(contentGate.drawAlpha { intro.contentAlpha.value }) { body.invoke() }
                                    }
                                }
                            } else if (body != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DaxOnboardingDialogDefaults.TextHorizontalPadding)
                                        .drawAlpha { intro.contentAlpha.value }
                                        .then(contentGate),
                                ) {
                                    body.invoke()
                                }
                            }
                        }

                        @Composable
                        fun DialogActions(landscape: Boolean) {
                            val hasActions = content != null ||
                                primaryCta != null ||
                                secondaryCta != null
                            val actionLayout = dialogActionLayout(
                                hasContent = content != null,
                                hasPrimaryCta = primaryCta != null,
                                hasSecondaryCta = secondaryCta != null,
                            )
                            if (!landscape && hasActions) {
                                Spacer(Modifier.size(actionLayout.headerToActions))
                            }

                            if (content != null) {
                                Box(
                                    modifier = contentGate
                                        .fillMaxWidth()
                                        .padding(horizontal = DaxOnboardingDialogDefaults.ContentHorizontalPadding)
                                        .drawAlpha { intro.contentAlpha.value },
                                ) {
                                    contentSlot(content)
                                }
                            }

                            if (primaryCta != null) {
                                Spacer(Modifier.height(actionLayout.contentToPrimary))
                                DaxBrandButton(
                                    text = primaryCta.text,
                                    onClick = { if (intro.settled) primaryCta.onClick() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DaxOnboardingDialogDefaults.ContentHorizontalPadding)
                                        .introCtaGate(settled, primaryCta.text)
                                        .drawAlpha { intro.contentAlpha.value },
                                    enabled = primaryCta.enabled,
                                    textStyle = DuckDuckGoTheme.typography.onboardingButton,
                                )
                            }

                            if (secondaryCta != null) {
                                DaxOnboardingSecondaryButton(
                                    text = secondaryCta.text,
                                    onClick = { if (intro.settled) secondaryCta.onClick() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = DaxOnboardingDialogDefaults.ContentHorizontalPadding,
                                            end = DaxOnboardingDialogDefaults.ContentHorizontalPadding,
                                            top = actionLayout.primaryToSecondary,
                                        )
                                        .introCtaGate(settled, secondaryCta.text)
                                        .drawAlpha { intro.contentAlpha.value },
                                    enabled = secondaryCta.enabled,
                                )
                            }

                            if (primaryCta != null || secondaryCta != null) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        val dialogText: @Composable () -> Unit = { DialogText() }

                        if (landscapeLayout) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) { dialogTextSlot(dialogText) }
                                Spacer(Modifier.width(DaxOnboardingDialogDefaults.LandscapeTextCtaSpacing))
                                Column(Modifier.width(DaxOnboardingDialogDefaults.LandscapeCtaWidth)) {
                                    DialogActions(landscape = true)
                                }
                            }
                        } else {
                            dialogTextSlot(dialogText)
                            DialogActions(landscape = false)
                        }

                        if (tail != DaxOnboardingDialogTail.None) {
                            Spacer(Modifier.height(DaxOnboardingDialogDefaults.TailBandHeight))
                        }
                    }
                }
            }

            if (dismiss != null) {
                DaxIconButton(
                    onClick = dismiss.onDismiss,
                    iconPainter = painterResource(R.drawable.ic_close_16),
                    contentDescription = dismiss.contentDescription,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = DaxOnboardingDialogDefaults.DismissOffset, y = -DaxOnboardingDialogDefaults.DismissOffset)
                        .size(DaxOnboardingDialogDefaults.DismissSize)
                        .drawAlpha { if (tailAtRest()) 1f else shell.dismissAlpha.value }
                        .clip(CircleShape)
                        .background(colors.surface, CircleShape)
                        .border(BorderStroke(DaxOnboardingDialogDefaults.StrokeWidth, colors.stroke), CircleShape),
                    enabled = settled,
                    iconSize = 16.dp,
                    colors = DaxIconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = onboardingIconsPrimary,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = onboardingIconsPrimary,
                    ),
                )
            }
        }
    }
}

private enum class TitleRenderMode { Hidden, Typing, Full }

internal fun tailPlacementFor(tail: DaxOnboardingDialogTail): DaxSpeechBubbleTailPlacement = when (tail) {
    DaxOnboardingDialogTail.None -> DaxSpeechBubbleTailPlacement(
        edgeFraction = 0f,
        offsetDp = 0f,
        present = false,
    )
    DaxOnboardingDialogTail.BottomStart -> DaxSpeechBubbleTailPlacement(
        edgeFraction = 0f,
        offsetDp = 80f,
        present = true,
    )
    DaxOnboardingDialogTail.BottomStartWide -> DaxSpeechBubbleTailPlacement(
        edgeFraction = 0f,
        offsetDp = 96f,
        present = true,
    )
    DaxOnboardingDialogTail.BottomEnd -> DaxSpeechBubbleTailPlacement(
        edgeFraction = 1f,
        offsetDp = 80f,
        present = true,
    )
}

internal fun DaxSpeechBubbleTailPlacement.toOffset(): Offset = Offset(edgeFraction, offsetDp)

private fun Offset.toPlacement(): DaxSpeechBubbleTailPlacement = DaxSpeechBubbleTailPlacement(
    edgeFraction = x,
    offsetDp = y,
    present = y > 0f,
)

private fun tailPositionFor(tail: DaxOnboardingDialogTail, placement: Offset): DaxSpeechBubbleTailPosition = when (tail) {
    DaxOnboardingDialogTail.None -> DaxSpeechBubbleTailPosition.None
    DaxOnboardingDialogTail.BottomStart,
    DaxOnboardingDialogTail.BottomStartWide,
    DaxOnboardingDialogTail.BottomEnd,
    -> DaxSpeechBubbleTailPosition.Interpolated(placement.x, placement.y)
}

// Match the default ChangeBounds interpolator used by the View onboarding dialog.
private val BoundsAnimationEasing = Easing(AccelerateDecelerateInterpolator()::getInterpolation)

private fun boundsTween(duration: Duration) = tween<IntSize>(duration.inWholeMilliseconds.toInt(), easing = BoundsAnimationEasing)

private class CardSizeTracker {
    var lastSize by mutableStateOf<IntSize?>(null)
    var hasMeasured = false
    var maxHeight = Int.MAX_VALUE
    val bounded: Boolean get() = maxHeight != Int.MAX_VALUE
}

private fun Modifier.shadowAndClip(shape: () -> Shape, color: Color): Modifier = then(
    if (Build.VERSION.SDK_INT >= 29) {
        Modifier.graphicsLayer {
            this.shape = shape()
            clip = true
            shadowElevation = DaxOnboardingDialogDefaults.Elevation.toPx()
            ambientShadowColor = color
            spotShadowColor = color
        }
    } else {
        Modifier
            .legacyDropShadow(shape, color, radius = 12.dp, offset = DpOffset(0.dp, 7.dp))
            .graphicsLayer {
                this.shape = shape()
                clip = true
            }
    },
)

// Compose's dropShadow caches by shape instance, so the animating tail draws its BlurMaskFilter shadow here instead.
private fun Modifier.legacyDropShadow(shape: () -> Shape, color: Color, radius: Dp, offset: DpOffset): Modifier = drawWithCache {
    val path = when (val outline = shape().createOutline(size, layoutDirection, this)) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    val radiusPx = radius.toPx()
    val paint = Paint().apply {
        this.color = color
        nativePaint.maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    onDrawBehind {
        translate(offset.x.toPx(), offset.y.toPx()) {
            clipRect(-radiusPx, -radiusPx, size.width + radiusPx, size.height + radiusPx) {
                drawContext.canvas.drawPath(path, paint)
            }
        }
    }
}

private fun Modifier.bubbleSurface(shape: () -> Shape, fill: Color, stroke: Color, strokeWidth: Dp): Modifier = drawWithCache {
    val outline = shape().createOutline(size, layoutDirection, this)
    val strokePx = minOf(ceil(strokeWidth.toPx()), ceil(size.minDimension / 2))
    onDrawWithContent {
        drawOutline(outline, fill)
        drawContent()
        if (outline is Outline.Generic && size.minDimension > 0f) {
            clipPath(outline.path) { drawPath(outline.path, stroke, style = Stroke(strokePx * 2)) }
        }
    }
}

private fun Modifier.animatedCardSize(
    animatable: Animatable<IntSize, AnimationVector2D>,
    tracker: CardSizeTracker,
    settled: () -> Boolean,
    holdingForMorph: () -> Boolean,
): Modifier = layout { measurable, constraints ->
    tracker.maxHeight = constraints.maxHeight
    // View hosts may probe the natural height with an unspecified spec; the card scroller then takes its content height.
    val scrollConstraints = if (constraints.hasBoundedHeight) {
        constraints
    } else {
        Constraints.fitPrioritizingHeight(constraints.minWidth, constraints.maxWidth, constraints.minHeight, Int.MAX_VALUE - 1)
    }
    val placeable = measurable.measure(scrollConstraints)
    val animatedSize = animatable.value
    val useAnimated = animatedSize != IntSize.Zero &&
        ((settled() && tracker.hasMeasured) || (holdingForMorph() && tracker.bounded))
    val height = if (useAnimated) animatedSize.height else placeable.height
    layout(placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        placeable.place(0, 0)
    }
}

private fun Modifier.drawAlpha(alpha: () -> Float): Modifier = graphicsLayer {
    val value = alpha()
    this.alpha = value
    clip = value < 1f
}

private val CardShadowOverflow = 24.dp

// Auto compositing clips an alpha layer to its own size, so the layer is grown to keep the shadow inside it.
private fun Modifier.groupAlphaWithOverflow(overflow: Dp, alpha: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val extra = overflow.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * extra, vertical = 2 * extra))
        layout(placeable.width - 2 * extra, placeable.height - 2 * extra) { placeable.place(-extra, -extra) }
    }
    .graphicsLayer { this.alpha = alpha() }
    .padding(overflow)

// Keep the enabled palette while fading, with interaction and accessibility blocked until the intro settles.
private fun Modifier.introCtaGate(settled: Boolean, label: String): Modifier = if (settled) {
    this
} else {
    focusProperties { canFocus = false }.clearAndSetSemantics {
        text = AnnotatedString(label)
        role = Role.Button
        disabled()
    }
}
