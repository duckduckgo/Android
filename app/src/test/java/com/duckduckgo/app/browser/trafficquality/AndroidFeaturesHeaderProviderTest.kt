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

import com.duckduckgo.app.browser.trafficquality.remote.RealAndroidFeaturesHeaderProvider
import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersion
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidFeaturesHeaderProviderTest {

    private val currentVersion = 5210000

    private val mockAutoconsent: Autoconsent = mock()
    private val mockGpc: Gpc = mock()
    private val mockAppTrackingProtection: AppTrackingProtection = mock()
    private val mockNetworkProtectionState: NetworkProtectionState = mock()

    private lateinit var testee: RealAndroidFeaturesHeaderProvider

    @Before
    fun setup() {
        testee = RealAndroidFeaturesHeaderProvider(
            mockAutoconsent,
            mockGpc,
            mockAppTrackingProtection,
            mockNetworkProtectionState,
        )
    }

    @Test
    fun whenNoFeaturesEnabledThenNoValueProvided() {
        val noFeaturesEnabled = TrafficQualityAppVersion(currentVersion, 5, 5, noFeaturesEnabled())

        val result = testee.provide(noFeaturesEnabled)

        assertNull(result)
    }

    @Test
    fun whenGPCFeatureEnabledAndGPCDisabledThenValueProvided() {
        whenever(mockGpc.isEnabled()).thenReturn(false)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true))

        val result = testee.provide(config)

        assertTrue(result == "gpc_enabled=false")
    }

    @Test
    fun whenGPCFeatureEnabledAndGPCEnabledThenValueProvided() {
        whenever(mockGpc.isEnabled()).thenReturn(true)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true))

        val result = testee.provide(config)

        assertTrue(result == "gpc_enabled=true")
    }

    @Test
    fun whenCPMFeatureEnabledAndCPMDisabledThenValueProvided() {
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(false)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(cpm = true))

        val result = testee.provide(config)

        assertTrue(result == "cpm_enabled=false")
    }

    @Test
    fun whenCPMFeatureEnabledAndCPMEnabledThenValueProvided() {
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(true)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(cpm = true))

        val result = testee.provide(config)

        assertTrue(result == "cpm_enabled=true")
    }

    @Test
    fun whenAppTPFeatureEnabledAndAppTPDisabledThenValueProvided() = runTest {
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(false)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(appTP = true))

        val result = testee.provide(config)

        assertTrue(result == "atp_enabled=false")
    }

    @Test
    fun whenAppTPFeatureEnabledAndAppTPEnabledThenValueProvided() = runTest {
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(appTP = true))

        val result = testee.provide(config)

        assertTrue(result == "atp_enabled=true")
    }

    @Test
    fun whenVPNFeatureEnabledAndVPNDisabledThenValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(false)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(netP = true))

        val result = testee.provide(config)

        assertTrue(result == "vpn_enabled=false")
    }

    @Test
    fun whenVPNFeatureEnabledAndVPNEnabledThenValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(true)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(netP = true))

        val result = testee.provide(config)

        assertTrue(result == "vpn_enabled=true")
    }

    @Test
    fun whenSeveralFeaturesEnabledThenOnlyOneValueProvided() = runTest {
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(true)
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(true)
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(true)
        val config = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled(gpc = true, cpm = true, appTP = true, netP = true))

        val result = testee.provide(config)
        assertTrue(result == "vpn_enabled=true" || result == "cpm_enabled=true" || result == "gpc_enabled=true" || result == "atp_enabled=true")
    }
}
