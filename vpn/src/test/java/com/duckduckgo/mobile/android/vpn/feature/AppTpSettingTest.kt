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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTpSettingTest {

    @Test
    fun whenAppTpSettingThenShouldHaveCorrectValues() {
        AppTpSetting.values().forEach { setting ->
            when (setting) {
                AppTpSetting.BadHealthMitigation -> assertTrue(setting.defaultValue)
                AppTpSetting.Ipv6Support -> assertFalse(setting.defaultValue)
                AppTpSetting.PrivateDnsSupport -> assertFalse(setting.defaultValue)
                AppTpSetting.NetworkSwitchHandling -> assertFalse(setting.defaultValue)
            }
        }
    }

    @Test
    fun whenSettingNameCreatedThenDefaultValueIsFalse() {
        assertFalse(SettingName { "setting" }.defaultValue)
    }
}
