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

package com.duckduckgo.duckchat.impl.feature

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "aiChat",
)
/**
 * This is the class that represents the aiChat feature flags
 */
interface DuckChatFeature {
    /**
     * @return `true` when the remote config has the global "aiChat" feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiPlus(): Toggle

    /**
     * @return `true` when the remote config has the "duckAiButtonInBrowser" Duck.ai button in browser
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiButtonInBrowser(): Toggle

    /**
     * @return `true` when the remote config has the "keepSessionAlive"
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun keepSession(): Toggle

    /**
     * @return `true` when the remote config has the "duckAiInputScreen"
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiInputScreen(): Toggle
}
