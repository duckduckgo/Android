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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import android.content.Context
import android.graphics.RectF
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class HighUsageModelFooterViewTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val testee = HighUsageModelFooterView(context)

    @Test
    fun whenRenderedThenMessageIncludesModelShortName() {
        testee.render(modelShortName = "Opus", onDismiss = {})

        val message = testee.findViewById<DaxTextView>(R.id.highUsageModelFooterMessage)
        assertEquals(
            "Opus uses limits up to 2-5x faster than basic models.",
            message.text.toString(),
        )
        assertEquals(12f, message.textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
    }

    @Test
    fun whenCreatedThenDismissControlIsExposed() {
        val dismiss = testee.findViewById<ImageView>(R.id.highUsageModelFooterDismiss)

        assertTrue(dismiss.isClickable)
        assertEquals("Dismiss", dismiss.contentDescription)
    }

    @Test
    fun whenCreatedThenDismissControlHasMinimumTouchTarget() {
        val dismiss = testee.findViewById<ImageView>(R.id.highUsageModelFooterDismiss)
        val minimumTouchTarget = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            48f,
            context.resources.displayMetrics,
        ).roundToInt()

        assertEquals(minimumTouchTarget, dismiss.layoutParams.width)
        assertEquals(minimumTouchTarget, dismiss.layoutParams.height)
    }

    @Test
    fun whenCreatedThenFooterUsesCardSurfaceCornersAndElevation() {
        val root: View = testee

        assertTrue(root is MaterialCardView)
        val card = root as MaterialCardView
        assertEquals(
            context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface),
            card.cardBackgroundColor.defaultColor,
        )
        assertTrue(card.cardElevation > 0f)
    }

    @Test
    fun whenCreatedThenOnlyBottomCornersAreRounded() {
        val bounds = RectF(0f, 0f, 100f, 100f)
        val shape = testee.shapeAppearanceModel
        val radius = context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)

        assertEquals(0f, shape.topLeftCornerSize.getCornerSize(bounds), 0f)
        assertEquals(0f, shape.topRightCornerSize.getCornerSize(bounds), 0f)
        assertEquals(radius, shape.bottomLeftCornerSize.getCornerSize(bounds), 0f)
        assertEquals(radius, shape.bottomRightCornerSize.getCornerSize(bounds), 0f)
    }

    @Test
    fun whenCreatedThenContentStartsBelowTheHostOverlap() {
        val content = testee.getChildAt(0) as LinearLayout

        assertEquals(context.resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap), content.paddingTop)
        assertEquals(0, content.paddingBottom)
    }

    @Test
    fun whenDismissControlIsClickedThenDismissCallbackIsInvoked() {
        var dismissed = false
        testee.render(modelShortName = "Opus", onDismiss = { dismissed = true })

        testee.findViewById<ImageView>(R.id.highUsageModelFooterDismiss).performClick()

        assertTrue(dismissed)
    }
}
