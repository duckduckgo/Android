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

package com.duckduckgo.nextsteps.impl.ui

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.nextsteps.impl.R
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCardBinding
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListItemBinding
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListView
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListViewModel.ViewState
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class NextStepsCardRebrandTest {
    @Test
    fun phoneCardKeepsLegacyAppearanceUntilEnabled() = checkStackedCard()

    @Test
    @Config(qualifiers = "sw600dp")
    fun tabletCardKeepsLegacyAppearanceUntilEnabled() = checkStackedCard()

    private fun checkStackedCard() {
        listOf(CommonR.style.Theme_DuckDuckGo_Light, CommonR.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            listOf(false, true).forEach { enabled ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
                if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_RmfCards, true)
                val binding = ViewNextStepsCardBinding.inflate(LayoutInflater.from(context))
                val originalColor = binding.root.cardBackgroundColor.defaultColor
                val originalPadding = binding.root.getChildAt(0).paddingLeft
                val originalCloseSize = binding.closeButton.layoutParams.width
                val originalButtonColor = binding.action.backgroundTintList!!.defaultColor
                val originalBodyColor = binding.itemDescription.currentTextColor
                val originalTitleColor = binding.itemTitle.currentTextColor
                binding.applyRmfStyle()
                assertEquals(context.getColorFromAttr(CommonR.attr.daxColorSecondaryText), binding.itemDescription.currentTextColor)
                assertEquals(context.getColorFromAttr(CommonR.attr.daxColorPrimaryText), binding.itemTitle.currentTextColor)
                if (!enabled) {
                    assertEquals(originalBodyColor, binding.itemDescription.currentTextColor)
                    assertEquals(originalTitleColor, binding.itemTitle.currentTextColor)
                }
                binding.action.text = "Set DuckDuckGo as your default browser"
                measure(binding.root)
                if (enabled) {
                    checkCard(binding.root, binding.closeButton)
                    val buttonContext = ContextThemeWrapper(context, CommonR.style.ThemeOverlay_Rebrand)
                    val expected = DaxButtonPrimary(buttonContext, null)
                    assertEquals(expected.backgroundTintList!!.defaultColor, binding.action.backgroundTintList!!.defaultColor)
                    assertEquals(expected.textColors.defaultColor, binding.action.textColors.defaultColor)
                    val content = binding.action.parent as ViewGroup
                    assertEquals(content.width - content.paddingLeft - content.paddingRight, binding.action.width)
                    for (line in 0 until binding.action.lineCount) assertEquals(0, binding.action.layout.getEllipsisCount(line))
                } else {
                    assertEquals(originalColor, binding.root.cardBackgroundColor.defaultColor)
                    assertEquals(originalPadding, binding.root.getChildAt(0).paddingLeft)
                    assertEquals(originalCloseSize, binding.closeButton.layoutParams.width)
                    assertEquals(originalButtonColor, binding.action.backgroundTintList!!.defaultColor)
                }
            }
        }
    }

    @Test
    fun checklistUsesTheSameGuardedContainerAndCloseButton() {
        listOf(false, true).forEach { enabled ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)
            if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_RmfCards, true)
            val view = NextStepsCheckListView(context)
            val card = view.findViewById<MaterialCardView>(R.id.checkListCard)
            val close = view.findViewById<ImageView>(R.id.closeButton)
            measure(view)
            if (enabled) {
                checkCard(card, close)
            } else {
                assertEquals(context.getColorFromAttr(CommonR.attr.daxColorWindow), card.cardBackgroundColor.defaultColor)
                assertEquals(context.resources.getDimensionPixelSize(R.dimen.nextStepsCheckListItemIconSize), close.width)
            }
        }
    }

    @Test
    fun populatedChecklistUsesOneInsetOnPhone() = checkPopulatedChecklist()

    @Test
    @Config(qualifiers = "sw600dp")
    fun populatedChecklistUsesOneInsetOnTablet() = checkPopulatedChecklist()

    private fun checkPopulatedChecklist() {
        listOf(false, true).forEach { enabled ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)
            if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_RmfCards, true)
            val view = NextStepsCheckListView(context)
            val item = CardItem.ListItem(
                id = "browser",
                type = CardItemType.ONE_ACTION_ITEM,
                titleText = "Default browser",
                descriptionText = "",
                placeholder = Content.Placeholder.ANNOUNCE,
                primaryAction = null,
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
                imageUrl = "android.resource://${context.packageName}/${CommonR.drawable.announcement_96}",
            )
            val items = listOf(ViewState.ListItem(item, false), ViewState.ListItem(item.copy(id = "sync", titleText = "Sync"), true))
            val bind = NextStepsCheckListView::class.java.getDeclaredMethod("bindItems", List::class.java).apply { isAccessible = true }
            bind.invoke(view, items)
            val card = view.findViewById<MaterialCardView>(R.id.checkListCard)
            val container = view.findViewById<ViewGroup>(R.id.itemsContainer)
            val firstRow = container.getChildAt(0)
            repeat(2) {
                if (it == 1) bind.invoke(view, items.map { row -> row.copy(completed = !row.completed) })
                measure(view, if (context.resources.configuration.smallestScreenWidthDp >= 600) 700 else 320)
                assertSame(firstRow, container.getChildAt(0))
                val density = context.resources.displayMetrics.density
                val inset = ((if (enabled) 24 else 16) * density).toInt()
                fun bounds(child: View): Rect = Rect(0, 0, child.width, child.height).also { rect ->
                    card.offsetDescendantRectToMyCoords(child, rect)
                }
                assertEquals(inset, bounds(view.findViewById(R.id.sectionTitle)).left)
                repeat(container.childCount) { index ->
                    val row = ViewNextStepsCheckListItemBinding.bind(container.getChildAt(index))
                    assertEquals(inset, bounds(row.itemImage).left)
                    assertEquals((24 * density).toInt(), row.itemImage.width)
                    assertEquals((12 * density).toInt(), row.itemRow.paddingTop)
                    assertEquals((12 * density).toInt(), row.itemRow.paddingBottom)
                    assertEquals((10 * density).toInt(), bounds(row.itemTitle).left - bounds(row.itemImage).right)
                    if (index > 0) {
                        val divider = bounds(row.divider)
                        assertEquals(View.VISIBLE, row.divider.visibility)
                        assertEquals(inset, divider.left)
                        assertEquals(inset, card.width - divider.right)
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun tabletImageCentersOnWholeTextBlockOnlyWhenEnabled() {
        listOf(false, true).forEach { enabled ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)
            if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_RmfCards, true)
            val binding = ViewNextStepsCardBinding.inflate(LayoutInflater.from(context))
            binding.applyRmfStyle()
            binding.sectionTitle.text = "Next steps"
            binding.action.text = "Continue"
            listOf(
                "Short",
                "Longer translated text that wraps over several lines to exercise alignment against the whole adjacent content block",
            ).forEach { text ->
                binding.itemTitle.text = text
                binding.itemDescription.text = text
                measure(binding.root, 700)
                if (enabled) {
                    assertEquals(
                        (binding.sectionTitle.top + binding.itemDescription.bottom) / 2f,
                        (binding.itemImage.top + binding.itemImage.bottom) / 2f,
                        1f,
                    )
                } else {
                    assertEquals(binding.sectionTitle.bottom, binding.itemImage.top)
                }
                assertTrue(binding.itemImage.height > 0)
            }
        }
    }

    private fun checkCard(card: MaterialCardView, close: ImageView) {
        val content = card.getChildAt(0) as ViewGroup
        val density = card.resources.displayMetrics.density
        assertEquals(card.context.getColorFromAttr(CommonR.attr.daxColorSurfaceSecondary), card.cardBackgroundColor.defaultColor)
        assertEquals((24 * density).toInt(), content.paddingLeft)
        assertEquals((36 * density).toInt(), close.width)
        assertEquals(16 * density, close.top.toFloat(), 1f)
        assertEquals(16 * density, (content.width - close.right).toFloat(), 1f)
    }

    private fun measure(view: View, widthDp: Int = 320) {
        val width = (widthDp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, width, view.measuredHeight)
    }
}
