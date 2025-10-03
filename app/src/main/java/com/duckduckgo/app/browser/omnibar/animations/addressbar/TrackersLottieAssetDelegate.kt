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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.airbnb.lottie.ImageAssetDelegate
import com.airbnb.lottie.LottieImageAsset
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx

internal class TrackersLottieAssetDelegate(
    val context: Context,
    val logos: List<TrackerLogo>,
) : ImageAssetDelegate {

    override fun fetchBitmap(asset: LottieImageAsset?): Bitmap? {
        return when (asset?.id) {
            "image_0" -> {
                runCatching { logos[0].asDrawable(context) }
                    .getOrDefault(
                        ContextCompat.getDrawable(context, R.drawable.network_logo_blank)!!.toBitmap(),
                    )
            }

            "image_1" -> {
                runCatching { logos[1].asDrawable(context) }
                    .getOrDefault(
                        ContextCompat.getDrawable(context, R.drawable.network_logo_blank)!!.toBitmap(),
                    )
            }

            "image_2" -> {
                runCatching { logos[2].asDrawable(context) }
                    .getOrDefault(
                        ContextCompat.getDrawable(context, R.drawable.network_logo_blank)!!.toBitmap(),
                    )
            }

            "image_3" ->
                runCatching { logos[3].asDrawable(context) }
                    .getOrNull()

            else -> null
        }
    }

    private fun TrackerLogo.asDrawable(context: Context): Bitmap {
        return runCatching {
            when (this) {
                is TrackerLogo.ImageLogo -> ContextCompat.getDrawable(context, resId)!!.toBitmap()
                is TrackerLogo.LetterLogo -> generateDefaultDrawable(context, this.trackerLetter).toBitmap(24.toPx(), 24.toPx())
                is TrackerLogo.StackedLogo -> ContextCompat.getDrawable(context, this.resId)!!.toBitmap()
            }
        }.getOrThrow()
    }

    private fun generateDefaultDrawable(
        context: Context,
        letter: String,
    ): Drawable {
        return object : Drawable() {

            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorPrimaryIcon)
            }

            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
                typeface = Typeface.SANS_SERIF
            }

            override fun draw(canvas: Canvas) {
                val centerX = bounds.width() * 0.5f
                val centerY = bounds.height() * 0.5f
                textPaint.textSize = (bounds.width() * 0.7f).toFloat()
                textPaint.typeface = Typeface.DEFAULT_BOLD
                val textWidth: Float = textPaint.measureText(letter) * 0.5f
                val textBaseLineHeight = textPaint.fontMetrics.ascent * -0.4f
                canvas.drawCircle(centerX, centerY, centerX, backgroundPaint)
                canvas.drawText(letter, centerX - textWidth, centerY + textBaseLineHeight, textPaint)
            }

            override fun setAlpha(alpha: Int) {
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
            }

            override fun getOpacity(): Int {
                return PixelFormat.TRANSPARENT
            }
        }
    }
}
