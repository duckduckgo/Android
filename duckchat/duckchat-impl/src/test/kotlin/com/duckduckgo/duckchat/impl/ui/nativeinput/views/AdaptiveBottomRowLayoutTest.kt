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
    ): Row {
        val layout = AdaptiveBottomRowLayout(context)
        val start = LinearLayout(context)
        val end = LinearLayout(context)
        val tools = FakeControl(context, toolsWidths)
        val model = FakeControl(context, modelWidths)
        start.addView(FakeControl(context, intArrayOf(startWidth)))
        start.addView(tools)
        end.addView(model)
        end.addView(FakeControl(context, intArrayOf(endFixedWidth)))
        layout.addView(start, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        layout.addView(end, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return Row(layout, model, tools)
    }

    private class FakeControl(
        context: Context,
        private val widthsPerLevel: IntArray,
    ) : View(context), CompactableControl {
        var level = 0
            private set

        override fun setCompactLevel(level: Int) {
            this.level = level
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(widthsPerLevel[minOf(level, widthsPerLevel.lastIndex)], 10)
        }
    }
}
