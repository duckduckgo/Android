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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeAppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class CheckBlockingFunctionSettingPluginTest {

    private lateinit var featureConfig: CheckBlockingFunctionSettingPlugin
    private lateinit var appTpFeatureConfig: AppTpFeatureConfig
    private val appBuildConfig: AppBuildConfig = mock()
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
    private val jsonInternal = """
        {
          "state": "internal",
          "settings": {}
        }
    """.trimIndent()

    @Before
    fun setup() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        appTpFeatureConfig = FakeAppTpFeatureConfig()
        featureConfig = CheckBlockingFunctionSettingPlugin(appTpFeatureConfig, appBuildConfig)
    }

    @Test
    fun whenStoreWithCorrectSettingAndEnabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonEnabled)

        assertTrue(appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction))
        assertTrue(result)
    }

    @Test
    fun whenStoreWithCorrectSettingAndDisabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonDisabled)

        assertFalse(appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction))
        assertTrue(result)
    }

    @Test
    fun whenStoreWithCorrectSettingAndInternalNotInternalBuildThenStoreAndReturnTrue() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        val result = featureConfig.store(featureConfig.settingName, jsonInternal)

        assertFalse(appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction))
        assertTrue(result)
    }

    @Test
    fun whenStoreWithCorrectSettingAndInternalWithInternalBuildThenStoreAndReturnTrue() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        val result = featureConfig.store(featureConfig.settingName, jsonInternal)

        assertTrue(appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction))
        assertTrue(result)
    }

    @Test
    fun whenStoreWithIncorrectSettingThenDoNotStoreAndReturnFalse() {
        val settingName = SettingName { "wrongSettingName" }
        val result = featureConfig.store(settingName, jsonEnabled)

        assertFalse(appTpFeatureConfig.isEnabled(AppTpSetting.CheckBlockingFunction))
        assertFalse(result)
    }
}
