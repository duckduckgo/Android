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

package com.duckduckgo.adblocking.api

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

interface AdBlockingOmnibarAnimationProvider {

    /**
     * Returns whether the ad-blocking badge should animate for [url]. [pageChanged] is true for a
     * document load/reload and false for an in-page SPA url change.
     */
    suspend fun getAnimation(url: String, pageChanged: Boolean): AdBlockingAnimation
}

sealed class AdBlockingAnimation {
    data class Show(
        @field:DrawableRes val icon: Int,
        @field:StringRes val text: Int,
    ) : AdBlockingAnimation()

    /**
     * No badge should show for [url] (not an ad-blocked video page, or the feature is off). Any prior
     * badge exclusivity for the page should be released.
     */
    data object Skip : AdBlockingAnimation()

    /**
     * An in-page change that stayed on the same already-badged video: don't re-animate, but keep the
     * current badge state (so a prior badge's exclusivity is preserved across the several url-change
     * events a single navigation can emit).
     */
    data object Retain : AdBlockingAnimation()
}
