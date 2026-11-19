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

package com.duckduckgo.duckchat.impl.contextual

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterDockLayout
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterView
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputLayout
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextualNativeInputHostLayoutTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )

    @Test
    fun whenContextualWebViewLayoutInflatesThenCardAndFooterStructureIsPreserved() {
        assertInputLayout(
            layoutId = R.layout.fragment_contextual_duck_ai_webview,
            cardId = R.id.contextualNativeInputCard,
            footerId = R.id.contextualNativeInputFooter,
        )
    }

    @Test
    fun whenContextualEntryDialogInflatesThenCardAndFooterStructureIsPreserved() {
        assertInputLayout(
            layoutId = R.layout.dialog_contextual_duck_ai_entry,
            cardId = R.id.entryNativeInputCard,
            footerId = R.id.entryNativeInputFooter,
        )
    }

    private fun assertInputLayout(
        @LayoutRes layoutId: Int,
        @IdRes cardId: Int,
        @IdRes footerId: Int,
    ) {
        val root = LayoutInflater.from(context).inflate(layoutId, null)

        root.measureAndLayout()

        val card = root.findViewById<MaterialCardView>(cardId)
        val footer = root.findViewById<NativeInputFooterView>(footerId)
        // The card can sit in a row with other controls; the dock-level spacing lives on that row.
        val cardRow = card.ancestorsAndSelf().first { it.parent == footer.parent }
        val cardRowMargins = cardRow.layoutParams as ViewGroup.MarginLayoutParams
        val footerMargins = footer.layoutParams as ViewGroup.MarginLayoutParams

        assertEquals(1, root.descendants().count { it is NativeInputFooterView })
        assertFalse(footer.isDescendantOf(card))
        assertTrue(footer.parent is NativeInputFooterDockLayout)
        assertTrue(card.isDescendantOf(footer.parent as ViewGroup))
        assertTrue(card is NativeInputLayout)
        assertEquals(NativeInputLayout.Surface.CONTEXTUAL, (card as NativeInputLayout).surface)
        assertEquals(1, card.childCount)
        assertTrue(card.getChildAt(0) is NativeInputModeWidget)
        assertEquals(cardRowMargins.bottomMargin, footerMargins.bottomMargin)
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

    private fun View.ancestorsAndSelf(): Sequence<View> = generateSequence(this) { it.parent as? View }

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
