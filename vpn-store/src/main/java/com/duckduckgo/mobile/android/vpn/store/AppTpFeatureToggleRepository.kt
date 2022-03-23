/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureName

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor

// marker interface to use delegate pattern
interface AppTpFeatureToggleRepository : VpnFeatureToggleStore {
    companion object {
        fun create(
            context: Context,
            appBuildConfig: AppBuildConfig,
        ): AppTpFeatureToggleRepository {
            val store = RealVpnFeatureToggleStore(context)
            return RealAppTpFeatureToggleRepository(store, appBuildConfig)
        }
    }
}

internal class RealAppTpFeatureToggleRepository constructor(
    private val vpnFeatureToggleStore: VpnFeatureToggleStore,
    private val appBuildConfig: AppBuildConfig,
) : AppTpFeatureToggleRepository, VpnFeatureToggleStore by vpnFeatureToggleStore {

    override fun get(featureName: AppTpFeatureName, defaultValue: Boolean): Boolean {
        val delegateValue = vpnFeatureToggleStore.get(featureName, defaultValue)

        // Ensure production builds have all these feature disabled for now
        return (appBuildConfig.flavor == BuildFlavor.INTERNAL) && delegateValue
    }
}
