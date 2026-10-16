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

package com.duckduckgo.common.ui.view.listitem

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem.VerticalAlignment
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TwoLineListItemLeadingIconAlignmentTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_DuckDuckGo_Light)
    }

    @Test
    fun whenAlignmentIsDefaultThenLeadingIconIsCentredOnTheItem() {
        val item = layOutItem(alignment = null)

        assertEquals(item.container.height / 2f, item.icon.verticalCentre(), 1f)
    }

    @Test
    fun whenAlignmentIsCenterThenLeadingIconIsCentredOnTheItem() {
        val item = layOutItem(VerticalAlignment.Center)

        assertEquals(item.container.height / 2f, item.icon.verticalCentre(), 1f)
    }

    @Test
    fun whenAlignmentIsPrimaryTextThenLeadingIconIsCentredOnThePrimaryText() {
        val item = layOutItem(VerticalAlignment.PrimaryText)

        assertEquals(item.primaryText.verticalCentre(), item.icon.verticalCentre(), 1f)
        assertTrue(
            "icon should move off the item centre, but both are at ${item.icon.verticalCentre()}",
            abs(item.icon.verticalCentre() - item.container.height / 2f) > 1f,
        )
    }

    private fun layOutItem(alignment: VerticalAlignment?): LaidOutItem {
        val item = TwoLineListItem(context).apply {
            setLeadingIconResource(R.drawable.ic_menu_vertical_24)
            setPrimaryText("A primary text long enough to wrap onto a second line in a narrow list item")
            setPrimaryTextTruncation(false)
            setSecondaryText("Secondary text")
            alignment?.let { setLeadingIconVerticalAlignment(it) }
            measure(
                MeasureSpec.makeMeasureSpec(720, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        return LaidOutItem(
            container = item.findViewById(R.id.item_container),
            icon = item.findViewById(R.id.leadingIconBackground),
            primaryText = item.findViewById(R.id.primaryText),
        )
    }

    private fun View.verticalCentre(): Float = (top + bottom) / 2f

    private class LaidOutItem(
        val container: View,
        val icon: View,
        val primaryText: View,
    )
}
