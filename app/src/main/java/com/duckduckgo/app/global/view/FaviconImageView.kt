/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat.getColor
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.baseHost
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.util.*
import kotlin.math.absoluteValue

fun ImageView.loadFavicon(file: File, domain: String) {
    val defaultDrawable = generateDefaultDrawable(this.context, domain)
    Glide.with(context)
        .load(file)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .transform(RoundedCorners(10))
        .placeholder(defaultDrawable)
        .error(defaultDrawable)
        .into(this)
}

fun ImageView.loadFavicon(bitmap: Bitmap?, domain: String) {
    val defaultDrawable = generateDefaultDrawable(this.context, domain)
    Glide.with(context)
        .load(bitmap)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .transform(RoundedCorners(10))
        .placeholder(defaultDrawable)
        .error(defaultDrawable)
        .into(this)
}

fun ImageView.loadDefaultFavicon(domain: String) {
    this.setImageDrawable(generateDefaultDrawable(this.context, domain))
}

fun generateDefaultDrawable(context: Context, domain: String): Drawable {
    return object : Drawable() {
        private val baseHost: String = domain.toUri().baseHost ?: ""

        private val letter
            get() = baseHost.firstOrNull()?.toString()?.toUpperCase(Locale.getDefault()) ?: ""

        private val palette = listOf(
            "#94B3AF",
            "#727998",
            "#645468",
            "#4D5F7F",
            "#855DB6",
            "#5E5ADB",
            "#678FFF",
            "#6BB4EF",
            "#4A9BAE",
            "#66C4C6",
            "#55D388",
            "#99DB7A",
            "#ECCC7B",
            "#E7A538",
            "#DD6B4C",
            "#D65D62"
        )

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = domainToColor(baseHost)
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = getColor(context, R.color.white)
            typeface = Typeface.SANS_SERIF
        }

        override fun draw(canvas: Canvas) {
            val centerX = bounds.width() * 0.5f
            val centerY = bounds.height() * 0.5f
            textPaint.textSize = (bounds.width() / 2).toFloat()
            val textWidth: Float = textPaint.measureText(letter) * 0.5f
            val textBaseLineHeight = textPaint.fontMetrics.ascent * -0.4f
            canvas.drawRoundRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), 10f, 10f, backgroundPaint)
            canvas.drawText(letter, centerX - textWidth, centerY + textBaseLineHeight, textPaint)
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        override fun getOpacity(): Int {
            return PixelFormat.UNKNOWN
        }

        private fun domainToColor(domain: String): Int {
            return domain.encodeUtf8().toByteArray().fold(5381L) { acc, byte ->
                (acc shl 5) + acc + byte.toLong()
            }.absoluteValue.let {
                val index = (it % palette.size).toInt()
                palette[index]
            }.toColorInt()
        }
    }
}
