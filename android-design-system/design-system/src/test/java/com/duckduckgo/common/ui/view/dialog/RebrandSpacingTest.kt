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

package com.duckduckgo.common.ui.view.dialog

import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.databinding.BottomSheetPromoBinding
import com.duckduckgo.mobile.android.databinding.DialogCustomAlertBinding
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class RebrandSpacingTest {
    // Insets are 4dp with the rebrand button overlay and Material's 6dp without it, so both are covered.
    private fun context(enabled: Boolean, rebrandButtons: Boolean = false): ContextThemeWrapper =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light).apply {
            if (rebrandButtons) theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand, true)
            if (enabled) theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_DialogSpacing, true)
        }

    private fun View.dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    private fun assertContainer(view: View, enabled: Boolean) {
        assertEquals(view.dp(24), view.paddingTop)
        assertEquals(view.dp(if (enabled) 0 else 24), view.paddingStart)
        assertEquals(view.dp(if (enabled) 0 else 24), view.paddingEnd)
        assertEquals(view.dp(if (enabled) 0 else 18), view.paddingBottom)
    }

    private fun ViewGroup.addButtons(context: ContextThemeWrapper): MaterialButton {
        addView(DaxButtonGhost(context, null))
        return DaxButtonPrimary(context, null).also { addView(it) }
    }

    // Padding and margins are view edges; Figma measures to the visible button edge, so the button's insets are added back.
    private fun assertButtons(view: View, button: MaterialButton, enabled: Boolean) {
        assertEquals(view.dp(if (enabled) 24 else 0), view.paddingStart)
        assertEquals(view.dp(if (enabled) 24 else 0), view.paddingEnd)
        if (enabled) {
            assertEquals(view.dp(8), view.paddingTop + button.insetTop)
            assertEquals(view.dp(24), view.paddingBottom + button.insetBottom)
        } else {
            assertEquals(0, view.paddingTop)
            assertEquals(0, view.paddingBottom)
        }
    }

    @Test
    fun buttonInsetsDifferWithAndWithoutRebrandOverlay() {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals((density * 4).toInt(), DaxButtonGhost(context(true, true), null).insetTop)
        assertEquals((density * 6).toInt(), DaxButtonGhost(context(true, false), null).insetTop)
    }

    @Test
    fun textDialogSpacingOnAndOff() {
        listOf(false, true).forEach { enabled ->
            listOf(false, true).forEach { rebrandButtons ->
                listOf(false, true).forEach { checkbox ->
                    val context = context(enabled, rebrandButtons)
                    val binding = DialogTextAlertBinding.inflate(LayoutInflater.from(context))
                    val button = binding.textAlertDialogButtonContainer.addButtons(context)
                    if (checkbox) binding.textAlertDialogCheckBox.isVisible = true
                    val oldCheckboxPadding = binding.textAlertDialogCheckBox.paddingTop
                    val oldDrawableWidth = binding.textAlertDialogCheckBox.buttonDrawable!!.intrinsicWidth
                    binding.applySpacing()
                    val container = binding.textAlertDialogButtonContainer
                    assertContainer(binding.root, enabled)
                    assertButtons(container, button, enabled)
                    assertEquals(container.dp(if (enabled) 24 else 0), binding.textAlertDialogTitle.marginStart)
                    assertEquals(container.dp(8), binding.textAlertDialogMessage.marginTop)
                    assertEquals(container.dp(40), binding.textAlertDialogImage.layoutParams.width)
                    assertEquals(container.dp(16), binding.textAlertDialogImage.marginBottom)
                    if (enabled) {
                        // Visible gaps: 16 from the message to the buttons, 20 from the checkbox row, 24 below the buttons.
                        val toButtons = container.marginTop + container.paddingTop + button.insetTop
                        val checkboxBottom = binding.textAlertDialogCheckBox.paddingBottom
                        assertEquals(container.dp(if (checkbox) 20 else 16), toButtons + if (checkbox) checkboxBottom else 0)
                        assertEquals(container.dp(if (checkbox) 8 else 16), toButtons)
                        assertEquals(container.dp(12), binding.textAlertDialogCheckBox.paddingTop)
                        assertEquals(oldDrawableWidth + container.dp(16), binding.textAlertDialogCheckBox.buttonDrawable!!.intrinsicWidth)
                    } else {
                        // Develop rendering: untouched layout margin and checkbox.
                        assertEquals(container.dp(16), container.marginTop)
                        assertEquals(oldCheckboxPadding, binding.textAlertDialogCheckBox.paddingTop)
                        assertEquals(oldDrawableWidth, binding.textAlertDialogCheckBox.buttonDrawable!!.intrinsicWidth)
                    }
                }
            }
        }
    }

    @Test
    fun stackedDialogSpacingOnAndOff() {
        listOf(false, true).forEach { enabled ->
            listOf(false, true).forEach { rebrandButtons ->
                val context = context(enabled, rebrandButtons)
                val builder = StackedAlertDialogBuilder(context).setTitle("Title").setMessage("Message").setRebrandUpdate(true)
                    .setStackedButtons(listOf(android.R.string.ok, android.R.string.cancel, android.R.string.paste))
                builder.build()
                val dialog = builder.javaClass.getDeclaredField("dialog").apply { isAccessible = true }.get(builder) as AlertDialog
                dialog.show()
                val layout = dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!
                val first = layout.getChildAt(0) as MaterialButton
                val title = dialog.findViewById<View>(CommonR.id.stackedAlertDialogTitle)!!
                if (enabled) {
                    assertButtons(layout, first, enabled)
                } else {
                    assertEquals(0, layout.paddingTop)
                    assertEquals(0, layout.paddingBottom)
                }
                assertEquals(0, first.marginTop)
                // Visible gap between stacked buttons is 8 with and without the toggle (insets of 6dp leave 12, the margin cannot go negative).
                layout.children.zipWithNext().forEach { (above, below) ->
                    val visible = below.marginTop + (above as MaterialButton).insetBottom + (below as MaterialButton).insetTop
                    assertEquals(maxOf(layout.dp(8), above.insetBottom + below.insetTop), visible)
                }
                assertEquals(layout.dp(if (enabled) 8 else 24), layout.marginTop)
                assertEquals(layout.dp(if (enabled) 16 else 0), title.marginTop)
                dialog.dismiss()
            }
        }
    }

    @Test
    fun stackedDialogWithoutRebrandUpdateKeepsDevelopButtonMargins() {
        val context = context(true)
        val builder = StackedAlertDialogBuilder(context).setTitle("Title").setStackedButtons(listOf(android.R.string.ok, android.R.string.cancel))
        builder.build()
        val dialog = builder.javaClass.getDeclaredField("dialog").apply { isAccessible = true }.get(builder) as AlertDialog
        dialog.show()
        val layout = dialog.findViewById<LinearLayout>(CommonR.id.stackedAlertDialogButtonLayout)!!
        assertEquals(0, layout.getChildAt(1).marginTop)
        dialog.dismiss()
    }

    @Test
    fun radioDialogSpacingOnAndOff() {
        listOf(false, true).forEach { enabled ->
            listOf(false, true).forEach { rebrandButtons ->
                val context = context(enabled, rebrandButtons)
                val binding = DialogSingleChoiceAlertBinding.inflate(LayoutInflater.from(context))
                val row = RadioButton(context, null)
                binding.radioListDialogRadioGroup.addView(row)
                val button = binding.radioListAlertDialogButtonContainer.addButtons(context)
                val oldPadding = row.paddingStart
                binding.radioListDialogTitle.text = "Title"
                binding.applySpacing()
                assertContainer(binding.root, enabled)
                assertButtons(binding.radioListAlertDialogButtonContainer, button, enabled)
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(binding.root.dp(300), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                val container = binding.radioListAlertDialogButtonContainer
                // Last row to the visible top of the buttons: 8 when on; develop's 16 view-edge gap when off.
                assertEquals(
                    binding.root.dp(if (enabled) 8 else 16),
                    container.top + container.paddingTop + (if (enabled) button.insetTop else 0) - binding.radioListDialogContent.bottom,
                )
                assertEquals(binding.root.dp(if (enabled) 8 else 0), binding.radioListDialogContent.marginStart)
                assertEquals(binding.root.dp(if (enabled) 0 else 16), binding.radioListDialogContent.marginBottom)
                assertEquals(if (enabled) 0 else oldPadding, row.paddingStart)
                if (enabled) {
                    // The button draws at the row's start edge, so the start side and the label gap are drawable insets.
                    // The 16dp start is to the visible circle, so the drawable's own 6dp blank margin comes off it.
                    val drawable = row.buttonDrawable as LayerDrawable
                    assertEquals(row.dp(10), drawable.getLayerInsetStart(0))
                    assertEquals(row.dp(16), drawable.getLayerInsetEnd(0))
                    assertEquals(row.dp(16), row.paddingEnd)
                    assertEquals(row.dp(8), row.paddingTop)
                    assertEquals(row.dp(8), row.paddingBottom)
                    assertEquals(row.dp(48), row.minimumHeight)
                    // Visible circle lines up with the title's 24dp start.
                    val circleStart = binding.radioListDialogContent.left + row.left + drawable.getLayerInsetStart(0) + row.dp(6)
                    assertEquals(binding.radioListDialogTitle.left, circleStart)
                }
            }
        }
    }

    @Test
    fun customDialogSpacingOnAndOff() {
        listOf(false, true).forEach { enabled ->
            listOf(false, true).forEach { rebrandButtons ->
                val binding = DialogCustomAlertBinding.inflate(LayoutInflater.from(context(enabled, rebrandButtons)))
                binding.customDialogPositiveButton.text = "Yes"
                binding.customDialogNegativeButton.text = "No"
                binding.applySpacing()
                val flow = binding.dialogButtonFlow
                assertContainer(binding.root, enabled)
                assertEquals(binding.root.dp(if (enabled) 24 else 0), flow.marginStart)
                assertEquals(binding.root.dp(16), flow.marginTop + if (enabled) binding.customDialogPositiveButton.insetTop else 0)
                assertEquals(
                    binding.root.dp(if (enabled) 24 else 0),
                    flow.marginBottom + if (enabled) binding.customDialogPositiveButton.insetBottom else 0,
                )
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(binding.root.dp(300), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
                val button = binding.customDialogPositiveButton
                val gapAbove = button.top + (if (enabled) button.insetTop else 0) - binding.customDialogContent.bottom
                val gapBelow = binding.root.height - binding.root.paddingBottom - button.bottom + (if (enabled) button.insetBottom else 0)
                assertEquals(binding.root.dp(16), gapAbove)
                assertEquals(binding.root.dp(if (enabled) 24 else 0), gapBelow)
                // Side-by-side buttons stay 8dp apart.
                assertEquals(binding.root.dp(8), button.left - binding.customDialogNegativeButton.right)
            }
        }
    }

    @Test
    fun promoSheetSpacingIsIdenticalWithSpacingToggleOnAndOff() {
        listOf(false, true).forEach { rebrandButtons ->
            val spacing = listOf(false, true).map { enabled ->
                val sheet = PromoBottomSheetDialog.Builder(context(enabled, rebrandButtons)).setTitle("Title").setContent("Description")
                    .setPrimaryButton("Continue").setSecondaryButton("Cancel").build()
                val field = sheet.javaClass.getDeclaredField("binding").apply { isAccessible = true }
                val binding = field.get(sheet) as BottomSheetPromoBinding
                val root = binding.root
                val values = listOf(
                    root.paddingStart,
                    root.paddingTop,
                    root.paddingEnd,
                    root.paddingBottom,
                    binding.bottomSheetPromoTitle.marginBottom,
                    binding.bottomSheetPromoPrimaryButton.marginTop,
                )
                sheet.dismiss()
                values
            }
            assertEquals(spacing[0], spacing[1])
        }
    }

    @Test
    fun checkboxLabelGapFollowsLayoutDirection() {
        val binding = DialogTextAlertBinding.inflate(LayoutInflater.from(context(true)))
        binding.applySpacing()
        val drawable = binding.textAlertDialogCheckBox.buttonDrawable as LayerDrawable
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
        assertEquals(0, drawable.getDrawable(0).bounds.left)
        assertEquals(drawable.intrinsicWidth - binding.root.dp(16), drawable.getDrawable(0).bounds.right)
        drawable.setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
        assertEquals(binding.root.dp(16), drawable.getDrawable(0).bounds.left)
        assertEquals(drawable.intrinsicWidth, drawable.getDrawable(0).bounds.right)
    }
}
