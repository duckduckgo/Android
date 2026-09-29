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
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeInputFooterDockLayoutTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )

    @Test
    fun whenInputAndFooterOverlapThenFooterDrawsBeforeInput() {
        assertEquals(1, footerFirstChildDrawingOrder(childCount = 2, drawingPosition = 0))
        assertEquals(0, footerFirstChildDrawingOrder(childCount = 2, drawingPosition = 1))
    }

    @Test
    fun whenCardHasCompatShadowPaddingThenFooterAlignsWithVisibleCardSurface() {
        val hierarchy = hierarchy(useCompatPadding = true)

        hierarchy.dock.measureAndLayout()

        assertTrue(hierarchy.card.paddingLeft > 0)
        assertFooterHugsCard(hierarchy)
    }

    @Test
    fun whenVisibleSiblingSitsBesideCardThenFooterAlignsWithCardNotRow() {
        val hierarchy = hierarchy(useCompatPadding = false, siblingVisibility = View.VISIBLE)

        hierarchy.dock.measureAndLayout()

        assertTrue(hierarchy.card.left > hierarchy.sibling.width)
        assertFooterHugsCard(hierarchy)
    }

    @Test
    fun whenSiblingVisibilityChangesThenFooterRealigns() {
        val hierarchy = hierarchy(useCompatPadding = false, siblingVisibility = View.GONE)
        hierarchy.dock.measureAndLayout()
        assertFooterHugsCard(hierarchy)

        hierarchy.sibling.visibility = View.VISIBLE
        hierarchy.dock.measureAndLayout()

        assertFooterHugsCard(hierarchy)
    }

    @Test
    fun whenLaidOutThenFooterCardMatchesInputCardElevation() {
        val hierarchy = hierarchy(useCompatPadding = true)

        hierarchy.dock.measureAndLayout()

        assertEquals(ELEVATION_PX, hierarchy.card.cardElevation, 0.1f)
        assertEquals(ELEVATION_PX, hierarchy.footerCard.cardElevation, 0.1f)
    }

    private fun assertFooterHugsCard(hierarchy: Hierarchy) {
        val card = hierarchy.card
        val footer = hierarchy.footer
        val cardVisibleLeft = hierarchy.row.left + card.left + card.paddingLeft
        val cardVisibleRight = hierarchy.row.left + card.right - card.paddingRight
        val cardVisibleBottom = hierarchy.row.top + card.bottom - card.paddingBottom
        val overlap = context.resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap)

        assertEquals(cardVisibleLeft, footer.left)
        assertEquals(cardVisibleRight, footer.right)
        assertEquals(cardVisibleBottom - overlap, footer.top)
    }

    private class TestDockLayout(context: Context) : NativeInputFooterDockLayout(context) {
        fun finishInflate() = onFinishInflate()
    }

    private class TestFooterView(context: Context) : NativeInputFooterView(context) {
        fun attach() = onAttachedToWindow()
    }

    private class Hierarchy(
        val dock: NativeInputFooterDockLayout,
        val row: LinearLayout,
        val sibling: View,
        val card: MaterialCardView,
        val footer: NativeInputFooterView,
        val footerCard: MaterialCardView,
    )

    private fun hierarchy(
        useCompatPadding: Boolean,
        siblingVisibility: Int = View.GONE,
    ): Hierarchy {
        val dock = TestDockLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(PADDING_PX, 0, PADDING_PX, 0)
        }
        val sibling = View(context).apply { visibility = siblingVisibility }
        row.addView(sibling, LinearLayout.LayoutParams(SIBLING_WIDTH_PX, CARD_HEIGHT_PX).apply { marginEnd = PADDING_PX })
        val card = MaterialCardView(context).apply {
            this.useCompatPadding = useCompatPadding
            cardElevation = ELEVATION_PX
            addView(NativeInputModeWidget(context), MATCH_PARENT, CARD_HEIGHT_PX)
        }
        row.addView(card, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { bottomMargin = PADDING_PX })
        dock.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val footer = TestFooterView(context)
        dock.addView(footer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val footerCard = MaterialCardView(context).apply { minimumHeight = FOOTER_HEIGHT_PX }
        footer.bind(
            coroutineRule.testScope,
            flowOf(NativeInputFooterCoordinator.State(view = footerCard)),
        )
        dock.finishInflate()
        footer.attach()
        return Hierarchy(dock, row, sibling, card, footer, footerCard)
    }

    private fun View.measureAndLayout() {
        measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private companion object {
        const val WIDTH_PX = 1080
        const val PADDING_PX = 20
        const val SIBLING_WIDTH_PX = 120
        const val CARD_HEIGHT_PX = 200
        const val FOOTER_HEIGHT_PX = 80
        const val ELEVATION_PX = 12f
    }
}
