/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.remote.FeaturesRequestHeaderStore
import com.duckduckgo.app.browser.trafficquality.remote.RealAndroidFeaturesHeaderProvider
import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersion
import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersionFeatures
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.Gpc
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidFeaturesHeaderProviderTest {

    private val currentVersion = 5210000
    private val anotherVersion = 5220000

    private val appBuildConfig: AppBuildConfig = mock()
    private val featuresRequestHeaderStore: FeaturesRequestHeaderStore = mock()
    private val mockAutoconsent: Autoconsent = mock()
    private val mockGpc: Gpc = mock()
    private val mockAppTrackingProtection: AppTrackingProtection = mock()
    private val mockNetworkProtectionState: NetworkProtectionState = mock()

    private lateinit var testee: RealAndroidFeaturesHeaderProvider

    @Before
    fun setup() {
        testee = RealAndroidFeaturesHeaderProvider(
            appBuildConfig,
            featuresRequestHeaderStore,
            mockAutoconsent,
            mockGpc,
            mockAppTrackingProtection,
            mockNetworkProtectionState,
        )

        whenever(appBuildConfig.versionCode).thenReturn(currentVersion)
        givenBuildDateDaysAgo(6)
    }

    @Test
    fun whenNoVersionsPresentThenNoValueProvided() {
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(emptyList())

        val result = testee.provide()
        assertNull(result)
    }

    @Test
    fun whenCurrentVersionNotPresentThenNoValueProvided() {
        val noFeaturesEnabled = TrafficQualityAppVersion(anotherVersion, 5, 5, noFeaturesEnabled())
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(noFeaturesEnabled))

        val result = testee.provide()
        assertNull(result)
    }

    @Test
    fun whenCurrentVersionPresentAndNoFeaturesEnabledThenNoValueProvided() {
        val noFeaturesEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, noFeaturesEnabled())
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(noFeaturesEnabled))

        val result = testee.provide()
        assertNull(result)
    }

    @Test
    fun whenCurrentVersionPresentAndGPCFeatureEnabledAndGPCDisabledThenValueProvided() {
        whenever(mockGpc.isEnabled()).thenReturn(false)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "gpc_enabled=false")
    }

    @Test
    fun whenCurrentVersionPresentAndGPCFeatureEnabledAndGPCEnabledThenValueProvided() {
        whenever(mockGpc.isEnabled()).thenReturn(true)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "gpc_enabled=true")
    }

    @Test
    fun whenCurrentVersionPresentAndCPMFeatureEnabledAndCPMDisabledThenValueProvided() {
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(false)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(cpm = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "cpm_enabled=false")
    }

    @Test
    fun whenCurrentVersionPresentAndCPMFeatureEnabledAndCPMEnabledThenValueProvided() {
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(true)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(cpm = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "cpm_enabled=true")
    }

    @Test
    fun whenCurrentVersionPresentAndAppTPFeatureEnabledAndAppTPDisabledThenValueProvided() = runTest {
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(false)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(appTP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "atp_enabled=false")
    }

    @Test
    fun whenCurrentVersionPresentAndAppTPFeatureEnabledAndAppTPEnabledThenValueProvided() = runTest {
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(appTP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "atp_enabled=true")
    }

    @Test
    fun whenCurrentVersionPresentAndVPNFeatureEnabledAndVPNDisabledThenValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(false)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(netP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "vpn_enabled=false")
    }

    @Test
    fun whenCurrentVersionPresentAndVPNFeatureEnabledAndVPNEnabledThenValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(true)
        val gpcEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(netP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(gpcEnabled))

        val result = testee.provide()
        assertTrue(result == "vpn_enabled=true")
    }

    @Test
    fun whenCurrentVersionPresentAndSeveralFeaturesEnabledThenOnlyOneValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(true)
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(true)
        val features = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true, cpm = true, appTP = true, netP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(features))

        val result = testee.provide()
        assertTrue(result == "vpn_enabled=true" || result == "cpm_enabled=true" || result == "gpc_enabled=true" || result == "atp_enabled=true")
    }

    @Test
    fun whenItsTooEarlyToLogThenNoValueProvided() = runTest {
        givenBuildDateDaysAgo(1)
        val features = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true, cpm = true, appTP = true, netP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(features))

        val result = testee.provide()
        assertNull(result)
    }

    @Test
    fun whenItsTooLateToLogThenNoValueProvided() = runTest {
        givenBuildDateDaysAgo(20)
        val features = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true, cpm = true, appTP = true, netP = true))
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(features))

        val result = testee.provide()
        assertNull(result)
    }

    private fun noFeaturesEnabled(): TrafficQualityAppVersionFeatures {
        return TrafficQualityAppVersionFeatures(gpc = false, cpm = false, appTP = false, netP = false)
    }

    private fun featuresEnabled(
        gpc: Boolean = false,
        cpm: Boolean = false,
        appTP: Boolean = false,
        netP: Boolean = false,
    ): TrafficQualityAppVersionFeatures {
        return TrafficQualityAppVersionFeatures(gpc = gpc, cpm = cpm, appTP = appTP, netP = netP)
    }

    private fun givenBuildDateDaysAgo(days: Long) {
        val daysAgo = LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(appBuildConfig.buildDateTimeMillis).thenReturn(daysAgo)
    }
}
