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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.SyncFeature
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.*

@ContributesBinding(
    scope = AppScope::class,
    boundType = DeviceSyncState::class,
    priority = ContributesBinding.Priority.HIGHEST,
)
class AppDeviceSyncState @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val syncFeature: SyncFeature,
    private val syncRepository: SyncRepository,
) : DeviceSyncState {

    override fun isUserSignedInOnDevice(): Boolean = syncRepository.isSignedIn()

    override fun isFeatureEnabled(): Boolean {
        return syncFeature.self().isEnabled() || appBuildConfig.isInternalBuild()
    }
}
