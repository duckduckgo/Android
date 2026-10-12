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
package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NativeInputFooterViewTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val testee = NativeInputFooterView(context)
    private val card get() = testee.getChildAt(0) as MaterialCardView
    private val rowsContainer get() = card.getChildAt(0) as ViewGroup

    @Test
    fun whenCreatedThenCardUsesSurfaceColourAndElevation() {
        assertEquals(
            context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface),
            card.cardBackgroundColor.defaultColor,
        )
        assertTrue(card.cardElevation > 0f)
    }

    @Test
    fun whenCreatedThenOnlyBottomCornersAreRounded() {
        val bounds = RectF(0f, 0f, 100f, 100f)
        val shape = card.shapeAppearanceModel
        val radius = context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)

        assertEquals(0f, shape.topLeftCornerSize.getCornerSize(bounds), 0f)
        assertEquals(0f, shape.topRightCornerSize.getCornerSize(bounds), 0f)
        assertEquals(radius, shape.bottomLeftCornerSize.getCornerSize(bounds), 0f)
        assertEquals(radius, shape.bottomRightCornerSize.getCornerSize(bounds), 0f)
    }

    @Test
    fun whenCreatedThenRowsStartBelowTheOverlap() {
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap), rowsContainer.paddingTop)
    }

    @Test
    fun whenSeveralRowsAreEmittedThenTheyStackInOrderWithSpacingBetweenThem() = runTest {
        val first = View(context)
        val second = View(context)
        val third = View(context)
        testee.bind(this, flowOf(NativeInputFooterCoordinator.State(footers = listOf(first, second, third).map(::footerOf))))
        shadowOf(testee).callOnAttachedToWindow()
        advanceUntilIdle()

        val rows = rowsContainer
        val spacing = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2)
        assertSame(first, rows.getChildAt(0))
        assertSame(second, rows.getChildAt(1))
        assertSame(third, rows.getChildAt(2))
        assertEquals(0, (first.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(spacing, (second.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(spacing, (third.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertEquals(View.VISIBLE, testee.visibility)
    }

    private fun footerOf(view: View): NativeInputFooter = object : NativeInputFooter {
        override val view: View = view
        override val state: Flow<NativeInputFooterState> = emptyFlow()
    }

    @Test
    fun whenNoRowsAreEmittedThenCardIsHidden() = runTest {
        testee.bind(this, flowOf(NativeInputFooterCoordinator.State()))
        shadowOf(testee).callOnAttachedToWindow()
        advanceUntilIdle()

        assertEquals(View.GONE, testee.visibility)
    }
}
