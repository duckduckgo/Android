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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.text.Annotation
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withTranslation
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncV2PairingHeaderBinding
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR

internal class SyncPairingHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewSyncV2PairingHeaderBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        context.withStyledAttributes(attrs, R.styleable.SyncPairingHeaderView) {
            getResourceId(R.styleable.SyncPairingHeaderView_headlineText, 0).takeIf { it != 0 }?.let(::setHeadline)
            getResourceId(R.styleable.SyncPairingHeaderView_bodyText, 0).takeIf { it != 0 }?.let(::setBody)
        }
    }

    fun setHeadline(@StringRes headline: Int) {
        binding.headlineText.setText(headline)
    }

    fun setBody(@StringRes body: Int) {
        val text = context.getText(body)
        binding.bodyText.text = if (text is SpannedString) text.withStyledAnnotations() else text
    }

    private fun SpannedString.withStyledAnnotations(): CharSequence {
        val spannable = SpannableStringBuilder(this)
        getSpans(0, length, Annotation::class.java).forEach { annotation ->
            val start = getSpanStart(annotation)
            val end = getSpanEnd(annotation)

            when (val value = annotation.value) {
                "gap" -> {
                    spannable.setSpan(AbsoluteSizeSpan(4, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                "highlight" -> {
                    val color = context.getColorFromAttr(CommonR.attr.daxColorPrimaryText)
                    spannable.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                "icon" -> {
                    val drawable = ContextCompat
                        .getDrawable(context, R.drawable.ic_ddg_logo_16)
                        ?.let { icon ->
                            val spacing = 4.toPx()
                            InsetDrawable(icon, spacing, 0, spacing, 0)
                        }
                        ?.apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }
                    if (drawable != null) {
                        spannable.setSpan(CenteredImageSpan(drawable), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }

                else -> logcat { "Unknown annotation: $value" }
            }
        }
        return spannable
    }

    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val fontMetrics = paint.fontMetricsInt
            val lineCenter = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.withTranslation(x, lineCenter - drawable.bounds.height() / 2f, drawable::draw)
        }
    }
}
