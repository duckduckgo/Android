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

package com.duckduckgo.app.browser.webview

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopOmnibarBrowserContainerLayoutBehaviorTest {

    private lateinit var behavior: TopOmnibarBrowserContainerLayoutBehavior
    private lateinit var parent: CoordinatorLayout
    private lateinit var child: View
    private lateinit var dependency: View

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        behavior = TopOmnibarBrowserContainerLayoutBehavior(context, null)
        parent = CoordinatorLayout(context)
        child = View(context)
        dependency = View(context)
        parent.addView(
            child,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    @Test
    fun whenDependencyFullyExpandedThenMarginSetToExactVisibleHeight() {
        dependency.layout(0, 0, 500, 224)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 0
        child.layoutParams = lp

        behavior.correctBottomMargin(child, dependency)

        assertEquals(224, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenDependencyFullyCollapsedThenMarginSetToZero() {
        dependency.layout(0, -224, 500, 0)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 100
        child.layoutParams = lp

        behavior.correctBottomMargin(child, dependency)

        assertEquals(0, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenDependencyPartiallyCollapsedThenMarginSetToVisiblePortionMinusOffset() {
        dependency.layout(0, -112, 500, 112)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 0
        child.layoutParams = lp

        behavior.correctBottomMargin(child, dependency)

        val marginOffsetPx = (7 * child.resources.displayMetrics.density).toInt()
        assertEquals(112 - marginOffsetPx, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenMarginAlreadyCorrectThenNoChange() {
        dependency.layout(0, -224, 500, 0)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 0
        child.layoutParams = lp

        behavior.correctBottomMargin(child, dependency)

        assertEquals(0, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenChildIsGoneThenNoChange() {
        dependency.layout(0, 0, 500, 224)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 50
        child.layoutParams = lp
        child.visibility = View.GONE

        behavior.correctBottomMargin(child, dependency)

        assertEquals(50, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenVisibleHeightLessThanOffsetThenMarginIsZero() {
        // dependency.top = -220, height = 224, visible = 4, less than marginOffsetPx
        dependency.layout(0, -220, 500, 4)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 50
        child.layoutParams = lp

        behavior.correctBottomMargin(child, dependency)

        assertEquals(0, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }
}
