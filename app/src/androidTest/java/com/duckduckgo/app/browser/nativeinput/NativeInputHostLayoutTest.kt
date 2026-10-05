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

package com.duckduckgo.app.browser.nativeinput

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeInputHostLayoutTest {

    private val context: Context by lazy {
        ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
        )
    }

    @Test
    fun whenTopInputLayoutInflatesThenCardAndFooterStructureIsPreservedWithoutClipping() {
        assertInputLayout(R.layout.input_mode_widget_card_view)
    }

    @Test
    fun whenBottomInputLayoutInflatesThenCardAndFooterStructureIsPreservedWithoutClipping() {
        assertInputLayout(R.layout.input_mode_widget_card_view_bottom)
    }

    private fun assertInputLayout(layoutId: Int) {
        val root = LayoutInflater.from(context).inflate(layoutId, null)

        root.measureAndLayout()

        val card = root.findViewById<ViewGroup>(R.id.inputModeWidgetCard)
        val footer = root.findViewById<View>(R.id.nativeInputFooter)
        val stack = footer.parent as LinearLayout

        assertEquals(1, root.descendants().count { it.id == R.id.nativeInputFooter })
        assertFalse(footer.isDescendantOf(card))
        assertSame(stack, (card.parent as View).parent)
        assertEquals(LinearLayout.VERTICAL, stack.orientation)
        assertEquals(1, card.childCount)
        assertEquals(R.id.inputModeWidget, card.getChildAt(0).id)
        assertFalse(stack.clipChildren)
        assertFalse(stack.clipToPadding)
    }

    private fun View.measureAndLayout() {
        measure(
            View.MeasureSpec.makeMeasureSpec(TEST_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(TEST_HEIGHT_PX, View.MeasureSpec.AT_MOST),
        )
        layout(0, 0, measuredWidth, measuredHeight)

        assertEquals(TEST_WIDTH_PX, measuredWidth)
        assertTrue(measuredHeight > 0)
    }

    private fun View.isDescendantOf(ancestor: ViewGroup): Boolean {
        var currentParent = parent
        while (true) {
            val parentView = currentParent as? View ?: return false
            if (parentView === ancestor) return true
            currentParent = parentView.parent
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) {
                yieldAll(getChildAt(index).descendants())
            }
        }
    }

    private companion object {
        const val TEST_WIDTH_PX = 1080
        const val TEST_HEIGHT_PX = 1920
    }
}
