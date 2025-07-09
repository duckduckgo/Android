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

package com.duckduckgo.sync.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "sync",
)
interface SyncFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    @Toggle.InternalAlwaysEnabled
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun level0ShowSync(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun level1AllowDataSyncing(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun level2AllowSetupFlows(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun level3AllowCreateAccount(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun gzipPatchRequests(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun seamlessAccountSwitching(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun exchangeKeysToSyncWithAnotherDevice(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun automaticallyUpdateSyncSettings(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun syncSetupBarcodeIsUrlBased(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun canScanUrlBasedSyncSetupBarcodes(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun canInterceptSyncSetupUrls(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun canOverrideThemeSyncSetup(): Toggle
}
