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
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class NativeInputModeWidgetShapeTest {

    @Test
    fun `when address bar rebrand is disabled top browser search-only state uses legacy radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = false)

        assertEquals(
            subject.context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius),
            subject.cornerSize(),
        )
    }

    @Test
    fun `when address bar rebrand is enabled top browser search-only state uses pill shape`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)

        assertEquals(32f, subject.cornerSize())
    }

    @Test
    fun `enabling address bar rebrand does not change top browser search-only card height`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = false)
        val legacyHeight = subject.measureHeight()

        subject.render(rebrandEnabled = true)

        assertEquals(legacyHeight, subject.measureHeight())
    }

    @Test
    fun `when top browser search-only transitions to bottom Duck AI it restores the fixed corner radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)
        subject.renderDuckAi()

        assertEquals(
            subject.context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius),
            subject.cornerSize(),
        )
    }

    @Test
    fun `when rebrand changes from enabled to disabled top browser search-only restores the fixed corner radius`() {
        val subject = createSubject()

        subject.render(rebrandEnabled = true)
        subject.render(rebrandEnabled = false)

        assertEquals(
            subject.context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius),
            subject.cornerSize(),
        )
    }

    private fun createSubject(): Subject {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
        )
        val card = MaterialCardView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT)
            useCompatPadding = true
            radius = context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)
        }
        val widget = NativeInputModeWidget(context)
        card.addView(widget)
        val toggles = FakeFeatureToggleFactory.create(AppBrandDesignUpdateToggles::class.java)
        widget.appBrandDesignUpdateToggles = toggles
        NativeInputModeWidget::class.java.getDeclaredField("nativeInputState").apply {
            isAccessible = true
            set(
                widget,
                NativeInputState(
                    inputMode = NativeInputState.InputMode.SEARCH_ONLY,
                    inputContext = NativeInputState.InputContext.BROWSER,
                    inputPosition = NativeInputState.InputPosition.TOP,
                ),
            )
        }

        val applyOmnibarShape = NativeInputModeWidget::class.java.getDeclaredMethod("applyOmnibarShape").apply {
            isAccessible = true
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val sampleBounds = RectF(0f, 0f, 200f, 64f)

        return Subject(context, card, widget, toggles, applyOmnibarShape, widthSpec, heightSpec, sampleBounds)
    }

    private data class Subject(
        val context: ContextThemeWrapper,
        val card: MaterialCardView,
        val widget: NativeInputModeWidget,
        val toggles: AppBrandDesignUpdateToggles,
        val applyOmnibarShape: java.lang.reflect.Method,
        val widthSpec: Int,
        val heightSpec: Int,
        val sampleBounds: RectF,
    ) {
        fun render(rebrandEnabled: Boolean) {
            toggles.addressBar().setRawStoredState(State(enable = rebrandEnabled))
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
    }
}
