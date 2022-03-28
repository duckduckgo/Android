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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    boundType = AppTpFeatureConfig::class
)
@SingleInstanceIn(AppScope::class)
class AppTpFeatureConfigImpl @Inject constructor() : AppTpFeatureConfig, AppTpFeatureConfig.Editor {

    override fun get(appTpSetting: AppTpSetting): AppTpConfig? {
        Timber.d("Get AppTpConfigs:")
        return when (appTpSetting) {
            AppTpSetting.BadHealthMitigation -> null
            AppTpSetting.Ipv6Support -> null
            AppTpSetting.PrivateDnsSupport -> null
            AppTpSetting.NetworkSwitchHandling -> null
        }
    }

    override fun edit(): AppTpFeatureConfig.Editor {
        return this
    }

    override fun put(appTpSetting: AppTpSetting, config: AppTpConfig) {
        Timber.d("Store AppTpConfigs: $config")
        when (appTpSetting) {
            AppTpSetting.BadHealthMitigation -> {}
            AppTpSetting.Ipv6Support -> {}
            AppTpSetting.PrivateDnsSupport -> {}
            AppTpSetting.NetworkSwitchHandling -> {}
        }
    }
}
