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

package com.duckduckgo.pir.impl.email

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirWebViewDataCleaner
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirEmailConfirmationTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirEmailConfirmation

    private val mockRepository: PirRepository = mock()
    private val mockBrokerStepsParser: BrokerStepsParser = mock()
    private val mockPirCssScriptLoader: PirCssScriptLoader = mock()
    private val mockPirActionsRunnerFactory: RealPirActionsRunner.Factory = mock()
    private val mockCallbacks: PluginPoint<PirCallbacks> = mock()
    private val mockContext: Context = mock()
    private val mockPirActionsRunner: RealPirActionsRunner = mock()
    private val mockWebViewDataCleaner: PirWebViewDataCleaner = mock()

    @Before
    fun setUp() {
        whenever(mockCallbacks.getPlugins()).thenReturn(emptyList())

        testee = RealPirEmailConfirmation(
            repository = mockRepository,
            brokerStepsParser = mockBrokerStepsParser,
            pirCssScriptLoader = mockPirCssScriptLoader,
            pirActionsRunnerFactory = mockPirActionsRunnerFactory,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            callbacks = mockCallbacks,
            webViewDataCleaner = mockWebViewDataCleaner,
        )
    }

    private val testCurrentTime = 1000L
    private val testBrokerName = "test-broker"
    private val testBrokerName2 = "test-broker-2"
    private val testScript = "test-script-content"
    private val testStepsJson = """{"stepType":"emailConfirmation","actions":[]}"""

    private val testBroker1 = Broker(
        name = testBrokerName,
        fileName = "test-broker-1.json",
        url = "https://test-broker-1.com",
        version = "1.0",
        parent = null,
        addedDatetime = testCurrentTime,
        removedAt = 0L,
    )

    private val testBroker2 = testBroker1.copy(
        name = testBrokerName2,
    )

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

    private val testExtractedProfile2 = testExtractedProfile.copy(
        dbId = 790L,
        profileQueryId = testProfileQuery2.id,
        brokerName = testBrokerName2,
        name = "Jane Smith",
        fullName = "Jane Smith",
        email = "jane@example.com",
    )

    private val testEmailData = EmailData(
        email = "test@example.com",
        attemptId = "attempt123",
    )

    private val testEmailConfirmationJobRecord = EmailConfirmationJobRecord(
        brokerName = testBrokerName,
        userProfileId = testProfileQuery.id,
        extractedProfileId = testExtractedProfile.dbId,
        emailData = testEmailData,
        dateCreatedInMillis = testCurrentTime,
    )

    private val testEmailConfirmationJobRecord2 = EmailConfirmationJobRecord(
        brokerName = testBrokerName2,
        userProfileId = testProfileQuery2.id,
        extractedProfileId = testExtractedProfile2.dbId,
        emailData = testEmailData.copy(email = "test2@example.com"),
        dateCreatedInMillis = testCurrentTime,
    )

    private val testEmailConfirmationStep = EmailConfirmationStep(
        broker = testBroker1,
        step = OptOutStepActions(
            stepType = "optOut",
            actions = emptyList(),
            optOutType = "form",
        ),
        emailConfirmationJob = testEmailConfirmationJobRecord,
        profileToOptOut = testExtractedProfile,
    )

    private val testEmailConfirmationStep2 = EmailConfirmationStep(
        broker = testBroker2,
        step = OptOutStepActions(
            stepType = "optOut",
            actions = emptyList(),
            optOutType = "form",
        ),
        emailConfirmationJob = testEmailConfirmationJobRecord2,
        profileToOptOut = testExtractedProfile2,
    )

    @Test
    fun whenEmptyJobRecordsThenDontCreateRunners() = runTest {
        val result = testee.executeForEmailConfirmationJobs(emptyList(), mockContext, RunType.MANUAL)

        assert(result.isSuccess)
        verifyNoInteractions(mockRepository)
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockPirActionsRunner)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenNoBrokerStepsThenDontCreateRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(null)

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenBrokerStepsEmptyThenDontCreateRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn("")

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenBrokerStepsParsingReturnsNullThenDontCreateRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(null)

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker1,
            testStepsJson,
            testEmailConfirmationJobRecord,
        )
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenNoRelevantProfilesThenDontCreateRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockRepository).getAllUserProfileQueries()
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenNoRelevantProfilesUsesDefaultProfileQueries() = runTest {
        val jobRecordWithDefaultProfile = testEmailConfirmationJobRecord.copy(
            userProfileId = DEFAULT_PROFILE_QUERIES[0].id,
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(emptyList())
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                jobRecordWithDefaultProfile,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(jobRecordWithDefaultProfile),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker1,
            testStepsJson,
            jobRecordWithDefaultProfile,
        )
        verify(mockPirCssScriptLoader).getScript()
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.MANUAL)
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenJobRecordHasNoMatchingProfileThenSkipsRecord() = runTest {
        val unknownProfileJobRecord = testEmailConfirmationJobRecord.copy(userProfileId = 999L)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                unknownProfileJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)

        val result = testee.executeForEmailConfirmationJobs(
            listOf(unknownProfileJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockRepository).getAllUserProfileQueries()
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenJobRecordHasNoMatchingBrokerThenSkipsRecord() = runTest {
        val unknownBrokerJobRecord = testEmailConfirmationJobRecord.copy(brokerName = "unknown-broker")
        whenever(mockRepository.getBrokerOptOutSteps("unknown-broker")).thenReturn(null)

        val result = testee.executeForEmailConfirmationJobs(
            listOf(unknownBrokerJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps("unknown-broker")
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenNoActiveBrokersThenDontCreateRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockRepository).getAllActiveBrokerObjects()
        verify(mockRepository).getAllUserProfileQueries()
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
        verifyNoInteractions(mockWebViewDataCleaner)
    }

    @Test
    fun whenValidJobRecordThenExecutesEmailConfirmation() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(testProfileQuery, listOf(testEmailConfirmationStep)))
            .thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockPirCssScriptLoader).getScript()
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.MANUAL)
        verify(mockPirActionsRunner).start(testProfileQuery, listOf(testEmailConfirmationStep))
        verify(mockPirActionsRunner).stop()
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenMultipleValidJobRecordsThenExecutesAllEmailConfirmations() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName2)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1, testBroker2))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker2,
                testStepsJson,
                testEmailConfirmationJobRecord2,
            ),
        ).thenReturn(testEmailConfirmationStep2)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord, testEmailConfirmationJobRecord2),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName2)
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker1,
            testStepsJson,
            testEmailConfirmationJobRecord,
        )
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker2,
            testStepsJson,
            testEmailConfirmationJobRecord2,
        )
        verify(mockPirCssScriptLoader).getScript()
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenDuplicateBrokerNamesThenParsesBrokerStepsForEachJobRecord() = runTest {
        val duplicateJobRecord = testEmailConfirmationJobRecord.copy(
            userProfileId = testProfileQuery2.id,
            extractedProfileId = 791L,
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                duplicateJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord, duplicateJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockRepository, times(1)).getBrokerOptOutSteps(testBrokerName)
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker1,
            testStepsJson,
            testEmailConfirmationJobRecord,
        )
        verify(mockBrokerStepsParser).parseEmailConfirmationStep(
            testBroker1,
            testStepsJson,
            duplicateJobRecord,
        )
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenExecuteForEmailConfirmationJobsWithScheduledRunTypeThenUsesScheduledType() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.SCHEDULED))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.SCHEDULED,
        )

        assert(result.isSuccess)
        verify(mockPirActionsRunnerFactory).create(mockContext, testScript, RunType.SCHEDULED)
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenStopThenCallsStopOnAllRunners() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        testee.stop()

        verify(mockPirActionsRunner, times(2)).stop()
        verify(mockWebViewDataCleaner, times(2)).cleanWebViewData()
    }

    @Test
    fun whenExecuteMultipleTimesWithoutStopThenCleansUpPreviousRun() = runTest {
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        verify(mockPirActionsRunner, times(3)).stop()
        verify(mockWebViewDataCleaner, times(2)).cleanWebViewData()
    }

    @Test
    fun whenMixOfValidAndInvalidJobRecordsThenExecutesOnlyValid() = runTest {
        val invalidJobRecord = testEmailConfirmationJobRecord.copy(
            userProfileId = 999L,
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                testEmailConfirmationJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                invalidJobRecord,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecord, invalidJobRecord),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockPirActionsRunner).start(eq(testProfileQuery), any())
        verify(mockPirActionsRunner, never()).start(eq(testProfileQuery2), any())
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }

    @Test
    fun whenDeprecatedProfileThenStillExecutesEmailConfirmation() = runTest {
        val deprecatedProfile = testProfileQuery.copy(deprecated = true)
        val jobRecordWithDeprecatedProfile = testEmailConfirmationJobRecord.copy(
            userProfileId = deprecatedProfile.id,
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(listOf(deprecatedProfile))
        whenever(
            mockBrokerStepsParser.parseEmailConfirmationStep(
                testBroker1,
                testStepsJson,
                jobRecordWithDeprecatedProfile,
            ),
        ).thenReturn(testEmailConfirmationStep)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, RunType.MANUAL))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(deprecatedProfile, listOf(testEmailConfirmationStep)))
            .thenReturn(Result.success(Unit))

        val result = testee.executeForEmailConfirmationJobs(
            listOf(jobRecordWithDeprecatedProfile),
            mockContext,
            RunType.MANUAL,
        )

        assert(result.isSuccess)
        verify(mockPirActionsRunner).start(deprecatedProfile, listOf(testEmailConfirmationStep))
        verify(mockWebViewDataCleaner).cleanWebViewData()
    }
}
