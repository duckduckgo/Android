/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.core.content.ContextCompat
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.button.ButtonType.Destructive
import com.duckduckgo.mobile.android.ui.view.button.ButtonType.Primary
import com.duckduckgo.mobile.android.ui.view.button.ButtonType.Secondary
import com.duckduckgo.mobile.android.ui.view.button.Size.Small
import com.google.android.material.button.MaterialButton

class ThemeButton @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = R.attr.themedButton,
) : MaterialButton(
    ctx,
    attrs,
    defStyleAttr,
) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.ThemeButton,
                0,
                0,
            )

        val buttonType = if (typedArray.hasValue(R.styleable.ThemeButton_buttonType)) {
            ButtonType.from(typedArray.getInt(R.styleable.ThemeButton_buttonType, 0))
        } else {
            Primary
        }

        when (buttonType){
            Primary -> {
                setTextColor(ContextCompat.getColorStateList(context, R.color.button_primary_text_color_selector))
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.button_primary_container_selector)
                rippleColor = ContextCompat.getColorStateList(context, R.color.button_primary_ripple_selector)
                iconTint = ContextCompat.getColorStateList(context, R.color.button_primary_text_color_selector)
            }
            Secondary -> {
                strokeColor = ContextCompat.getColorStateList(context, R.color.button_secondary_stroke_selector)
                strokeWidth = resources.getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_btn_stroke_size)
                setTextColor(ContextCompat.getColorStateList(context, R.color.button_secondary_text_color_selector))
                rippleColor = ContextCompat.getColorStateList(context, R.color.button_secondary_ripple_selector)
                iconTint = ContextCompat.getColorStateList(context, R.color.button_secondary_text_color_selector)
                iconPadding = resources.getDimensionPixelSize(R.dimen.keyline_2)
            }
            Destructive -> {

            }
        }

        typedArray.recycle()

        val sidePadding = resources.getDimensionPixelSize(R.dimen.buttonSmallSidePadding)
        val topPadding = resources.getDimensionPixelSize(R.dimen.buttonSmallTopPadding)
        minHeight = resources.getDimensionPixelSize(R.dimen.buttonSmallHeight)
        setPadding(sidePadding, topPadding, sidePadding, topPadding)
    }
}

enum class ButtonType {
    Primary,
    Secondary,
    Destructive
    ;

    companion object {
        fun from(type: Int): ButtonType {
            // same order as attrs-button.xml
            return when (type) {
                0 -> Primary
                1 -> Secondary
                else -> Destructive
            }
        }
    }
}
