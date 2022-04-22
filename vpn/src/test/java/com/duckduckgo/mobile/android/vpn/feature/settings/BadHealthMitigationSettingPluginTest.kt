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
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class BadHealthMitigationSettingPluginTest {

    private lateinit var featureConfig: BadHealthMitigationSettingPlugin
    private val appTpFeatureConfig: AppTpFeatureConfig = mock()
    private val appTpFeatureConfigEditor: AppTpFeatureConfig.Editor = mock()
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
        whenever(appTpFeatureConfig.edit()).thenReturn(appTpFeatureConfigEditor)
        featureConfig = BadHealthMitigationSettingPlugin(appTpFeatureConfig)
    }

    @Test
    fun whenStoreWithCorrectSettingAndEnabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonEnabled)

        verify(appTpFeatureConfigEditor).setEnabled(AppTpSetting.BadHealthMitigation, enabled = true, isManualOverride = false)
        assertTrue(result)
    }

    @Test
    fun whenStoreWithCorrectSettingAndDisabledThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonDisabled)

        verify(appTpFeatureConfigEditor).setEnabled(AppTpSetting.BadHealthMitigation, enabled = false, isManualOverride = false)
        assertTrue(result)
    }

    @Test
    fun whenStoreWithIncorrectSettingThenDoNotStoreAndReturnFalse() {
        val settingName = SettingName { "wrongSettingName" }
        val result = featureConfig.store(settingName, jsonEnabled)

        verify(appTpFeatureConfigEditor, never()).setEnabled(any(), any(), any())
        assertFalse(result)
    }
}
