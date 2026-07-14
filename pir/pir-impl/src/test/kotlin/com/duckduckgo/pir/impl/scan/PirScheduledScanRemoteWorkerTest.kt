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

package com.duckduckgo.pir.impl.scan

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirEligibility
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.scheduling.PirJobsRunner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirScheduledScanRemoteWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirJobsRunner: PirJobsRunner = mock()
    private val mockPirWorkHandler: PirWorkHandler = mock()
    private val mockPirFeatureDataCleaner: PirFeatureDataCleaner = mock()
    private val mockMonitor: PirForegroundScanServiceMonitor = mock()
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
        whenever(context.applicationContext).thenReturn(context)
    }

    private fun buildWorker(): PirScheduledScanRemoteWorker =
        TestListenableWorkerBuilder<PirScheduledScanRemoteWorker>(context = context).build().also {
            it.pirJobsRunner = mockPirJobsRunner
            it.pirWorkHandler = mockPirWorkHandler
            it.pirFeatureDataCleaner = mockPirFeatureDataCleaner
            it.dispatcherProvider = coroutineRule.testDispatcherProvider
            it.pirForegroundScanServiceMonitor = mockMonitor
        }

    @Test
    fun whenForegroundScanServiceRunningThenSkipsRunWithSuccessWithoutRunningJobs() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled))
        whenever(mockMonitor.isRunning()).thenReturn(true)

        val result = buildWorker().doRemoteWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockPirJobsRunner, never()).runEligibleJobs(any(), any())
    }

    @Test
    fun whenForegroundScanServiceNotRunningThenRunsEligibleJobs() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled))
        whenever(mockMonitor.isRunning()).thenReturn(false)
        whenever(mockPirJobsRunner.runEligibleJobs(any(), any())).thenReturn(kotlin.Result.success(Unit))

        val result = buildWorker().doRemoteWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockPirJobsRunner).runEligibleJobs(any(), any())
    }
}
