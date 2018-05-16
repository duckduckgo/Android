/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.duckduckgo.app.browser.R

class TabIconRenderer {

    companion object {

        fun icon(context: Context, count: Int): Drawable {
            val text = if (count < 100) "$count" else "~"
            return makeDrawable(context, text)
        }

        private fun makeDrawable(context: Context, text: String): Drawable {
            val drawable = context.getDrawable(R.drawable.ic_tabs_gray_24dp)
            val bitmap = drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
            val paint = createPaint(context)
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)

            val canvas = Canvas(bitmap)
            val x = (bitmap.width / 2f) - bounds.centerX() - xOffset(context)
            val y = (bitmap.height / 2f) - bounds.centerY() + yOffset(context)
            canvas.drawText(text, x, y, paint)
            return bitmap.toDrawable(context.resources)
        }

        private fun createPaint(context: Context): Paint {
            val paint = Paint()
            paint.apply {
                color = ContextCompat.getColor(context, R.color.colorPrimary)
                style = Paint.Style.FILL
                textSize = context.resources.getDimensionPixelSize(R.dimen.tabIconTextSize).toFloat()
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            return paint
        }

        private fun xOffset(context: Context): Int {
            return context.resources.getDimensionPixelSize(R.dimen.tabIconXOffset)
        }

        private fun yOffset(context: Context): Int {
            return context.resources.getDimensionPixelSize(R.dimen.tabIconYOffset)
        }
    }
}