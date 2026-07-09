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

package com.duckduckgo.common.ui

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * By default, when bottom sheet dialog is expanded, the corners become squared.
 * This function ensures that the bottom sheet dialog will have rounded corners even when in an expanded state.
 */
fun BottomSheetDialog.setRoundCorners() {
    val bottomSheet = this.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

    val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(context)
    shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
        .toBuilder()
        .setTopLeftCorner(CornerFamily.ROUNDED, context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius))
        .setTopRightCorner(CornerFamily.ROUNDED, context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.dialogBorderRadius))
        .build()
    bottomSheet?.background = shapeDrawable
}

/**
 * Pads [this]'s bottom by the navigation-bar inset (on top of its existing bottom padding) so a bottom sheet's
 * content clears the gesture pill / button bar under edge-to-edge (Android 15+, targetSdk 35), where a
 * BottomSheetDialog's own window is edge-to-edge regardless of the host activity. Only call this when the sheet
 * is actually using an `.EdgeToEdge` theme (see `EdgeToEdgeBucket.BOTTOM_SHEETS`), otherwise the inset is 0 and
 * the padding never changes. Call on the view that owns the sheet's rounded background (not necessarily
 * `binding.root` - e.g. when the root is a backgroundless `NestedScrollView` wrapping a styled `ConstraintLayout`,
 * pad that inner view instead, or the reserved clearance won't be covered by the sheet's background). Call in
 * onViewCreated / onCreateView. The original bottom padding is captured once, so repeated inset dispatches never
 * accumulate.
 */
fun View.applyBottomSystemBarInsetPadding() {
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updatePadding(bottom = initialBottom + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
