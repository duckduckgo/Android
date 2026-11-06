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

import android.content.Context
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.isRebrandAreaEnabled
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogCustomAlertBinding
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.duckduckgo.mobile.android.databinding.DialogStackedAlertBinding
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.google.android.material.button.MaterialButton

internal fun Context.rebrandDialogCardWidth(): Int {
    val maxWidth = resources.getDimensionPixelSize(R.dimen.rebrandDialogMaxWidth)
    val margin = resources.getDimensionPixelSize(R.dimen.keyline_5)
    return minOf(maxWidth, resources.displayMetrics.widthPixels - margin * 2)
}

internal fun ImageView.applyRebrandHeaderIconStyle() {
    val inset = resources.getDimensionPixelSize(R.dimen.rebrandDialogIconInset)
    setBackgroundResource(R.drawable.background_dialog_icon_circular)
    setPadding(inset, inset, inset, inset)
}

private fun View.applyDialogSpacing() {
    setPadding(0, resources.getDimensionPixelSize(R.dimen.keyline_5), 0, 0)
}

private fun View.dialogSides() {
    val side = resources.getDimensionPixelSize(R.dimen.keyline_5)
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = side
        marginEnd = side
    }
}

// Figma measures to the visible button edge, but a MaterialButton draws inside its vertical insets, so the insets come off the padding.
private fun ViewGroup.dialogButtons(topMargin: Int = resources.getDimensionPixelSize(R.dimen.keyline_2)) {
    val side = resources.getDimensionPixelSize(R.dimen.keyline_5)
    val gap = resources.getDimensionPixelSize(R.dimen.keyline_2)
    val button = children.filterIsInstance<MaterialButton>().firstOrNull()
    setPadding(side, (gap - (button?.insetTop ?: 0)).coerceAtLeast(0), side, (side - (button?.insetBottom ?: 0)).coerceAtLeast(0))
    updateLayoutParams<ViewGroup.MarginLayoutParams> { this.topMargin = topMargin }
}

internal fun DialogTextAlertBinding.applySpacing() {
    if (!root.context.isRebrandAreaEnabled(R.attr.daxRebrandSpacing)) return
    root.applyDialogSpacing()
    textAlertDialogTitle.dialogSides()
    textAlertDialogMessage.dialogSides()
    textAlertDialogCheckBox.dialogSides()
    val vertical = root.resources.getDimensionPixelSize(R.dimen.keyline_3)
    textAlertDialogCheckBox.setPaddingRelative(0, vertical, 0, vertical)
    textAlertDialogCheckBox.spaceButtonLabel()
    // The checkbox row's own 12dp bottom padding already gives most of the gap to the buttons.
    val gap = root.resources.getDimensionPixelSize(R.dimen.keyline_2)
    textAlertDialogButtonContainer.dialogButtons(topMargin = if (textAlertDialogCheckBox.isVisible) 0 else gap)
}

internal fun DialogStackedAlertBinding.applySpacing() {
    if (!root.context.isRebrandAreaEnabled(R.attr.daxRebrandSpacing)) return
    root.applyDialogSpacing()
    stackedAlertDialogTitle.dialogSides()
    stackedlertDialogMessage.dialogSides()
    stackedAlertDialogTitle.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
        goneTopMargin = 0
        topMargin = root.resources.getDimensionPixelSize(R.dimen.keyline_4)
    }
    stackedAlertDialogButtonLayout.dialogButtons()
}

internal fun DialogSingleChoiceAlertBinding.applySpacing() {
    if (!root.context.isRebrandAreaEnabled(R.attr.daxRebrandSpacing)) return
    root.applyDialogSpacing()
    radioListDialogTitle.dialogSides()
    radioListDialogMessage.dialogSides()
    val inset = root.resources.getDimensionPixelSize(R.dimen.keyline_2)
    radioListDialogContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = inset
        marginEnd = inset
        bottomMargin = 0
    }
    radioListAlertDialogButtonContainer.dialogButtons()
    radioListDialogRadioGroup.children.forEach { row ->
        val side = root.resources.getDimensionPixelSize(R.dimen.keyline_4)
        // CompoundButton draws its button at the start edge, ignoring padding, so the start side goes on the drawable,
        // less the blank margin the Material radio drawable keeps around its circle.
        row.setPaddingRelative(0, inset, side, inset)
        row.minimumHeight = root.resources.getDimensionPixelSize(R.dimen.keyline_7)
        (row as CompoundButton).spaceButtonLabel(start = side - root.resources.getDimensionPixelSize(R.dimen.rebrandRadioIconInset))
        row.updateLayoutParams<ViewGroup.MarginLayoutParams> { marginStart = 0 }
    }
}

internal fun DialogCustomAlertBinding.applySpacing() {
    if (!root.context.isRebrandAreaEnabled(R.attr.daxRebrandSpacing)) return
    root.applyDialogSpacing()
    customDialogTitle.dialogSides()
    customDialogMessage.dialogSides()
    customDialogContent.dialogSides()
    val side = root.resources.getDimensionPixelSize(R.dimen.keyline_5)
    val button = listOf(customDialogNegativeButton, customDialogPositiveButton).firstOrNull { it.isVisible }
    dialogButtonFlow.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = side
        marginEnd = side
        topMargin = (root.resources.getDimensionPixelSize(R.dimen.keyline_4) - (button?.insetTop ?: 0)).coerceAtLeast(0)
        bottomMargin = (side - (button?.insetBottom ?: 0)).coerceAtLeast(0)
    }
}

private fun CompoundButton.spaceButtonLabel(start: Int = 0) {
    val gap = resources.getDimensionPixelSize(R.dimen.keyline_4)
    buttonDrawable = LayerDrawable(arrayOf(buttonDrawable)).apply {
        setLayerInsetRelative(0, start, 0, gap, 0)
    }
}
