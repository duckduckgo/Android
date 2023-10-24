/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.pixels.remoteconfig

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle

/**
 * This is the class that represents the browser feature flags
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidBrowserConfig",
)
interface AndroidBrowserConfigFeature {

    /**
     * @return `true` when the remote config has the global "androidBrowserConfig" feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    /**
     * @return `true` when the remote config has the global "collectFullWebViewVersion" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun collectFullWebViewVersion(): Toggle
}
