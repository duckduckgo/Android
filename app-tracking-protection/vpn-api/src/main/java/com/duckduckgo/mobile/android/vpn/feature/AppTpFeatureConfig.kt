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
    /**
     * @param settingName the [SettingName]
     * @return `true` if the setting is enabled, else `false`
     *
     * usage:
     * ```kotlin
     *   val enabled = appTpFeatureConfig.isEnabled(settingName)
     * ```
     */
    fun isEnabled(settingName: SettingName): Boolean

    /**
     * @return the [Editor] instance required to modify the configurations
     */
    fun edit(): Editor

    interface Editor {
        /**
         * @param settingName the name of the setting to set
         * @param enabled `true` to set the setting enabled, `false` otherwise
         * @param isManualOverride set to `true` to signal that this has been set manually by the user
         *
         * usage:
         * ```kotlin
         *   val config = appTpFeatureConfig.edit {
         *      setEnabled(settingName, true)
         *   }
         * ```
         */
        fun setEnabled(settingName: SettingName, enabled: Boolean, isManualOverride: Boolean = false)
    }
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
