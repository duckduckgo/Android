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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class DaxOnboardingDialogTest {

    private var durationScale = 1f
    private val motionScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = durationScale
    }

    @get:Rule
    val compose = createComposeRule(effectContext = motionScale)

    private val state = DaxOnboardingDialogState()
    private val contentKey = mutableStateOf("first")
    private val title = mutableStateOf("A title long enough to keep typing while a pointer gesture is in progress")
    private val animate = mutableStateOf(true)
    private val mounted = mutableStateOf(true)
    private val ctaEnabled = mutableStateOf(true)
    private val registerReady = mutableStateOf(true)
    private val finished = mutableListOf<String>()
    private val ready = mutableListOf<Pair<String, Boolean>>()
    private val enabledObservations = mutableListOf<Pair<String, Boolean>>()
    private val disposedContent = mutableListOf<String>()
    private var clicks = 0
    private var dismissals = 0
    private var composingContent = false
    private var onFinishedAction: (String) -> Unit = {}
    private var onReadyAction: () -> Unit = {}
    private var slotContent: (@Composable () -> Unit)? = null
    private var hostContent: (@Composable () -> Unit)? = null
    private var dialogBody: AnnotatedString? = null
    private var slotPosition = Offset.Zero
    private var slotClicks = 0

    @Before
    fun pauseAnimationClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun naturalCompletionNotifiesOnceAndEnablesActions() {
        showDialog()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        assertTrue(state.isAnimating)

        finishAnimation()

        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to true), ready)
        assertFalse(state.isAnimating)
        compose.onNodeWithText("Continue").assertIsEnabled()
        finishAnimation()
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun firstTapDuringTypingSkipsAndSecondTapActivatesCta() {
        showDialog()
        advance(1_000)
        val position = ctaPosition()

        tap(position)

        assertEquals(0, clicks)
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
        compose.onNodeWithText("Continue").assertIsEnabled()

        tap(position)
        assertEquals(1, clicks)
    }

    @Test
    fun gestureStartingDuringIntroCannotActivateAfterClockAdvances() {
        showDialog()
        advance(1_000)
        val position = ctaPosition()
        compose.onRoot().performTouchInput { down(position) }

        finishAnimation()
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()

        assertEquals(0, clicks)
        assertEquals(listOf("first"), finished)
        tap(position)
        assertEquals(1, clicks)
    }

    @Test
    fun tappingDismissDuringIntroSkipsBeforeDismissCanActivate() {
        showDialog()
        advance(1_000)
        val position = compose.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot.center
        tap(position)
        assertEquals(0, dismissals)
        tap(position)
        assertEquals(1, dismissals)
    }

    @Test
    fun skipDuringHeaderSettlesOnce() {
        showDialog(header = true)
        advance(700)
        skipAndAssertSettled()
    }

    @Test
    fun skipDuringTypingSettlesOnce() {
        showDialog()
        advance(1_000)
        skipAndAssertSettled()
    }

    @Test
    fun skipDuringContentFadeSettlesOnce() {
        title.value = "Hi"
        showDialog()
        advance(720)
        skipAndAssertSettled()
    }

    @Test
    fun emptyTitleCompletesWithoutWaitingForTyping() {
        title.value = ""
        showDialog()
        advance(2_000)
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to true), ready)
    }

    @Test
    fun immediatePresentationNotifiesOnceWithoutAnimating() {
        animate.value = false
        showDialog()
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.runOnIdle { title.value = "Changed title" }
        advanceRecompositionFrames()
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun inspectionModeSnapsTheIntro() {
        showDialog(inspection = true)
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
    }

    @Test
    fun reducedMotionSnapsTheIntro() {
        durationScale = 0f
        showDialog()
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
    }

    @Test
    fun repeatedSkipsAndSameKeyUpdatesDoNotReplayIntro() {
        showDialog()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle {
            state.skip()
            ctaEnabled.value = false
            animate.value = false
            title.value = "Updated title"
        }
        advanceRecompositionFrames()
        compose.onNodeWithText("Updated title").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { ctaEnabled.value = true }
        advanceRecompositionFrames()
        compose.onNodeWithText("Continue").assertIsEnabled()
        finishAnimation()
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
    }

    @Test
    fun titleChangedDuringTypingRendersFullyAndCompletes() {
        showDialog()
        advance(1_000)
        compose.runOnIdle { title.value = "Replacement" }
        advanceRecompositionFrames()
        compose.onNodeWithText("Replacement").assertExists()
        advance(500)
        assertEquals(listOf("first"), finished)
        assertFalse(state.isAnimating)
    }

    @Test
    fun replacingUnfinishedContentSuppressesOldCallbackAndStartsGated() {
        showDialog()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { contentKey.value = "second" }
        advanceRecompositionFrames()

        assertTrue(state.isAnimating)
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        assertTrue(enabledObservations.filter { it.first == "second" }.all { !it.second })
        compose.runOnIdle { contentKey.value = "third" }
        advanceRecompositionFrames()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()

        assertEquals(listOf("first", "third"), finished)
        assertEquals(listOf("first" to false, "third" to false), ready)
        assertTrue(disposedContent.containsAll(listOf("first", "second")))
    }

    @Test
    fun returningToEarlierKeyStartsAFreshIntro() {
        showDialog()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { contentKey.value = "second" }
        advanceRecompositionFrames()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { contentKey.value = "first" }
        advanceRecompositionFrames()

        assertTrue(state.isAnimating)
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        assertEquals(listOf("first", "second", "first"), finished)
    }

    @Test
    fun disposingUnfinishedDialogResetsStateWithoutCompletion() {
        showDialog()
        advance(1_000)
        compose.runOnIdle { mounted.value = false }
        advanceRecompositionFrames()
        assertFalse(state.isAnimating)
        compose.runOnIdle { state.skip() }
        finishAnimation()
        assertTrue(finished.isEmpty())
        assertTrue(ready.isEmpty())
        assertEquals(listOf("first"), disposedContent)
    }

    @Test
    fun unattachedSkipIsANoOp() {
        state.skip()
        assertFalse(state.isAnimating)
        showDialog()
        assertTrue(state.isAnimating)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun lateContentReadyRegistrationRunsAfterCompositionAndOnlyOnce() {
        registerReady.value = false
        animate.value = false
        showDialog()
        assertTrue(ready.isEmpty())
        compose.runOnIdle { registerReady.value = true }
        advanceRecompositionFrames()
        assertEquals(listOf("first" to false), ready)
        compose.runOnIdle { ctaEnabled.value = false }
        advanceRecompositionFrames()
        assertEquals(1, ready.size)
    }

    @Test
    fun completionRemovingDialogDeliversContentReadyBeforeDisposal() {
        onFinishedAction = {
            assertEquals(listOf("first" to true), ready)
            mounted.value = false
        }
        showDialog()

        finishAnimation()

        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to true), ready)
        assertEquals(listOf("first"), disposedContent)
        assertFalse(state.isAnimating)
    }

    @Test
    fun completionReplacingDialogDeliversOldContentReadyBeforeDisposal() {
        onFinishedAction = { key ->
            if (key == "first") {
                assertEquals(listOf("first" to false), ready)
                contentKey.value = "second"
            }
        }
        showDialog()

        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()

        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
        assertEquals(listOf("first"), disposedContent)
        assertTrue(state.isAnimating)
    }

    @Test
    fun firstGestureOnAlwaysClickableSlotIsConsumedAndSecondGestureClicks() {
        installClickableSlot()
        showDialog()
        advance(1_000)

        tap(slotPosition)

        assertEquals(0, slotClicks)
        assertFalse(state.isAnimating)
        tap(slotPosition)
        assertEquals(1, slotClicks)
    }

    @Test
    fun heldSlotGestureIsConsumedEvenWhenIntroSettlesBeforeRelease() {
        installClickableSlot()
        showDialog()
        advance(1_000)
        compose.onRoot().performTouchInput { down(slotPosition) }

        finishAnimation()
        compose.onRoot().performTouchInput { up() }
        advanceRecompositionFrames()

        assertEquals(0, slotClicks)
        tap(slotPosition)
        assertEquals(1, slotClicks)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun finishAccessibilityActionRevealsInitiallyHiddenTitleBodyAndSlot() {
        dialogBody = AnnotatedString("Dialog body")
        installClickableSlot()
        showDialog()
        compose.onNodeWithText(title.value).assertDoesNotExist()
        compose.onNodeWithText("Dialog body").assertDoesNotExist()
        compose.onNodeWithContentDescription("Slot action").assertDoesNotExist()

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .performCustomAccessibilityActionWithLabel("Finish animation")
        advanceRecompositionFrames()

        assertFalse(state.isAnimating)
        assertEquals(listOf("first" to false), ready)
        compose.onNodeWithText(title.value).assertExists()
        compose.onNodeWithText("Dialog body").assertExists()
        compose.onNodeWithContentDescription("Slot action").assertExists()
    }

    @Test
    fun keyboardTraversalSkipsIntroCtasAndReachesThemAfterSettling() {
        val before = FocusRequester()
        lateinit var focus: FocusManager
        var afterFocused = false
        compose.setContent {
            focus = LocalFocusManager.current
            DuckDuckGoTheme {
                OnboardingTheme {
                    Column {
                        Box(Modifier.size(24.dp).focusRequester(before).focusable())
                        DaxOnboardingDialog(
                            contentKey = "focus",
                            title = "Title",
                            state = state,
                            primaryCta = DaxOnboardingDialogCta("Continue", onClick = {}),
                            secondaryCta = DaxOnboardingDialogCta("Skip for now", onClick = {}),
                        )
                        Box(Modifier.size(24.dp).onFocusChanged { afterFocused = it.isFocused }.focusable())
                    }
                }
            }
        }
        compose.runOnIdle {
            before.requestFocus()
            focus.moveFocus(FocusDirection.Next)
        }
        assertTrue("Keyboard focus must skip the fading buttons", afterFocused)
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle {
            before.requestFocus()
            focus.moveFocus(FocusDirection.Next)
        }
        compose.onNodeWithText("Continue").assertIsFocused()
        compose.runOnIdle { focus.moveFocus(FocusDirection.Next) }
        compose.onNodeWithText("Skip for now").assertIsFocused()
    }

    @Test
    fun slotRejectsFocusDuringIntroAndAcceptsFocusAfterSettling() {
        val requester = FocusRequester()
        var focused = false
        slotContent = {
            Box(
                Modifier.size(48.dp)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable(),
            )
        }
        showDialog()

        compose.runOnIdle { requester.requestFocus() }
        advanceRecompositionFrames()
        assertFalse(focused)

        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { requester.requestFocus() }
        advanceRecompositionFrames()
        assertTrue(focused)
    }

    @Test
    fun nestedFocusGroupInSlotRejectsFocusDuringIntro() {
        val requester = FocusRequester()
        var focused = false
        slotContent = {
            Box(Modifier.focusGroup()) {
                Box(
                    Modifier.size(48.dp)
                        .focusRequester(requester)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable(),
                )
            }
        }
        showDialog()

        compose.runOnIdle { requester.requestFocus() }
        advanceRecompositionFrames()
        assertFalse(focused)

        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { requester.requestFocus() }
        advanceRecompositionFrames()
        assertTrue(focused)
    }

    @Test
    fun focusRequestedFromContentReadyIsGranted() {
        val requester = FocusRequester()
        var focused = false
        slotContent = {
            Box(
                Modifier.size(48.dp)
                    .focusRequester(requester)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable(),
            )
        }
        onReadyAction = { requester.requestFocus() }
        showDialog()

        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()

        assertTrue(focused)
    }

    @Test
    fun replacingDialogContentPreservesFocusOwnedByHost() {
        val hostRequester = FocusRequester()
        val slotRequester = FocusRequester()
        var hostFocused = false
        hostContent = {
            Box(
                Modifier.size(48.dp)
                    .focusRequester(hostRequester)
                    .onFocusChanged { hostFocused = it.isFocused }
                    .focusable(),
            )
        }
        slotContent = { Box(Modifier.size(48.dp).focusRequester(slotRequester).focusable()) }
        showDialog()
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        compose.runOnIdle { slotRequester.requestFocus() }
        advanceRecompositionFrames()
        compose.runOnIdle { hostRequester.requestFocus() }
        advanceRecompositionFrames()
        assertTrue(hostFocused)

        compose.runOnIdle { contentKey.value = "second" }
        advanceRecompositionFrames()

        assertTrue(hostFocused)
        assertTrue(state.isAnimating)
    }

    private fun installClickableSlot() {
        slotContent = {
            Box(
                Modifier.size(48.dp)
                    .onGloballyPositioned { slotPosition = it.boundsInRoot().center }
                    .semantics { contentDescription = "Slot action" }
                    .clickable { slotClicks++ },
            )
        }
    }

    private fun showDialog(header: Boolean = false, inspection: Boolean = false) {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    CompositionLocalProvider(LocalInspectionMode provides inspection) {
                        Column {
                            hostContent?.invoke()
                            if (mounted.value) Dialog(header)
                        }
                    }
                }
            }
        }
        advanceRecompositionFrames()
    }

    @Composable
    private fun Dialog(header: Boolean) {
        DaxOnboardingDialog(
            contentKey = contentKey.value,
            title = title.value,
            body = dialogBody,
            modifier = Modifier.fillMaxWidth(),
            state = state,
            animate = animate.value,
            headerImage = if (header) ColorPainter(Color.Red) else null,
            primaryCta = DaxOnboardingDialogCta("Continue", enabled = ctaEnabled.value) { clicks++ },
            dismiss = DaxOnboardingDialogDismiss("Close") { dismissals++ },
            onIntroFinished = {
                finished.add(it)
                onFinishedAction(it)
            },
        ) {
            val key = contentKey.value
            enabledObservations.add(key to enabled)
            DisposableEffect(key) {
                onDispose { disposedContent.add(key) }
            }
            composingContent = true
            if (registerReady.value) {
                onContentReady { animated ->
                    assertFalse("Content ready must run after composition", composingContent)
                    ready.add(key to animated)
                    onReadyAction()
                }
            }
            composingContent = false
            slotContent?.invoke()
        }
    }

    private fun skipAndAssertSettled() {
        assertTrue(state.isAnimating)
        compose.runOnIdle { state.skip() }
        advanceRecompositionFrames()
        assertFalse(state.isAnimating)
        compose.onNodeWithText("Continue").assertIsEnabled()
        assertEquals(listOf("first"), finished)
        assertEquals(listOf("first" to false), ready)
        compose.runOnIdle { state.skip() }
        finishAnimation()
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    private fun ctaPosition(): Offset = compose.onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot.center

    private fun tap(position: Offset) {
        compose.onRoot().performTouchInput {
            down(position)
            up()
        }
        advanceRecompositionFrames()
    }

    private fun finishAnimation() = advance(10_000)

    private fun advanceRecompositionFrames() {
        // Completion and late registration can schedule another effect after the first recomposition.
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    @Test
    fun dialogMeasuredWithUnboundedHeightTakesItsContentHeight() {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    Box(Modifier.wrapContentHeight(unbounded = true)) {
                        DaxOnboardingDialog(contentKey = "unbounded", title = "Unbounded", body = "Body", animate = false)
                    }
                }
            }
        }

        compose.onNodeWithText("Body").assertExists()
    }

    @Test
    fun shortCenteredTitleSpansTheTextWidthSoItCentres() {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    DaxOnboardingDialog(contentKey = "centred", title = "Hi", body = "A body that is wider than the title", animate = false)
                }
            }
        }

        val title = compose.onNodeWithText("Hi").getUnclippedBoundsInRoot()
        val body = compose.onNodeWithText("A body that is wider than the title").getUnclippedBoundsInRoot()
        assertEquals((body.left + body.right).value / 2, (title.left + title.right).value / 2, 1f)
    }

    @Test
    fun dialogWithTailMeasuredAtZeroWidthDoesNotCrash() {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    Box(Modifier.size(width = 0.dp, height = 400.dp)) {
                        DaxOnboardingDialog(
                            contentKey = "zero-width",
                            title = "Zero width",
                            body = "Body",
                            animate = false,
                            tail = DaxOnboardingDialogTail.BottomStart,
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Body").assertExists()
    }
}
