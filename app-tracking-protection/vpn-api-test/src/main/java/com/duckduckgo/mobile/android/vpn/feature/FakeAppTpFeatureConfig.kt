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

class FakeAppTpFeatureConfig : AppTpFeatureConfig, AppTpFeatureConfig.Editor {
    private val settings = mutableMapOf<String, Setting>()

    override fun isEnabled(settingName: SettingName): Boolean {
        return settings[settingName.value]?.isEnabled ?: settingName.defaultValue
    }

    override fun edit() = this

    override fun setEnabled(settingName: SettingName, enabled: Boolean, isManualOverride: Boolean) {
        settings[settingName.value] = Setting(settingName, enabled, isManualOverride)
    }

    private data class Setting(val name: SettingName, val isEnabled: Boolean, val isManualOverride: Boolean)
}
