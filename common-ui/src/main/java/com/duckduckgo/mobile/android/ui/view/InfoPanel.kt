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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.duckduckgo.mobile.android.ui.view

import android.content.Context
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewInfoPanelBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class InfoPanel : FrameLayout {

    private val binding: ViewInfoPanelBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : this(
        context,
        attrs,
        R.style.Widget_DuckDuckGo_InfoPanel
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.InfoPanel)
        setText(attributes.getString(R.styleable.InfoPanel_panelText) ?: "")
        setImageResource(
            attributes.getResourceId(
                R.styleable.InfoPanel_panelDrawable,
                R.drawable.ic_link_color_24
            )
        )
        setBackgroundResource(
            attributes.getResourceId(
                R.styleable.InfoPanel_panelBackground,
                R.drawable.background_blue_tooltip
            )
        )
        attributes.recycle()
    }

    /**
     * Sets the panel text
     */
    fun setText(text: String) {
        binding.infoPanelText.text = text
    }

    fun setClickableLink(
        annotation: String,
        fullText: CharSequence,
        onClick: () -> Unit
    ) {
        val spannedText = fullText as SpannedString
        val spannableString = SpannableString(spannedText)
        val annotations = spannedText.getSpans(0, spannedText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.almostBlackDark)
                    ),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        binding.infoPanelText.apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /**
     * Sets the panel image resource
     */
    fun setImageResource(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.infoPanelImage.setImageDrawable(drawable)
    }

    companion object {
        const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        const val APPTP_SETTINGS_ANNOTATION = "app_settings_link"
    }
}
