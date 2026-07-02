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

package com.duckduckgo.app.onboarding.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class OnboardingDecorationFitCorrectorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Harness(
        val corrector: OnboardingDecorationFitCorrector,
        val dialog: View,
        val decoration: View,
    )

    private fun harness(
        rootHeight: Int,
        rootPaddingBottom: Int = 0,
        dialogHeight: Int,
        dialogTopMargin: Int = 0,
        contentHeight: Int,
        viewportHeight: Int,
        decorationHeight: Int,
        decorationBottomMargin: Int = 0,
        minHeightPx: Int,
        maxHeightPx: Int,
        wrapDialogInScrollView: Boolean = false,
        dialogMeasuredHeight: Int = dialogHeight,
        contentMeasuredHeight: Int = contentHeight,
        viewportMeasuredHeight: Int = viewportHeight,
        bottomOverlapPx: Int = 0,
        cardBottomInsetPx: Int = 0,
        enabled: Boolean = true,
        onDecorationHidden: () -> Unit = {},
    ): Harness {
        val root = ConstraintLayout(context)
        root.setPadding(0, 0, 0, rootPaddingBottom)
        root.layout(0, 0, 1920, rootHeight)

        // ScrollView viewport (the card's inner scroll container) wrapping the content.
        val viewport = FrameLayout(context)
        val cardContainer = View(context)
        viewport.addView(cardContainer)
        viewport.layout(0, 0, 1920, viewportHeight)
        cardContainer.layout(0, 0, 1920, contentHeight)

        val dialog = View(context)
        if (wrapDialogInScrollView) {
            val pageScroll = ScrollView(context)
            root.addView(pageScroll)
            pageScroll.addView(dialog)
        } else {
            root.addView(dialog)
        }
        (dialog.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dialogTopMargin
        dialog.layout(0, 0, 1920, dialogHeight)

        // The corrector reads measuredHeight (the settled target), which can differ from the laid-out
        // height mid-transition. Measure viewport first, then cardContainer (it is viewport's child, so
        // measuring the parent would otherwise clobber it), then the dialog.
        fun View.measureExactly(height: Int) = measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        viewport.measureExactly(viewportMeasuredHeight)
        cardContainer.measureExactly(contentMeasuredHeight)
        dialog.measureExactly(dialogMeasuredHeight)

        val decoration = View(context)
        root.addView(decoration)
        (decoration.layoutParams as ViewGroup.MarginLayoutParams).apply {
            height = decorationHeight
            bottomMargin = decorationBottomMargin
        }
        decoration.layout(0, 0, 200, decorationHeight)

        val corrector = OnboardingDecorationFitCorrector(root, dialog, cardContainer, onDecorationHidden, { cardBottomInsetPx })
        corrector.enabled = enabled
        corrector.track(decoration, minHeightPx = minHeightPx, maxHeightPx = maxHeightPx, bottomOverlapPx = bottomOverlapPx)
        return Harness(corrector, dialog, decoration)
    }

    @Test
    fun whenCardOverflowsByAFewPixelsThenDecorationShrinksToFit() {
        // available = 1200 - 84 = 1116. overflow = 869 - 866 = 3. dialogSpace = 866 + 3 = 869.
        // slack = 1116 - 869 = 247 -> clamp[247,299] = 247 < current 250 -> shrink to 247.
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 866,
            contentHeight = 869,
            viewportHeight = 866,
            decorationHeight = 250,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertFalse(h.corrector.correctOnce())
        assertEquals(247, h.decoration.layoutParams.height)
    }

    @Test
    fun whenDisabledThenDecorationIsNeitherShrunkNorHidden() {
        // Same tight fit that would hide the decoration when enabled, but with the flag off the
        // corrector must leave the decoration's height and visibility untouched (V2-off == develop).
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 1080,
            contentHeight = 1100,
            viewportHeight = 1080,
            decorationHeight = 299,
            minHeightPx = 247,
            maxHeightPx = 299,
            enabled = false,
        )

        assertTrue(h.corrector.correctOnce())
        assertEquals(299, h.decoration.layoutParams.height)
        assertFalse(h.decoration.isGone)
    }

    @Test
    fun whenAlreadyFittedThenSecondPassIsIdempotent() {
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 866,
            contentHeight = 869,
            viewportHeight = 866,
            decorationHeight = 250,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertFalse(h.corrector.correctOnce()) // shrinks to 247
        assertTrue(h.corrector.correctOnce()) // target == current -> no mutation, proceed with draw
        assertEquals(247, h.decoration.layoutParams.height)
    }

    @Test
    fun whenSlackBelowMinThenDecorationHiddenAndCardFillsParent() {
        // overflow = 1100 - 1080 = 20. dialogSpace = 1080 + 20 = 1100. slack = 1116 - 1100 = 16 < min 247 -> null.
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 1080,
            contentHeight = 1100,
            viewportHeight = 1080,
            decorationHeight = 299,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertFalse(h.corrector.correctOnce())
        assertTrue(h.decoration.isGone)
        val lp = h.dialog.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(0f, lp.verticalBias)
        assertEquals(ConstraintLayout.LayoutParams.UNSET, lp.bottomToTop)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.bottomToBottom)
    }

    @Test
    fun whenAlreadyHiddenThenNoOp() {
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 1080,
            contentHeight = 1100,
            viewportHeight = 1080,
            decorationHeight = 299,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertFalse(h.corrector.correctOnce()) // hides
        assertTrue(h.corrector.correctOnce()) // already gone -> proceed, no change
        assertTrue(h.decoration.isGone)
    }

    @Test
    fun whenDecorationHiddenThenOnHiddenCallbackFiresOncePerTransition() {
        var hiddenCount = 0
        val h = harness(
            rootHeight = 1200, rootPaddingBottom = 84,
            dialogHeight = 1080, contentHeight = 1100, viewportHeight = 1080,
            decorationHeight = 299, minHeightPx = 247, maxHeightPx = 299,
            onDecorationHidden = { hiddenCount++ },
        )

        assertFalse(h.corrector.correctOnce()) // hides -> callback fires
        assertTrue(h.corrector.correctOnce()) // already gone -> no second fire
        assertEquals(1, hiddenCount)
    }

    @Test
    fun whenDecorationOnlyShrinksThenOnHiddenCallbackDoesNotFire() {
        var hiddenCount = 0
        val h = harness(
            rootHeight = 1200, rootPaddingBottom = 84,
            dialogHeight = 866, contentHeight = 869, viewportHeight = 866,
            decorationHeight = 250, minHeightPx = 247, maxHeightPx = 299,
            onDecorationHidden = { hiddenCount++ },
        )

        assertFalse(h.corrector.correctOnce()) // shrinks, does not hide
        assertEquals(0, hiddenCount)
    }

    @Test
    fun whenSlackExceedsCurrentThenDecorationIsNotGrown() {
        // Lots of room (target would clamp to max 299) but shrink-only must never increase a smaller decoration.
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 0,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertTrue(h.corrector.correctOnce())
        assertEquals(200, h.decoration.layoutParams.height)
    }

    @Test
    fun whenDialogInsideScrollViewThenCorrectorIsANoOp() {
        // Land-phone page-scroll regime: decoration must stay at max, page scrolls.
        val h = harness(
            rootHeight = 1200, rootPaddingBottom = 0,
            dialogHeight = 1100, contentHeight = 1200, viewportHeight = 1100,
            decorationHeight = 299, minHeightPx = 247, maxHeightPx = 299,
            wrapDialogInScrollView = true,
        )

        assertTrue(h.corrector.correctOnce())
        assertEquals(299, h.decoration.layoutParams.height)
        assertFalse(h.decoration.isGone)
    }

    @Test
    fun whenRootNotLaidOutThenNoOp() {
        val h = harness(
            rootHeight = 0,
            dialogHeight = 866,
            contentHeight = 869,
            viewportHeight = 866,
            decorationHeight = 250,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertTrue(h.corrector.correctOnce())
        assertEquals(250, h.decoration.layoutParams.height)
    }

    @Test
    fun whenNothingTrackedThenNoOp() {
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 866,
            contentHeight = 869,
            viewportHeight = 866,
            decorationHeight = 250,
            minHeightPx = 247,
            maxHeightPx = 299,
        )
        h.corrector.clear()

        assertTrue(h.corrector.correctOnce())
        assertEquals(250, h.decoration.layoutParams.height)
    }

    @Test
    fun whenViewportNotLaidOutThenNoOp() {
        // A not-yet-laid-out viewport (height 0) with positive content must not be read as full-content overflow.
        val h = harness(
            rootHeight = 1200,
            rootPaddingBottom = 84,
            dialogHeight = 866,
            contentHeight = 869,
            viewportHeight = 0,
            decorationHeight = 250,
            minHeightPx = 247,
            maxHeightPx = 299,
        )

        assertTrue(h.corrector.correctOnce())
        assertEquals(250, h.decoration.layoutParams.height)
        assertFalse(h.decoration.isGone)
    }

    @Test
    fun whenLaidOutHeightMidTransitionExceedsButMeasuredHeightFitsThenDecorationKept() {
        // Mid inter-dialog ChangeBounds: laid-out dialog still reports the previous (taller) dialog at
        // 1804, but the settled target measures 1410. Off measuredHeight there is room (availPx 646 >=
        // min 390), so the decoration must be kept; off laid-out height it would wrongly hide.
        val h = harness(
            rootHeight = 2340,
            dialogHeight = 1804,
            dialogMeasuredHeight = 1410,
            dialogTopMargin = 128,
            contentHeight = 1589,
            contentMeasuredHeight = 1195,
            viewportHeight = 1589,
            viewportMeasuredHeight = 1195,
            decorationHeight = 468,
            decorationBottomMargin = 156,
            minHeightPx = 390,
            maxHeightPx = 468,
        )

        assertTrue(h.corrector.correctOnce())
        assertFalse(h.decoration.isGone)
        assertEquals(468, h.decoration.layoutParams.height)
    }

    @Test
    fun whenMeasuredHeightGenuinelyTooTallThenDecorationHidden() {
        // The settled target itself leaves less than min below the card: the decoration must still hide,
        // so the measured-height read does not regress genuine-overflow handling.
        val h = harness(
            rootHeight = 2340,
            dialogHeight = 1804,
            dialogMeasuredHeight = 2100,
            dialogTopMargin = 128,
            contentHeight = 1589,
            contentMeasuredHeight = 1885,
            viewportHeight = 1589,
            viewportMeasuredHeight = 1885,
            decorationHeight = 468,
            decorationBottomMargin = 156,
            minHeightPx = 390,
            maxHeightPx = 468,
        )

        assertFalse(h.corrector.correctOnce())
        assertTrue(h.decoration.isGone)
    }

    // The card reserves the bottom-bar inset only when it is the bottom-most element: bottom-anchored
    // AND no decoration shown below it (a shown decoration is >= its min height, which exceeds any bar
    // inset, so it covers the bar on the card's behalf). The four states:

    @Test
    fun whenCardBottomAnchoredAndNoDecorationThenItReservesTheBottomInset() {
        // Card alone at the bottom (no decoration tracked) → must reserve the inset to clear the bar.
        val h = harness(
            rootHeight = 1200,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
            cardBottomInsetPx = 108,
        )
        h.corrector.clear()
        (h.dialog.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToTop = ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        assertFalse(h.corrector.correctOnce())
        assertEquals(108, (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
    }

    @Test
    fun whenDisabledThenBottomAnchoredCardReservesNoInset() {
        // V2 off: the corrector must stay inert so V2-off layout matches legacy, which never reserved an
        // inset. Even bottom-anchored with no decoration, the margin stays 0.
        val h = harness(
            rootHeight = 1200,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
            cardBottomInsetPx = 108,
            enabled = false,
        )
        h.corrector.clear()
        (h.dialog.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToTop = ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        h.corrector.correctOnce()
        assertEquals(0, (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
    }

    @Test
    fun whenCardBottomAnchoredButDecorationShownThenNoBottomInset() {
        // Phone regime: the card is bottom-anchored even while a decoration is shown (only tablets stack
        // it above). The decoration covers the bar, so the card must reserve NO inset — otherwise the
        // inset feeds dialogSpace and the corrector hides the very decoration below it.
        val h = harness(
            rootHeight = 1200,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
            cardBottomInsetPx = 108,
        )
        (h.dialog.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToTop = ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }

        h.corrector.correctOnce()
        assertEquals(0, (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
    }

    @Test
    fun whenDecorationShownAboveCardThenNoBottomInset() {
        // Tablet regime: card stacked above a shown decoration (bottomToTop). No inset; a stale one left
        // by a previous bottom-anchored state is cleared so it cannot steal the decoration's fit room.
        val h = harness(
            rootHeight = 1200,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
            cardBottomInsetPx = 108,
        )
        (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 108
        (h.dialog.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            bottomToTop = h.decoration.id
        }

        assertFalse(h.corrector.correctOnce())
        assertEquals(0, (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
    }

    @Test
    fun whenCardNotBottomAnchoredAndNoDecorationThenNoBottomInset() {
        // Card neither at the bottom nor with a decoration → no inset; a stale one is cleared.
        val h = harness(
            rootHeight = 1200,
            dialogHeight = 600,
            contentHeight = 600,
            viewportHeight = 600,
            decorationHeight = 200,
            minHeightPx = 247,
            maxHeightPx = 299,
            cardBottomInsetPx = 108,
        )
        h.corrector.clear()
        (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 108
        (h.dialog.layoutParams as ConstraintLayout.LayoutParams).apply {
            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            bottomToTop = ConstraintLayout.LayoutParams.UNSET
        }

        assertFalse(h.corrector.correctOnce())
        assertEquals(0, (h.dialog.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
    }

    @Test
    fun whenDecorationFitsOnlyViaBottomOverlapThenItIsKept() {
        // Input-screen left wing: without reclaiming the band the card reserves below its body for the
        // tail, the wing misses its 130dp floor and hides; reclaiming it (minus the card gap) keeps it.
        val tight = harness(
            rootHeight = 2340, dialogHeight = 1722, dialogTopMargin = 128,
            contentHeight = 1566, viewportHeight = 1566,
            decorationHeight = 390, decorationBottomMargin = 156,
            minHeightPx = 390, maxHeightPx = 588,
        )
        assertFalse(tight.corrector.correctOnce())
        assertTrue(tight.decoration.isGone)

        val withOverlap = harness(
            rootHeight = 2340, dialogHeight = 1722, dialogTopMargin = 128,
            contentHeight = 1566, viewportHeight = 1566,
            decorationHeight = 390, decorationBottomMargin = 156,
            minHeightPx = 390, maxHeightPx = 588,
            bottomOverlapPx = 132,
        )
        assertTrue(withOverlap.corrector.correctOnce())
        assertFalse(withOverlap.decoration.isGone)
    }
}
