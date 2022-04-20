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

enum class AppTpSetting(override val value: String, override val defaultValue: Boolean = false) : SettingName {
    BadHealthMitigation("badHealthMitigation", defaultValue = true),
    Ipv6Support("ipv6Support"),
    PrivateDnsSupport("privateDnsSupport"),
    NetworkSwitchHandling("networkSwitchSupport"),
    SetActiveNetworkDns("setActiveNetworkDns"),
    VpnDdgBrowserTraffic("vpnDdgBrowserTraffic"),
    AlwaysSetDNS("alwaysSetDNS"),
}

interface SettingName {
    val value: String
    val defaultValue: Boolean
}

/**
 * Fake constructor for [SettingName] from the passed in [block] lambda
 * instead of using the anonymous `object : FeatureName` syntax.
 *
 * Usage:
 *
 * ```kotlin
 * val name = SettingName {
 *
 * }
 * ```
 */
fun SettingName(block: () -> String): SettingName {
    return object : SettingName {
        override val value: String
            get() = block()
        override val defaultValue: Boolean = false
    }
}
