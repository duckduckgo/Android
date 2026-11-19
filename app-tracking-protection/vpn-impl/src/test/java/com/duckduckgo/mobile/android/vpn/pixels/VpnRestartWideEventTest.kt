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

package com.duckduckgo.mobile.android.vpn.pixels

import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures
import com.duckduckgo.mobile.android.vpn.pixels.VpnRestartWideEvent.FailureReason
import com.duckduckgo.mobile.android.vpn.pixels.VpnRestartWideEvent.Phase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VpnRestartWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val appTpRemoteFeatures = FakeFeatureToggleFactory.create(AppTpRemoteFeatures::class.java)

    private lateinit var wideEvent: VpnRestartWideEventImpl

    @Before
    fun setup() {
        wideEvent = VpnRestartWideEventImpl(
            wideEventClient = wideEventClient,
            appTpRemoteFeatures = appTpRemoteFeatures,
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenRestartStartedThenFlowStartsWithModeAndStackAndTotalInterval() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))

        wideEvent.onRestartStarted(networkStackName = AppTpVpnFeature.APPTP_VPN.featureName, inPlace = true)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("vpn-restart"),
            flowEntryPoint = anyOrNull(),
            metadata = eq(mapOf("restart_mode" to "in_place", "network_stack" to "apptp")),
            cleanupPolicy = any(),
            samplingProbability = eq(0.1f),
            definition = any(),
        )
        verify(wideEventClient).intervalStart(
            wideEventId = eq(42L),
            key = eq("restart_duration_ms_bucketed"),
            timeout = anyOrNull(),
            buckets = eq(setOf(100.milliseconds, 250.milliseconds, 500.milliseconds, 1.seconds, 2.seconds, 5.seconds, 10.seconds, 30.seconds)),
        )
    }

    @Test
    fun whenRestartStartedForLegacyNetpRestartThenMetadataReflectsIt() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))

        wideEvent.onRestartStarted(networkStackName = "NETP_VPN", inPlace = false)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = eq("vpn-restart"),
            flowEntryPoint = anyOrNull(),
            metadata = eq(mapOf("restart_mode" to "legacy", "network_stack" to "netp")),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun whenNetworkStackIsUnknownThenItIsReportedAsOther() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))

        wideEvent.onRestartStarted(networkStackName = "empty", inPlace = true)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = any(),
            flowEntryPoint = anyOrNull(),
            metadata = eq(mapOf("restart_mode" to "in_place", "network_stack" to "other")),
            cleanupPolicy = any(),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun whenToggleDisabledThenNoFlowStarts() = runTest {
        appTpRemoteFeatures.restartWideEvent().setRawStoredState(Toggle.State(enable = false))

        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)
        wideEvent.onPhaseStarted(Phase.TUNNEL_ESTABLISH)
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenNoRestartIsActiveThenPhasesAreIgnored() = runTest {
        wideEvent.onPhaseStarted(Phase.NETWORK_STACK_PREPARE)
        wideEvent.onPhaseCompleted(Phase.NETWORK_STACK_PREPARE)
        wideEvent.onRestartSucceeded()
        wideEvent.onRestartFailed(FailureReason.TUNNEL_ESTABLISH_FAILED)
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun whenPhaseStartsAndCompletesThenIntervalAndStepAreRecorded() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)

        wideEvent.onPhaseStarted(Phase.TUNNEL_ESTABLISH)
        wideEvent.onPhaseCompleted(Phase.TUNNEL_ESTABLISH)
        advanceUntilIdle()

        inOrder(wideEventClient) {
            verify(wideEventClient).intervalStart(
                wideEventId = eq(42L),
                key = eq("tunnel_establish_duration_ms_bucketed"),
                timeout = anyOrNull(),
                buckets = any(),
            )
            verify(wideEventClient).intervalEnd(wideEventId = 42L, key = "tunnel_establish_duration_ms_bucketed")
            verify(wideEventClient).flowStep(wideEventId = eq(42L), stepName = eq("tunnel_establish"), success = eq(true), metadata = any())
        }
    }

    @Test
    fun whenRestartSucceedsThenTotalIntervalEndsAndFlowFinishesWithSuccess() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)

        wideEvent.onRestartSucceeded()
        advanceUntilIdle()

        inOrder(wideEventClient) {
            verify(wideEventClient).intervalEnd(wideEventId = 42L, key = "restart_duration_ms_bucketed")
            verify(wideEventClient).flowFinish(wideEventId = eq(42L), status = eq(FlowStatus.Success), metadata = any())
        }
    }

    @Test
    fun whenRestartSucceedsThenLaterPhasesAreIgnored() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)
        wideEvent.onRestartSucceeded()

        wideEvent.onPhaseStarted(Phase.NETWORK_STACK_START)
        advanceUntilIdle()

        verify(wideEventClient, never()).intervalStart(any(), eq("network_stack_start_duration_ms_bucketed"), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenRestartFailsThenFlowFinishesWithFailureReason() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)

        wideEvent.onRestartFailed(FailureReason.NETWORK_STACK_PREPARE_FAILED)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = eq(42L),
            status = eq(FlowStatus.Failure("network_stack_prepare_failed")),
            metadata = any(),
        )
    }

    @Test
    fun whenRestartFailsMidPhaseThenPhaseAndTotalIntervalsEndBeforeFinish() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)
        wideEvent.onPhaseStarted(Phase.TUNNEL_ESTABLISH)

        wideEvent.onRestartFailed(FailureReason.TUNNEL_ESTABLISH_FAILED)
        advanceUntilIdle()

        inOrder(wideEventClient) {
            verify(wideEventClient).intervalEnd(wideEventId = 42L, key = "tunnel_establish_duration_ms_bucketed")
            verify(wideEventClient).intervalEnd(wideEventId = 42L, key = "restart_duration_ms_bucketed")
            verify(wideEventClient).flowFinish(wideEventId = eq(42L), status = eq(FlowStatus.Failure("tunnel_establish_failed")), metadata = any())
        }
    }

    @Test
    fun whenRestartFailsBetweenPhasesThenOnlyTotalIntervalEnds() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(42L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)
        wideEvent.onPhaseStarted(Phase.NETWORK_STACK_PREPARE)
        wideEvent.onPhaseCompleted(Phase.NETWORK_STACK_PREPARE)

        wideEvent.onRestartFailed(FailureReason.NETWORK_STACK_START_FAILED)
        advanceUntilIdle()

        verify(wideEventClient, times(1)).intervalEnd(wideEventId = 42L, key = "network_stack_prepare_duration_ms_bucketed")
        verify(wideEventClient).intervalEnd(wideEventId = 42L, key = "restart_duration_ms_bucketed")
    }

    @Test
    fun whenRestartStartsWhileAnotherIsActiveThenPreviousFinishesAsUnknown() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))
        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)

        wideEvent.onRestartStarted(networkStackName = "APP_TP_VPN", inPlace = true)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(wideEventId = eq(1L), status = eq(FlowStatus.Unknown), metadata = any())
        verify(wideEventClient).intervalStart(wideEventId = eq(2L), key = eq("restart_duration_ms_bucketed"), timeout = anyOrNull(), buckets = any())
    }
}
