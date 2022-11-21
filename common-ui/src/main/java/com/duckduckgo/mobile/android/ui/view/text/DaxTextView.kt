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

package com.duckduckgo.mobile.android.ui.view.text

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Type.Body1
import com.google.android.material.textview.MaterialTextView
import java.lang.reflect.Array.getInt

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

        val typographyType = if (typedArray.hasValue(R.styleable.DaxTextView_typography)) {
            Type.from(typedArray.getInt(R.styleable.DaxTextView_typography, 0))
        } else {
            Body1
        }

        setTypography(typographyType)

        val textColor = if (typedArray.hasValue(R.styleable.DaxTextView_textColor)) {
            TextColor.from(typedArray.getInt(R.styleable.DaxTextView_textColor, 0))
        } else {
            TextColor.Primary
        }

        val textColorInverted = typedArray.getBoolean(R.styleable.DaxTextView_textColorInverted, false)
        setTextColorStateList(textColor, textColorInverted)

        typedArray.recycle()
    }

    fun setTypography(type: Type) {
        setTextAppearance(Type.getTextAppearanceStyle(type))
    }

    fun setTextColorStateList(textColor: TextColor, textColorInverted: Boolean = false) {
        setTextColor(ContextCompat.getColorStateList(context, TextColor.getTextColorStateList(textColor, textColorInverted)))
    }

    enum class Type {
        Title,
        H1,
        H2,
        H3,
        H4,
        H5,
        Body1,
        Body2,
        Button,
        Caption,
        ;

        companion object {
            fun from(type: Int): Type {
                // same order as attrs-typography.xml
                return when (type) {
                    0 -> Title
                    1 -> H1
                    2 -> H2
                    3 -> H3
                    4 -> H4
                    5 -> H5
                    6 -> Body1
                    7 -> Body2
                    8 -> Button
                    9 -> Caption
                    else -> Body1
                }
            }

            fun getTextAppearanceStyle(type: Type): Int {
                return when (type) {
                    Title -> R.style.Typography_DuckDuckGo_Title
                    H1 -> R.style.Typography_DuckDuckGo_H1
                    H2 -> R.style.Typography_DuckDuckGo_H2
                    H3 -> R.style.Typography_DuckDuckGo_H3
                    H4 -> R.style.Typography_DuckDuckGo_H4
                    H5 -> R.style.Typography_DuckDuckGo_H5
                    Body1 -> R.style.Typography_DuckDuckGo_Body1
                    Body2 -> R.style.Typography_DuckDuckGo_Body2
                    Button -> R.style.Typography_DuckDuckGo_Button
                    Caption -> R.style.Typography_DuckDuckGo_Caption
                }
            }
        }
    }

    enum class TextColor {
        Primary,
        Secondary,
        ;

        companion object {
            fun from(type: Int): TextColor {
                // same order as attrs-typography.xml
                return when (type) {
                    0 -> Primary
                    else -> Secondary
                }
            }

            fun getTextColorStateList(textColor: TextColor, textColorInverted: Boolean = false): Int {
                return when (textColor) {
                    Primary -> if (textColorInverted) {
                        R.color.primary_text_color_inverted_selector
                    } else {
                        R.color.primary_text_color_selector
                    }
                    Secondary -> if (textColorInverted) {
                         R.color.secondary_text_color_inverted_selector
                    } else {
                        R.color.secondary_text_color_selector
                    }
                }
            }
        }
    }
}
