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
    fun whenNumberOfSocketReadExceptionsThenAlwaysReportsGoodHealth() {
        testee.determineHealthSocketChannelReadExceptions(Long.MAX_VALUE, "socketReadExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketReadExceptionsAboveRemoteThresholdThenReportsBadHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketReadExceptionAlerts", true, 5))
        )
        testee.determineHealthSocketChannelReadExceptions(10, "socketReadExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketReadExceptionsThenDefaultIsReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketReadExceptionAlerts", true))
        )
        testee.determineHealthSocketChannelReadExceptions(Long.MAX_VALUE, "socketReadExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketReadExceptionsDisabledThenAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketReadExceptionAlerts", false, 5))
        )
        testee.determineHealthSocketChannelReadExceptions(10, "socketReadExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketWriteExceptionsBelowThresholdThenReportsGoodHealth() {
        testee.determineHealthSocketChannelWriteExceptions(10, "socketWriteExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketWriteExceptionsAboveThresholdThenReportsGoodHealth() {
        testee.determineHealthSocketChannelWriteExceptions(21, "socketWriteExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketWriteExceptionsBelowRemoteThresholdThenReportsGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketWriteExceptionAlerts", true, 10))
        )
        testee.determineHealthSocketChannelWriteExceptions(5, "socketWriteExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketWriteExceptionsAboveRemoteThresholdThenReportsGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketWriteExceptionAlerts", true, 5))
        )
        testee.determineHealthSocketChannelWriteExceptions(10, "socketWriteExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketWriteExceptionsDisabledThenAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketWriteExceptionAlerts", false))
        )
        testee.determineHealthSocketChannelWriteExceptions(10, "socketWriteExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsBelowThresholdThenReportsGoodHealth() {
        testee.determineHealthSocketChannelConnectExceptions(10, "socketConnectExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsAboveThresholdThenReportsBadHealth() {
        testee.determineHealthSocketChannelConnectExceptions(21, "socketConnectExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsBelowRemoteThresholdThenReportsGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketConnectExceptionAlerts", true))
        )
        testee.determineHealthSocketChannelConnectExceptions(5, "socketConnectExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsAboveRemoteThresholdThenReportsBadHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketConnectExceptionAlerts", true, 5))
        )
        testee.determineHealthSocketChannelConnectExceptions(10, "socketConnectExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsDisabledThenAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("socketConnectExceptionAlerts", false))
        )
        testee.determineHealthSocketChannelConnectExceptions(10, "socketConnectExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsAboveDefaultThresholdThenReportBadHealth() {
        testee.determineHealthSocketChannelConnectExceptions(21, "socketConnectExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenNumberOfSocketConnectExceptionsAboveDefaultThresholdThenReportGoodHealth() {
        testee.determineHealthSocketChannelConnectExceptions(19, "socketConnectExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenTooFewTunInputsThenReportsInitializing() {
        val tunInputs: Long = 0
        val queueReads = QueueReads(0, 0, 0, 0)
        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertInitializing()
    }

    @Test
    fun whenLotsOfTunInputsButNoQueueReadsThenReportsBadHealth() {
        // success rate: 0%
        val tunInputs: Long = 100
        val queueReads = QueueReads(0, 0, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertBadHealth()
    }

    @Test
    fun whenLotsOfTunInputsAndLowNumberOfQueueReadsThenReportsBadHealth() {
        // success rate: 10%
        val tunInputs: Long = 900
        val queueReads = QueueReads(100, 100, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertBadHealth()
    }

    @Test
    fun whenLotsOfTunInputsButNoQueueReadsRateBelowRemoteThresholdThenReportsBadHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunInputsQueueReadRate", true))
        )

        // success rate: 10%
        val tunInputs: Long = 900
        val queueReads = QueueReads(100, 100, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertBadHealth()
    }

    @Test
    fun whenLotsOfTunInputsAndQueueReadsThenReportsGoodHealth() {
        // success rate: 100%
        val tunInputs: Long = 900
        val queueReads = QueueReads(900, 900, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertGoodHealth()
    }

    @Test
    fun whenLotsOfTunInputsAndQueueReadsAndRemoteThresholdThenReportsGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunInputsQueueReadRate", true))
        )

        // success rate: 100%
        val tunInputs: Long = 900
        val queueReads = QueueReads(900, 900, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertGoodHealth()
    }

    @Test
    fun whenTunInputsToQueueReadRateIsOver100PercentThenIsStillGoodHealth() {
        // success rate: 900%
        val tunInputs: Long = 100
        val queueReads = QueueReads(900, 900, 0, 0)

        testee.determineHealthTunInputQueueReadRatio(tunInputs, queueReads, "tunInputsQueueReadRate").assertGoodHealth()
    }

    @Test
    fun whenNoNetworkConnectivityAlertsBelowRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", true, 10))
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
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", true))
        )
        testee.determineHealthVpnConnectivity(10, "noNetworkConnectivityAlert").assertBadHealth()
    }

    @Test
    fun whenNoNetworkConnectivityDisabledAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("noNetworkConnectivityAlert", false, 5))
        )
        testee.determineHealthVpnConnectivity(10, "noNetworkConnectivityAlert").assertGoodHealth()
    }

    @Test
    fun whenTunReadExceptionAlertsBelowRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunReadExceptionAlerts", true, 10))
        )
        testee.determineHealthTunReadExceptions(5, "tunReadExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenTunReadExceptionAlertsAboveRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunReadExceptionAlerts", true))
        )
        testee.determineHealthTunReadExceptions(10, "tunReadExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenTunReadExceptionAlertsDisabledAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunReadExceptionAlerts", false, 5))
        )
        testee.determineHealthTunReadExceptions(10, "tunReadExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenTunWriteExceptionAlertsBelowRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteExceptionAlerts", true, 10))
        )
        testee.determineHealthTunWriteExceptions(5, "tunWriteExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenTunWriteExceptionAlertsAboveRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteExceptionAlerts", true, 5))
        )
        testee.determineHealthTunWriteExceptions(10, "tunWriteExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenTunWriteExceptionAlertsAboveDefaultThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteExceptionAlerts", true))
        )
        testee.determineHealthTunWriteExceptions(2, "tunWriteExceptionAlerts").assertBadHealth()
    }

    @Test
    fun whenTunWriteExceptionAlertsDisabledAlwaysReturnGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteExceptionAlerts", false, 1))
        )
        testee.determineHealthTunWriteExceptions(2, "tunWriteExceptionAlerts").assertGoodHealth()
    }

    @Test
    fun whenTunWriteIOExceptionAlertsBelowRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteIOMemoryExceptionsAlerts", true, 10))
        )

        testee.determineHealthTunWriteMemoryExceptions(5, "tunWriteIOMemoryExceptionsAlerts").assertGoodHealth()
    }

    @Test
    fun whenTunWriteIOExceptionAlertsAboveRemoteThresholdThenGoodHealth() {
        whenever(thresholds.triggers()).thenReturn(
            listOf(HealthTriggerEntity("tunWriteIOMemoryExceptionsAlerts", true))
        )
        testee.determineHealthTunWriteMemoryExceptions(10, "tunWriteIOMemoryExceptionsAlerts").assertBadHealth()
    }

    private fun HealthState.assertGoodHealth() {
        assertTrue(String.format("Expected GoodHealth but was %s", this.javaClass.simpleName), (this) is GoodHealth)
    }

    private fun HealthState.assertBadHealth() {
        assertTrue(String.format("Expected BadHealth but was %s", this.javaClass.simpleName), (this) is BadHealth)
    }

    private fun HealthState.assertInitializing() {
        assertTrue(String.format("Expected Initializing but was %s", this.javaClass.simpleName), (this) is Initializing)
    }
}
