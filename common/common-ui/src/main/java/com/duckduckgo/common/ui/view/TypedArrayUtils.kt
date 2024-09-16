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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.StyleableRes
import androidx.appcompat.content.res.AppCompatResources
import com.duckduckgo.mobile.android.R

/**
 * Utility methods for extracting [ColorStateList]s and [Drawable]s from a [TypedArray].
 * The resources are inflated using [AppCompatResources], which provides bug fixes and backports
 * features introduced on new platforms.
 */
object TypedArrayUtils {

    fun getColorStateList(
        context: Context,
        typedArray: TypedArray,
        @StyleableRes index: Int,
        @ColorRes defaultValue: Int = R.color.primary_text_color_selector,
    ): ColorStateList? {
        if (typedArray.hasValue(index)) {
            val resourceId = typedArray.getResourceId(index, defaultValue)
            if (resourceId != 0) {
                val value = AppCompatResources.getColorStateList(context, resourceId)
                if (value != null) {
                    return value
                }
            }
        }
        return typedArray.getColorStateList(index)
    }

    fun getDrawable(
        context: Context,
        typedArray: TypedArray,
        @StyleableRes index: Int,
    ): Drawable? {
        if (typedArray.hasValue(index)) {
            val resourceId = typedArray.getResourceId(index, 0)
            if (resourceId != 0) {
                val value = AppCompatResources.getDrawable(context, resourceId)
                if (value != null) {
                    return value
                }
            }
        }
        return typedArray.getDrawable(index)
    }
}
