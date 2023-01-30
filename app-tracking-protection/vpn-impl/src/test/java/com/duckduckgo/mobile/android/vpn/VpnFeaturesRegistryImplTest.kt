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

package com.duckduckgo.mobile.android.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VpnFeaturesRegistryImplTest {

    private val sharedPreferencesProvider: VpnSharedPreferencesProvider = mock()
    private lateinit var vpnServiceWrapper: TestVpnServiceWrapper

    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Before
    fun setup() {
        val prefs = InMemorySharedPreferences()
        vpnServiceWrapper = TestVpnServiceWrapper()

        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.mobile.android.vpn.feature.registry.v1"), eq(true), eq(false)),
        ).thenReturn(prefs)

        vpnFeaturesRegistry = VpnFeaturesRegistryImpl(vpnServiceWrapper, sharedPreferencesProvider)
    }

    @Test
    fun whenRegisterFeatureThenRestartVpnService() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)

        assertEquals(0, vpnServiceWrapper.restartCount)
        assertTrue(vpnServiceWrapper.isServiceRunning())
    }

    @Test
    fun whenFeaturesAreRegisteredThenVpnIsStarted() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.BAR)

        assertTrue(vpnFeaturesRegistry.isFeatureRegistered(TestVpnFeatures.FOO))
        assertTrue(vpnFeaturesRegistry.isFeatureRegistered(TestVpnFeatures.BAR))
        assertTrue(vpnServiceWrapper.isServiceRunning())
    }

    @Test
    fun whenIsFeatureRegisteredAndFeaturesAreRegisteredAndVpnIsRunningThenReturnTrue() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.BAR)
        vpnServiceWrapper.startService()

        assertTrue(vpnFeaturesRegistry.isFeatureRegistered(TestVpnFeatures.FOO))
        assertTrue(vpnFeaturesRegistry.isFeatureRegistered(TestVpnFeatures.BAR))
    }

    @Test
    fun whenIsAnyFeatureRegisteredAndFeaturesAreNotRegisteredThenReturnFalse() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
    }

    @Test
    fun whenIsAnyFeatureRegisteredAndFeaturesAreRegisteredThenReturnTrue() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)

        assertTrue(vpnFeaturesRegistry.isAnyFeatureRegistered())
        assertTrue(vpnServiceWrapper.isServiceRunning())
    }

    @Test
    fun whenIsAnyFeatureRegisteredAndFeaturesAreRegisteredAndVpnDisabledThenReturnFalse() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnServiceWrapper.stopService()

        assertFalse(vpnFeaturesRegistry.isAnyFeatureRegistered())
        assertFalse(vpnServiceWrapper.isServiceRunning())
    }

    @Test
    fun whenUnregisterFeatureThenFeatureIsUnregistered() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.FOO)

        assertFalse(vpnFeaturesRegistry.isFeatureRegistered(TestVpnFeatures.FOO))
    }

    @Test
    fun whenUnregisterLastFeatureThenVpnIsNotRunning() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.BAR)
        vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.BAR)

        assertFalse(vpnServiceWrapper.isServiceRunning())
    }

    @Test
    fun whenUnregisterFeatureIfFeaturesStillRegisteredThenRestartVpnService() {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO) // no restart
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.BAR) // restart
        vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.FOO) // restart

        assertEquals(2, vpnServiceWrapper.restartCount)
    }

    @Test
    fun whenRefreshUnregisteredFeatureThenDoNotRestartVpn() = runTest {
        vpnFeaturesRegistry.refreshFeature(TestVpnFeatures.FOO)

        assertEquals(0, vpnServiceWrapper.restartCount)
    }

    @Test
    fun whenRefreshRegisteredFeatureThenRestartVpn() = runTest {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.refreshFeature(TestVpnFeatures.FOO)

        assertTrue(vpnServiceWrapper.isServiceRunning())
        assertEquals(1, vpnServiceWrapper.restartCount)
    }

    @Test
    fun whenRegisterFeatureThenStartVpn() = runTest {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)

        assertTrue(vpnServiceWrapper.isServiceRunning())
        assertEquals(0, vpnServiceWrapper.restartCount)
    }

    @Test
    fun whenRegisterFeatureThenEmitChange() = runTest {
        vpnFeaturesRegistry.registryChanges().test {
            vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
            vpnFeaturesRegistry.registerFeature(TestVpnFeatures.BAR)
            assertEquals(TestVpnFeatures.FOO.featureName to true, awaitItem())
            assertEquals(TestVpnFeatures.BAR.featureName to true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUnregisterNotRegisterFeatureThenNoEmission() = runTest {
        vpnFeaturesRegistry.registryChanges().test {
            vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.FOO)
            expectNoEvents()
        }
    }

    @Test
    fun whenUnregisterRegisteredFeatureThenEmitChange() = runTest {
        vpnFeaturesRegistry.registerFeature(TestVpnFeatures.FOO)
        vpnFeaturesRegistry.registryChanges().test {
            vpnFeaturesRegistry.unregisterFeature(TestVpnFeatures.FOO)
            assertEquals(TestVpnFeatures.FOO.featureName to true, awaitItem())
            assertEquals(TestVpnFeatures.FOO.featureName to false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private enum class TestVpnFeatures(override val featureName: String) : VpnFeature {
        FOO("FOO"),
        BAR("BAR"),
    }

    private class TestVpnServiceWrapper : VpnServiceWrapper(InstrumentationRegistry.getInstrumentation().context) {
        private var isRunning = false
        var restartCount = 0

        override fun restartVpnService(forceRestart: Boolean) {
            if (isServiceRunning()) {
                restartCount++
            } else if (forceRestart) {
                startService()
            }
        }

        override fun stopService() {
            isRunning = false
        }

        override fun startService() {
            isRunning = true
        }

        override fun isServiceRunning(): Boolean {
            return isRunning
        }
    }
}
