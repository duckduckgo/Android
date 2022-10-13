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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.heartbeat.VpnServiceHeartbeatMonitor
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SharedPreferencesVpnStoreTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val sharedPreferencesProvider = mock<VpnSharedPreferencesProvider>()
    private val vpnHeartBeatDao = mock<VpnHeartBeatDao>()
    private val vpnFeaturesRegistry = mock<VpnFeaturesRegistry>()
    private val vpnServiceStateDao = mock<VpnServiceStateStatsDao>()

    private lateinit var sharedPreferencesVpnStore: SharedPreferencesVpnStore

    @Before
    fun setup() {
        val prefs = InMemorySharedPreferences()
        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.android.atp.onboarding.store"), eq(true), eq(true))
        ).thenReturn(prefs)

        sharedPreferencesVpnStore = SharedPreferencesVpnStore(
            sharedPreferencesProvider,
            coroutineRule.testDispatcherProvider,
            vpnHeartBeatDao,
            vpnFeaturesRegistry,
            vpnServiceStateDao,
        )
    }

    @Test
    fun whenOnboardingDidShowThenSetPreferenceValueToTrue() {
        assertFalse(sharedPreferencesVpnStore.didShowOnboarding())

        sharedPreferencesVpnStore.onboardingDidShow()

        assertTrue(sharedPreferencesVpnStore.didShowOnboarding())
    }

    @Test
    fun whenOnboardingDidNotShowThenSetPreferenceValueToFalse() {
        sharedPreferencesVpnStore.onboardingDidShow()

        assertTrue(sharedPreferencesVpnStore.didShowOnboarding())

        sharedPreferencesVpnStore.onboardingDidNotShow()

        assertFalse(sharedPreferencesVpnStore.didShowOnboarding())
    }

    @Test
    fun whenOnAppTpManuallyEnabledThenSetToTrueAndIncrementCounter() {
        sharedPreferencesVpnStore.onAppTPManuallyEnabled()
        sharedPreferencesVpnStore.onAppTPManuallyEnabled()

        assertEquals(2, sharedPreferencesVpnStore.getAppTPManuallyEnables())
    }

    @Test
    fun whenResetAppTpManuallyEnablesCounterThenResetCounter() {
        sharedPreferencesVpnStore.onAppTPManuallyEnabled()

        sharedPreferencesVpnStore.resetAppTPManuallyEnablesCounter()

        assertEquals(0, sharedPreferencesVpnStore.getAppTPManuallyEnables())
    }

    @Test
    fun whenUserAllowsShowPromoteAlwaysOnThenReturnDefaultValueTrue() {
        assertTrue(sharedPreferencesVpnStore.userAllowsShowPromoteAlwaysOn())
    }

    @Test
    fun whenIsAllaysOnEnabledThenReturnDefaultValueFalse() {
        assertFalse(sharedPreferencesVpnStore.isAlwaysOnEnabled())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun whenSetAlwaysOnThenSetAlwaysOnValue() = runTest {
        sharedPreferencesVpnStore.setAlwaysOn(true)

        assertTrue(sharedPreferencesVpnStore.isAlwaysOnEnabled())

        sharedPreferencesVpnStore.setAlwaysOn(false)

        assertFalse(sharedPreferencesVpnStore.isAlwaysOnEnabled())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun whenVpnLastDisabledByAndroidAndVpnKilledBySystemThenReturnTrue() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(null)
        whenever(vpnHeartBeatDao.hearBeats()).thenReturn(listOf(HeartBeatEntity(type = VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE)))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(false)

        assertTrue(sharedPreferencesVpnStore.vpnLastDisabledByAndroid())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun whenVpnLastDisabledByAndroidAndVpnUnexpectedlyDisabledThenReturnTrue() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = VpnServiceState.DISABLED)
        )

        assertTrue(sharedPreferencesVpnStore.vpnLastDisabledByAndroid())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun whenVpnLastDisabledByAndroidAndVpnDisabledByUserThenReturnFalse() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = VpnServiceState.DISABLED, stopReason = VpnStoppingReason.SELF_STOP)
        )

        assertFalse(sharedPreferencesVpnStore.vpnLastDisabledByAndroid())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun whenVpnLastDisabledByAndroidAndVpnEnabledThenReturnFalse() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = VpnServiceState.ENABLED)
        )

        assertFalse(sharedPreferencesVpnStore.vpnLastDisabledByAndroid())
    }
}
