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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.dao.AppHealthTriggersDao
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import com.duckduckgo.mobile.android.vpn.model.HealthTriggerEntity
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class BadHealthMitigationSettingPluginTest {

    private lateinit var featureConfig: BadHealthMitigationSettingPlugin
    private val appTpFeatureConfig: AppTpFeatureConfig = mock()
    private val appTpFeatureConfigEditor: AppTpFeatureConfig.Editor = mock()
    private val appHealthDatabase: AppHealthDatabase = mock()
    private val thresholdsTable = FakeAppHealthTriggersDao()
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

    private val jsonThresholds = """
        {
          "state": "disabled",
          "settings": {
              "triggers": {
                "tunInputsQueueReadRate": {
                    "state": "enabled",
                    "threshold": 1
                },
                "noNetworkConnectivityAlert": {
                    "state": "enabled",
                    "threshold": 2
                },
                "socketReadExceptionAlerts": {
                    "state": "disabled"
                },
                "socketWriteExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 4
                },
                "socketConnectExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 5
                },
                "tunReadExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 6
                },
                "tunWriteExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 7
                },
                "tunWriteIOMemoryExceptionsAlerts": {
                    "state": "enabled",
                    "threshold": 8
                }
              }
          }
        }
    """.trimIndent()

    private val jsonThresholdsIncomplete = """
        {
          "state": "disabled",
          "settings": {
              "triggers": {
                "tunInputsQueueReadRate": {
                    "state": "enabled",
                    "threshold": 1
                },
                "socketReadExceptionAlerts": {
                    "state": "disabled"
                },
                "socketWriteExceptionAlerts": {
                    "state": "disabled"
                },
                "socketConnectExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 5
                },
                "tunReadExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 6
                },
                "tunWriteExceptionAlerts": {
                    "state": "enabled",
                    "threshold": 7
                },
                "tunWriteIOMemoryExceptionsAlerts": {
                    "state": "enabled",
                    "threshold": 8
                }
              }
          }
        }
    """.trimIndent()

    @Before
    fun setup() {
        whenever(appHealthDatabase.appHealthTriggersDao()).thenReturn(thresholdsTable)
        whenever(appTpFeatureConfig.edit()).thenReturn(appTpFeatureConfigEditor)
        featureConfig = BadHealthMitigationSettingPlugin(appTpFeatureConfig, appHealthDatabase)
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

    @Test
    fun whenStoreWithThresholdSettingsThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonThresholds)

        val triggers = thresholdsTable.triggers()
        assertEquals(8, triggers.size)
        assertEquals(listOf(HealthTriggerEntity(name = "socketReadExceptionAlerts", enabled = false)), triggers.filter { !it.enabled })
        assertTrue(result)
    }

    @Test
    fun whenStoreWithIncompleteThresholdSettingsThenStoreAndReturnTrue() {
        val result = featureConfig.store(featureConfig.settingName, jsonThresholdsIncomplete)

        val triggers = thresholdsTable.triggers()
        assertEquals(7, triggers.size)
        assertEquals(
            listOf(
                HealthTriggerEntity(name = "socketReadExceptionAlerts", enabled = false),
                HealthTriggerEntity(name = "socketWriteExceptionAlerts", enabled = false, threshold = null)
            ),
            triggers.filter { !it.enabled }
        )
        assertTrue(result)
    }

    private class FakeAppHealthTriggersDao : AppHealthTriggersDao {
        private val triggers = mutableMapOf<String, HealthTriggerEntity>()
        override fun insert(thresholds: HealthTriggerEntity) {
            triggers[thresholds.name] = thresholds
        }

        override fun insertAll(thresholds: List<HealthTriggerEntity>) {
            thresholds.forEach {
                triggers[it.name] = it
            }
        }

        override fun triggers(): List<HealthTriggerEntity> {
            return triggers.values.toList()
        }
    }
}
