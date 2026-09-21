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

package com.duckduckgo.dataclearing.feature.toggles

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Per-data-source rollout of the data clearing plugin architecture.
 *
 * Each sub-toggle is read by the plugin that owns that data source, not by the caller: several
 * Regular sources share one `ClearableData` variant, so the orchestrator cannot switch them
 * individually. When a sub-toggle is off the legacy `ClearPersonalDataAction` / `WebDataManager`
 * path clears that source; when it is on the plugin does. Never both.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "dataClearingModularization",
)
interface DataClearingModularizationFeature {
    /**
     * @return `true` when the remote config has the global "dataClearingModularization" feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun navigationHistory(): Toggle

    /** Prunes tombstoned bookmarks/favorites; only runs when the user is signed in to Sync. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun savedSitesPrune(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun appCache(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun sitePermissions(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun sitePreferences(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun cookies(): Toggle

    /** The 7-day blocked-tracker log. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun trackerStats(): Toggle

    /** Regular-mode tabs; the Fire-mode tab clear already runs through the plugin. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun tabs(): Toggle

    /** Regular-mode full burn of WebView storage. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun webStorage(): Toggle

    /** Regular-mode single-tab burn of WebView storage. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun webStorageSingleTab(): Toggle
}
