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

package com.duckduckgo.app.statistics.wideevents

import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupDecision
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventInterval
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WideEventCleanupPolicyEvaluatorTest {
    private val evaluator = WideEventCleanupPolicyEvaluator(processName = "main")

    @Test
    fun `process-start event owned by this process is closed with the policy status`() {
        val event = event(policy = processStartPolicy())

        assertEquals(PROCESS_START_DECISION, evaluator.evaluate(event, NOW))
    }

    @Test
    fun `process-start event owned by another process stays open`() {
        val event = event(policy = processStartPolicy(ownerProcessName = ":vpn"))

        assertNull(evaluator.evaluate(event, NOW))
    }

    @Test
    fun `process-start event owned by this process with pending interval timeout stays open when the policy says so`() {
        val event = event(
            policy = processStartPolicy(ignoreIfIntervalTimeoutPresent = true),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(10), timeout = Duration.ofMinutes(1))),
        )

        assertNull(evaluator.evaluate(event, NOW))
    }

    @Test
    fun `process-start event owned by this process with pending interval timeout is closed when the policy ignores it`() {
        val event = event(
            policy = processStartPolicy(ignoreIfIntervalTimeoutPresent = false),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(10), timeout = Duration.ofMinutes(1))),
        )

        assertEquals(PROCESS_START_DECISION, evaluator.evaluate(event, NOW))
    }

    @Test
    fun `process-start event owned by this process with interval without timeout is closed regardless of the policy flag`() {
        val event = event(
            policy = processStartPolicy(ignoreIfIntervalTimeoutPresent = true),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(10), timeout = null)),
        )

        assertEquals(PROCESS_START_DECISION, evaluator.evaluate(event, NOW))
    }

    @Test
    fun `timed-out event is closed with the policy status`() {
        val event = event(
            policy = CleanupPolicy.OnTimeout(duration = Duration.ofHours(1), status = WideEventStatus.FAILURE, metadata = mapOf("k" to "v")),
            createdAt = NOW.minus(Duration.ofHours(1)),
        )

        assertEquals(CleanupDecision(status = WideEventStatus.FAILURE, metadata = mapOf("k" to "v")), evaluator.evaluate(event, NOW))
    }

    @Test
    fun `event within its timeout stays open`() {
        val event = event(
            policy = CleanupPolicy.OnTimeout(duration = Duration.ofHours(1), status = WideEventStatus.FAILURE),
            createdAt = NOW.minus(Duration.ofMinutes(59)),
        )

        assertNull(evaluator.evaluate(event, NOW))
    }

    @Test
    fun `expired interval timeout closes the event as unknown`() {
        val event = event(
            policy = CleanupPolicy.OnTimeout(duration = Duration.ofDays(7), status = WideEventStatus.FAILURE),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(30), timeout = Duration.ofSeconds(30))),
        )

        assertEquals(UNKNOWN_DECISION, evaluator.evaluate(event, NOW))
    }

    @Test
    fun `interval timeout is measured from the interval start, not the event start`() {
        val oldEventWithFreshInterval = event(
            policy = CleanupPolicy.OnTimeout(duration = Duration.ofDays(7), status = WideEventStatus.FAILURE),
            createdAt = NOW.minus(Duration.ofHours(2)),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(5), timeout = Duration.ofMinutes(1))),
        )
        val freshEventWithOldInterval = event(
            policy = CleanupPolicy.OnTimeout(duration = Duration.ofDays(7), status = WideEventStatus.FAILURE),
            createdAt = NOW.minusSeconds(5),
            activeIntervals = listOf(interval(startedAt = NOW.minus(Duration.ofHours(2)), timeout = Duration.ofMinutes(1))),
        )

        assertNull(evaluator.evaluate(oldEventWithFreshInterval, NOW))
        assertEquals(UNKNOWN_DECISION, evaluator.evaluate(freshEventWithOldInterval, NOW))
    }

    @Test
    fun `expired interval timeout closes events owned by another process`() {
        val otherProcessEvent = event(
            policy = processStartPolicy(ownerProcessName = ":vpn", ignoreIfIntervalTimeoutPresent = true),
            activeIntervals = listOf(interval(startedAt = NOW.minus(Duration.ofHours(1)), timeout = Duration.ofMinutes(1))),
        )

        assertEquals(UNKNOWN_DECISION, evaluator.evaluate(otherProcessEvent, NOW))
    }

    @Test
    fun `event with pending interval timeout and unmet policy stays open`() {
        val event = event(
            policy = processStartPolicy(ownerProcessName = ":vpn"),
            activeIntervals = listOf(interval(startedAt = NOW.minusSeconds(1), timeout = Duration.ofMinutes(1))),
        )

        assertNull(evaluator.evaluate(event, NOW))
    }

    private fun processStartPolicy(
        ownerProcessName: String = "main",
        ignoreIfIntervalTimeoutPresent: Boolean = false,
    ) = CleanupPolicy.OnProcessStart(
        ownerProcessName = ownerProcessName,
        ignoreIfIntervalTimeoutPresent = ignoreIfIntervalTimeoutPresent,
        status = PROCESS_START_DECISION.status,
        metadata = PROCESS_START_DECISION.metadata,
    )

    private fun interval(
        startedAt: Instant,
        timeout: Duration?,
    ) = WideEventInterval(name = "interval", startedAt = startedAt, timeout = timeout, buckets = null)

    private fun event(
        policy: CleanupPolicy,
        createdAt: Instant = NOW.minusSeconds(60),
        activeIntervals: List<WideEventInterval> = emptyList(),
    ) = WideEvent(
        id = 1L,
        name = "flow",
        status = null,
        steps = emptyList(),
        metadata = emptyMap(),
        flowEntryPoint = null,
        cleanupPolicy = policy,
        activeIntervals = activeIntervals,
        createdAt = createdAt,
        metaType = "android-flow",
        metaVersion = "1.0.0",
        appVersion = "5.123.0",
    )

    private companion object {
        val NOW: Instant = Instant.parse("2025-12-03T10:15:30.00Z")
        val PROCESS_START_DECISION = CleanupDecision(status = WideEventStatus.CANCELLED, metadata = mapOf("reason" to "test"))
        val UNKNOWN_DECISION = CleanupDecision(status = WideEventStatus.UNKNOWN, metadata = emptyMap())
    }
}
