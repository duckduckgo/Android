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

package com.duckduckgo.app.onboarding.ui.page

object OnboardingDecorationSizing {

    /**
     * Shrink-to-fit clamp shared by onboarding decorations (welcome-screen wings/Dax and the new-tab
     * waving Dax). Returns the height to apply, clamped to [maxHeightPx], when [availablePx] meets the
     * floor; returns null to hide when there is less room than [minHeightPx].
     */
    fun fitHeight(availablePx: Int, minHeightPx: Int, maxHeightPx: Int): Int? =
        if (availablePx < minHeightPx) null else availablePx.coerceAtMost(maxHeightPx)
}
