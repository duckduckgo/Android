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

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
class DaxOnboardingDialogLifecycleTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = DaxOnboardingDialogState()
    private val orientation = mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
    private val layoutDirection = mutableStateOf(LayoutDirection.Ltr)
    private val contentKey = mutableStateOf("first")
    private val title = mutableStateOf("Title")
    private val animate = mutableStateOf(false)
    private val mounted = mutableStateOf(true)
    private val showHeader = mutableStateOf(false)
    private val showSlot = mutableStateOf(true)
    private val ctaEnabled = mutableStateOf(true)
    private val ctaVersion = mutableStateOf("first")
    private val bodyHeight = mutableStateOf(32.dp)
    private val slotHeight = mutableStateOf(48.dp)
    private val identities = mutableMapOf<String, Any>()
    private val measuredHeights = mutableMapOf<String, Int>()
    private val effectStarts = mutableMapOf<String, Int>()
    private val jobStarts = mutableMapOf<String, Int>()
    private val disposals = mutableMapOf<String, Int>()
    private val ready = mutableListOf<Pair<Any, Boolean>>()
    private val finished = mutableListOf<String>()
    private val clicks = mutableListOf<String>()
    private val focusRequester = FocusRequester()
    private var focused = false
    private var includeFocusAndView = false
    private var viewFactories = 0
    private var viewReleases = 0
    private var embeddedView: View? = null
    private var unboundedParent = false
    private var dialogWidth = 300.dp
    private var density = 1f

    @Before
    fun pauseAnimationClock() {
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun rotationPreservesBodyAndSlotRememberedStateAndEffects() {
        showDialog()
        val body = identities.getValue("body")
        val slot = identities.getValue("slot")

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        rotate(Configuration.ORIENTATION_PORTRAIT)

        assertSame(body, identities.getValue("body"))
        assertSame(slot, identities.getValue("slot"))
        assertEquals(mapOf("body" to 1, "slot" to 1), effectStarts)
        assertEquals(mapOf("body" to 1, "slot" to 1), jobStarts)
        assertTrue(disposals.isEmpty())
        assertEquals(listOf(slot to false), ready)
        assertEquals(listOf("first"), finished)
    }

    @Test
    fun rotationPreservesFocusedSlotAndAndroidViewUntilContentIsRemoved() {
        includeFocusAndView = true
        showDialog()
        compose.runOnIdle { focusRequester.requestFocus() }
        frames()
        assertTrue(focused)
        val view = embeddedView

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        rotate(Configuration.ORIENTATION_PORTRAIT)

        assertTrue(focused)
        assertSame(view, embeddedView)
        assertEquals(1, viewFactories)
        assertEquals(0, viewReleases)
        compose.runOnIdle { showSlot.value = false }
        frames()
        assertFalse(focused)
        assertEquals(1, viewReleases)
        assertEquals(1, disposals["slot"])
    }

    @Test
    fun rotatedActionsUseCurrentEligibilityAndCallback() {
        showDialog()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle {
            ctaEnabled.value = false
            ctaVersion.value = "second"
        }
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        assertEquals(listOf("first"), clicks)

        compose.runOnIdle { ctaEnabled.value = true }
        rotate(Configuration.ORIENTATION_PORTRAIT)
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertEquals(listOf("first", "second"), clicks)
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun sameKeyBodyAndSlotUpdatesAfterRotationUseCurrentInputsWithoutRemounting() {
        showDialog()
        val body = identities.getValue("body")
        val slot = identities.getValue("slot")
        rotate(Configuration.ORIENTATION_LANDSCAPE)
        compose.runOnIdle {
            bodyHeight.value = 64.dp
            slotHeight.value = 96.dp
        }
        frames()

        assertEquals(64f * density, measuredHeights.getValue("body").toFloat(), 1f)
        assertEquals(96f * density, measuredHeights.getValue("slot").toFloat(), 1f)
        assertSame(body, identities.getValue("body"))
        assertSame(slot, identities.getValue("slot"))
        assertEquals(mapOf("body" to 1, "slot" to 1), effectStarts)
        assertEquals(mapOf("body" to 1, "slot" to 1), jobStarts)
        assertTrue(disposals.isEmpty())
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun rotationDuringTypingKeepsOriginalCompletionDeadlineAndNotifiesOnce() {
        animate.value = true
        title.value = "Typing should continue through rotation without starting over."
        showDialog()
        advance(1_800)
        assertTrue(state.isAnimating)
        assertTrue(finished.isEmpty())

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        advance(1_200)

        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf(identities.getValue("slot") to true), ready)
        rotate(Configuration.ORIENTATION_PORTRAIT)
        advance(5_000)
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun rotationDuringContentFadePreservesSlotAndCompletion() {
        animate.value = true
        title.value = ""
        showDialog()
        advance(700)
        assertTrue(state.isAnimating)
        val slot = identities.getValue("slot")

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        advance(1_000)

        assertSame(slot, identities.getValue("slot"))
        assertEquals(listOf(slot to true), ready)
        assertEquals(listOf("first"), finished)
        assertEquals(1, effectStarts["slot"])
        assertTrue(disposals.isEmpty())
    }

    @Test
    fun rotationDuringReplacementResizePreservesNewContent() {
        showDialog()
        compose.runOnIdle {
            animate.value = true
            contentKey.value = "second"
            slotHeight.value = 160.dp
            title.value = "Next"
        }
        frames()
        advance(300)
        assertTrue(state.isAnimating)
        val body = identities.getValue("body")
        val slot = identities.getValue("slot")

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        advance(2_000)

        assertSame(body, identities.getValue("body"))
        assertSame(slot, identities.getValue("slot"))
        assertEquals(mapOf("body" to 2, "slot" to 2), effectStarts)
        assertEquals(mapOf("body" to 1, "slot" to 1), disposals)
        assertEquals(listOf("first", "second"), finished)
        assertEquals(2, ready.size)
        assertEquals(slot to true, ready.last())
    }

    @Test
    fun sameKeyHeaderAdditionAndRemovalPreserveBodyAndSlot() {
        showDialog()
        val body = identities.getValue("body")
        val slot = identities.getValue("slot")

        compose.runOnIdle { showHeader.value = true }
        frames()
        assertSame(body, identities.getValue("body"))
        assertSame(slot, identities.getValue("slot"))
        compose.runOnIdle { showHeader.value = false }
        frames()

        assertSame(body, identities.getValue("body"))
        assertSame(slot, identities.getValue("slot"))
        assertEquals(mapOf("body" to 1, "slot" to 1), effectStarts)
        assertEquals(mapOf("body" to 1, "slot" to 1), jobStarts)
        assertTrue(disposals.isEmpty())
        assertEquals(listOf(slot to false), ready)
        assertEquals(listOf("first"), finished)
    }

    @Test
    fun replacingNonNullSlotUsesLatestReadinessCallbackWithoutRepeatingDelivery() {
        val useReplacement = mutableStateOf(false)
        val deliveries = mutableListOf<String>()
        val original: @Composable DaxOnboardingDialogContentScope.() -> Unit = {
            onContentReady { deliveries.add("original") }
        }
        val replacement: @Composable DaxOnboardingDialogContentScope.() -> Unit = {
            onContentReady { deliveries.add("replacement") }
        }
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    DaxOnboardingDialog(
                        contentKey = "first",
                        title = "A title long enough to swap the slot before the intro completes",
                        state = state,
                        content = if (useReplacement.value) replacement else original,
                    )
                }
            }
        }
        frames()
        assertTrue(state.isAnimating)
        assertTrue(deliveries.isEmpty())

        compose.runOnIdle { useReplacement.value = true }
        frames()
        advance(5_000)
        assertEquals(listOf("replacement"), deliveries)

        compose.runOnIdle { useReplacement.value = false }
        frames()
        advance(5_000)
        assertEquals(listOf("replacement"), deliveries)
    }

    @Test
    fun sameKeyTitleRemovalAndRestorationPreserveBody() {
        showDialog()
        val body = identities.getValue("body")

        compose.runOnIdle { title.value = "" }
        frames()
        assertSame(body, identities.getValue("body"))
        compose.runOnIdle { title.value = "Restored" }
        frames()

        assertSame(body, identities.getValue("body"))
        assertEquals(1, effectStarts["body"])
        assertEquals(1, jobStarts["body"])
        assertTrue(disposals.isEmpty())
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun sameKeyTitleRemovedDuringTypingAndRestoredPreservesBodyAndCompletesOnce() {
        animate.value = true
        title.value = "This title should still be typing when it is removed from the dialog."
        showDialog()
        advance(1_000)
        assertTrue(state.isAnimating)
        val body = identities.getValue("body")
        compose.runOnIdle { title.value = "" }
        frames()

        assertSame(body, identities.getValue("body"))
        advance(500)
        assertFalse(state.isAnimating)
        assertEquals(listOf("first"), finished)
        assertEquals(listOf(identities.getValue("slot") to true), ready)
        compose.runOnIdle { title.value = "Restored" }
        frames()

        assertSame(body, identities.getValue("body"))
        assertEquals(1, effectStarts["body"])
        assertEquals(1, jobStarts["body"])
        assertTrue(disposals.isEmpty())
        assertEquals(1, finished.size)
        assertEquals(1, ready.size)
    }

    @Test
    fun removedSlotDoesNotReceiveReadinessAndReinsertionReceivesPendingReadiness() {
        animate.value = true
        showDialog()
        val removedSlot = identities.getValue("slot")
        compose.runOnIdle { showSlot.value = false }
        frames()
        advance(5_000)

        assertEquals(listOf("first"), finished)
        assertTrue(ready.isEmpty())
        assertEquals(1, disposals["slot"])
        compose.runOnIdle { showSlot.value = true }
        frames()
        val replacement = identities.getValue("slot")

        assertNotSame(removedSlot, replacement)
        assertEquals(listOf(replacement to true), ready)
        advance(1_000)
        assertEquals(1, ready.size)
        assertEquals(1, finished.size)
    }

    @Test
    fun slotReinsertedBeforeReadyReceivesReadinessWithFreshState() {
        animate.value = true
        showDialog()
        val removedSlot = identities.getValue("slot")
        compose.runOnIdle { showSlot.value = false }
        frames()
        compose.runOnIdle { showSlot.value = true }
        frames()
        val replacement = identities.getValue("slot")

        assertNotSame(removedSlot, replacement)
        assertTrue(ready.isEmpty())
        advance(5_000)

        assertEquals(listOf(replacement to true), ready)
        assertEquals(listOf("first"), finished)
        assertEquals(2, effectStarts["slot"])
        assertEquals(1, disposals["slot"])
    }

    @Test
    fun slotReinsertedAfterDeliveredReadinessDoesNotReceiveItAgain() {
        showDialog()
        val original = identities.getValue("slot")
        assertEquals(listOf(original to false), ready)
        compose.runOnIdle { showSlot.value = false }
        frames()
        compose.runOnIdle { showSlot.value = true }
        frames()

        assertNotSame(original, identities.getValue("slot"))
        assertEquals(listOf(original to false), ready)
        assertEquals(listOf("first"), finished)
        assertEquals(2, effectStarts["slot"])
        assertEquals(1, disposals["slot"])
    }

    @Test
    fun changingKeyAndRemovingDialogDisposeBodySlotAndAndroidView() {
        includeFocusAndView = true
        showDialog()
        val body = identities.getValue("body")
        val slot = identities.getValue("slot")
        val view = embeddedView

        compose.runOnIdle { contentKey.value = "second" }
        frames()

        assertNotSame(body, identities.getValue("body"))
        assertNotSame(slot, identities.getValue("slot"))
        assertNotSame(view, embeddedView)
        assertEquals(mapOf("body" to 1, "slot" to 1), disposals)
        assertEquals(2, viewFactories)
        assertEquals(1, viewReleases)
        compose.runOnIdle { mounted.value = false }
        frames()

        assertEquals(mapOf("body" to 2, "slot" to 2), disposals)
        assertEquals(2, viewReleases)
        assertFalse(state.isAnimating)
    }

    @Test
    fun portraitStacksGroupsAndLandscapeKeepsActionWidthAndGapInBothDirections() {
        title.value = ""
        showDialog()
        val portraitBody = bounds("body")
        val portraitSlot = bounds("slot")
        assertTrue(portraitSlot.top >= portraitBody.bottom)

        rotate(Configuration.ORIENTATION_LANDSCAPE)
        val ltrBody = bounds("body")
        val ltrSlot = bounds("slot")
        assertEquals(160f * density, ltrSlot.width, 1f)
        assertEquals(60f * density, ltrSlot.left - ltrBody.right, 1f)

        compose.runOnIdle { layoutDirection.value = LayoutDirection.Rtl }
        frames()
        val rtlBody = bounds("body")
        val rtlSlot = bounds("slot")
        assertEquals(ltrSlot.width, rtlSlot.width, 1f)
        assertEquals(60f * density, rtlBody.left - rtlSlot.right, 1f)
    }

    @Test
    fun narrowLandscapeConstrainsActionGroupToAvailableWidth() {
        orientation.value = Configuration.ORIENTATION_LANDSCAPE
        title.value = ""
        dialogWidth = 150.dp
        showDialog()

        val dialog = bounds("dialog")
        val slot = bounds("slot")
        assertTrue(dialog.width <= 150f * density)
        assertTrue(slot.left >= dialog.left)
        assertTrue(slot.right <= dialog.right)
        assertTrue(slot.width > 0f)
        assertEquals(28f * density, slot.left - dialog.left, 1f)

        compose.runOnIdle { layoutDirection.value = LayoutDirection.Rtl }
        frames()
        val rtlSlot = bounds("slot")
        assertEquals(slot.width, rtlSlot.width, 1f)
        assertTrue(rtlSlot.left >= dialog.left)
        assertTrue(rtlSlot.right <= dialog.right)
        assertEquals(28f * density, dialog.right - rtlSlot.right, 1f)
    }

    @Test
    fun unboundedLandscapeMeasuresTallSlotAtItsNaturalHeight() {
        orientation.value = Configuration.ORIENTATION_LANDSCAPE
        title.value = ""
        unboundedParent = true
        slotHeight.value = 1_000.dp
        showDialog()

        assertEquals(1_000f * density, measuredHeights.getValue("slot").toFloat(), 1f)
        assertTrue(measuredHeights.getValue("dialog") > 1_000f * density)
    }

    private fun showDialog() {
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    density = LocalDensity.current.density
                    val configuration = Configuration(LocalConfiguration.current).apply {
                        orientation = this@DaxOnboardingDialogLifecycleTest.orientation.value
                    }
                    CompositionLocalProvider(
                        LocalConfiguration provides configuration,
                        LocalLayoutDirection provides layoutDirection.value,
                    ) {
                        Box(Modifier.then(if (unboundedParent) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                            if (mounted.value) dialogContent()
                        }
                    }
                }
            }
        }
        frames()
    }

    private val dialogContent: @Composable () -> Unit = {
        val currentCtaVersion = ctaVersion.value
        val currentBodyHeight = bodyHeight.value
        val currentSlotHeight = slotHeight.value
        DaxContextualOnboardingDialog(
            contentKey = contentKey.value,
            title = title.value,
            body = { probeContent("body", currentBodyHeight, remember { Any() }) },
            modifier = Modifier.width(dialogWidth).testTag("dialog")
                .onGloballyPositioned { measuredHeights["dialog"] = it.size.height },
            state = state,
            animate = animate.value,
            headerImage = if (showHeader.value) ColorPainter(Color.Red) else null,
            primaryCta = DaxOnboardingDialogCta("Continue", enabled = ctaEnabled.value) { clicks.add(currentCtaVersion) },
            onIntroFinished = { finished.add(it) },
            content = if (showSlot.value) {
                {
                    val token = remember { Any() }
                    probeContent("slot", currentSlotHeight, token)
                    onContentReady { animated -> ready.add(token to animated) }
                    if (includeFocusAndView) {
                        Box(
                            Modifier.size(24.dp).focusRequester(focusRequester)
                                .onFocusChanged { focused = it.isFocused }.focusable(),
                        )
                        AndroidView(
                            factory = { context ->
                                viewFactories++
                                View(context).also { embeddedView = it }
                            },
                            modifier = Modifier.size(24.dp),
                            onReset = null,
                            onRelease = { viewReleases++ },
                            update = {},
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    private val probeContent: @Composable (String, Dp, Any) -> Unit = { name, height, identity ->
        SideEffect { identities[name] = identity }
        DisposableEffect(identity) {
            effectStarts[name] = effectStarts.getOrDefault(name, 0) + 1
            onDispose { disposals[name] = disposals.getOrDefault(name, 0) + 1 }
        }
        LaunchedEffect(identity) { jobStarts[name] = jobStarts.getOrDefault(name, 0) + 1 }
        Box(Modifier.fillMaxWidth().height(height).testTag(name).onGloballyPositioned { measuredHeights[name] = it.size.height })
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun rotate(newOrientation: Int) {
        compose.runOnIdle { orientation.value = newOrientation }
        frames()
    }

    private fun frames() {
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun advance(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }
}
