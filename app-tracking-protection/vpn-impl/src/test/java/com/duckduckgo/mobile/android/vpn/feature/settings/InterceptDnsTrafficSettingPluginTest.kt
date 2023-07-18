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

import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeAppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterceptDnsTrafficSettingPluginTest {

    private lateinit var featureConfig: InterceptDnsTrafficSettingPlugin
    private lateinit var appTpFeatureConfig: AppTpFeatureConfig
    private val jsonEnabled = """
        {
          "state": "enabled",
          "settings": {}
        }
    """.trimIndent()
    private val jsonDisabled = """
        {
          "state": "disabled",
          "settings": {}
        }
    """.trimIndent()

    @Before
    fun setup() {
        appTpFeatureConfig = FakeAppTpFeatureConfig()
        featureConfig = InterceptDnsTrafficSettingPlugin(appTpFeatureConfig)
    }

    @Test
    fun whenStoreWithCorrectSettingAndEnabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonEnabled)

        assertTrue(result)
        assertTrue(appTpFeatureConfig.isEnabled(AppTpSetting.InterceptDnsRequests))
    }

    @Test
    fun whenStoreWithCorrectSettingAndDisabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonDisabled)

        assertTrue(result)
        assertFalse(appTpFeatureConfig.isEnabled(AppTpSetting.InterceptDnsRequests))
    }

    @Test
    fun whenStoreWithIncorrectSettingThenDoNotStoreAndReturnFalse() {
        val settingName = SettingName { "wrongSettingName" }
        val result = featureConfig.store(settingName, jsonEnabled)

        assertFalse(result)
        assertTrue(appTpFeatureConfig.isEnabled(AppTpSetting.InterceptDnsRequests))
    }
}
