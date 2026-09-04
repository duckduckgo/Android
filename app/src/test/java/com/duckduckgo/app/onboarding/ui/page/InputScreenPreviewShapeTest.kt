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

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.RectF
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.applyInputScreenPreviewInsets
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.applyInputScreenPreviewShape
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.applyInputTextMode
import com.duckduckgo.app.onboarding.ui.page.configdriven.binders.updateInputModePreservingSelection
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class InputScreenPreviewShapeTest {

    private val baseContext = ApplicationProvider.getApplicationContext<Context>()
    private val baseShape = ShapeAppearanceModel.builder().build()

    @Test
    fun whenAddressBarRebrandEnabledThenUsesHalfTheRebrandInputRadius() {
        val suppliedRebrandInputRadius = 10f
        val card = card(
            shapeResources(
                rebrandInputRadius = suppliedRebrandInputRadius,
                legacyRadius = 3f,
            ),
        )

        card.applyInputScreenPreviewShape(isAddressBarRebrandEnabled = true)

        val shape = argumentCaptor<ShapeAppearanceModel>()
        verify(card).shapeAppearanceModel = shape.capture()
        assertFixedCorners(shape.firstValue, suppliedRebrandInputRadius / 2f)
        verify(card.resources).getDimension(CommonR.dimen.rebrandInputRadius)
        verify(card, never()).radius = any()
        verify(card).clipToOutline = false
        verify(card).invalidateOutline()
    }

    @Test
    fun whenAddressBarRebrandDisabledThenUsesLegacyRadius() {
        val suppliedLegacyRadius = 3f
        val card = card(
            shapeResources(
                rebrandInputRadius = 10f,
                legacyRadius = suppliedLegacyRadius,
            ),
        )

        card.applyInputScreenPreviewShape(isAddressBarRebrandEnabled = false)

        val shape = argumentCaptor<ShapeAppearanceModel>()
        verify(card).shapeAppearanceModel = shape.capture()
        assertFixedCorners(shape.firstValue, suppliedLegacyRadius)
        verify(card.resources).getDimension(CommonR.dimen.largeShapeCornerRadius)
    }

    @Test
    fun whenAddressBarRebrandEnabledThenUsesConfiguredHorizontalAndVerticalInsets() {
        val horizontalInset = 31
        val verticalInset = 17
        val context = contextWithInsets(
            horizontalInset = horizontalInset,
            verticalInset = verticalInset,
        )
        val (container, input) = inputFixture(context)
        val actionIcon = container.getChildAt(1)

        input.applyInputScreenPreviewInsets(isAddressBarRebrandEnabled = true, actionIcon = actionIcon)

        val inputMargins = input.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(horizontalInset, container.paddingStart + inputMargins.marginStart + input.paddingStart)
        val actionIconMargin = (actionIcon.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        val actionIconContentInset = (
            actionIcon.layoutParams.width -
                context.resources.getDimensionPixelSize(CommonR.dimen.toolbarIconSize)
            ) / 2
        assertEquals(horizontalInset, container.paddingEnd + actionIconMargin + actionIconContentInset)
        assertEquals(verticalInset, container.paddingTop + input.paddingTop)
        assertEquals(verticalInset, container.paddingBottom + input.paddingBottom)
        verify(context.resources).getDimensionPixelSize(CommonR.dimen.keyline_4)
        verify(context.resources).getDimensionPixelSize(CommonR.dimen.keyline_3)
    }

    @Test
    fun whenAddressBarRebrandEnabledAndInputAlreadyHasStartPaddingThenUsesConfiguredInsetOnce() {
        val horizontalInset = 31
        val context = contextWithInsets(horizontalInset = horizontalInset)
        val (container, input) = inputFixture(context, inputStartPadding = horizontalInset)

        input.applyInputScreenPreviewInsets(isAddressBarRebrandEnabled = true, actionIcon = container.getChildAt(1))

        val margins = input.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(horizontalInset, container.paddingStart + margins.marginStart + input.paddingStart)
    }

    @Test
    fun whenAddressBarRebrandDisabledThenKeepsExistingInsets() {
        val context = contextWithInsets()
        val (container, input) = inputFixture(context)
        val actionIcon = container.getChildAt(1)
        val initialInputMarginStart = (input.layoutParams as ViewGroup.MarginLayoutParams).marginStart
        val initialActionIconMarginEnd = (actionIcon.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        val initialInputPadding = listOf(input.paddingStart, input.paddingTop, input.paddingEnd, input.paddingBottom)
        val initialContainerPadding = listOf(container.paddingStart, container.paddingTop, container.paddingEnd, container.paddingBottom)

        input.applyInputScreenPreviewInsets(isAddressBarRebrandEnabled = false, actionIcon = actionIcon)

        assertEquals(initialInputMarginStart, (input.layoutParams as ViewGroup.MarginLayoutParams).marginStart)
        assertEquals(initialActionIconMarginEnd, (actionIcon.layoutParams as ViewGroup.MarginLayoutParams).marginEnd)
        assertEquals(initialInputPadding, listOf(input.paddingStart, input.paddingTop, input.paddingEnd, input.paddingBottom))
        assertEquals(initialContainerPadding, listOf(container.paddingStart, container.paddingTop, container.paddingEnd, container.paddingBottom))
        verify(context.resources, never()).getDimensionPixelSize(CommonR.dimen.keyline_4)
        verify(context.resources, never()).getDimensionPixelSize(CommonR.dimen.keyline_3)
    }

    @Test
    fun whenSwitchingFromSearchToDuckAiThenKeepsCursorAtEnd() {
        val input = EditText(baseContext).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText("typed query")
            setSelection(11)
        }

        input.updateInputModePreservingSelection {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        assertEquals(11, input.selectionStart)
        assertEquals(11, input.selectionEnd)
    }

    @Test
    fun whenSwitchingFromDuckAiToSearchThenKeepsCursorAtEnd() {
        val input = EditText(baseContext).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText("typed query")
            setSelection(11)
        }

        input.updateInputModePreservingSelection {
            inputType = InputType.TYPE_CLASS_TEXT
        }

        assertEquals(11, input.selectionStart)
        assertEquals(11, input.selectionEnd)
    }

    @Test
    fun whenDuckAiModeIsRestoredThenKeepsTheSameExpandableLineBounds() {
        val switchedInput = EditText(baseContext).apply {
            applyInputTextMode(isSearchSelected = true)
            applyInputTextMode(isSearchSelected = false)
        }
        val restoredInput = EditText(baseContext).apply {
            maxLines = 1
            applyInputTextMode(isSearchSelected = false)
        }

        assertEquals(3, restoredInput.minLines)
        assertEquals(Int.MAX_VALUE, restoredInput.maxLines)
        assertEquals(switchedInput.minLines, restoredInput.minLines)
        assertEquals(switchedInput.maxLines, restoredInput.maxLines)
    }

    private fun shapeResources(
        rebrandInputRadius: Float,
        legacyRadius: Float,
    ): Resources = mock {
        on { getDimension(CommonR.dimen.rebrandInputRadius) } doReturn rebrandInputRadius
        on { getDimension(CommonR.dimen.largeShapeCornerRadius) } doReturn legacyRadius
    }

    private fun card(resources: Resources): MaterialCardView = mock {
        on { shapeAppearanceModel } doReturn baseShape
        on { getResources() } doReturn resources
    }

    private fun contextWithInsets(
        horizontalInset: Int = 31,
        verticalInset: Int = 17,
        containerInset: Int = 5,
        actionIconContainerSize: Int = 29,
        actionIconSize: Int = 13,
    ): Context {
        val resources = spy(baseContext.resources)
        doReturn(horizontalInset).whenever(resources).getDimensionPixelSize(CommonR.dimen.keyline_4)
        doReturn(verticalInset).whenever(resources).getDimensionPixelSize(CommonR.dimen.keyline_3)
        doReturn(containerInset).whenever(resources).getDimensionPixelSize(CommonR.dimen.keyline_0)
        doReturn(actionIconContainerSize).whenever(resources).getDimensionPixelSize(CommonR.dimen.toolbarIcon)
        doReturn(actionIconSize).whenever(resources).getDimensionPixelSize(CommonR.dimen.toolbarIconSize)
        return object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = resources
        }
    }

    private fun inputFixture(
        context: Context,
        inputStartPadding: Int = 0,
    ): Pair<LinearLayout, EditText> {
        val containerInset = context.resources.getDimensionPixelSize(CommonR.dimen.keyline_0)
        val actionIconContainerSize = context.resources.getDimensionPixelSize(CommonR.dimen.toolbarIcon)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(containerInset, containerInset, containerInset, containerInset)
        }
        val input = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            background = null
            minHeight = actionIconContainerSize
            minLines = 1
            maxLines = 1
            setPaddingRelative(inputStartPadding, 0, 0, 0)
        }
        container.addView(input)
        container.addView(
            View(context),
            LinearLayout.LayoutParams(actionIconContainerSize, actionIconContainerSize),
        )
        return container to input
    }

    private fun assertFixedCorners(
        shape: ShapeAppearanceModel,
        expected: Float,
    ) {
        val corners = listOf(
            shape.topLeftCornerSize,
            shape.topRightCornerSize,
            shape.bottomLeftCornerSize,
            shape.bottomRightCornerSize,
        )
        assertFalse(corners.any { it is RelativeCornerSize })
        corners.forEach { assertEquals(expected, it.getCornerSize(RectF()), 0f) }
    }
}
