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

package com.duckduckgo.pir.impl.email

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirEligibility
import com.duckduckgo.pir.impl.checker.PirRunMode
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PirEmailConfirmationRemoteWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirEmailConfirmationJobsRunner: PirEmailConfirmationJobsRunner = mock()
    private val mockPirWorkHandler: PirWorkHandler = mock()
    private val mockPirFeatureDataCleaner: PirFeatureDataCleaner = mock()
    private val mockPirScanScheduler: PirScanScheduler = mock()
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
        whenever(context.applicationContext).thenReturn(context)
    }

    private fun buildWorker(): PirEmailConfirmationRemoteWorker =
        TestListenableWorkerBuilder<PirEmailConfirmationRemoteWorker>(context = context).build().also {
            it.pirEmailConfirmationJobsRunner = mockPirEmailConfirmationJobsRunner
            it.pirWorkHandler = mockPirWorkHandler
            it.pirFeatureDataCleaner = mockPirFeatureDataCleaner
            it.dispatcherProvider = coroutineRule.testDispatcherProvider
            it.pirScanScheduler = mockPirScanScheduler
        }

    @Test
    fun whenRunModeIsScanOnlyThenNoEmailConfirmationWorkRuns() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled(PirRunMode.SCAN_ONLY)))

        val result = buildWorker().doRemoteWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verifyNoInteractions(mockPirEmailConfirmationJobsRunner)
    }

    @Test
    fun whenRunModeIsScanOnlyThenScheduledEmailConfirmationIsCancelled() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled(PirRunMode.SCAN_ONLY)))

        buildWorker().doRemoteWork()

        verify(mockPirScanScheduler).cancelScheduledEmailConfirmation()
    }

    @Test
    fun whenRunModeIsScanAndOptOutThenEmailConfirmationWorkRuns() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT)))
        whenever(mockPirEmailConfirmationJobsRunner.runEligibleJobs(any())).thenReturn(kotlin.Result.success(Unit))

        buildWorker().doRemoteWork()

        verify(mockPirEmailConfirmationJobsRunner).runEligibleJobs(any())
    }

    @Test
    fun whenRunModeIsScanAndOptOutThenScheduledEmailConfirmationIsNeverCancelled() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT)))
        whenever(mockPirEmailConfirmationJobsRunner.runEligibleJobs(any())).thenReturn(kotlin.Result.success(Unit))

        buildWorker().doRemoteWork()

        verify(mockPirScanScheduler, never()).cancelScheduledEmailConfirmation()
    }

    @Test
    fun whenNoEligibilityEmittedThenSkipsWithoutCancellingScheduledEmailConfirmation() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(emptyFlow())

        val result = buildWorker().doRemoteWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verifyNoInteractions(mockPirEmailConfirmationJobsRunner)
        verify(mockPirScanScheduler, never()).cancelScheduledEmailConfirmation()
    }
}
