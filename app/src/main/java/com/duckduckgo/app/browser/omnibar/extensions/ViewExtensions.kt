/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.extensions

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.card.MaterialCardView

/**
 * Adds a custom shadow below a view with specified size in dp and rounded corners.
 *
 * @param shadowSizeDp The size of the shadow in dp
 * @param offsetYDp Optional vertical offset in dp to position shadow below the view
 * @param insetDp Optional horizontal inset in dp to prevent shadow from being cut off
 * @param shadowColor Optional shadow color (Android P and above only)
 */
@RequiresApi(28)
fun MaterialCardView.addBottomShadow(
    shadowSizeDp: Float = 12f,
    offsetYDp: Float = 3f,
    insetDp: Float = 3f,
    @ColorInt shadowColor: Int = ContextCompat.getColor(this.context, com.duckduckgo.mobile.android.R.color.background_omnibar_shadow),
): ViewOutlineProvider {
    val shadowSize = shadowSizeDp.toPx(context)
    val offsetY = offsetYDp.toPx(context)
    val inset = insetDp.toPx(context).toInt()

    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            // Create outline with rounded corners that match the view
            outline.setRoundRect(
                -inset,
                0,
                view.width + inset,
                view.height,
                radius,
            )
            // Make the shadow appear only below the view
            outline.offset(0, offsetY.toInt())
        }
    }

    // Set custom shadow color if specified (Android P and above)
    outlineSpotShadowColor = ContextCompat.getColor(context, android.R.color.transparent)
    outlineAmbientShadowColor = shadowColor

    clipToOutline = false
    elevation = shadowSize

    return outlineProvider
}
