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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class BrandDesignUpdateOnboardingLayoutHelperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenRootViewHeightIsZeroThenReturnFalse() {
        val root = createViewWithSize(width = 1080, height = 0)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val decoration = createViewWithSize(width = 200, height = 200)

        root.addView(dialog)
        root.addView(decoration)

        assertFalse(BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(root, dialog, decoration))
    }

    @Test
    fun whenDialogIsInsideScrollViewThenReturnTrue() {
        val root = createViewWithSize(width = 1080, height = 800)
        val scrollView = ScrollView(context)
        val dialog = createViewWithSize(width = 1080, height = 600)
        val decoration = createViewWithSize(width = 200, height = 200)

        scrollView.addView(dialog)
        root.addView(scrollView)
        root.addView(decoration)

        assertTrue(BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(root, dialog, decoration))
    }

    @Test
    fun whenEnoughSpaceForBothDialogAndDecorationThenReturnTrue() {
        val root = createViewWithSize(width = 1080, height = 1000)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val decoration = createViewWithSize(width = 200, height = 200)

        root.addView(dialog)
        root.addView(decoration)

        assertTrue(BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(root, dialog, decoration))
    }

    @Test
    fun whenNotEnoughSpaceForDialogAndDecorationThenReturnFalse() {
        val root = createViewWithSize(width = 1080, height = 500)
        val dialog = createViewWithSize(width = 1080, height = 400)
        val decoration = createViewWithSize(width = 200, height = 200)

        root.addView(dialog)
        root.addView(decoration)

        assertFalse(BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(root, dialog, decoration))
    }

    @Test
    fun whenDecorationMarginsIncludedAndNotEnoughSpaceThenReturnFalse() {
        val root = createViewWithSize(width = 1080, height = 600)
        val dialog = createViewWithSize(width = 1080, height = 300)
        val decoration = createViewWithSize(width = 200, height = 200)

        root.addView(dialog)
        root.addView(decoration)

        // Set margins after addView to avoid FrameLayout replacing the LayoutParams
        (decoration.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = 150

        assertFalse(BrandDesignUpdateOnboardingLayoutHelper.hasSpaceForAnimation(root, dialog, decoration))
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
        // Set minimum height so the view reports correct measuredHeight
        // when re-measured with UNSPECIFIED spec inside hasSpaceForAnimation()
        view.minimumHeight = height

        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, width, height)

        return view
    }
}
