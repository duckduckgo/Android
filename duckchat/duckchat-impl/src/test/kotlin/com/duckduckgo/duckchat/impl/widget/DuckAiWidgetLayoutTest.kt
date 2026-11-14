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
package com.duckduckgo.duckchat.impl.widget

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiWidgetLayoutTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val density = activity.resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).toInt()

    private fun inflateAt320dp(): View {
        val root = LayoutInflater.from(activity).inflate(R.layout.duck_ai_widget_daynight, FrameLayout(activity), false)
        root.measure(MeasureSpec.makeMeasureSpec(dp(320), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(400), MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    @Test
    fun whenInflatedThenPromptBarIsInsetLikeTheActionsRowContent() {
        val root = inflateAt320dp()
        val pill = root.findViewById<View>(R.id.promptBarContainer)
        val actionsRow = root.findViewById<View>(R.id.actionsRow)

        val contentStart = root.paddingLeft + actionsRow.left + actionsRow.paddingLeft
        val contentEnd = root.width - root.paddingRight - (root.width - actionsRow.right) - actionsRow.paddingRight

        assertEquals(dp(16), contentStart)
        assertEquals(contentStart, pill.left)
        assertEquals(contentEnd, pill.right)
    }

    @Test
    fun whenInflatedThenGapBetweenPromptBarAndActionsRowIs14dp() {
        val root = inflateAt320dp()
        val pill = root.findViewById<View>(R.id.promptBarContainer)
        val actionsRow = root.findViewById<View>(R.id.actionsRow)

        assertEquals(dp(14), actionsRow.top - pill.bottom)
    }

    @Test
    fun whenInflatedThenThreeEqualWidthPillsSpanTheRowContentWithGaps() {
        val root = inflateAt320dp()
        val row = root.findViewById<View>(R.id.actionsRow)
        val pills = listOf(R.id.actionVoice, R.id.actionCreateImage, R.id.actionTakePhoto).map { root.findViewById<View>(it) }

        val contentWidth = row.width - row.paddingLeft - row.paddingRight
        val gaps = pills.zipWithNext { a, b -> b.left - a.right }

        assertTrue(pills.maxOf { it.width } - pills.minOf { it.width } <= 1)
        assertEquals(listOf(dp(8), dp(8)), gaps)
        assertEquals(contentWidth, pills.sumOf { it.width } + gaps.sum())
        assertEquals(row.paddingLeft, pills.first().left)
        assertEquals(row.width - row.paddingRight, pills.last().right)
    }

    @Test
    fun whenInflatedThenNoQuickActionsLabelOrAttachViewExists() {
        val root = inflateAt320dp()

        assertNull(root.findViewById<View>(root.resources.getIdentifier("actionsLabel", "id", activity.packageName)))
        assertNull(root.findViewById<View>(root.resources.getIdentifier("actionAttachFile", "id", activity.packageName)))
    }

    private fun widgetColor(name: String) = activity.getColor(
        activity.resources.getIdentifier("duckAiWidget${name}Color", "color", activity.packageName),
    )

    private fun current(name: String) = activity.getColor(activity.resources.getIdentifier(name, "color", activity.packageName))

    private fun assertFallbackColors(
        container: String,
        divider: String,
    ) {
        assertEquals(current("searchWidgetBackgroundColor"), widgetColor("Background"))
        assertEquals(current("searchWidgetSearchBarBackgroundColor"), widgetColor("SearchBar"))
        assertEquals(current(container), widgetColor("Container"))
        assertEquals(current(divider), widgetColor("Divider"))
        assertEquals(current("searchWidgetSearchBarHintColor"), widgetColor("Hint"))
        assertEquals(current("searchWidgetSearchBarTrailingIconColor"), widgetColor("Text"))
        assertEquals(current("searchWidgetSearchBarTrailingIconColor"), widgetColor("OnContainer"))
    }

    @Test
    @Config(sdk = [30])
    fun whenApi30ThenColorsMatchCurrentLightColors() = assertFallbackColors(container = "black9", divider = "black12")

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun whenApi30NightThenColorsMatchCurrentDarkColors() = assertFallbackColors(container = "white9", divider = "white12")
}
