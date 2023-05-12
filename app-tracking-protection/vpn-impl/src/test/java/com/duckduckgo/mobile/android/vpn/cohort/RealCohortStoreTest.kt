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

package com.duckduckgo.mobile.android.vpn.cohort

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.threeten.bp.LocalDate

@ExperimentalCoroutinesApi
class RealCohortStoreTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private val sharedPreferencesProvider = mock<VpnSharedPreferencesProvider>()

    private lateinit var cohortStore: CohortStore

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val prefs = InMemorySharedPreferences()
        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.mobile.atp.cohort.prefs"), eq(true), eq(true)),
        ).thenReturn(prefs)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        cohortStore = RealCohortStore(sharedPreferencesProvider, vpnFeaturesRegistry, coroutineRule.testDispatcherProvider, appBuildConfig)
    }

    @Test
    fun whenCohortNotSetThenReturnNull() {
        assertNull(cohortStore.getCohortStoredLocalDate())
    }

    @Test
    fun whenSetCohortLocalDateThenStoredCorrectly() {
        val date = LocalDate.now().plusDays(3)
        cohortStore.setCohortLocalDate(date)

        assertEquals(date, cohortStore.getCohortStoredLocalDate())
    }

    @Test
    fun whenInitialCohortFirstCalledThenStoreInitialCohort() {
        whenever(vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)).thenReturn(true)
        (cohortStore as RealCohortStore).onVpnStarted(TestScope())

        assertEquals(LocalDate.now(), cohortStore.getCohortStoredLocalDate())
    }

    @Test
    fun whenInitialCohortSubsequentCalledThenNoop() {
        whenever(vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)).thenReturn(true)
        val date = LocalDate.now().plusDays(3)
        cohortStore.setCohortLocalDate(date)

        (cohortStore as RealCohortStore).onVpnStarted(TestScope())

        assertEquals(date, cohortStore.getCohortStoredLocalDate())
    }

    @Test
    fun whenAppTpNotRegisteredThenDoNothingWithCohort() {
        whenever(vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)).thenReturn(false)
        (cohortStore as RealCohortStore).onVpnStarted(TestScope())

        assertNull(cohortStore.getCohortStoredLocalDate())
    }
}
