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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.duckduckgo.mobile.android.R
import javax.inject.Inject

internal class CustomTabShieldDrawableFactory @Inject constructor() {

    fun create(
        context: Context,
        @DrawableRes drawableRes: Int,
        isLightMode: Boolean,
    ): Drawable {
        val theme = context.resources.newTheme().apply {
            setTo(context.theme)
            applyStyle(
                if (isLightMode) {
                    R.style.Theme_DuckDuckGo_Light
                } else {
                    R.style.Theme_DuckDuckGo_Dark
                },
                true,
            )
        }

        return requireNotNull(ResourcesCompat.getDrawable(context.resources, drawableRes, theme))
    }
}
