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

package com.duckduckgo.mobile.android.vpn.feature

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.store.RealVpnFeatureToggleStore
import com.duckduckgo.mobile.android.vpn.store.VpnFeatureToggleStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

// marker interface to use delegate pattern
interface AppTpFeatureToggleRepository : VpnFeatureToggleStore

class RealAppTpFeatureToggleRepository constructor(
    private val vpnFeatureToggleStore: VpnFeatureToggleStore,
    private val appBuildConfig: AppBuildConfig,
) : AppTpFeatureToggleRepository, VpnFeatureToggleStore by vpnFeatureToggleStore {

    override fun get(featureName: String, defaultValue: Boolean): Boolean? {
        val delegateValue = vpnFeatureToggleStore.get(featureName, defaultValue) ?: return null

        // Ensure production builds have all these feature disabled for now
        return (appBuildConfig.flavor == BuildFlavor.INTERNAL) && delegateValue
    }
}

@ContributesTo(AppScope::class)
@Module
class AppTpFeatureToggleRepositoryModule {
    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAppTpFeatureToggleRepository(context: Context, appBuildConfig: AppBuildConfig): AppTpFeatureToggleRepository {
        val store = RealVpnFeatureToggleStore(context)
        return RealAppTpFeatureToggleRepository(store, appBuildConfig)
    }
}
