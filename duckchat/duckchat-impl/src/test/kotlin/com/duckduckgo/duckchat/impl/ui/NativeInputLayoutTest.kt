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

import android.content.Context
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputLayout
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputLayout.Surface
import com.google.android.material.shape.RelativeCornerSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class NativeInputLayoutTest {

    @Test
    fun `the widget is the card's only child`() {
        val layout = NativeInputLayout(lightContext())

        assertEquals(1, layout.childCount)
        assertSame(layout.widget, layout.getChildAt(0))
        assertEquals(R.id.inputModeWidget, layout.widget.id)
    }

    @Test
    fun `contextual surface rounds all four corners`() {
        val layout = NativeInputLayout(lightContext()).apply { surface = Surface.CONTEXTUAL }
        val expected = layout.resources.getDimension(CommonR.dimen.extraLargeShapeCornerRadius)
        val model = layout.shapeAppearanceModel

        listOf(model.topLeftCornerSize, model.topRightCornerSize, model.bottomLeftCornerSize, model.bottomRightCornerSize).forEach {
            assertEquals(expected, it.getCornerSize(SAMPLE_BOUNDS))
        }
        assertFalse(layout.useCompatPadding)
    }

    @Test
    fun `only the top omnibar surface uses compat padding`() {
        val layout = NativeInputLayout(lightContext())

        Surface.entries.forEach { surface ->
            layout.surface = surface
            assertEquals(surface == Surface.OMNIBAR_TOP, layout.useCompatPadding)
        }
    }

    @Test
    fun `omnibar position switches between the omnibar surfaces only`() {
        val omnibar = NativeInputLayout(lightContext())
        val contextual = NativeInputLayout(lightContext()).apply { surface = Surface.CONTEXTUAL }

        omnibar.setOmnibarPosition(isBottom = true)
        contextual.setOmnibarPosition(isBottom = true)

        assertEquals(Surface.OMNIBAR_BOTTOM, omnibar.surface)
        assertEquals(Surface.CONTEXTUAL, contextual.surface)
    }

    @Test
    fun `in a dark Fire Tab the card and widget take the fire card colour`() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Dark_Fire)
        val fireCard = context.getColor(CommonR.color.omnibar_card_fire_dark)

        Surface.entries.forEach { surface ->
            val layout = NativeInputLayout(context).apply { this.surface = surface }
            assertEquals(fireCard, layout.cardBackgroundColor.defaultColor)
        }
        assertEquals(fireCard, NativeInputLayout(context).widget.context.getColorFromAttr(CommonR.attr.daxColorWindow))
    }

    @Test
    fun `in a light Fire Tab the card and widget take the dark fire card theme`() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light_Fire)
        val cardTheme = ContextThemeWrapper(context, CommonR.style.ThemeOverlay_DuckDuckGo_InputModeCard_DarkFire)
        val expectedWindow = cardTheme.getColorFromAttr(CommonR.attr.daxColorWindow)
        assertNotEquals(context.getColorFromAttr(CommonR.attr.daxColorWindow), expectedWindow)

        val layout = NativeInputLayout(context).apply { surface = Surface.CONTEXTUAL }

        assertEquals(expectedWindow, layout.cardBackgroundColor.defaultColor)
        assertEquals(expectedWindow, layout.widget.context.getColorFromAttr(CommonR.attr.daxColorWindow))
        assertEquals(
            cardTheme.getColorFromAttr(CommonR.attr.daxColorOmnibarAccent),
            layout.widget.context.getColorFromAttr(CommonR.attr.daxColorOmnibarAccent),
        )
    }

    @Test
    fun `when address bar rebrand is disabled top browser search-only state uses legacy radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = false)

        assertEquals(subject.dimension(CommonR.dimen.largeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `when address bar rebrand is enabled top browser search-only state uses pill shape`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)

        assertTrue(subject.card.shapeAppearanceModel.topLeftCornerSize is RelativeCornerSize)
        assertEquals(32f, subject.cornerSize())
    }

    @Test
    fun `when address bar rebrand is enabled bottom browser search-only state uses rebrand radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render(rebrandEnabled = true)

        assertEquals(subject.dimension(CommonR.dimen.rebrandInputRadius), subject.card.radius)
        assertFalse(subject.card.useCompatPadding)
    }

    @Test
    fun `when address bar rebrand is disabled bottom browser search-only state uses legacy radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render(rebrandEnabled = false)

        assertEquals(subject.dimension(CommonR.dimen.largeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `enabling address bar rebrand preserves top browser search-only geometry`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = false)
        val legacyHeight = subject.measureHeight()
        val legacyPaddingTop = subject.card.paddingTop
        val legacyTop = subject.visibleTop()
        assertTrue(legacyPaddingTop > 0)

        subject.render(rebrandEnabled = true)

        assertTrue(subject.card.useCompatPadding)
        assertEquals(legacyHeight, subject.measureHeight())
        assertEquals(legacyPaddingTop, subject.card.paddingTop)
        assertEquals(legacyTop, subject.visibleTop())
    }

    @Test
    fun `when top browser search-only transitions to bottom Duck AI it restores the fixed corner radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)
        subject.renderDuckAi()

        assertEquals(subject.dimension(CommonR.dimen.extraLargeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `when bottom browser search-only transitions to bottom Duck AI it restores the fixed corner radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render(rebrandEnabled = true)
        subject.renderDuckAi()

        assertEquals(subject.dimension(CommonR.dimen.extraLargeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `when address bar rebrand is disabled bottom Duck AI uses extra large radius`() {
        val subject = createSubject(inputPosition = NativeInputState.InputPosition.BOTTOM)

        subject.render(rebrandEnabled = false)
        subject.renderDuckAi()

        assertEquals(subject.dimension(CommonR.dimen.extraLargeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `when rebrand changes from enabled to disabled top browser search-only restores the fixed corner radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)
        subject.render(rebrandEnabled = false)

        assertEquals(subject.dimension(CommonR.dimen.largeShapeCornerRadius), subject.cornerSize())
    }

    @Test
    fun `when edit surface receives a top browser search-only state it keeps its card shape`() {
        val subject = createSubject(surface = Surface.EDIT)
        val originalShape = subject.card.shapeAppearanceModel

        subject.render(rebrandEnabled = true)

        assertSame(originalShape, subject.card.shapeAppearanceModel)
    }

    @Test
    fun `when contextual surface receives a top browser search-only state it keeps its card shape`() {
        val subject = createSubject(surface = Surface.CONTEXTUAL)
        val originalShape = subject.card.shapeAppearanceModel

        subject.render(rebrandEnabled = true)

        assertSame(originalShape, subject.card.shapeAppearanceModel)
    }

    private fun lightContext(): Context =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)

    private fun createSubject(
        inputPosition: NativeInputState.InputPosition = NativeInputState.InputPosition.TOP,
        surface: Surface? = null,
    ): Subject {
        val isBottom = inputPosition == NativeInputState.InputPosition.BOTTOM
        val card = NativeInputLayout(lightContext()).apply {
            layoutParams = ViewGroup.MarginLayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT)
            this.surface = surface ?: if (isBottom) Surface.OMNIBAR_BOTTOM else Surface.OMNIBAR_TOP
        }
        return Subject(card, inputPosition)
    }

    private class Subject(
        val card: NativeInputLayout,
        private val inputPosition: NativeInputState.InputPosition,
    ) {
        private var rebrandEnabled = false

        fun render(rebrandEnabled: Boolean) {
            this.rebrandEnabled = rebrandEnabled
            card.applyOmnibarShape(
                NativeInputState(
                    inputMode = NativeInputState.InputMode.SEARCH_ONLY,
                    inputContext = NativeInputState.InputContext.BROWSER,
                    inputPosition = inputPosition,
                ),
                rebrandEnabled,
            )
        }

        fun renderDuckAi() {
            card.applyOmnibarShape(
                NativeInputState(
                    inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
                    inputContext = NativeInputState.InputContext.DUCK_AI,
                    inputPosition = NativeInputState.InputPosition.BOTTOM,
                ),
                rebrandEnabled,
            )
        }

        fun dimension(id: Int): Float = card.resources.getDimension(id)

        fun cornerSize(): Float = card.shapeAppearanceModel.topLeftCornerSize.getCornerSize(SAMPLE_BOUNDS)

        fun measureHeight(): Int {
            card.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            return card.measuredHeight
        }

        fun visibleTop(): Int {
            val params = card.layoutParams as ViewGroup.MarginLayoutParams
            return params.topMargin + card.paddingTop
        }
    }

    private companion object {
        val SAMPLE_BOUNDS = RectF(0f, 0f, 200f, 64f)
    }
}
