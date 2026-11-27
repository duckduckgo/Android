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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.service.VpnOperation.Destroy
import com.duckduckgo.mobile.android.vpn.service.VpnOperation.Start
import com.duckduckgo.mobile.android.vpn.service.VpnOperation.Stop
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnOperationQueueTest {

    private val restart = Start(alwaysOnTriggered = false, waitForForeground = false, startId = 1)
    private val selfStop = Stop(VpnStopReason.SELF_STOP(), startId = 1)

    /** Every operation takes virtual time, so operations submitted while one runs overlap it in time. */
    private class RecordingExecutor {
        val executed = mutableListOf<VpnOperation>()
        var maxConcurrent = 0
        private var running = 0

        suspend fun execute(operation: VpnOperation) {
            executed += operation
            running++
            maxConcurrent = maxOf(maxConcurrent, running)
            delay(100)
            running--
        }
    }

    // Not runTest's backgroundScope: advanceUntilIdle() doesn't run background work, and the queue's consumer never completes
    private val queueScope = CoroutineScope(Job())

    @After
    fun tearDown() {
        queueScope.cancel()
    }

    private fun TestScope.queue(executor: RecordingExecutor): VpnOperationQueue =
        VpnOperationQueue(queueScope, StandardTestDispatcher(testScheduler), executor::execute)

    @Test
    fun whenOperationsAreSubmittedWhileOneRunsThenTheyRunOneAtATimeInSubmissionOrder() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)
        val alwaysOnStart = Start(alwaysOnTriggered = true, waitForForeground = true, startId = 1)

        queue.submit(restart)
        runCurrent()
        queue.submit(selfStop)
        queue.submit(alwaysOnStart)
        advanceUntilIdle()

        assertEquals(listOf(restart, selfStop, alwaysOnStart), executor.executed)
        assertEquals(1, executor.maxConcurrent)
    }

    @Test
    fun whenThreeRestartsAreSubmittedWhileOneRunsThenOneFollowUpStartRuns() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        repeat(3) { queue.submit(restart) }
        advanceUntilIdle()

        assertEquals(listOf(restart, restart.copy(coalescedRequests = 2)), executor.executed)
    }

    @Test
    fun whenPendingStartsAreMergedThenTheMergedStartKeepsEveryRequestedFlag() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(restart)
        queue.submit(Start(alwaysOnTriggered = true, waitForForeground = false, startId = 1))
        queue.submit(Start(alwaysOnTriggered = false, waitForForeground = true, startId = 1))
        advanceUntilIdle()

        assertEquals(
            listOf(restart, Start(alwaysOnTriggered = true, waitForForeground = true, startId = 1, coalescedRequests = 2)),
            executor.executed,
        )
    }

    @Test
    fun whenStopIsSubmittedWhileAStartIsPendingThenThePendingStartIsDroppedAndStopRuns() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(restart)
        queue.submit(selfStop)
        advanceUntilIdle()

        assertEquals(listOf(restart, selfStop), executor.executed)
        assertEquals(1, executor.maxConcurrent)
    }

    @Test
    fun whenStartIsSubmittedAfterAPendingStopThenItRunsAfterTheStop() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(selfStop)
        queue.submit(restart)
        advanceUntilIdle()

        assertEquals(listOf(restart, selfStop, restart), executor.executed)
    }

    @Test
    fun whenPendingStartsAreMergedThenTheMergedStartKeepsTheLatestStartId() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(restart.copy(startId = 2))
        queue.submit(restart.copy(startId = 3))
        advanceUntilIdle()

        assertEquals(listOf(restart, restart.copy(startId = 3, coalescedRequests = 1)), executor.executed)
    }

    @Test
    fun whenAStopIsAlreadyPendingThenTheLaterStopReplacesIt() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(Stop(VpnStopReason.SELF_STOP(), startId = 2))
        queue.submit(restart.copy(startId = 3))
        queue.submit(Stop(VpnStopReason.ERROR, startId = 4))
        advanceUntilIdle()

        assertEquals(listOf(restart, Stop(VpnStopReason.ERROR, startId = 4)), executor.executed)
    }

    @Test
    fun whenAStopReplacesAPendingStopThenItKeepsTheLatestStartId() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(Stop(VpnStopReason.SELF_STOP(), startId = 5))
        queue.submit(Stop(VpnStopReason.ERROR, startId = 4))
        advanceUntilIdle()

        assertEquals(listOf(restart, Stop(VpnStopReason.ERROR, startId = 5)), executor.executed)
    }

    @Test
    fun whenARevokeIsPendingThenALaterStopKeepsTheRevokeReasonAndTakesTheLatestStartId() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(Stop(VpnStopReason.REVOKED, startId = 2))
        queue.submit(Stop(VpnStopReason.SELF_STOP(), startId = 3))
        advanceUntilIdle()

        assertEquals(listOf(restart, Stop(VpnStopReason.REVOKED, startId = 3)), executor.executed)
    }

    @Test
    fun whenDestroyIsSubmittedWhileAStopIsPendingThenTheStopRunsBeforeTheDestroy() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(selfStop)
        queue.submit(restart.copy(startId = 2))
        queue.submit(Destroy)
        advanceUntilIdle()

        assertEquals(listOf(restart, selfStop, Destroy), executor.executed)
        assertEquals(1, executor.maxConcurrent)
    }

    @Test
    fun whenDestroyIsSubmittedThenPendingStartsAreDroppedAndLaterOperationsIgnored() = runTest {
        val executor = RecordingExecutor()
        val queue = queue(executor)

        queue.submit(restart)
        runCurrent()
        queue.submit(restart)
        queue.submit(Destroy)
        queue.submit(restart)
        queue.submit(selfStop)
        advanceUntilIdle()
        queue.submit(restart)
        advanceUntilIdle()

        assertEquals(listOf(restart, Destroy), executor.executed)
        assertEquals(1, executor.maxConcurrent)
    }
}
