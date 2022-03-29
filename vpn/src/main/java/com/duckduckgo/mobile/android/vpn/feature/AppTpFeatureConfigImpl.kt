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
import kotlin.reflect.KClass

@ContributesBinding(
    scope = AppScope::class,
    boundType = AppTpFeatureConfig::class
)
@SingleInstanceIn(AppScope::class)
class AppTpFeatureConfigImpl @Inject constructor() : AppTpFeatureConfig, AppTpFeatureConfig.Editor {

    override fun <T : AppTpConfig> get(type: KClass<T>): T? {
        Timber.d("Get AppTpConfigs:")
        return when (type) {
            AppTpConfig.Ipv6Config::class -> null
            AppTpConfig.BadHealthMitigationConfig::class -> null
            AppTpConfig.PrivateDnsConfig::class -> null
            AppTpConfig.NetworkSwitchHandlingConfig::class -> null
            else -> null
        }
    }

    override fun edit(): AppTpFeatureConfig.Editor {
        return this
    }

    override fun <T : AppTpConfig> put(config: T) {
        Timber.d("Store AppTpConfigs: $config")
        when (config) {
            is AppTpConfig.Ipv6Config -> null
            is AppTpConfig.BadHealthMitigationConfig -> null
            is AppTpConfig.PrivateDnsConfig -> null
            is AppTpConfig.NetworkSwitchHandlingConfig -> null
        }
    }
}
