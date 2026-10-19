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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.isRebrandAreaEnabled
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewInfoPanelBinding

class InfoPanel : FrameLayout {

    private val isRebrandEnabled = context.isRebrandAreaEnabled(R.attr.daxRebrandInfoPanels)
    private var panelBackground = R.drawable.info_panel_tooltip_background

    private val binding: ViewInfoPanelBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(
        context,
        attrs,
        R.style.Widget_DuckDuckGo_InfoPanel,
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
    ) : super(context, attrs, defStyle) {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.InfoPanel,
            0,
            if (isRebrandEnabled) R.style.Widget_DuckDuckGo_InfoPanel_Rebrand else 0,
        )
        panelBackground = attributes.getResourceId(
            R.styleable.InfoPanel_panelBackground,
            R.drawable.info_panel_tooltip_background,
        )
        if (isRebrandEnabled) {
            binding.infoPanelImage.updateLayoutParams<MarginLayoutParams> { topMargin = 0 }
        }
        setText(attributes.getString(R.styleable.InfoPanel_panelText) ?: "")
        setImageResource(
            attributes.getResourceId(
                R.styleable.InfoPanel_panelDrawable,
                R.drawable.ic_info_panel_info,
            ),
        )
        setBackgroundResource(
            if (isRebrandEnabled && panelBackground in listOf(R.drawable.info_panel_tooltip_background, R.drawable.info_panel_alert_background)) {
                R.drawable.info_panel_rebrand_background
            } else {
                panelBackground
            },
        )
        attributes.recycle()
    }

    /**
     * Sets the panel text
     */
    fun setText(text: CharSequence) {
        binding.infoPanelText.text = text
    }

    fun setClickableLink(
        annotation: String,
        fullText: CharSequence,
        onClick: () -> Unit,
    ) {
        setClickableLink(
            annotation = annotation,
            spannableFullText = SpannableString(fullText),
            onClick = onClick,
        )
    }

    fun setClickableLink(
        annotation: String,
        spannableFullText: SpannableString,
        onClick: () -> Unit,
    ) {
        val annotations = spannableFullText.getSpans(0, spannableFullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableFullText.apply {
                setSpan(
                    clickableSpan,
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    UnderlineSpan(),
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(context.getColorFromAttr(R.attr.daxColorPrimaryText)),
                    spannableFullText.getSpanStart(it),
                    spannableFullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        binding.infoPanelText.apply {
            text = spannableFullText
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /**
     * Sets the panel image resource
     */
    fun setImageResource(idRes: Int) {
        val icon = if (isRebrandEnabled) {
            when (idRes) {
                R.drawable.ic_info_panel_info -> if (panelBackground == R.drawable.info_panel_alert_background) {
                    R.drawable.ic_exclamation_recolorable_invert_16
                } else {
                    R.drawable.ic_info_recolorable_16
                }
                R.drawable.ic_exclamation_yellow_16 -> R.drawable.ic_exclamation_recolorable_invert_16
                else -> idRes
            }
        } else {
            idRes
        }
        binding.infoPanelImage.setImageResource(icon)
    }

    companion object {
        const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        const val APPTP_SETTINGS_ANNOTATION = "app_settings_link"
    }
}
