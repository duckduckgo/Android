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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class BrandDesignUpdateOnboardingLayoutHelperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenRootHeightIsZeroThenCalculateWalkingDaxHeightReturnsNull() {
        val root = createViewWithSize(width = 1080, height = 0)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val dax = createViewWithSize(width = 200, height = 274)

        root.addView(dialog)
        root.addView(dax)

        assertNull(BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenDialogIsInsideScrollViewThenCalculateWalkingDaxHeightReturnsMax() {
        val root = createViewWithSize(width = 1080, height = 800)
        val scrollView = ScrollView(context)
        val dialog = createViewWithSize(width = 1080, height = 600)
        val dax = createViewWithSize(width = 200, height = 274)

        scrollView.addView(dialog)
        root.addView(scrollView)
        root.addView(dax)

        assertEquals(274, BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenEnoughSpaceForMaxDaxHeightThenReturnMaxHeight() {
        // root=1000, dialog=400 → available=600 ≥ max=274 → returns 274
        val root = createViewWithSize(width = 1080, height = 1000)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val dax = createViewWithSize(width = 200, height = 274)

        root.addView(dialog)
        root.addView(dax)

        assertEquals(274, BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenSpaceBetweenMinAndMaxThenReturnAvailableHeight() {
        // root=800, dialog=400, daxMargin=200 → available=200 (between 174 and 274) → returns 200
        val root = createViewWithSize(width = 1080, height = 800)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val dax = createViewWithSize(width = 200, height = 274)

        root.addView(dialog)
        root.addView(dax)
        // Set margin after addView to avoid FrameLayout replacing the LayoutParams
        (dax.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 200

        assertEquals(200, BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenAvailableSpaceBelowMinHeightThenReturnNull() {
        // root=600, dialog=400, daxMargin=100 → available=100 < min=174 → null
        val root = createViewWithSize(width = 1080, height = 600)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val dax = createViewWithSize(width = 200, height = 274)

        root.addView(dialog)
        root.addView(dax)
        // Set margin after addView to avoid FrameLayout replacing the LayoutParams
        (dax.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 100

        assertNull(BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenAvailableSpaceExactlyAtMinHeightThenReturnMinHeight() {
        // root=750, dialog=400, daxMargin=176 → available=174 == min=174 → returns 174
        val root = createViewWithSize(width = 1080, height = 750)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val dax = createViewWithSize(width = 200, height = 274)

        root.addView(dialog)
        root.addView(dax)
        // Set margin after addView to avoid FrameLayout replacing the LayoutParams
        (dax.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 176

        assertEquals(174, BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(root, dialog, dax, maxHeightPx = 274, minHeightPx = 174))
    }

    @Test
    fun whenRootHasBottomPaddingThenAvailableHeightIsReduced() {
        // root 800 tall, paddingBottom 200 → content 600. dialog 400, deco margin 0 → available 200 (between 174 and 274) → 200
        val root = createViewWithSize(width = 1080, height = 800)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val deco = createViewWithSize(width = 200, height = 274)
        root.setPadding(0, 0, 0, 200)
        root.addView(dialog)
        root.addView(deco)

        assertEquals(
            200,
            BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(
                root,
                dialog,
                deco,
                maxHeightPx = 274,
                minHeightPx = 174,
            ),
        )
    }

    @Test
    fun whenTabletNavBarPaddingThenWingShrinksToFloor() {
        // Tab A9 repro @ density 1.5: root 1200px, navBar paddingBottom 84px → contentH 1116.
        // dialog 869px, wing max 299px (199dp), floor 247px (165dp), deco margin 0.
        // available = 1116 - 869 = 247 → clamped to [247, 299] → 247 (wing shrinks, dialog fits).
        // Without the padding fix: 1200 - 869 = 331 → clamped 299 → overflowed card by 52px.
        val root = createViewWithSize(width = 1920, height = 1200)
        val dialog = createViewWithSize(width = 1920, height = 869)
        val deco = createViewWithSize(width = 200, height = 299)
        root.setPadding(0, 0, 0, 84)
        root.addView(dialog)
        root.addView(deco)

        assertEquals(
            247,
            BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(
                root,
                dialog,
                deco,
                maxHeightPx = 299,
                minHeightPx = 247,
            ),
        )
    }

    // computeDecorationHeight (pure) tests

    @Test
    fun whenAvailableAboveMaxThenComputeReturnsMax() {
        // availableContent 1000 - dialogSpace 400 - decoMargin 100 = 500 ≥ max 274 → 274
        assertEquals(
            274,
            BrandDesignUpdateOnboardingLayoutHelper.computeDecorationHeight(
                availableContentHeight = 1000,
                dialogSpace = 400,
                decorationBottomMargin = 100,
                maxHeightPx = 274,
                minHeightPx = 174,
            ),
        )
    }

    @Test
    fun whenAvailableBetweenMinAndMaxThenComputeReturnsAvailable() {
        // 800 - 400 - 200 = 200, between 174 and 274 → 200
        assertEquals(
            200,
            BrandDesignUpdateOnboardingLayoutHelper.computeDecorationHeight(
                availableContentHeight = 800,
                dialogSpace = 400,
                decorationBottomMargin = 200,
                maxHeightPx = 274,
                minHeightPx = 174,
            ),
        )
    }

    @Test
    fun whenAvailableBelowMinThenComputeReturnsNull() {
        // 600 - 400 - 100 = 100 < min 174 → null
        assertNull(
            BrandDesignUpdateOnboardingLayoutHelper.computeDecorationHeight(
                availableContentHeight = 600,
                dialogSpace = 400,
                decorationBottomMargin = 100,
                maxHeightPx = 274,
                minHeightPx = 174,
            ),
        )
    }

    @Test
    fun whenReproNumbersThenComputeShrinksWingToFloor() {
        // Tab A9 repro (px @ density 1.5): rootContentH 1116 (= rootH 1200 − navBar padding 84),
        // dialogSpace 869, decoMargin 0; bottom wing max 299px (199dp), floor 247px (165dp).
        // available = 1116 − 869 − 0 = 247 → clamp to [247, 299] → 247
        assertEquals(
            247,
            BrandDesignUpdateOnboardingLayoutHelper.computeDecorationHeight(
                availableContentHeight = 1116,
                dialogSpace = 869,
                decorationBottomMargin = 0,
                maxHeightPx = 299,
                minHeightPx = 247,
            ),
        )
    }

    private fun createViewWithSize(
        width: Int,
        height: Int,
        topMargin: Int = 0,
        bottomMargin: Int = 0,
    ): FrameLayout {
        val view = FrameLayout(context)
        val params = ViewGroup.MarginLayoutParams(width, height).apply {
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
        }
        view.layoutParams = params
        view.minimumHeight = height

        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, width, height)

        return view
    }
}
