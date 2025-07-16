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

package com.duckduckgo.common.ui.anim

import android.os.Build.VERSION
import com.duckduckgo.mobile.android.R

object AnimationResourceProvider {

    // The overshoot alpha transition can result in a black screen flash on older Android devices,
    // so we use the standard decelerate transition instead for these cases in the provider functions below.
    // The check is for version 34, as that's the range of devices on which we tested and confirmed that it works.

    fun getSlideInFromBottomFadeIn(): Int {
        return if (VERSION.SDK_INT >= 34) {
            R.anim.slide_in_from_bottom_fade_in_overshoot
        } else {
            R.anim.slide_in_from_bottom_fade_in
        }
    }

    fun getSlideOutToTopFadeOut(): Int {
        return if (VERSION.SDK_INT >= 34) {
            R.anim.slide_out_to_top_fade_out_overshoot
        } else {
            R.anim.slide_out_to_top_fade_out
        }
    }

    fun getSlideInFromTopFadeIn(): Int {
        return if (VERSION.SDK_INT >= 34) {
            R.anim.slide_in_from_top_fade_in_overshoot
        } else {
            R.anim.slide_in_from_top_fade_in
        }
    }

    fun getSlideOutToBottomFadeOut(): Int {
        return if (VERSION.SDK_INT >= 34) {
            R.anim.slide_out_to_bottom_fade_out_overshoot
        } else {
            R.anim.slide_out_to_bottom_fade_out
        }
    }
}
