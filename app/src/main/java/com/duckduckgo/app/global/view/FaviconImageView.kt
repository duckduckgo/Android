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
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat.getColor
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.duckduckgo.common.utils.baseHost
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.util.*
import kotlin.math.absoluteValue
import com.duckduckgo.mobile.android.R as CommonR

fun ImageView.loadFavicon(
    file: File,
    domain: String,
    placeholder: String? = null,
) {
    runCatching {
        val defaultDrawable = generateDefaultDrawable(this.context, domain, placeholder)
        Glide.with(context).clear(this@loadFavicon)
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .transform(RoundedCorners(context.resources.getDimensionPixelSize(CommonR.dimen.verySmallShapeCornerRadius)))
            .placeholder(defaultDrawable)
            .error(defaultDrawable)
            .into(this)
    }.onFailure {
        logcat(ERROR) { "Error loading favicon: ${it.asLog()}" }
    }
}

fun ImageView.loadFavicon(
    bitmap: Bitmap?,
    domain: String,
    placeholder: String? = null,
) {
    runCatching {
        val defaultDrawable = generateDefaultDrawable(this.context, domain, placeholder)
        Glide.with(context).clear(this)
        Glide.with(context)
            .load(bitmap)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .transform(RoundedCorners(context.resources.getDimensionPixelSize(CommonR.dimen.verySmallShapeCornerRadius)))
            .placeholder(defaultDrawable)
            .error(defaultDrawable)
            .into(this)
    }.onFailure {
        logcat(ERROR) { "Error loading favicon: ${it.asLog()}" }
    }
}

fun ImageView.loadDefaultFavicon(domain: String) {
    runCatching {
        this.setImageDrawable(generateDefaultDrawable(this.context, domain))
    }.onFailure {
        logcat(ERROR) { "Error loading default favicon: ${it.asLog()}" }
    }
}

/**
 * Generates a default drawable which has a colored background with a character drawn centrally on top
 * The given domain is used to generate a related background color.
 * If an `overridePlaceholderCharacter` is specified, it will be used for the placeholder character
 * If no `overridePlaceholderCharacter` is specified, the placeholder character will be extracted from the domain
 */
fun generateDefaultDrawable(
    context: Context,
    domain: String,
    overridePlaceholderCharacter: String? = null,
    @DimenRes cornerRadius: Int = CommonR.dimen.verySmallShapeCornerRadius,
): Drawable {
    return object : Drawable() {
        private val baseHost: String = domain.toUri().baseHost ?: ""

        private val letter: String
            get() {
                if (overridePlaceholderCharacter != null) {
                    return overridePlaceholderCharacter
                }
                return baseHost.firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: ""
            }
        private val faviconDefaultSize = context.resources.getDimension(CommonR.dimen.savedSiteGridItemFavicon)

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
            "#D65D62",
        )

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = domainToColor(baseHost)
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = getColor(context, CommonR.color.white)
            typeface = Typeface.SANS_SERIF
        }

        override fun draw(canvas: Canvas) {
            val centerX = bounds.width() * 0.5f
            val centerY = bounds.height() * 0.5f
            textPaint.textSize = (bounds.width() / 2).toFloat()
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val textWidth: Float = textPaint.measureText(letter) * 0.5f
            val textBaseLineHeight = textPaint.fontMetrics.ascent * -0.4f
            val radius = (bounds.width() * context.resources.getDimension(cornerRadius)) / faviconDefaultSize
            canvas.drawRoundRect(0f, 0f, bounds.width().toFloat(), bounds.height().toFloat(), radius, radius, backgroundPaint)
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
