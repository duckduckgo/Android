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

package com.duckduckgo.pir.impl.scan

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirScanTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirScan

    private val mockRepository: PirRepository = mock()
    private val mockBrokerStepsParser: BrokerStepsParser = mock()
    private val mockPirCssScriptLoader: PirCssScriptLoader = mock()
    private val mockPirActionsRunnerFactory: RealPirActionsRunner.Factory = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockCallbacks: PluginPoint<PirCallbacks> = mock()
    private val mockContext: Context = mock()
    private val mockPirActionsRunner: RealPirActionsRunner = mock()

    @Before
    fun setUp() {
        whenever(mockCallbacks.getPlugins()).thenReturn(emptyList())

        testee = RealPirScan(
            repository = mockRepository,
            brokerStepsParser = mockBrokerStepsParser,
            pirCssScriptLoader = mockPirCssScriptLoader,
            pirActionsRunnerFactory = mockPirActionsRunnerFactory,
            currentTimeProvider = mockCurrentTimeProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            callbacks = mockCallbacks,
        )
    }

    // Test data
    private val testCurrentTime = 1000L
    private val testBrokerName = "test-broker"
    private val testBrokerName2 = "test-broker-2"
    private val testScript = "test-script-content"
    private val testStepsJson = """{"stepType":"scan","actions":[]}"""

    private val testProfileQuery = ProfileQuery(
        id = 123L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    private val testProfileQuery2 = ProfileQuery(
        id = 456L,
        firstName = "Jane",
        lastName = "Smith",
        city = "Chicago",
        state = "IL",
        addresses = emptyList(),
        birthYear = 1985,
        fullName = "Jane Smith",
        age = 38,
        deprecated = false,
    )

    private val testUserProfileQueries = listOf(testProfileQuery, testProfileQuery2)

    private val testScanJobRecord = ScanJobRecord(
        brokerName = testBrokerName,
        userProfileId = testProfileQuery.id,
        status = ScanJobStatus.NOT_EXECUTED,
        lastScanDateInMillis = 0L,
    )

    private val testScanJobRecord2 = ScanJobRecord(
        brokerName = testBrokerName2,
        userProfileId = testProfileQuery2.id,
        status = ScanJobStatus.NOT_EXECUTED,
        lastScanDateInMillis = 0L,
    )

    private val testScanStep = ScanStep(
        brokerName = testBrokerName,
        stepType = "scan",
        actions = emptyList(),
        scanType = "data",
    )

    private val testScanStep2 = ScanStep(
        brokerName = testBrokerName2,
        stepType = "scan",
        actions = emptyList(),
        scanType = "data",
    )

    @Test
    fun whenEmptyJobRecordsThenDontCreateRunners() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(emptyList(), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenNoBrokerScanStepsThenDontCreateRunners() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(null)

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenBrokerStepsParsingReturnsEmptyThenDontCreateRunners() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(emptyList())

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenNoRelevantProfilesThenDontCreateRunners() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(emptyList())

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenNoRelevantProfilesUsesDefaultProfileQueries() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(emptyList())
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL)).thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(1)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        val jobRecordWithDefaultProfile = testScanJobRecord.copy(userProfileId = DEFAULT_PROFILE_QUERIES[0].id)

        // When
        testee.executeScanForJobs(listOf(jobRecordWithDefaultProfile), mockContext, RunType.MANUAL)

        // Then
        verify(mockPirCssScriptLoader).getScript()
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.MANUAL)
    }

    @Test
    fun whenJobRecordHasNoMatchingProfileThenSkipsRecord() = runTest {
        // Given
        val unknownProfileJobRecord = testScanJobRecord.copy(userProfileId = 999L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(unknownProfileJobRecord), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenJobRecordHasNoMatchingBrokerStepThenSkipsRecord() = runTest {
        // Given
        val unknownBrokerJobRecord = testScanJobRecord.copy(brokerName = "unknown-broker")
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps("unknown-broker")).thenReturn(null)
        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(0)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(unknownBrokerJobRecord), mockContext, RunType.MANUAL)

        // Then
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
    }

    @Test
    fun whenValidJobRecordThenExecutesScan() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL)).thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(testProfileQuery, listOf(testScanStep))).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(1)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)

        // Then
        verify(mockPirCssScriptLoader).getScript()
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.MANUAL)
        verify(mockPirActionsRunner).start(testProfileQuery, listOf(testScanStep))
        verify(mockPirActionsRunner).stop()
    }

    @Test
    fun whenMultipleValidJobRecordsThenExecutesAllScans() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName2)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockBrokerStepsParser.parseStep(testBrokerName2, testStepsJson)).thenReturn(listOf(testScanStep2))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner, mock<RealPirActionsRunner>())
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(2)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord, testScanJobRecord2), mockContext, RunType.MANUAL)

        // Then
        verify(mockRepository).getBrokerScanSteps(testBrokerName)
        verify(mockRepository).getBrokerScanSteps(testBrokerName2)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson)
        verify(mockBrokerStepsParser).parseStep(testBrokerName2, testStepsJson)
        verify(mockPirCssScriptLoader).getScript()
    }

    @Test
    fun whenDuplicateBrokerNamesThenParsesBrokerStepsOnlyOnce() = runTest {
        // Given
        val duplicateJobRecord = testScanJobRecord.copy(userProfileId = testProfileQuery2.id)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner, mock<RealPirActionsRunner>())
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(2)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord, duplicateJobRecord), mockContext, RunType.MANUAL)

        // Then
        // Verify broker steps parsed only once for the same broker name
        verify(mockRepository).getBrokerScanSteps(testBrokerName)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson)
    }

    @Test
    fun whenExecuteScanForJobsWithScheduledRunTypeThenUsesScheduledPixels() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.SCHEDULED)).thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(1)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.SCHEDULED)

        // Then
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.SCHEDULED)
    }

    @Test
    fun whenExecuteScanForJobsThenCleansUpPreviousRun() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL)).thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(1)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)

        // Then
        verify(mockRepository).deleteAllScanResults()
    }

    @Test
    fun whenStopThenCallsStopOnAllRunners() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockRepository.getBrokerScanSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(testBrokerName, testStepsJson)).thenReturn(listOf(testScanStep))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL)).thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        whenever(mockRepository.getScanSuccessResultsCount()).thenReturn(1)
        whenever(mockRepository.getScanErrorResultsCount()).thenReturn(0)

        // When
        testee.executeScanForJobs(listOf(testScanJobRecord), mockContext, RunType.MANUAL)
        testee.stop()

        verify(mockPirActionsRunner, times(2)).stop()
    }
}
