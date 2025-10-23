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

package com.duckduckgo.common.ui.themepreview.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.duckduckgo.mobile.android.R
import com.google.android.material.textview.MaterialTextView

/**
 * A reusable text view that displays a platform label (either "Compose" or "View")
 * with caption all caps typography styling.
 */
class PlatformLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr) {

    init {
        val paddingStart = resources.getDimensionPixelSize(R.dimen.keyline_4)
        setPaddingRelative(paddingStart, 0, 0, 0)
        setTextAppearance(R.style.Typography_DuckDuckGo_Caption_AllCaps)
        setTextColor(ContextCompat.getColorStateList(context, R.color.secondary_text_color_selector))

        context.withStyledAttributes(
            attrs,
            R.styleable.PlatformLabelView,
            0,
            0,
        ) {

            val platformType = PlatformType.from(
                getInt(R.styleable.PlatformLabelView_platformType, 0),
            )

            text = when (platformType) {
                PlatformType.Compose -> "Compose"
                PlatformType.View -> "View"
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val marginTop = resources.getDimensionPixelSize(R.dimen.keyline_1)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.keyline_1)

        if (layoutParams == null) {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = marginTop
                bottomMargin = marginBottom
            }
        } else if (layoutParams is ViewGroup.MarginLayoutParams) {
            (layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = marginTop
                bottomMargin = marginBottom
            }
        }
    }

    /**
     * Sets the platform type to display
     */
    fun setPlatformType(platformType: PlatformType) {
        text = when (platformType) {
            PlatformType.Compose -> "Compose"
            PlatformType.View -> "View"
        }
    }

    enum class PlatformType {
        Compose,
        View,
        ;

        companion object {
            fun from(type: Int): PlatformType {
                return when (type) {
                    0 -> Compose
                    1 -> View
                    else -> Compose
                }
            }
        }
    }
}
