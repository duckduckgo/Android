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

package com.duckduckgo.sync.impl.ui.pairing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.text.Annotation
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewPairingHeaderBinding
import logcat.logcat
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

internal class SyncPairingHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewPairingHeaderBinding.inflate(LayoutInflater.from(context), this)

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

    fun setBody(@StringRes body: Int, vararg formatArgs: String) {
        val text = context.getText(body)
        val styledText = if (text is SpannedString) text.withStyledAnnotations() else SpannableStringBuilder(text)
        binding.bodyText.text = styledText.withFormatArgs(formatArgs)
    }

    // We can't use getString(id, args) because it drops the annotations, so we replace each placeholder and keep the spans around it.
    private fun SpannableStringBuilder.withFormatArgs(formatArgs: Array<out String>): SpannableStringBuilder {
        formatArgs.forEachIndexed { index, arg ->
            val placeholder = "%${index + 1}\$s"
            val start = indexOf(placeholder).takeIf { it >= 0 } ?: return@forEachIndexed
            val end = start + placeholder.length
            val spans = getSpans(start, end, Any::class.java).map { span ->
                SpanRange(span, getSpanStart(span), getSpanEnd(span), getSpanFlags(span))
            }
            spans.forEach { removeSpan(it.span) }
            replace(start, end, arg)
            fun shifted(position: Int) = if (position >= end) position + arg.length - placeholder.length else position
            spans.forEach { range ->
                setSpan(range.span, shifted(range.start), shifted(range.end), range.flags)
            }
        }
        return this
    }

    private class SpanRange(val span: Any, val start: Int, val end: Int, val flags: Int)

    private fun SpannedString.withStyledAnnotations(): SpannableStringBuilder {
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

                "url" -> {
                    // No theme color matches the design's pill, so we strengthen the closest one by a third in both themes.
                    val fill = context.getColorFromAttr(CommonR.attr.daxColorControlFillTertiary)
                    val span = UrlSpan(
                        backgroundColor = ColorUtils.setAlphaComponent(fill, (Color.alpha(fill) * 4 / 3).coerceAtMost(255)),
                        textColor = context.getColorFromAttr(CommonR.attr.daxColorPrimaryText),
                        horizontalPadding = 8.toPx().toFloat(),
                        verticalPadding = 2.toPx().toFloat(),
                        cornerRadius = 6.toPx().toFloat(),
                    )
                    spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                else -> logcat { "Unknown annotation: $value" }
            }
        }
        return spannable
    }

    private class UrlSpan(
        private val backgroundColor: Int,
        private val textColor: Int,
        private val horizontalPadding: Float,
        private val verticalPadding: Float,
        private val cornerRadius: Float,
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            if (fm != null) {
                // We grow the line by the pill's padding, otherwise the pill is clipped when it's on the last line.
                paint.getFontMetricsInt(fm)
                val padding = verticalPadding.roundToInt()
                fm.top -= padding
                fm.ascent -= padding
                fm.descent += padding
                fm.bottom += padding
            }
            return (paint.measureText(text, start, end) + 2 * horizontalPadding).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val fontMetrics = paint.fontMetrics
            val background = RectF(
                x,
                y + fontMetrics.ascent - verticalPadding,
                x + paint.measureText(text, start, end) + 2 * horizontalPadding,
                y + fontMetrics.descent + verticalPadding,
            )
            val originalColor = paint.color
            paint.color = backgroundColor
            canvas.drawRoundRect(background, cornerRadius, cornerRadius, paint)
            paint.color = textColor
            canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
            paint.color = originalColor
        }
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
