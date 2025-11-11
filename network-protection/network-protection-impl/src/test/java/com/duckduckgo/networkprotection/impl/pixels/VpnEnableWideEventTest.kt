/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VpnEnableWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private val networkProtectionState: NetworkProtectionState = mock()
    private val subscriptions: Subscriptions = mock()

    @SuppressLint("DenyListedApi")
    private val vpnRemoteFeatures: VpnRemoteFeatures = FakeFeatureToggleFactory
        .create(VpnRemoteFeatures::class.java)
        .apply { sendVpnEnableWideEvent().setRawStoredState(Toggle.State(enable = true)) }

    private val vpnEnableWideEvent = VpnEnableWideEventImpl(
        wideEventClient = wideEventClient,
        dispatchers = coroutineRule.testDispatcherProvider,
        networkProtectionState = networkProtectionState,
        subscriptions = subscriptions,
        vpnRemoteFeatures = vpnRemoteFeatures,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Test
    fun `onUserRequestedVpnStart starts a new flow`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(123L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "vpn-enable",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "subscription_status" to "Auto-Renewable",
                "is_first_setup" to "false",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onUserRequestedVpnStart with SYSTEM_TILE entry point`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(456L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.SYSTEM_TILE)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "vpn-enable",
            flowEntryPoint = "system_tile",
            metadata = mapOf(
                "subscription_status" to "Unknown",
                "is_first_setup" to "true",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onUserRequestedVpnStart with NOTIFICATION entry point`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(789L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.NOTIFICATION)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "vpn-enable",
            flowEntryPoint = "notification",
            metadata = mapOf(
                "subscription_status" to "Waiting",
                "is_first_setup" to "false",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onUserRequestedVpnStart ends previous flow if one is active`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(1L, FlowStatus.Unknown)
    }

    @Test
    fun `onVpnConflictDialogShown sends flowStep`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(100L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnConflictDialogShown()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 100L,
            stepName = "show_vpn_conflict_dialog",
        )
    }

    @Test
    fun `onVpnConflictDialogCancel finishes flow with Cancelled`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(200L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnConflictDialogCancel()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 200L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancellation_reason" to "vpn_conflict"),
        )
    }

    @Test
    fun `onVpnConflictDialogCancel clears wideEventId`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(300L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnConflictDialogCancel()

        reset(wideEventClient)

        // After cancel, subsequent calls should not interact with client
        vpnEnableWideEvent.onAskForVpnPermission()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onAskForVpnPermission sends flowStep`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(400L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onAskForVpnPermission()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 400L,
            stepName = "ask_for_vpn_permission",
        )
    }

    @Test
    fun `onVpnPermissionRejected finishes flow with Cancelled`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(500L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnPermissionRejected()
        advanceUntilIdle()

        verify(wideEventClient).flowFinish(
            wideEventId = 500L,
            status = FlowStatus.Cancelled,
            metadata = mapOf("cancellation_reason" to "permission_denied"),
        )
    }

    @Test
    fun `onVpnPermissionRejected clears wideEventId`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(600L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnPermissionRejected()

        reset(wideEventClient)

        // After rejection, subsequent calls should not interact with client
        vpnEnableWideEvent.onStartVpn()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onStartVpn sends flowStep and starts interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(700L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onStartVpn()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 700L,
            stepName = "vpn_start_attempt",
        )
        verify(wideEventClient).intervalStart(
            wideEventId = 700L,
            key = "service_start_duration_ms_bucketed",
        )
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() = runTest {
        vpnRemoteFeatures.sendVpnEnableWideEvent().setRawStoredState(Toggle.State(enable = false))
        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        vpnEnableWideEvent.onVpnConflictDialogShown()
        vpnEnableWideEvent.onStartVpn()
        advanceUntilIdle()

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `subscription status exception is handled gracefully`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(800L))
        whenever(subscriptions.getSubscriptionStatus()).thenThrow(RuntimeException("Error"))
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "vpn-enable",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "subscription_status" to "",
                "is_first_setup" to "true",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `onboarded check exception is handled gracefully`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any()))
            .thenReturn(Result.success(900L))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(networkProtectionState.isOnboarded()).thenThrow(RuntimeException("Error"))

        vpnEnableWideEvent.onUserRequestedVpnStart(VpnEnableWideEvent.EntryPoint.APP_SETTINGS)
        advanceUntilIdle()

        verify(wideEventClient).flowStart(
            name = "vpn-enable",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "subscription_status" to "Unknown",
                "is_first_setup" to "",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }
}
