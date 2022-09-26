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
import com.duckduckgo.mobile.android.vpn.feature.FakeAppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtectGamesSettingPluginTest {

    private val appTpFeatureConfig = FakeAppTpFeatureConfig()
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

    private lateinit var featureConfig: ProtectGamesSettingPlugin

    @Before
    fun setup() {
        featureConfig = ProtectGamesSettingPlugin(appTpFeatureConfig)
    }

    @Test
    fun whenStoreWithEnabledSettingThenStoreAndReturnTrue() {
        assertTrue(featureConfig.store(featureConfig.settingName, jsonEnabled))
        assertTrue(appTpFeatureConfig.isProtectGamesEnabled())
    }

    @Test
    fun whenStoreWithDisabledSettingThenStoreAndReturnFalse() {
        assertTrue(featureConfig.store(featureConfig.settingName, jsonDisabled))
        assertFalse(appTpFeatureConfig.isProtectGamesEnabled())
    }

    @Test
    fun whenStoreWithWrongSettingEnabledThenSkipStoreAndReturnFalse() {
        assertFalse(featureConfig.store(SettingName { "wrongSetting" }, jsonEnabled))
        assertFalse(appTpFeatureConfig.isProtectGamesEnabled())
    }

    @Test
    fun whenStoreWithWrongSettingDisabledThenSkipStoreAndReturnTrue() {
        appTpFeatureConfig.setEnabled(featureConfig.settingName, true)

        assertFalse(featureConfig.store(SettingName { "wrongSetting" }, jsonDisabled))
        assertTrue(appTpFeatureConfig.isProtectGamesEnabled())
    }

    @Test
    fun whenStoreWithEmptyJsonThenSkipStoreAndReturnTrue() {
        appTpFeatureConfig.setEnabled(featureConfig.settingName, true)

        assertTrue(featureConfig.store(featureConfig.settingName, "{}"))
        assertTrue(appTpFeatureConfig.isProtectGamesEnabled())
    }

    @Test
    fun whenStoreWithInvalidJsonThenSkipStoreAndReturnTrue() {
        appTpFeatureConfig.setEnabled(featureConfig.settingName, true)

        assertTrue(featureConfig.store(featureConfig.settingName, ""))
        assertTrue(appTpFeatureConfig.isProtectGamesEnabled())
    }

    private fun AppTpFeatureConfig.isProtectGamesEnabled(): Boolean {
        return isEnabled(featureConfig.settingName)
    }
}
