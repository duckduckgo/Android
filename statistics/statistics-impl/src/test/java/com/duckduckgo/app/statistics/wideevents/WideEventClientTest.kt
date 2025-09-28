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

package com.duckduckgo.app.statistics.wideevents

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class WideEventClientTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventRepository: WideEventRepository = mock()

    @SuppressLint("DenyListedApi")
    private val wideEventFeature: WideEventFeature =
        FakeFeatureToggleFactory
            .create(WideEventFeature::class.java)
            .apply { self().setRawStoredState(State(true)) }

    private val wideEventClient =
        WideEventClientImpl(
            wideEventRepository = wideEventRepository,
            wideEventFeature = { wideEventFeature },
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

    @Test
    fun `when flowStart called with all parameters then returns success with event id`() =
        runTest {
            val expectedEventId = 123L
            val name = "subscription_purchase"
            val flowEntryPoint = "app_settings"
            val metadata = mapOf("free_trial_eligible" to "true", "user_type" to "premium")
            val cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true)

            whenever(wideEventRepository.insertWideEvent(any(), any(), any(), anyOrNull()))
                .thenReturn(expectedEventId)

            val result = wideEventClient.flowStart(name, flowEntryPoint, metadata, cleanupPolicy)

            assertTrue(result.isSuccess)
            assertEquals(expectedEventId, result.getOrNull())

            verify(wideEventRepository).insertWideEvent(
                name = name,
                flowEntryPoint = flowEntryPoint,
                metadata = mapOf("free_trial_eligible" to "true", "user_type" to "premium"),
                cleanupPolicy =
                    WideEventRepository.CleanupPolicy.OnProcessStart(
                        ignoreIfIntervalTimeoutPresent = true,
                        status = WideEventRepository.WideEventStatus.UNKNOWN,
                        metadata = emptyMap(),
                    ),
            )
        }

    @Test
    fun `when flowStart called with minimal parameters then uses defaults`() =
        runTest {
            val expectedEventId = 456L
            val name = "login_flow"

            whenever(wideEventRepository.insertWideEvent(any(), anyOrNull(), any(), anyOrNull()))
                .thenReturn(expectedEventId)

            val result = wideEventClient.flowStart(name)

            assertTrue(result.isSuccess)
            assertEquals(expectedEventId, result.getOrNull())

            verify(wideEventRepository).insertWideEvent(
                name = name,
                flowEntryPoint = null,
                metadata = emptyMap(),
                cleanupPolicy =
                    WideEventRepository.CleanupPolicy.OnTimeout(
                        duration = Duration.ofDays(7),
                        status = WideEventRepository.WideEventStatus.UNKNOWN,
                        metadata = emptyMap(),
                    ),
            )
        }

    @Test
    fun `when flowStart encounters repository exception then returns failure`() =
        runTest {
            val exception = RuntimeException("Database error")
            whenever(wideEventRepository.insertWideEvent(any(), anyOrNull(), any(), anyOrNull()))
                .thenThrow(exception)

            val result = wideEventClient.flowStart("test_flow")

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when flowStep called with success step then records step in repository`() =
        runTest {
            val wideEventId = 123L
            val stepName = "account_created"
            val metadata = mapOf("account_type" to "premium")
            val expectedStep = WideEventRepository.WideEventStep(stepName, true)

            val result = wideEventClient.flowStep(wideEventId, stepName, true, metadata)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).addWideEventStep(
                eventId = wideEventId,
                step = expectedStep,
                metadata = mapOf("account_type" to "premium"),
            )
        }

    @Test
    fun `when flowStep called with failed step then records failure in repository`() =
        runTest {
            val wideEventId = 789L
            val stepName = "payment_processing"
            val expectedStep = WideEventRepository.WideEventStep(stepName, false)

            val result = wideEventClient.flowStep(wideEventId, stepName, false)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).addWideEventStep(
                eventId = wideEventId,
                step = expectedStep,
                metadata = emptyMap(),
            )
        }

    @Test
    fun `when flowStep called with empty metadata then uses empty map`() =
        runTest {
            val wideEventId = 555L
            val stepName = "user_verification"
            val expectedStep = WideEventRepository.WideEventStep(stepName, true)

            val result = wideEventClient.flowStep(wideEventId, stepName, true)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).addWideEventStep(
                eventId = wideEventId,
                step = expectedStep,
                metadata = emptyMap(),
            )
        }

    @Test
    fun `when flowStep encounters repository exception then returns failure`() =
        runTest {
            val wideEventId = 999L
            val exception = RuntimeException("Database connection failed")
            whenever(wideEventRepository.addWideEventStep(any(), any(), any()))
                .thenThrow(exception)

            val result = wideEventClient.flowStep(wideEventId, "test_step", true)

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when flowFinish called with Success status then sets status`() =
        runTest {
            val wideEventId = 123L
            val status = FlowStatus.Success
            val metadata = mapOf("completion_time" to "2025-09-22T10:00:00Z")
            val expectedWideEventStatus = WideEventRepository.WideEventStatus.SUCCESS

            val result = wideEventClient.flowFinish(wideEventId, status, metadata)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).setWideEventStatus(
                eventId = wideEventId,
                status = expectedWideEventStatus,
                metadata = mapOf("completion_time" to "2025-09-22T10:00:00Z"),
            )
        }

    @Test
    fun `when flowFinish called with Failure status then sets status`() =
        runTest {
            val wideEventId = 456L
            val status = FlowStatus.Failure("Payment declined")
            val metadata = mapOf("error_code" to "PAYMENT_DECLINED")
            val expectedWideEventStatus = WideEventRepository.WideEventStatus.FAILURE

            val result = wideEventClient.flowFinish(wideEventId, status, metadata)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).setWideEventStatus(
                eventId = wideEventId,
                status = expectedWideEventStatus,
                metadata =
                    mapOf(
                        "failure_reason" to "Payment declined",
                        "error_code" to "PAYMENT_DECLINED",
                    ),
            )
        }

    @Test
    fun `when flowFinish called with Cancelled status then sets status`() =
        runTest {
            val wideEventId = 789L
            val status = FlowStatus.Cancelled
            val expectedWideEventStatus = WideEventRepository.WideEventStatus.CANCELLED

            val result = wideEventClient.flowFinish(wideEventId, status)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).setWideEventStatus(
                eventId = wideEventId,
                status = expectedWideEventStatus,
                metadata = emptyMap(),
            )
        }

    @Test
    fun `when flowFinish called with Unknown status then sets status`() =
        runTest {
            val wideEventId = 111L
            val status = FlowStatus.Unknown
            val metadata = mapOf("reason" to "timeout")
            val expectedWideEventStatus = WideEventRepository.WideEventStatus.UNKNOWN

            val result = wideEventClient.flowFinish(wideEventId, status, metadata)

            assertTrue(result.isSuccess)

            verify(wideEventRepository).setWideEventStatus(
                eventId = wideEventId,
                status = expectedWideEventStatus,
                metadata = mapOf("reason" to "timeout"),
            )
        }

    @Test
    fun `when flowFinish encounters repository exception then returns failure`() =
        runTest {
            val wideEventId = 222L
            val exception = RuntimeException("Database error")
            whenever(wideEventRepository.setWideEventStatus(any(), any(), any()))
                .thenThrow(exception)

            val result = wideEventClient.flowFinish(wideEventId, FlowStatus.Success)

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when flowAbort called then deletes event from repository`() =
        runTest {
            val wideEventId = 123L
            whenever(wideEventRepository.deleteWideEvent(wideEventId)).thenReturn(true)

            val result = wideEventClient.flowAbort(wideEventId)

            assertTrue(result.isSuccess)
            verify(wideEventRepository).deleteWideEvent(wideEventId)
        }

    @Test
    fun `when flowAbort called and event not found then returns failure`() =
        runTest {
            val wideEventId = 456L
            whenever(wideEventRepository.deleteWideEvent(wideEventId)).thenReturn(false)

            val result = wideEventClient.flowAbort(wideEventId)

            assertTrue(result.isFailure)
            verify(wideEventRepository).deleteWideEvent(wideEventId)
        }

    @Test
    fun `when flowAbort encounters repository exception then returns failure`() =
        runTest {
            val wideEventId = 789L
            val exception = RuntimeException("Database error")
            whenever(wideEventRepository.deleteWideEvent(wideEventId)).thenThrow(exception)

            val result = wideEventClient.flowAbort(wideEventId)

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when intervalStart called then starts interval in repository`() =
        runTest {
            val wideEventId = 101L
            val key = "network_call"
            val timeout = Duration.ofSeconds(5)

            val result = wideEventClient.intervalStart(wideEventId, key, timeout)

            assertTrue(result.isSuccess)
            verify(wideEventRepository).startInterval(
                eventId = wideEventId,
                name = key,
                timeout = timeout,
            )
        }

    @Test
    fun `when intervalEnd called then ends interval in repository and returns duration`() =
        runTest {
            val wideEventId = 202L
            val key = "network_call"
            val expectedDuration = Duration.ofSeconds(3)

            whenever(wideEventRepository.endInterval(wideEventId, key)).thenReturn(expectedDuration)

            val result = wideEventClient.intervalEnd(wideEventId, key)

            assertTrue(result.isSuccess)
            assertEquals(expectedDuration, result.getOrNull())
            verify(wideEventRepository).endInterval(wideEventId, key)
        }

    @Test
    fun `when intervalStart encounters repository exception then returns failure`() =
        runTest {
            val wideEventId = 303L
            val key = "network_call"
            val timeout = Duration.ofSeconds(5)
            val exception = RuntimeException("Database error")
            whenever(wideEventRepository.startInterval(any(), any(), anyOrNull())).thenThrow(exception)

            val result = wideEventClient.intervalStart(wideEventId, key, timeout)

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when intervalEnd encounters repository exception then returns failure`() =
        runTest {
            val wideEventId = 404L
            val key = "network_call"
            val exception = RuntimeException("Database error")
            whenever(wideEventRepository.endInterval(any(), any())).thenThrow(exception)

            val result = wideEventClient.intervalEnd(wideEventId, key)

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `when flowStart called with OnTimeout cleanupPolicy then maps correctly`() =
        runTest {
            val wideEventId = 505L
            val cleanupPolicy =
                CleanupPolicy.OnTimeout(
                    duration = Duration.ofMinutes(5),
                    flowStatus = FlowStatus.Failure("timeout"),
                )

            whenever(wideEventRepository.insertWideEvent(any(), anyOrNull(), any(), anyOrNull()))
                .thenReturn(wideEventId)

            val result =
                wideEventClient.flowStart(
                    name = "timeout_flow",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = cleanupPolicy,
                )

            assertTrue(result.isSuccess)
            verify(wideEventRepository).insertWideEvent(
                name = "timeout_flow",
                flowEntryPoint = null,
                metadata = emptyMap(),
                cleanupPolicy =
                    WideEventRepository.CleanupPolicy.OnTimeout(
                        duration = Duration.ofMinutes(5),
                        status = WideEventRepository.WideEventStatus.FAILURE,
                        metadata = mapOf("failure_reason" to "timeout"),
                    ),
            )
        }
}
