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

/**
 * [AppTpFeatureConfig] returns the configuration of the AppTP features.
 * The configuration is set from either remote config, or the app AppTp internal settings
 */
interface AppTpFeatureConfig {
    fun get(): Set<AppTpConfig>

    fun edit(): Editor

    interface Editor {
        fun put(config: AppTpConfig)
    }
}

fun Set<AppTpConfig>.isIpv6Enabled(): Boolean {
    return this.firstNotNullOfOrNull { model ->
        return if (model is AppTpConfig.Ipv6Config) {
            model.isEnabled
        } else {
            false
        }
    } ?: false
}

fun Set<AppTpConfig>.isBadHealthMitigationEnabled(): Boolean {
    return this.firstNotNullOfOrNull { model ->
        return if (model is AppTpConfig.BadHealthMitigationConfig) {
            model.isEnabled
        } else {
            false
        }
    } ?: false
}

fun Set<AppTpConfig>.isPrivateDnsSupportEnabled(): Boolean {
    return this.firstNotNullOfOrNull { model ->
        return if (model is AppTpConfig.PrivateDnsConfig) {
            model.isEnabled
        } else {
            false
        }
    } ?: false
}

fun Set<AppTpConfig>.isNetworkSwitchingHandlingEnabled(): Boolean {
    return this.firstNotNullOfOrNull { model ->
        return if (model is AppTpConfig.NetworkSwitchHandlingConfig) {
            model.isEnabled
        } else {
            false
        }
    } ?: false
}

inline fun AppTpFeatureConfig.edit(
    action: AppTpFeatureConfig.Editor.() -> Unit
) {
    val editor = edit()
    action(editor)
}

sealed class AppTpConfig(open val isEnabled: Boolean) {
    data class Ipv6Config(override val isEnabled: Boolean) : AppTpConfig(isEnabled)
    data class BadHealthMitigationConfig(override val isEnabled: Boolean) : AppTpConfig(isEnabled)
    data class PrivateDnsConfig(override val isEnabled: Boolean) : AppTpConfig(isEnabled)
    data class NetworkSwitchHandlingConfig(override val isEnabled: Boolean) : AppTpConfig(isEnabled)
}
