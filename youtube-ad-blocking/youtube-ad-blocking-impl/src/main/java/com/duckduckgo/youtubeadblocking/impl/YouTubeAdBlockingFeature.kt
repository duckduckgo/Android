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

package com.duckduckgo.youtubeadblocking.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Remote feature toggle for YouTube ad blocking.
 *
 * When enabled, scriptlets are injected into YouTube pages at document start
 * to intercept and block ad-related API calls before YouTube's own JS initialises.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "youTubeAdBlocking",
)
interface YouTubeAdBlockingFeature {

    /**
     * @return `true` when the remote config has the "youTubeAdBlocking" feature flag enabled.
     * Defaults to `false` (off) when remote feature is not present.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * Controls the injection mechanism.
     *
     * When enabled: scriptlets are injected via `evaluateJavascript` in `onPageStarted`.
     *   - No HTML modification, no CSP stripping, no OkHttp fetch
     *   - evaluateJavascript is not subject to CSP
     *   - Timing may be slightly later than HTML injection
     *
     * When disabled (default): scriptlets are injected via `shouldInterceptRequest` HTML modification.
     *   - Guaranteed pre-init timing (script is in the HTML before parsing)
     *   - Requires CSP stripping and OkHttp fetch with cookie bridging
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun useEvaluateJs(): Toggle
}
