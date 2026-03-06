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

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        behavior = TopOmnibarBrowserContainerLayoutBehavior(context, null)
        parent = CoordinatorLayout(context)
        child = View(context)
        parent.addView(
            child,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    @Test
    fun whenChildDoesNotExtendPastParentThenMarginResetToZero() {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 19
        child.layoutParams = lp

        behavior.correctBottomMarginIfNeeded(child, parent)

        assertEquals(0, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenChildExtendsPastParentThenMarginSetToDiff() {
        // Force layout so child.bottom and parent.height have real values
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 500, 500)

        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 50
        child.layoutParams = lp

        // Shift the child down so child.bottom + translationY > parent.height
        child.translationY = 30f

        behavior.correctBottomMarginIfNeeded(child, parent)

        assertEquals(30, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }

    @Test
    fun whenBottomMarginIsAlreadyZeroThenNoChange() {
        val lp = child.layoutParams as CoordinatorLayout.LayoutParams
        lp.bottomMargin = 0
        child.layoutParams = lp

        behavior.correctBottomMarginIfNeeded(child, parent)

        assertEquals(0, (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin)
    }
}
