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

package com.duckduckgo.app.browser.nativeinput

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealNativeInputAnimatorTest {

    private lateinit var context: Context
    private lateinit var testee: RealNativeInputAnimator

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testee = RealNativeInputAnimator()
    }

    /**
     * Bottom-mode hierarchy as introduced by #8730: the card is a weighted child of a horizontal
     * LinearLayout, so card.layoutParams is LinearLayout.LayoutParams (not FrameLayout.LayoutParams).
     */
    private data class Hierarchy(val widgetView: View, val card: View, val omnibarCard: View)

    private fun bottomHierarchy(): Hierarchy {
        val widgetView = FrameLayout(context)
        val linear = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        widgetView.addView(linear, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val card = FrameLayout(context)
        linear.addView(card, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val omnibarCard = FrameLayout(context)
        widgetView.layout(0, 0, 1000, 200)
        linear.layout(0, 0, 1000, 200)
        card.layout(0, 0, 1000, 200)
        omnibarCard.layout(0, 0, 900, 100)
        return Hierarchy(widgetView, card, omnibarCard)
    }

    @Test
    fun whenAnimateExitInBottomModeWithLinearLayoutParentThenDoesNotThrow() {
        val (widgetView, card, omnibarCard) = bottomHierarchy()

        // Pre-fix this throws ClassCastException at stripCompatPadding; reaching the end is the assertion.
        testee.animateExit(
            widgetCard = card,
            widgetView = widgetView,
            omnibarCard = omnibarCard,
            isBottom = true,
            onComplete = {},
        )
    }

    @Test
    fun whenInitInBottomModeWithLinearLayoutParentThenReturnsMarginsAndZeroesWeight() {
        val (_, card, omnibarCard) = bottomHierarchy()

        val margins = testee.init(card, omnibarCard, omnibarCard.width, omnibarCard.height, isBottom = true)

        // Enter morph must no longer be skipped in bottom mode...
        assertNotNull(margins)
        // ...and the weight must be zeroed so explicit width lerps take effect.
        assertEquals(0f, (card.layoutParams as LinearLayout.LayoutParams).weight, 0f)
    }

    @Test
    fun whenInitInTopModeWithFrameLayoutParentThenReturnsMargins() {
        val widgetView = FrameLayout(context)
        val card = FrameLayout(context)
        widgetView.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val omnibarCard = FrameLayout(context)
        widgetView.layout(0, 0, 1000, 200)
        card.layout(0, 0, 1000, 200)
        omnibarCard.layout(0, 0, 900, 100)

        val margins = testee.init(card, omnibarCard, omnibarCard.width, omnibarCard.height, isBottom = false)

        assertNotNull(margins)
    }
}
