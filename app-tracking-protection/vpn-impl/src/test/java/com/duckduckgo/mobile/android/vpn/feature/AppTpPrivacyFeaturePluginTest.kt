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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.internal.verification.VerificationModeFactory.times
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppTpPrivacyFeaturePluginTest {

    private lateinit var featurePlugin: AppTpPrivacyFeaturePlugin

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val plugins: HashSet<AppTpSettingPlugin> = hashSetOf<AppTpSettingPlugin>()

    private val moshi: Moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
    private val configAdapter: JsonAdapter<JsonAppTpFeatureConfig> = moshi.adapter(JsonAppTpFeatureConfig::class.java)
    private val settingAdapter: JsonAdapter<SimpleSettingJsonModel> = moshi.adapter(SimpleSettingJsonModel::class.java)

    private val testSettings = HashMap<String, JSONObject?>()
    private val testSetting = SimpleSettingJsonModel("enabled")
    private val testSettingName = "test"
    private val testConfig = JsonAppTpFeatureConfig("enabled", null, testSettings, "2a58bdb505a789fcefe2bc24fb9ead16")

    private val mockPlugin: AppTpSettingPlugin = mock()

    @Before
    fun setup() {
        whenever(mockPlugin.settingName).thenReturn(SettingName { testSettingName })

        plugins.add(mockPlugin)
        featurePlugin = AppTpPrivacyFeaturePlugin(plugins, context)
        testSettings[testSettingName] = JSONObject(settingAdapter.toJson(testSetting))
    }

    @Test
    fun whenNoNameReturnsFalse() {
        assertFalse(featurePlugin.store("", ""))
    }

    @Test
    fun whenNoJSONReturnsFalse() {
        assertFalse(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, ""))
    }

    @Test
    fun whenPluginNameNotMatchingDontStore() {
        whenever(mockPlugin.settingName).thenReturn(SettingName { "unmatched" })
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig)))

        verify(mockPlugin, never()).store(any(), any())
    }

    @Test
    fun whenHashIsTheSameSkipStore() {
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig)))
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig)))

        verify(mockPlugin, times(1)).store(mockPlugin.settingName, settingAdapter.toJson(testSetting))
    }

    @Test
    fun whenHashChangesStore() {
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig)))

        val testConfig2 = JsonAppTpFeatureConfig("enabled", null, testSettings, "b123f6ba3f75a565f14b2350e9c2751c")
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig2)))

        verify(mockPlugin, times(2)).store(mockPlugin.settingName, settingAdapter.toJson(testSetting))
    }

    @Test
    fun whenHashMissingAlwaysStore() {
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig)))

        val testConfig2 = JsonAppTpFeatureConfig("enabled", null, testSettings, null)
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig2)))
        assertTrue(featurePlugin.store(AppTpFeatureName.AppTrackerProtection.value, configAdapter.toJson(testConfig2)))

        verify(mockPlugin, times(3)).store(mockPlugin.settingName, settingAdapter.toJson(testSetting))
    }

    private data class SimpleSettingJsonModel(val state: String)
}
