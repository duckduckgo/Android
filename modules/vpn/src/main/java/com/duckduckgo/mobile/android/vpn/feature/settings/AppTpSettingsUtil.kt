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

package com.duckduckgo.mobile.android.vpn.feature.settings

import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting

/**
 * Convenience method to get the [AppTpSetting] from its [String] value
 */
internal fun appTpSettingValueOf(value: String): AppTpSetting? {
    return AppTpSetting.values().find { it.value == value }
}
