/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.bugreport

import android.content.Context
import android.os.PowerManager
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.di.BatteryOptimizationState
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider

@ContributesMultibinding(ActivityScope::class)
class DeviceInfoCollector @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    @BatteryOptimizationState private val isIgnoringBatteryOptimizations: Provider<Boolean>,
) : VpnStateCollectorPlugin {
    override val collectorName: String
        get() = "deviceInfo"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            put("buildFlavor", appBuildConfig.flavor.toString())
            put("os", appBuildConfig.sdkInt)
            put("batteryOptimizations", (!isIgnoringBatteryOptimizations.get()).toString())
            put("man", appBuildConfig.manufacturer)
        }
    }
}

@Module
@ContributesTo(AppScope::class)
object DeviceInfoCollectorModule {
    // this convenience class is just to allow testing
    @Provides
    @BatteryOptimizationState
    fun provideIsIgnoringBatteryOptimizations(context: Context): Boolean {
        return runCatching {
            context.packageName?.let {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } ?: false
        }.getOrDefault(false)
    }
}
