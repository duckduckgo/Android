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

import com.duckduckgo.mobile.android.R

object AnimationResourceProvider {

    fun getSlideInFromBottomFadeIn(): Int {
        return R.anim.slide_in_from_bottom_fade_in
    }

    fun getSlideOutToTopFadeOut(): Int {
        return R.anim.slide_out_to_top_fade_out
    }

    fun getSlideInFromTopFadeIn(): Int {
        return R.anim.slide_in_from_top_fade_in
    }

    fun getSlideOutToBottomFadeOut(): Int {
        return R.anim.slide_out_to_bottom_fade_out
    }
}
