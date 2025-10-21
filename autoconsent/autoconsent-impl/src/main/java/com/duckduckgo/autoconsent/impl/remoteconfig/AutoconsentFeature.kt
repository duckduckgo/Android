/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.remoteconfig

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * This is the class that represents the autoconsent feature flags
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "autoconsent",
)
interface AutoconsentFeature {
    /**
     * @return `true` when the remote config has the global "voiceSearch" feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onByDefault(): Toggle

    /**
     * Kill switch for autoconsent rule filtering
     * @return `true` when the remote config has the global "ruleFiltering" autoconsent
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun ruleFiltering(): Toggle
}
