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

package com.duckduckgo.adblocking.impl.remoteconfig

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "adBlockingExtension",
)
interface AdBlockingExtensionFeature {
    /**
     * @return `true` when the feature is operational.
     * When false, feature is still accessible, but not working as expected.
     */
    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * Kill-switch. When false, the feature is completely inaccessible.
     */
    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun isDiscoverable(): Toggle

    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun enabledByDefault(): Toggle
}
