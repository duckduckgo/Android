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

package com.duckduckgo.dataclearing.impl.toggles

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "dataClearingModularization",
)
interface DataClearingModularizationFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun navigationHistory(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun savedSitesPrune(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun appCache(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contextualChats(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun sitePermissions(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun sitePreferences(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun cookies(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tabs(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun webStorage(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun webStorageSingleTab(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun duckAiWebStorage(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun trackerStats(): Toggle
}
