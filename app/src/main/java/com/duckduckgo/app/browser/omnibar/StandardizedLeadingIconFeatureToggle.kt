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

package com.duckduckgo.app.browser.omnibar

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Feature toggle for standardized leading icon behavior in the omnibar.
 *
 * This feature standardizes how the leading icon (Globe vs Privacy Shield) is
 * displayed to match behavior across other DuckDuckGo platforms.
 *
 * When **disabled** (legacy behavior):
 * - Local/private network addresses show Privacy Shield (potentially with UNPROTECTED state)
 * - Users see warning dots for their router IPs and speculatively mitigated sites
 *
 * When **enabled** (new behavior):
 * - Local/private network addresses (localhost, 192.168.x.x, etc.) show Globe icon
 * - Remote config exceptions no longer show UNPROTECTED state
 * - Reduces non-actionable breakage reports from Android users
 * - Aligns with iOS/macOS/Windows behavior
 *
 * @see <a href="https://github.com/duckduckgo/Android/pull/7436">PR #7436</a>
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "standardizedLeadingIcon",
)
interface StandardizedLeadingIconFeatureToggle {
    /**
     * Root feature toggle for standardized leading icon behavior.
     *
     * @return Toggle that controls whether to use the new icon determination logic
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
