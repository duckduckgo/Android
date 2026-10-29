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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxIconButtonColors
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration

internal data class DialogActionLayout(
    val headerToActions: Dp,
    val contentToPrimary: Dp,
)

internal fun dialogActionLayout(
    hasContent: Boolean,
    hasPrimaryCta: Boolean,
    headerToActionsSpacing: Dp = DaxOnboardingDialogDefaults.HeaderContentSpacing,
    contentToPrimarySpacing: Dp = DaxOnboardingDialogDefaults.ContentToPrimaryCtaSpacing,
): DialogActionLayout = DialogActionLayout(
    headerToActions = if (hasContent || hasPrimaryCta) {
        headerToActionsSpacing
    } else {
        0.dp
    },
    contentToPrimary = if (hasContent && hasPrimaryCta) {
        contentToPrimarySpacing
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
    onIntroFinished: (contentKey: String) -> Unit,
    dismiss: DaxOnboardingDialogDismiss?,
    content: (@Composable DaxOnboardingDialogContentScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    body: (@Composable () -> Unit)? = null,
    landscapeLayout: Boolean = false,
    step: DaxOnboardingDialogStep? = null,
    headerToActionsSpacing: Dp = DaxOnboardingDialogDefaults.HeaderContentSpacing,
    textHorizontalPadding: Dp = DaxOnboardingDialogDefaults.TextHorizontalPadding,
    contentHorizontalPadding: Dp = DaxOnboardingDialogDefaults.ContentHorizontalPadding,
    topPadding: Dp = DaxOnboardingDialogDefaults.TopPadding,
    bottomPadding: Dp = DaxOnboardingDialogDefaults.BottomPadding,
    titleBodySpacing: Dp = DaxOnboardingDialogDefaults.TitleBodySpacing,
    elevation: Dp = DaxOnboardingDialogDefaults.Elevation,
    useNativeCtaInsets: Boolean = false,
    showScrollbar: Boolean = false,
) {
    val titleMeasurer = rememberTextMeasurer()
    val naturalTitleStyle = titleStyle.asTextStyle.copy(lineHeight = TextUnit.Unspecified)
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
            morphEnteredShell = morphEnteredShell@{
                if (replacingEnteredShell) {
                    val target = snapshotFlow { cardSizeTracker.lastSize }.filterNotNull().firstOrNull() ?: return@morphEnteredShell
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

    val dismissOffsetPx = with(LocalDensity.current) { DaxOnboardingDialogDefaults.DismissOffset.roundToPx() }
    fun updateCardSize(size: IntSize) {
        cardSizeTracker.naturalSize = size
        val maximum = state.availableHeightPx?.let { (it - dismissOffsetPx).coerceAtLeast(0) } ?: cardSizeTracker.maxHeight
        val target = IntSize(size.width, size.height.coerceAtMost(maximum))
        cardSizeTracker.lastSize = target
        val awaitingMorph = replacingEnteredShell && sampledAnimate && !cardSizeTracker.hasMeasured && !intro.settled
        if (awaitingMorph) return
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
    LaunchedEffect(cardSizeTracker, state.availableHeightPx) {
        cardSizeTracker.naturalSize?.let { updateCardSize(it) }
    }

    val animateTailPosition = if (settled) animate else sampledAnimate
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

    val contentSlot = remember(contentScope) {
        movableContentOf<@Composable DaxOnboardingDialogContentScope.() -> Unit> { slot ->
            DisposableEffect(Unit) { onDispose { contentScope.clearReadyCallback() } }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                contentScope.installColumnScope(this)
                with(contentScope) { slot() }
            }
        }
    }
    val scrollState = remember(contentKey) { ScrollState(0) }
    val context = LocalContext.current
    val scrollbar = remember(context, showScrollbar) { if (showScrollbar) PlatformScrollbar.from(context) else null }
    val focusedContentRequester = remember(contentKey) { BringIntoViewRequester() }
    var focusedChild by remember(contentKey) { mutableStateOf<LayoutCoordinates?>(null) }
    var scrollingContent by remember(contentKey) { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(contentKey, scrollState) {
        snapshotFlow { Triple(scrollState.viewportSize, focusedChild, scrollingContent) }
            .collectLatest { (viewportHeight, child, parent) ->
                if (viewportHeight > 0 && child?.isAttached == true && parent?.isAttached == true) {
                    focusedContentRequester.bringIntoView(parent.localBoundingBoxOf(child, clipBounds = false))
                }
            }
    }
    val tailAtRest = { !sampledAnimate && !intro.finished }
    val renderedTailPlacement = { if (tailAtRest()) tailPlacementFor(tail).toOffset() else shell.tailPlacement.value }
    val renderedTailDepth = { if (tailAtRest()) { if (tailExtended) 1f else 0f } else shell.tailDepth.value }
    val renderedTailMirror = { if (tailAtRest()) tailPlacementFor(tail).mirrorFraction else shell.tailMirror.value }
    val bubbleShape = {
        DaxSpeechBubbleShape(
            cornerRadius = DaxOnboardingDialogDefaults.CornerRadius,
            tailPosition = tailPositionFor(tail, renderedTailPlacement()),
            depth = renderedTailDepth(),
            mirrorFraction = renderedTailMirror(),
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
                .bubbleStroke(bubbleShape, colors.stroke, DaxOnboardingDialogDefaults.StrokeWidth)
                .shadowAndClip(bubbleShape, colors.shadow, elevation)
                .bubbleFill(bubbleShape, colors.surface)
                .then(
                    if (inInspectionMode) {
                        Modifier
                    } else {
                        Modifier.animatedCardSize(
                            shell.cardSize,
                            cardSizeTracker,
                            { intro.settled },
                            { replacingEnteredShell && sampledAnimate },
                            { boundsAnimationDuration == Duration.ZERO },
                        )
                    },
                )
                .then(scrollbar?.let { Modifier.platformScrollbar(scrollState, it) } ?: Modifier)
                .verticalScroll(scrollState, enabled = settled)

            Box(modifier = cardModifier.fillMaxWidth()) {
                key(contentKey) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusedBoundsChanged { focusedChild = it }
                            .onGloballyPositioned { scrollingContent = it }
                            .bringIntoViewRequester(focusedContentRequester)
                            .onSizeChanged { size ->
                                state.updateContentHeight(requestSkip, size.height)
                                updateCardSize(size)
                            }
                            .padding(
                                top = topPadding,
                                bottom = bottomPadding,
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

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = textHorizontalPadding),
                                horizontalAlignment = if (textAlign == DaxOnboardingDialogTextAlign.Center) {
                                    Alignment.CenterHorizontally
                                } else {
                                    Alignment.Start
                                },
                            ) {
                                if (title.text.isNotEmpty()) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                val naturalHeight = titleMeasurer.measure(
                                                    text = title,
                                                    style = naturalTitleStyle,
                                                    constraints = constraints.copy(minHeight = 0),
                                                ).size.height
                                                layout(
                                                    placeable.width,
                                                    constraints.constrainHeight(naturalHeight),
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
                                }

                                if (body != null) {
                                    if (title.text.isNotEmpty()) {
                                        Spacer(
                                            Modifier.size(
                                                if (landscapeLayout) {
                                                    DaxOnboardingDialogDefaults.LandscapeTitleBodySpacing
                                                } else {
                                                    titleBodySpacing
                                                },
                                            ),
                                        )
                                    }
                                    Box(
                                        contentGate
                                            .then(if (title.text.isEmpty()) Modifier.fillMaxWidth() else Modifier)
                                            .drawAlpha { intro.contentAlpha.value },
                                    ) { body.invoke() }
                                }
                            }
                        }

                        @Composable
                        fun DialogActions(landscape: Boolean) {
                            val hasActions = content != null || primaryCta != null
                            val density = LocalDensity.current
                            val contentToPrimarySpacing = if (useNativeCtaInsets) {
                                with(density) { (20.dp.roundToPx() + 4.dp.roundToPx()).toDp() }
                            } else {
                                DaxOnboardingDialogDefaults.ContentToPrimaryCtaSpacing
                            }
                            val actionLayout = dialogActionLayout(
                                hasContent = content != null,
                                hasPrimaryCta = primaryCta != null,
                                headerToActionsSpacing = headerToActionsSpacing,
                                contentToPrimarySpacing = contentToPrimarySpacing,
                            )
                            if (!landscape && hasActions) {
                                Spacer(Modifier.size(actionLayout.headerToActions))
                            }

                            if (content != null) {
                                Box(
                                    modifier = contentGate
                                        .fillMaxWidth()
                                        .padding(horizontal = contentHorizontalPadding)
                                        .drawAlpha { intro.contentAlpha.value },
                                ) {
                                    contentSlot(content)
                                }
                            }

                            if (primaryCta != null) {
                                Spacer(Modifier.height(actionLayout.contentToPrimary))
                                DaxOnboardingPrimaryButton(
                                    text = primaryCta.text,
                                    onClick = { if (intro.settled) primaryCta.onClick() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DaxOnboardingDialogDefaults.ContentHorizontalPadding)
                                        .introCtaGate(settled, primaryCta.text)
                                        .drawAlpha { intro.contentAlpha.value },
                                    enabled = primaryCta.enabled,
                                )
                            }

                            if (primaryCta != null) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        Layout(
                            content = {
                                Column { DialogText() }
                                Column { DialogActions(landscapeLayout) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { measurables, constraints ->
                            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                            if (landscapeLayout) {
                                val gap = DaxOnboardingDialogDefaults.LandscapeTextCtaSpacing.roundToPx()
                                    .coerceAtMost(constraints.maxWidth)
                                val actionsWidth = DaxOnboardingDialogDefaults.LandscapeCtaWidth.roundToPx()
                                    .coerceAtMost(constraints.maxWidth - gap)
                                val actions = measurables[1].measure(looseConstraints.copy(minWidth = actionsWidth, maxWidth = actionsWidth))
                                val textWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - actions.width - gap else 0
                                val text = measurables[0].measure(
                                    if (constraints.hasBoundedWidth) {
                                        looseConstraints.copy(minWidth = textWidth, maxWidth = textWidth)
                                    } else {
                                        looseConstraints
                                    },
                                )
                                val height = maxOf(text.height, actions.height)
                                layout(constraints.constrainWidth(text.width + gap + actions.width), constraints.constrainHeight(height)) {
                                    text.placeRelative(0, (height - text.height) / 2)
                                    actions.placeRelative(text.width + gap, (height - actions.height) / 2)
                                }
                            } else {
                                val text = measurables[0].measure(looseConstraints)
                                val actions = measurables[1].measure(looseConstraints.offset(vertical = -text.height))
                                layout(
                                    constraints.constrainWidth(maxOf(text.width, actions.width)),
                                    constraints.constrainHeight(text.height + actions.height),
                                ) {
                                    text.placeRelative(0, 0)
                                    actions.placeRelative(0, text.height)
                                }
                            }
                        }

                        if (tail != DaxOnboardingDialogTail.None) {
                            val tailBandHeight = with(LocalDensity.current) { onboardingTailBandHeightPx().toDp() }
                            Spacer(Modifier.height(tailBandHeight))
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
        AnimatedDaxOnboardingStepIndicator(
            step = step,
            animate = animate && motionScale != 0f && !inInspectionMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = DaxOnboardingDialogDefaults.StepIndicatorEndInset)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // ConstraintLayout rounds negative solved coordinates toward zero.
                    layout(placeable.width, 0) { placeable.place(0, (-placeable.height / 2 + .5f).toInt()) }
                }
                .graphicsLayer { alpha = shellAlpha() },
        )
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
    DaxOnboardingDialogTail.BottomStartMirrored -> DaxSpeechBubbleTailPlacement(
        edgeFraction = 0f,
        offsetDp = 96f,
        present = true,
        mirrorFraction = 1f,
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
    DaxOnboardingDialogTail.BottomStartMirrored,
    DaxOnboardingDialogTail.BottomStartWide,
    DaxOnboardingDialogTail.BottomEnd,
    -> DaxSpeechBubbleTailPosition.Interpolated(placement.x, placement.y)
}

// Match the default ChangeBounds interpolator used by the View onboarding dialog.
private val BoundsAnimationEasing = Easing(AccelerateDecelerateInterpolator()::getInterpolation)

private fun boundsTween(duration: Duration) = tween<IntSize>(duration.inWholeMilliseconds.toInt(), easing = BoundsAnimationEasing)

private class CardSizeTracker {
    var naturalSize: IntSize? = null
    var lastSize by mutableStateOf<IntSize?>(null)
    var hasMeasured = false
    var maxHeight = Int.MAX_VALUE
    val bounded: Boolean get() = maxHeight != Int.MAX_VALUE
}

private fun Modifier.shadowAndClip(shape: () -> Shape, color: Color, elevation: Dp): Modifier = then(
    if (elevation == 0.dp) {
        Modifier.graphicsLayer {
            this.shape = shape()
            clip = true
        }
    } else if (Build.VERSION.SDK_INT >= 29) {
        Modifier.graphicsLayer {
            this.shape = shape()
            clip = true
            shadowElevation = elevation.toPx()
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

private fun Modifier.bubbleFill(shape: () -> Shape, fill: Color): Modifier = drawWithCache {
    val outline = shape().createOutline(size, layoutDirection, this)
    onDrawBehind { drawOutline(outline, fill) }
}

// Outside the card clip: like the View card's Material stroke, it overhangs the fill along the tail's slopes.
private fun Modifier.bubbleStroke(shape: () -> Shape, stroke: Color, strokeWidth: Dp): Modifier = drawWithCache {
    val strokePx = minOf(ceil(strokeWidth.toPx()), ceil(size.minDimension / 2))
    val path = (shape() as DaxSpeechBubbleShape).strokePath(size, layoutDirection, this, strokePx)
    onDrawWithContent {
        drawContent()
        if (size.minDimension > 0f) drawPath(path, stroke, style = Stroke(strokePx))
    }
}

private fun Modifier.animatedCardSize(
    animatable: Animatable<IntSize, AnimationVector2D>,
    tracker: CardSizeTracker,
    settled: () -> Boolean,
    holdingForMorph: () -> Boolean,
    followContent: () -> Boolean,
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
    val height = if (useAnimated && !followContent()) animatedSize.height else placeable.height
    layout(placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        placeable.place(0, 0)
    }
}

private class PlatformScrollbar(val thumb: Drawable, val thickness: Int) {
    companion object {
        @SuppressLint("ResourceType")
        fun from(context: Context): PlatformScrollbar? {
            val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.scrollbarThumbVertical, android.R.attr.scrollbarSize))
            return try {
                attributes.getDrawable(0)?.let { thumb ->
                    // A bare drawable has no state, which colour state lists resolve to their disabled colour.
                    thumb.state = intArrayOf(android.R.attr.state_enabled)
                    PlatformScrollbar(thumb, attributes.getDimensionPixelSize(1, 0))
                }
            } finally {
                attributes.recycle()
            }
        }
    }
}

// Mirrors ScrollBarDrawable's thumb geometry so the card matches the View ScrollView with fadeScrollbars="false".
private fun Modifier.platformScrollbar(scrollState: ScrollState, scrollbar: PlatformScrollbar): Modifier = drawWithContent {
    drawContent()
    val extent = size.height.roundToInt()
    val scrollRange = scrollState.maxValue
    if (extent <= 0 || scrollRange <= 0) return@drawWithContent
    val thickness = scrollbar.thickness
    val length = maxOf((extent.toFloat() * extent / (extent + scrollRange)).roundToInt(), thickness * 2)
    val top = ((extent - length).toFloat() * scrollState.value / scrollRange).roundToInt()
    val left = size.width.roundToInt() - thickness
    scrollbar.thumb.setBounds(left, top, left + thickness, top + length)
    scrollbar.thumb.draw(drawContext.canvas.nativeCanvas)
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
