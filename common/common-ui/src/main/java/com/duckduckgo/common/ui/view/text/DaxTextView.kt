/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.common.ui.view.text

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.TypedArrayUtils
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography.Body1
import com.duckduckgo.mobile.android.R
import com.google.android.material.textview.MaterialTextView

class DaxTextView @JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DaxTextView,
                0,
                0,
            )

        val typography = if (typedArray.hasValue(R.styleable.DaxTextView_typography)) {
            Typography.from(typedArray.getInt(R.styleable.DaxTextView_typography, 0))
        } else {
            Body1
        }

        setTypography(typography)

        val hasType = typedArray.hasValue(R.styleable.DaxTextView_textType)
        val hasTextColor = typedArray.hasValue(R.styleable.DaxTextView_android_textColor)

        when {
            hasType -> {
                val textType = TextType.from(typedArray.getInt(R.styleable.DaxTextView_textType, 0))
                setTextColorStateList(textType)
            }

            hasTextColor -> {
                val colorStateList = TypedArrayUtils.getColorStateList(context, typedArray, R.styleable.DaxTextView_android_textColor)
                if (colorStateList != null) {
                    setTextColor(colorStateList)
                } else {
                    setTextColor(ContextCompat.getColorStateList(context, R.color.primary_text_color_selector))
                }
            }

            else -> setTextColorStateList(TextType.Primary)
        }

        typedArray.recycle()
    }

    fun setTypography(typography: Typography) {
        setTextAppearance(Typography.getTextAppearanceStyle(typography))
    }

    fun setTextColorStateList(textType: TextType) {
        setTextColor(
            ContextCompat.getColorStateList(
                context,
                TextType.getTextColorStateList(textType),
            ),
        )
    }

    enum class Typography {
        Title,
        H1,
        H2,
        H3,
        H4,
        H5,
        Body1,
        Body1Bold,
        Body1Mono,
        Body2,
        Body2Bold,
        Button,
        Caption,
        CaptionAllCaps,
        ;

        companion object {
            fun from(type: Int): Typography {
                // same order as attrs-typography.xml
                return when (type) {
                    0 -> Title
                    1 -> H1
                    2 -> H2
                    3 -> H3
                    4 -> H4
                    5 -> H5
                    6 -> Body1
                    7 -> Body1Bold
                    8 -> Body1Mono
                    9 -> Body2
                    10 -> Body2Bold
                    11 -> Button
                    12 -> Caption
                    13 -> CaptionAllCaps
                    else -> Body1
                }
            }

            fun getTextAppearanceStyle(typography: Typography): Int {
                return when (typography) {
                    Title -> R.style.Typography_DuckDuckGo_Title
                    H1 -> R.style.Typography_DuckDuckGo_H1
                    H2 -> R.style.Typography_DuckDuckGo_H2
                    H3 -> R.style.Typography_DuckDuckGo_H3
                    H4 -> R.style.Typography_DuckDuckGo_H4
                    H5 -> R.style.Typography_DuckDuckGo_H5
                    Body1 -> R.style.Typography_DuckDuckGo_Body1
                    Body1Bold -> R.style.Typography_DuckDuckGo_Body1_Bold
                    Body1Mono -> R.style.Typography_DuckDuckGo_Body1_Mono
                    Body2 -> R.style.Typography_DuckDuckGo_Body2
                    Body2Bold -> R.style.Typography_DuckDuckGo_Body2_Bold
                    Button -> R.style.Typography_DuckDuckGo_Button
                    Caption -> R.style.Typography_DuckDuckGo_Caption
                    CaptionAllCaps -> R.style.Typography_DuckDuckGo_Caption_AllCaps
                }
            }
        }
    }

    enum class TextType {
        Primary,
        Secondary,
        ;

        companion object {
            fun from(type: Int): TextType {
                // same order as attrs-typography.xml
                return when (type) {
                    0 -> Primary
                    else -> Secondary
                }
            }

            fun getTextColorStateList(textType: TextType): Int {
                return when (textType) {
                    Primary -> R.color.primary_text_color_selector
                    Secondary -> R.color.secondary_text_color_selector
                }
            }
        }
    }
}
