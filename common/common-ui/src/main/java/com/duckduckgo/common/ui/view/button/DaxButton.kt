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

package com.duckduckgo.common.ui.view.button

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.common.ui.view.button.Size.Small
import com.duckduckgo.mobile.android.R
import com.google.android.material.button.MaterialButton

open class DaxButton @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
) : MaterialButton(
    ctx,
    attrs,
    defStyleAttr,
) {

    init {
        val typedArray =
            context.obtainStyledAttributes(
                attrs,
                R.styleable.DaxButton,
                0,
                0,
            )

        val buttonSize = if (typedArray.hasValue(R.styleable.DaxButton_daxButtonSize)) {
            Size.from(typedArray.getInt(R.styleable.DaxButton_daxButtonSize, 0))
        } else {
            Small
        }

        typedArray.recycle()

        val sidePadding = if (buttonSize == Small) {
            resources.getDimensionPixelSize(R.dimen.buttonSmallSidePadding)
        } else {
            resources.getDimensionPixelSize(R.dimen.buttonLargeSidePadding)
        }

        val topPadding = if (buttonSize == Small) {
            resources.getDimensionPixelSize(R.dimen.buttonSmallTopPadding)
        } else {
            resources.getDimensionPixelSize(R.dimen.buttonLargeTopPadding)
        }

        minHeight = resources.getDimensionPixelSize(Size.dimension(buttonSize))
        setPadding(sidePadding, topPadding, sidePadding, topPadding)
        setCornerRadiusResource(R.dimen.mediumShapeCornerRadius)
    }
}

enum class Size {
    Small,
    Large,
    ;

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

enum class ButtonType {
    PRIMARY,
    GHOST,
    SECONDARY,
    DESTRUCTIVE,
    GHOST_DESTRUCTIVE,
    GHOST_ALT,
    ;

    fun getView(context: Context): DaxButton {
        return when (this) {
            PRIMARY -> DaxButtonPrimary(context, null)
            GHOST -> DaxButtonGhost(context, null)
            SECONDARY -> DaxButtonSecondary(context, null)
            DESTRUCTIVE -> DaxButtonDestructive(context, null)
            GHOST_DESTRUCTIVE -> DaxButtonGhostDestructive(context, null)
            GHOST_ALT -> DaxButtonGhostAlt(context, null)
        }
    }
}
