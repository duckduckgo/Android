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

package com.duckduckgo.mobile.android.vpn.health

import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState
import com.duckduckgo.mobile.android.vpn.health.AppTPHealthMonitor.HealthState.*
import com.duckduckgo.mobile.android.vpn.model.HealthTriggerEntity
import com.duckduckgo.mobile.android.vpn.store.AppHealthTriggersRepository
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HealthClassifierTest {

    private val thresholds: AppHealthTriggersRepository = mock()

    private lateinit var testee: HealthClassifier

    @Before
    fun setup() {
        testee = HealthClassifier(thresholds)
    }

    @Test
    fun whenNoNetworkConnectivityAlertsBelowRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", true, 10)),
        )
        testee.determineHealthVpnConnectivity(5, "noNetworkConnectivityAlert").assertGoodHealth()
    }

    @Test
    fun whenNoNetworkConnectivityAlertsAboveDefaultThresholdThenReportBadHealth() {
        testee.determineHealthVpnConnectivity(3, "noNetworkConnectivityAlert").assertBadHealth()
    }

    @Test
    fun whenNoNetworkConnectivityAlertsBelowDefaultThresholdThenReportGoodHealth() {
        testee.determineHealthVpnConnectivity(1, "noNetworkConnectivityAlert").assertGoodHealth()
    }

    @Test
    fun whenNoNetworkConnectivityAlertsAboveRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", true)),
        )
        testee.determineHealthVpnConnectivity(10, "noNetworkConnectivityAlert").assertBadHealth()
    }

    @Test
    fun whenNoNetworkConnectivityDisabledAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", false, 5)),
        )
        testee.determineHealthVpnConnectivity(10, "noNetworkConnectivityAlert").assertGoodHealth()
    }

    private fun HealthState.assertGoodHealth() {
        assertTrue(String.format("Expected GoodHealth but was %s", this.javaClass.simpleName), (this) is GoodHealth)
    }

    private fun HealthState.assertBadHealth() {
        assertTrue(String.format("Expected BadHealth but was %s", this.javaClass.simpleName), (this) is BadHealth)
    }
}
