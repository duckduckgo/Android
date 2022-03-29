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

import kotlin.reflect.KClass

/**
 * [AppTpFeatureConfig] returns the configuration of the AppTP features.
 * The configuration is set from either remote config, or the app AppTp internal settings
 */
interface AppTpFeatureConfig {
    /**
     * @param type this is the [AppTpConfig] config type
     * @return the config if stored or null if not found
     *
     * usage:
     * ```kotlin
     *   val config = appTpFeatureConfig.get(AppTpConfig.Ipv6Config::class)
     * ```
     */
    fun <T : AppTpConfig> get(type: KClass<T>): T?

    /**
     * @return the [Editor] instance required to modify the configurations
     */
    fun edit(): Editor

    interface Editor {
        /**
         *
         * usage:
         * ```kotlin
         *   val config = appTpFeatureConfig.edit {
         *      put(AppTpConfig.Ipv6Config(isEnabled = true))
         *   }
         * ```
         */
        fun <T : AppTpConfig> put(config: T)
    }
}

/**
 * Convenience method
 * @return `true` if the feature is present and enabled, else `false`
 */
fun AppTpConfig?.isEnabled(): Boolean {
    return this?.isEnabled ?: false
}

/**
 * Convenience extension function to use lambda block
 *
 * ```kotlin
 *   val config = appTpFeatureConfig.edit {
 *      ...
 *   }
 * ```
 */
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
