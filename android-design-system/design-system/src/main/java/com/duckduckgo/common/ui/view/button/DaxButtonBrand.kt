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

package com.duckduckgo.common.ui.view.button

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.common.ui.view.button.Size.Large
import com.duckduckgo.common.ui.view.button.Size.Small
import com.duckduckgo.mobile.android.R

class DaxButtonBrand @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = R.attr.daxButtonBrand,
) : DaxButton(
    ctx,
    attrs,
    defStyleAttr,
) {

    init {
        val daxButtonAttrs = context.obtainStyledAttributes(attrs, R.styleable.DaxButton, 0, 0)
        val buttonSize = if (daxButtonAttrs.hasValue(R.styleable.DaxButton_daxButtonSize)) {
            Size.from(daxButtonAttrs.getInt(R.styleable.DaxButton_daxButtonSize, 0))
        } else {
            Small
        }
        daxButtonAttrs.recycle()

        val brandAttrs = context.obtainStyledAttributes(attrs, R.styleable.DaxButtonBrand, 0, 0)
        val duckSans = brandAttrs.getInt(R.styleable.DaxButtonBrand_daxButtonBrandTypography, 0) == 1
        brandAttrs.recycle()

        val sidePadding = resources.getDimensionPixelSize(
            if (buttonSize == Small) R.dimen.keyline_4 else R.dimen.keyline_5,
        )
        val topPadding = resources.getDimensionPixelSize(
            if (buttonSize == Small) R.dimen.rebrandButtonSmallTopPadding else R.dimen.rebrandButtonLargeTopPadding,
        )
        val inset = resources.getDimensionPixelSize(
            if (buttonSize == Small) R.dimen.rebrandButtonSmallVerticalInset else R.dimen.rebrandButtonLargeVerticalInset,
        )
        insetTop = inset
        insetBottom = inset

        val visibleHeight = resources.getDimensionPixelSize(
            if (buttonSize == Small) R.dimen.rebrandButtonSmallHeight else R.dimen.rebrandButtonLargeHeight,
        )
        minHeight = if (buttonSize == Large) visibleHeight + inset * 2 else visibleHeight

        setPadding(sidePadding, topPadding, sidePadding, topPadding)

        setTextAppearance(
            when {
                duckSans && buttonSize == Large -> R.style.Typography_DuckDuckGo_Rebrand_ButtonDuckSansLarge
                duckSans -> R.style.Typography_DuckDuckGo_Rebrand_ButtonDuckSans
                buttonSize == Large -> R.style.Typography_DuckDuckGo_Rebrand_ButtonLarge
                else -> R.style.Typography_DuckDuckGo_Rebrand_Button
            },
        )
    }
}
