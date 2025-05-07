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

package com.duckduckgo.networkprotection.internal.feature

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "netpInternal",
    // we define store because we need something multi-process
    toggleStore = NetPInternalFeatureToggleStore::class,
)
interface NetPInternalFeatureToggles {
    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun self(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun excludeSystemApps(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun enablePcapRecording(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun useVpnStagingEnvironment(): Toggle
}
