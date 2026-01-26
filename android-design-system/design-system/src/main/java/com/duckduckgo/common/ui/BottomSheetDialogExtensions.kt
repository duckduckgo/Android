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

import android.widget.FrameLayout
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
