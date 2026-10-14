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

package com.duckduckgo.sync.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SyncBuildConfig : AppBuildConfig {
    /**
     * True in production. Some UI tests turn it off with a build flag so they can get past device authentication.
     */
    val isAuthRequired: Boolean
}

@ContributesBinding(AppScope::class, boundType = SyncBuildConfig::class)
class RealSyncBuildConfig @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SyncBuildConfig, AppBuildConfig by appBuildConfig {
    override val isAuthRequired get() = BuildConfig.AUTH_REQUIRED
}
