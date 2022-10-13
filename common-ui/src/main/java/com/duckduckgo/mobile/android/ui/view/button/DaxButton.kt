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

package com.duckduckgo.mobile.android.ui.view.button

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.button.DaxButton.Type.Primary
import com.duckduckgo.mobile.android.ui.view.text.DaxTextView.Type.Body1
import com.google.android.material.button.MaterialButton

class DaxButton @JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DaxButton,
                0,
                0
            )

        val buttonType = if (typedArray.hasValue(R.styleable.DaxButton_buttonType)) {
            Type.from(typedArray.getInt(R.styleable.DaxButton_buttonType, 0))
        } else {
            Primary
        }

        typedArray.recycle()

        setButtonType(buttonType)
        setTextAppearance(R.style.TextAppearance_DuckDuckGo_Button1)
    }

    fun setButtonType(buttonType: Type) {
        when (buttonType) {
            Primary -> setButtonPrimary()
            else -> setButtonSecondary()
        }
    }

    private fun setButtonPrimary(){
        setBackgroundResource(R.drawable.background_blue_button_primary)
        setTextColor(resources.getColorStateList(R.color.primary_text_color_selector))
    }

    private fun setButtonSecondary(){
        setBackgroundResource(R.drawable.background_transparent_button_rounded_corners)
        setTextColor(resources.getColorStateList(R.color.accent_blue_text_color_selector))
    }

    enum class Type {
        Primary,
        Secondary,
        Ghost,
        Destructive,
        GhostDestructive;

        companion object {
            fun from(type: Int): Type {
                // same order as attrs-button.xml
                return when (type) {
                    0 -> Primary
                    1 -> Secondary
                    2 -> Ghost
                    3 -> Destructive
                    4 -> GhostDestructive
                    else -> Primary
                }
            }
        }
    }
}
