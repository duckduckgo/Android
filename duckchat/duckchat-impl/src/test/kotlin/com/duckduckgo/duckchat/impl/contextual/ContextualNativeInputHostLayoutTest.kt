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
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterDockLayout
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterView
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun whenContextualNativeLayoutInflatesThenCardAndFooterStructureIsPreserved() {
        assertInputLayout(
            layoutId = R.layout.fragment_contextual_duck_ai_native,
            cardId = R.id.contextualNativeInputCard,
            footerId = R.id.contextualNativeInputFooter,
        )
    }

    @Test
    fun whenContextualNativeLayoutInflatesThenLegacyComposerRemainsAStackedSibling() {
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_contextual_duck_ai_native, null)

        val inputContainer = root.findViewById<FrameLayout>(R.id.contextualInputContainer)
        val nativeCard = root.findViewById<MaterialCardView>(R.id.contextualNativeInputCard)
        val nativeFooter = root.findViewById<NativeInputFooterView>(R.id.contextualNativeInputFooter)
        val nativeDock = nativeCard.parent as LinearLayout
        val legacyComposer = root.findViewById<View>(R.id.contextualModeNativeContent)

        assertTrue(nativeDock is NativeInputFooterDockLayout)
        assertSame(nativeDock, nativeFooter.parent)
        assertSame(inputContainer, nativeDock.parent)
        assertSame(inputContainer, legacyComposer.parent)
    }

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
        val cardMargins = card.layoutParams as ViewGroup.MarginLayoutParams
        val footerMargins = footer.layoutParams as ViewGroup.MarginLayoutParams

        assertEquals(1, root.descendants().count { it is NativeInputFooterView })
        assertFalse(footer.isDescendantOf(card))
        assertSame(card.parent, footer.parent)
        assertTrue(card.parent is NativeInputFooterDockLayout)
        assertEquals(1, card.childCount)
        assertTrue(card.getChildAt(0) is NativeInputModeWidget)
        assertEquals(cardMargins.bottomMargin, footerMargins.bottomMargin)
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
