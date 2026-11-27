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

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Kill switch for loading a complete http(s) URL as given in [QueryUrlConverter].
 *
 * When enabled (default), an http(s) URL with a host, no whitespace and no userinfo is not passed through URL extraction.
 * When disabled, every input classified as a URL goes through extraction again, which cuts valid URLs at characters such as `[` or `|`.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidLoadCompleteWebUrlAsGiven",
)
interface LoadCompleteWebUrlAsGivenFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
