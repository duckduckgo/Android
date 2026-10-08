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
package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveBottomRowLayoutTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )

    @Test
    fun whenEverythingFitsThenControlsStayAtFullSize() {
        val row = row(startWidth = 100, modelWidths = intArrayOf(200, 40, 40), toolsWidths = intArrayOf(80, 80, 40), endFixedWidth = 100)

        row.measureWithWidth(1000)

        assertEquals(0, row.model.level)
        assertEquals(0, row.tools.level)
    }

    @Test
    fun whenRowOverflowsThenModelCollapsesFirst() {
        val row = row(startWidth = 100, modelWidths = intArrayOf(300, 40, 40), toolsWidths = intArrayOf(80, 80, 40), endFixedWidth = 100)

        row.measureWithWidth(440)

        assertEquals(1, row.model.level)
        assertEquals(1, row.tools.level)
    }

    @Test
    fun whenCollapsingTheModelIsNotEnoughThenToolsMergeToo() {
        val row = row(startWidth = 100, modelWidths = intArrayOf(300, 40, 40), toolsWidths = intArrayOf(80, 80, 40), endFixedWidth = 100)

        row.measureWithWidth(300)

        assertEquals(2, row.model.level)
        assertEquals(2, row.tools.level)
    }

    @Test
    fun whenNothingFitsThenTheMostCompactLevelIsKept() {
        val row = row(startWidth = 100, modelWidths = intArrayOf(300, 40, 40), toolsWidths = intArrayOf(80, 80, 40), endFixedWidth = 100)

        row.measureWithWidth(50)

        assertEquals(AdaptiveBottomRowLayout.LEVEL_MAX, row.model.level)
    }

    @Test
    fun whenRowGrowsAgainThenControlsReturnToFullSize() {
        val row = row(startWidth = 100, modelWidths = intArrayOf(300, 40, 40), toolsWidths = intArrayOf(80, 80, 40), endFixedWidth = 100)
        row.measureWithWidth(300)
        assertEquals(2, row.model.level)

        row.measureWithWidth(1000)

        assertEquals(0, row.model.level)
    }

    @Test
    fun whenRealContentIsShorterThanWorstCaseThenLevelFollowsTheWorstCase() {
        val row = row(
            startWidth = 100,
            modelWidths = intArrayOf(50, 40, 40),
            toolsWidths = intArrayOf(80, 80, 40),
            endFixedWidth = 100,
            modelWorstCase = intArrayOf(300, 40, 40),
        )

        row.measureWithWidth(440)

        assertEquals(1, row.model.level)
    }

    @Test
    fun whenSelectedContentDiffersThenTheSameLevelIsChosen() {
        val shortName = row(100, intArrayOf(60, 40, 40), intArrayOf(80, 80, 40), 100, modelWorstCase = intArrayOf(300, 40, 40))
        val longName = row(100, intArrayOf(300, 40, 40), intArrayOf(80, 80, 40), 100, modelWorstCase = intArrayOf(300, 40, 40))

        shortName.measureWithWidth(440)
        longName.measureWithWidth(440)

        assertEquals(longName.model.level, shortName.model.level)
    }

    @Test
    fun whenAControlIsHiddenThenItsWorstCaseIsStillReserved() {
        val shown = row(100, intArrayOf(300, 40, 40), intArrayOf(80, 80, 40), 100)
        val hidden = row(100, intArrayOf(300, 40, 40), intArrayOf(80, 80, 40), 100, modelVisible = false)

        shown.measureWithWidth(440)
        hidden.measureWithWidth(440)

        assertEquals(shown.model.level, hidden.model.level)
    }

    @Test
    fun whenAWorstCaseShrinksThenTheNextMeasureRelaxesTheLevel() {
        val row = row(100, intArrayOf(60, 40, 40), intArrayOf(80, 80, 40), 100, modelWorstCase = intArrayOf(300, 40, 40))
        row.measureWithWidth(440)
        assertEquals(1, row.model.level)

        row.model.worstCasePerLevel = intArrayOf(60, 40, 40)
        row.measureWithWidth(440)

        assertEquals(0, row.model.level)
    }

    @Test
    fun whenAContainerHoldsNoCompactableControlThenItsWidthStillCounts() {
        val without = row(100, intArrayOf(300, 40, 40), intArrayOf(80, 80, 40), 100)
        val with = row(100, intArrayOf(300, 40, 40), intArrayOf(80, 80, 40), 100, extraFixedWidth = 200)

        without.measureWithWidth(700)
        with.measureWithWidth(700)

        assertEquals(0, without.model.level)
        assertEquals(1, with.model.level)
    }

    private class Row(
        val layout: AdaptiveBottomRowLayout,
        val model: FakeControl,
        val tools: FakeControl,
    ) {
        fun measureWithWidth(width: Int) {
            layout.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
        }
    }

    // Start group: a fixed button plus the tools control. End group: the model control plus a fixed submit.
    private fun row(
        startWidth: Int,
        modelWidths: IntArray,
        toolsWidths: IntArray,
        endFixedWidth: Int,
        modelWorstCase: IntArray = modelWidths,
        modelVisible: Boolean = true,
        extraFixedWidth: Int = 0,
    ): Row {
        val layout = AdaptiveBottomRowLayout(context)
        val start = LinearLayout(context)
        val end = LinearLayout(context)
        val tools = FakeControl(context, toolsWidths)
        val model = FakeControl(context, modelWidths, modelWorstCase).apply { if (!modelVisible) visibility = View.GONE }
        start.addView(FakeControl(context, intArrayOf(startWidth)))
        if (extraFixedWidth > 0) start.addView(FixedView(context, extraFixedWidth))
        start.addView(tools)
        end.addView(model)
        end.addView(FakeControl(context, intArrayOf(endFixedWidth)))
        layout.addView(start, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        layout.addView(end, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return Row(layout, model, tools)
    }

    // A control that is not compactable, like the attach button: it takes its width at every level.
    private class FixedView(
        context: Context,
        private val widthPx: Int,
    ) : View(context) {
        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(widthPx, 10)
        }
    }

    private class FakeControl(
        context: Context,
        private val widthsPerLevel: IntArray,
        var worstCasePerLevel: IntArray = widthsPerLevel,
    ) : View(context), CompactableControl {
        var level = 0
            private set

        override fun setCompactLevel(level: Int) {
            this.level = level
        }

        override fun worstCaseWidth(level: Int): Int = worstCasePerLevel[minOf(level, worstCasePerLevel.lastIndex)]

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(widthsPerLevel[minOf(level, widthsPerLevel.lastIndex)], 10)
        }
    }
}
