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

package com.duckduckgo.pir.internal.optout

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.internal.PirInternalConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.internal.callbacks.PirCallbacks
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.internal.common.RealPirActionsRunner
import com.duckduckgo.pir.internal.models.ExtractedProfile
import com.duckduckgo.pir.internal.models.ProfileQuery
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirOptOutTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirOptOut

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

        testee = RealPirOptOut(
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
    private val testStepsJson = """{"stepType":"optOut","actions":[]}"""

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

    private val testExtractedProfile = ExtractedProfile(
        dbId = 789L,
        profileQueryId = testProfileQuery.id,
        brokerName = testBrokerName,
        name = "John Doe",
        alternativeNames = emptyList(),
        age = "33",
        addresses = emptyList(),
        phoneNumbers = emptyList(),
        relatives = emptyList(),
        reportId = "report123",
        email = "john@example.com",
        fullName = "John Doe",
        profileUrl = "https://example.com/profile",
        identifier = "id123",
    )

    private val testOptOutJobRecord = OptOutJobRecord(
        brokerName = testBrokerName,
        userProfileId = testProfileQuery.id,
        extractedProfileId = testExtractedProfile.dbId,
        status = OptOutJobStatus.NOT_EXECUTED,
        attemptCount = 0,
        lastOptOutAttemptDateInMillis = 0L,
        optOutRequestedDateInMillis = testCurrentTime,
        optOutRemovedDateInMillis = 0L,
    )

    private val testOptOutStep = OptOutStep(
        brokerName = testBrokerName,
        stepType = "optOut",
        actions = emptyList(),
        optOutType = "form",
        profileToOptOut = testExtractedProfile,
    )

    @Test
    fun whenEmptyJobRecordsThenDontCreateRunners() = runTest {
        // Given
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(emptyList(), mockContext)

        // Then
        verify(mockRepository, atLeast(1)).saveScanLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenNoUserProfilesAvailableThenDontCreateRunners() = runTest {
        // Given
        whenever(mockRepository.getUserProfileQueries()).thenReturn(emptyList())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockRepository, times(2)).saveScanLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenNoUserProfilesUsesDefaultProfileQueries() = runTest {
        // Given
        whenever(mockRepository.getUserProfileQueries()).thenReturn(emptyList())
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                DEFAULT_PROFILE_QUERIES[0].id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT)).thenReturn(
            mockPirActionsRunner,
        )
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        val jobRecordWithDefaultProfile =
            testOptOutJobRecord.copy(userProfileId = DEFAULT_PROFILE_QUERIES[0].id)

        // When
        testee.executeOptOutForJobs(listOf(jobRecordWithDefaultProfile), mockContext)

        // Then
        verify(mockBrokerStepsParser).parseStep(
            testBrokerName,
            testStepsJson,
            DEFAULT_PROFILE_QUERIES[0].id,
        )
    }

    @Test
    fun whenNoBrokerOptOutStepsThenDontCreateRunner() = runTest {
        // Given
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(null)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockRepository, times(2)).saveScanLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenJobRecordHasNoMatchingProfileQueryThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        val unknownProfileJobRecord = testOptOutJobRecord.copy(userProfileId = 999L)
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(unknownProfileJobRecord), mockContext)

        // Then
        verify(mockRepository, times(2)).saveScanLog(any())
        verify(mockBrokerStepsParser, never()).parseStep(any(), any(), any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenBrokerStepsParsingFailsThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(emptyList())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockRepository, times(2)).saveScanLog(any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenExtractedProfileNotFoundInBrokerStepsThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        val stepWithDifferentProfile =
            testOptOutStep.copy(profileToOptOut = testExtractedProfile.copy(dbId = 999L))
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(stepWithDifferentProfile))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockRepository, times(2)).saveScanLog(any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenValidJobRecordThenExecutesOptOut() = runTest {
        // Given
        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT)).thenReturn(
            mockPirActionsRunner,
        )
        whenever(mockPirActionsRunner.start(testProfileQuery, listOf(testOptOutStep))).thenReturn(
            Result.success(Unit),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockPirCssScriptLoader).getScript()
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, OPTOUT)
        verify(mockPirActionsRunner).start(testProfileQuery, listOf(testOptOutStep))
        verify(mockPirActionsRunner).stop()
        verify(mockRepository, times(2)).saveScanLog(any())
    }

    @Test
    fun whenJobRecordUsedCachedBrokerStepsThenDoesNotReparseSteps() = runTest {
        // Given
        val duplicateJobRecord =
            testOptOutJobRecord.copy(extractedProfileId = testExtractedProfile.dbId + 1)
        val duplicateOptOutStep =
            testOptOutStep.copy(profileToOptOut = testExtractedProfile.copy(dbId = testExtractedProfile.dbId + 1))

        whenever(mockRepository.getUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(testOptOutStep, duplicateOptOutStep))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT))
            .thenReturn(mockPirActionsRunner, mock<RealPirActionsRunner>())
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(
            listOf(testOptOutJobRecord, duplicateJobRecord),
            mockContext,
        )

        // Verify parseStep is called only once, not twice (caching works)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery.id)
        verify(mockPirCssScriptLoader).getScript()
        verify(mockRepository, times(2)).saveScanLog(any())
    }
}
