/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.internalfeedback

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.startup.metrics.ProcessTimeProvider
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyConfigData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RealInternalFeedbackDeviceInfoProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockDeviceInfo: DeviceInfo = mock()
    private val mockInternalTestUserChecker: InternalTestUserChecker = mock()
    private val mockWebViewVersionProvider: WebViewVersionProvider = mock()
    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private val mockNetworkProtectionState: NetworkProtectionState = mock()
    private val mockPrivacyConfig: PrivacyConfig = mock()
    private val mockFeatureTogglesInventory: FeatureTogglesInventory = mock()
    private val mockTabRepository: TabRepository = mock()
    private val mockProcessTimeProvider: ProcessTimeProvider = mock()

    private val provider = RealInternalFeedbackDeviceInfoProvider(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        appBuildConfig = mockAppBuildConfig,
        deviceInfo = mockDeviceInfo,
        internalTestUserChecker = mockInternalTestUserChecker,
        webViewVersionProvider = mockWebViewVersionProvider,
        statisticsDataStore = mockStatisticsDataStore,
        variantManager = mockVariantManager,
        networkProtectionState = mockNetworkProtectionState,
        privacyConfig = mockPrivacyConfig,
        featureTogglesInventory = mockFeatureTogglesInventory,
        tabRepository = mockTabRepository,
        processTimeProvider = mockProcessTimeProvider,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setUp() = runTest {
        whenever(mockAppBuildConfig.versionName).thenReturn("5.296.0")
        whenever(mockAppBuildConfig.versionCode).thenReturn(52960000)
        whenever(mockAppBuildConfig.model).thenReturn("Pixel 9")
        whenever(mockAppBuildConfig.manufacturer).thenReturn("Google")
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        whenever(mockAppBuildConfig.buildType).thenReturn("release")
        whenever(mockDeviceInfo.formFactor()).thenReturn(FormFactor.PHONE)
        whenever(mockInternalTestUserChecker.isInternalTestUser).thenReturn(true)
        whenever(mockWebViewVersionProvider.getFullVersion()).thenReturn("140.0.7339.51")
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("v512-1"))
        whenever(mockVariantManager.getVariantKey()).thenReturn("ma")
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(false)
        whenever(mockPrivacyConfig.privacyConfigData()).thenReturn(PrivacyConfigData(version = "1787840040634", eTag = "etag"))
        whenever(mockFeatureTogglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(mockTabRepository.getOpenTabCount()).thenReturn(3)
        whenever(mockProcessTimeProvider.startupTimeMs()).thenReturn(0L)
        whenever(mockProcessTimeProvider.currentUptimeMs()).thenReturn(MINUTES_83_MS)
    }

    @Test
    fun whenDeviceInfoRequestedThenRequiredFieldsArePresent() = runTest {
        val deviceInfo = provider.getDeviceInfo().getOrThrow()

        assertEquals("android", deviceInfo.getString("platform"))
        assertEquals("5.296.0", deviceInfo.getString("appVersion"))
        assertEquals("Android", deviceInfo.getString("osName"))
        assertEquals(Build.VERSION.RELEASE, deviceInfo.getString("osVersion"))
        assertEquals("Pixel 9", deviceInfo.getString("deviceModel"))
        assertEquals("Google", deviceInfo.getString("deviceManufacturer"))
    }

    @Test
    fun whenDeviceInfoRequestedThenOptionalFieldsArePresent() = runTest {
        val deviceInfo = provider.getDeviceInfo().getOrThrow()

        assertEquals("52960000", deviceInfo.getString("appBuild"))
        assertEquals("mobile", deviceInfo.getString("formFactor"))
        assertEquals("en-US", deviceInfo.getString("locale"))
        assertEquals("Play (release)", deviceInfo.getString("channel"))
        assertTrue(deviceInfo.getBoolean("isInternalUser"))
        assertEquals("140.0.7339.51", deviceInfo.getString("webViewVersion"))
        assertEquals("v512-1ma", deviceInfo.getString("atb"))
        assertFalse(deviceInfo.getBoolean("vpnOn"))
        assertEquals("1787840040634", deviceInfo.getString("remoteConfigVersion"))
    }

    @Test
    fun whenDeviceInfoRequestedThenFieldsWithoutAndroidSourceAreOmitted() = runTest {
        val deviceInfo = provider.getDeviceInfo().getOrThrow()

        assertFalse(deviceInfo.has("debugFlags"))
        assertFalse(deviceInfo.has("remoteConfigEtag"))
    }

    @Test
    fun whenTabletThenFormFactorIsTablet() = runTest {
        whenever(mockDeviceInfo.formFactor()).thenReturn(FormFactor.TABLET)

        assertEquals("tablet", provider.getDeviceInfo().getOrThrow().getString("formFactor"))
    }

    @Test
    fun whenInternalDebugBuildThenChannelIncludesFlavorAndBuildType() = runTest {
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(mockAppBuildConfig.buildType).thenReturn("debug")

        assertEquals("Internal (debug)", provider.getDeviceInfo().getOrThrow().getString("channel"))
    }

    @Test
    fun whenFdroidBuildThenChannelIsFdroid() = runTest {
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.FDROID)

        assertEquals("F-Droid (release)", provider.getDeviceInfo().getOrThrow().getString("channel"))
    }

    @Test
    fun whenNoAtbThenAtbIsOmitted() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)

        assertFalse(provider.getDeviceInfo().getOrThrow().has("atb"))
    }

    @Test
    fun whenGettingAValueFailsThenReturnsFailure() = runTest {
        whenever(mockTabRepository.getOpenTabCount()).thenThrow(IllegalStateException())

        assertTrue(provider.getDeviceInfo().isFailure)
    }

    @Test
    fun whenActiveExperimentsThenTheyAreIncludedWithNameAndCohort() = runTest {
        val experimentA = experimentToggle(featureName = "experimentA", cohort = "treatment")
        val experimentB = experimentToggle(featureName = "experimentB", cohort = "control")
        whenever(mockFeatureTogglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(experimentA, experimentB))

        val experiments = provider.getDeviceInfo().getOrThrow().getJSONArray("activeExperiments")

        assertEquals(2, experiments.length())
        assertEquals("experimentA", experiments.getJSONObject(0).getString("name"))
        assertEquals("treatment", experiments.getJSONObject(0).getString("cohort"))
        assertEquals("experimentB", experiments.getJSONObject(1).getString("name"))
        assertEquals("control", experiments.getJSONObject(1).getString("cohort"))
    }

    @Test
    fun whenNoActiveExperimentsThenActiveExperimentsIsOmitted() = runTest {
        assertFalse(provider.getDeviceInfo().getOrThrow().has("activeExperiments"))
    }

    @Test
    fun whenDeviceInfoRequestedThenDiagnosticsArePresent() = runTest {
        val diagnostics = provider.getDeviceInfo().getOrThrow().getJSONObject("diagnostics")

        assertEquals("3", diagnostics.getString("Tabs"))
        assertEquals("1h 23m", diagnostics.getString("Session"))
        assertTrue(diagnostics.getString("Memory").contains("low memory:"))
        assertTrue(diagnostics.getString("Disk").endsWith("free"))
    }

    @Test
    fun whenSessionShorterThanAMinuteThenSessionIsZero() = runTest {
        whenever(mockProcessTimeProvider.currentUptimeMs()).thenReturn(SECONDS_59_MS)

        assertEquals("0s", provider.getDeviceInfo().getOrThrow().getJSONObject("diagnostics").getString("Session"))
    }

    private suspend fun experimentToggle(featureName: String, cohort: String): Toggle {
        val toggle: Toggle = mock()
        whenever(toggle.featureName()).thenReturn(Toggle.FeatureName(parentName = null, name = featureName))
        whenever(toggle.getCohort()).thenReturn(Toggle.State.Cohort(name = cohort, weight = 1))
        return toggle
    }

    private companion object {
        const val MINUTES_83_MS = 83 * 60 * 1000L
        const val SECONDS_59_MS = 59 * 1000L
    }
}
