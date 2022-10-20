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
import com.duckduckgo.mobile.android.ui.view.button.Size.Small
import com.google.android.material.button.MaterialButton

open class DaxButton @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet,
    defStyleAttr: Int
) : MaterialButton(
    ctx,
    attrs,
    defStyleAttr
) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DaxButton,
                0,
                0
            )

        val buttonSize = if (typedArray.hasValue(R.styleable.DaxButton_buttonSize)) {
            Size.from(typedArray.getInt(R.styleable.DaxButton_buttonSize, 0))
        } else {
            Small
        }
        typedArray.recycle()

        minHeight = resources.getDimensionPixelSize(Size.dimension(buttonSize))
        iconSize = if (buttonSize == Small){
            resources.getDimensionPixelSize(R.dimen.buttonSmallIconSize)
        } else {
            resources.getDimensionPixelSize(R.dimen.buttonSmallIconSize)
        }
    }

}

enum class Size {
    Small,
    Large;

    companion object {
        fun from(size: Int): Size {
            // same order as attrs-button.xml
            return when (size) {
                0 -> Small
                1 -> Large
                else -> Large
            }
        }

        fun dimension(size: Size): Int {
            return when (size) {
                Small -> R.dimen.buttonSmallHeight
                Large -> R.dimen.buttonLargeHeight
                else -> R.dimen.buttonSmallHeight
            }
        }
    }
}
