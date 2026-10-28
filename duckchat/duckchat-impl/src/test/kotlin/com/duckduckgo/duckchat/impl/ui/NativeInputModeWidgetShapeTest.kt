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

package com.duckduckgo.duckchat.impl.ui

import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RelativeCornerSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class NativeInputModeWidgetShapeTest {

    @Test
    fun `when top browser search-only state is rendered it uses pill shape`() {
        val subject = createSubject()

        subject.render()

        assertTrue(subject.card.shapeAppearanceModel.topLeftCornerSize is RelativeCornerSize)
        assertEquals(32f, subject.cornerSize())
    }

    @Test
    fun `when bottom browser search-only state is rendered it uses rebrand radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render()

        assertEquals(
            subject.context.resources.getDimension(CommonR.dimen.rebrandInputRadius),
            subject.card.radius,
        )
        assertFalse(subject.card.useCompatPadding)
    }

    @Test
    fun `when top browser search-only transitions to bottom Duck AI it restores the fixed corner radius`() {
        val subject = createSubject()

        subject.render()
        subject.renderDuckAi()

        assertEquals(
            subject.context.resources.getDimension(CommonR.dimen.extraLargeShapeCornerRadius),
            subject.cornerSize(),
        )
    }

    @Test
    fun `when bottom browser search-only transitions to bottom Duck AI it restores the fixed corner radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render()
        subject.renderDuckAi()

        assertEquals(
            subject.context.resources.getDimension(CommonR.dimen.extraLargeShapeCornerRadius),
            subject.cornerSize(),
        )
    }

    @Test
    fun `when edit widget receives a top browser search-only state it keeps its card shape`() {
        val subject = createSubject()
        NativeInputModeWidget::class.java.getDeclaredField("isEditWidget").apply {
            isAccessible = true
            setBoolean(subject.widget, true)
        }
        val originalShape = subject.card.shapeAppearanceModel

        subject.render()

        assertSame(originalShape, subject.card.shapeAppearanceModel)
    }

    private fun createSubject(inputPosition: NativeInputState.InputPosition = NativeInputState.InputPosition.TOP): Subject {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            CommonR.style.Theme_DuckDuckGo_Light,
        )
        val isBottom = inputPosition == NativeInputState.InputPosition.BOTTOM
        val elevation = (if (isBottom) 3f else 6f) * context.resources.displayMetrics.density
        val card = MaterialCardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT)
            useCompatPadding = !isBottom
            maxCardElevation = elevation
            cardElevation = elevation
            radius = context.resources.getDimension(CommonR.dimen.largeShapeCornerRadius)
        }
        val widget = NativeInputModeWidget(context)
        card.addView(widget)
        NativeInputModeWidget::class.java.getDeclaredField("nativeInputState").apply {
            isAccessible = true
            set(
                widget,
                NativeInputState(
                    inputMode = NativeInputState.InputMode.SEARCH_ONLY,
                    inputContext = NativeInputState.InputContext.BROWSER,
                    inputPosition = inputPosition,
                ),
            )
        }

        val applyOmnibarShape = NativeInputModeWidget::class.java.getDeclaredMethod("applyOmnibarShape").apply {
            isAccessible = true
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val sampleBounds = RectF(0f, 0f, 200f, 64f)

        return Subject(context, card, widget, applyOmnibarShape, widthSpec, heightSpec, sampleBounds)
    }

    private data class Subject(
        val context: ContextThemeWrapper,
        val card: MaterialCardView,
        val widget: NativeInputModeWidget,
        val applyOmnibarShape: java.lang.reflect.Method,
        val widthSpec: Int,
        val heightSpec: Int,
        val sampleBounds: RectF,
    ) {
        fun render() {
            applyOmnibarShape.invoke(widget)
        }

        fun renderDuckAi() {
            NativeInputModeWidget::class.java.getDeclaredField("nativeInputState").apply {
                isAccessible = true
                set(
                    widget,
                    NativeInputState(
                        inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
                        inputContext = NativeInputState.InputContext.DUCK_AI,
                        inputPosition = NativeInputState.InputPosition.BOTTOM,
                    ),
                )
            }
            applyOmnibarShape.invoke(widget)
        }

        fun cornerSize(): Float = card.shapeAppearanceModel.topLeftCornerSize.getCornerSize(sampleBounds)

        fun measureHeight(): Int {
            card.measure(widthSpec, heightSpec)
            return card.measuredHeight
        }

        fun visibleTop(): Int {
            val params = card.layoutParams as ViewGroup.MarginLayoutParams
            return params.topMargin + card.paddingTop
        }
    }
}
