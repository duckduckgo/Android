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

package com.duckduckgo.pir.impl.optout

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
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
    private val mockEventsRepository: PirEventsRepository = mock()
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
            eventsRepository = mockEventsRepository,
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
        verify(mockEventsRepository, atLeast(1)).saveEventLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenNoUserProfilesAvailableThenDontCreateRunners() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(emptyList())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenNoUserProfilesUsesDefaultProfileQueries() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(emptyList())
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
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(null)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenJobRecordHasNoMatchingProfileQueryThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        val unknownProfileJobRecord = testOptOutJobRecord.copy(userProfileId = 999L)
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(unknownProfileJobRecord), mockContext)

        // Then
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verify(mockBrokerStepsParser, never()).parseStep(any(), any(), any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenBrokerStepsParsingFailsThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
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
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenExtractedProfileNotFoundInBrokerStepsThenSkipsRecordAndDontCreateRunner() = runTest {
        // Given
        val stepWithDifferentProfile =
            testOptOutStep.copy(profileToOptOut = testExtractedProfile.copy(dbId = 999L))
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
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
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenValidJobRecordThenExecutesOptOut() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
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
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenJobRecordUsedCachedBrokerStepsThenDoesNotReparseSteps() = runTest {
        // Given
        val duplicateJobRecord =
            testOptOutJobRecord.copy(extractedProfileId = testExtractedProfile.dbId + 1)
        val duplicateOptOutStep =
            testOptOutStep.copy(profileToOptOut = testExtractedProfile.copy(dbId = testExtractedProfile.dbId + 1))

        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
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
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenDeprecatedProfileWithExtractedProfileThenExecutesOptOut() = runTest {
        // Given - deprecated profile with extracted profile should still process opt-out
        val deprecatedProfile = testProfileQuery.copy(deprecated = true)
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(listOf(deprecatedProfile))
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                deprecatedProfile.id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT)).thenReturn(
            mockPirActionsRunner,
        )
        whenever(mockPirActionsRunner.start(deprecatedProfile, listOf(testOptOutStep))).thenReturn(
            Result.success(Unit),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, deprecatedProfile.id)
        verify(mockPirActionsRunner).start(deprecatedProfile, listOf(testOptOutStep))
        verify(mockPirActionsRunner).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenMixOfDeprecatedAndNonDeprecatedProfilesThenExecutesOptOutForBoth() = runTest {
        // Given
        val deprecatedProfile = testProfileQuery.copy(deprecated = true)
        val nonDeprecatedProfile = testProfileQuery2.copy(deprecated = false)
        val extractedProfileDeprecated = testExtractedProfile.copy(
            dbId = 800L,
            profileQueryId = deprecatedProfile.id,
        )
        val extractedProfileNonDeprecated = testExtractedProfile.copy(
            dbId = 801L,
            profileQueryId = nonDeprecatedProfile.id,
        )
        val optOutStepDeprecated = testOptOutStep.copy(profileToOptOut = extractedProfileDeprecated)
        val optOutStepNonDeprecated = testOptOutStep.copy(profileToOptOut = extractedProfileNonDeprecated)
        val jobRecordDeprecated = testOptOutJobRecord.copy(
            userProfileId = deprecatedProfile.id,
            extractedProfileId = extractedProfileDeprecated.dbId,
        )
        val jobRecordNonDeprecated = testOptOutJobRecord.copy(
            userProfileId = nonDeprecatedProfile.id,
            extractedProfileId = extractedProfileNonDeprecated.dbId,
        )

        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(
            listOf(deprecatedProfile, nonDeprecatedProfile),
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                deprecatedProfile.id,
            ),
        ).thenReturn(listOf(optOutStepDeprecated))
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                nonDeprecatedProfile.id,
            ),
        ).thenReturn(listOf(optOutStepNonDeprecated))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(
            listOf(jobRecordDeprecated, jobRecordNonDeprecated),
            mockContext,
        )

        // Then - both deprecated and non-deprecated profiles should execute
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, deprecatedProfile.id)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, nonDeprecatedProfile.id)
        verify(mockPirActionsRunner, times(2)).start(any(), any())
        verify(mockPirActionsRunner, times(2)).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenMultipleJobRecordsForDifferentBrokersThenExecutesAllOptOuts() = runTest {
        // Given
        val extractedProfile2 = testExtractedProfile.copy(
            dbId = 888L,
            brokerName = testBrokerName2,
        )
        val optOutStep2 = testOptOutStep.copy(
            brokerName = testBrokerName2,
            profileToOptOut = extractedProfile2,
        )
        val jobRecord2 = testOptOutJobRecord.copy(
            brokerName = testBrokerName2,
            extractedProfileId = extractedProfile2.dbId,
        )
        val stepsJson2 = """{"stepType":"optOut","actions":[]}"""

        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName2)).thenReturn(stepsJson2)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName2,
                stepsJson2,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(optOutStep2))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord, jobRecord2), mockContext)

        // Then
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName2)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery.id)
        verify(mockBrokerStepsParser).parseStep(testBrokerName2, stepsJson2, testProfileQuery.id)
        verify(mockPirActionsRunner, times(2)).start(any(), any())
        verify(mockPirActionsRunner, times(2)).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenMultipleJobRecordsForSameBrokerDifferentProfilesThenExecutesAllOptOuts() = runTest {
        // Given
        val extractedProfile2 = testExtractedProfile.copy(
            dbId = 890L,
            profileQueryId = testProfileQuery2.id,
        )
        val optOutStep2 = testOptOutStep.copy(profileToOptOut = extractedProfile2)
        val jobRecord2 = testOptOutJobRecord.copy(
            userProfileId = testProfileQuery2.id,
            extractedProfileId = extractedProfile2.dbId,
        )

        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery2.id,
            ),
        ).thenReturn(listOf(optOutStep2))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord, jobRecord2), mockContext)

        // Then
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery.id)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery2.id)
        verify(mockPirActionsRunner, times(2)).start(any(), any())
        verify(mockPirActionsRunner, times(2)).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenStopCalledThenCleansUpRunners() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
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
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // Execute opt-out to create runners
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // When
        testee.stop()

        // Then - stop should be called twice: once during execution, once during stop()
        verify(mockPirActionsRunner, times(2)).stop()
    }

    @Test
    fun whenExecuteWithBrokersListThenExecutesOptOut() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery.id,
            ),
        ).thenReturn(listOf(testOptOutStep))
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                testProfileQuery2.id,
            ),
        ).thenReturn(emptyList())
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT)).thenReturn(
            mockPirActionsRunner,
        )
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.execute(listOf(testBrokerName), mockContext)

        // Then
        verify(mockRepository).getAllUserProfileQueries()
        verify(mockRepository).getBrokerOptOutSteps(testBrokerName)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery.id)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, testProfileQuery2.id)
        verify(mockPirActionsRunner).start(any(), any())
        verify(mockPirActionsRunner).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenExecuteWithNoBrokersListThenDoesNothing() = runTest {
        // Given
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)

        // When
        testee.execute(emptyList(), mockContext)

        // Then
        verify(mockRepository).getAllUserProfileQueries()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verify(mockPirCssScriptLoader).getScript()
        verifyNoInteractions(mockBrokerStepsParser)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenExecuteForBrokersWithRecordsThenDelegatestoExecute() = runTest {
        // Given
        val brokers = listOf(testBrokerName, testBrokerName2)
        whenever(mockRepository.getBrokersForOptOut(formOptOutOnly = true)).thenReturn(brokers)
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName2)).thenReturn(testStepsJson)
        whenever(mockBrokerStepsParser.parseStep(any(), any(), any())).thenReturn(emptyList())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeForBrokersWithRecords(mockContext)

        // Then
        verify(mockRepository).getBrokersForOptOut(formOptOutOnly = true)
        verify(mockRepository).getAllUserProfileQueries()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }

    @Test
    fun whenDeprecatedProfileWithNoExtractedProfileThenSkipsOptOut() = runTest {
        // Given - deprecated profile but no matching extracted profile in steps
        val deprecatedProfile = testProfileQuery.copy(deprecated = true)
        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(listOf(deprecatedProfile))
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                deprecatedProfile.id,
            ),
        ).thenReturn(emptyList()) // No extracted profiles found
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)

        // Then
        verify(mockEventsRepository, times(2)).saveEventLog(any())
        verifyNoInteractions(mockPirCssScriptLoader)
        verifyNoInteractions(mockPirActionsRunnerFactory)
    }

    @Test
    fun whenAllDeprecatedProfilesWithExtractedProfilesThenExecutesOptOut() = runTest {
        // Given
        val deprecatedProfile1 = testProfileQuery.copy(deprecated = true)
        val deprecatedProfile2 = testProfileQuery2.copy(deprecated = true)
        val extractedProfile1 = testExtractedProfile.copy(profileQueryId = deprecatedProfile1.id)
        val extractedProfile2 = testExtractedProfile.copy(
            dbId = 891L,
            profileQueryId = deprecatedProfile2.id,
        )
        val optOutStep1 = testOptOutStep.copy(profileToOptOut = extractedProfile1)
        val optOutStep2 = testOptOutStep.copy(profileToOptOut = extractedProfile2)
        val jobRecord1 = testOptOutJobRecord.copy(
            userProfileId = deprecatedProfile1.id,
            extractedProfileId = extractedProfile1.dbId,
        )
        val jobRecord2 = testOptOutJobRecord.copy(
            userProfileId = deprecatedProfile2.id,
            extractedProfileId = extractedProfile2.dbId,
        )

        whenever(mockRepository.getAllUserProfileQueries()).thenReturn(
            listOf(deprecatedProfile1, deprecatedProfile2),
        )
        whenever(mockRepository.getBrokerOptOutSteps(testBrokerName)).thenReturn(testStepsJson)
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                deprecatedProfile1.id,
            ),
        ).thenReturn(listOf(optOutStep1))
        whenever(
            mockBrokerStepsParser.parseStep(
                testBrokerName,
                testStepsJson,
                deprecatedProfile2.id,
            ),
        ).thenReturn(listOf(optOutStep2))
        whenever(mockPirCssScriptLoader.getScript()).thenReturn(testScript)
        whenever(mockPirActionsRunnerFactory.create(mockContext, testScript, OPTOUT))
            .thenReturn(mockPirActionsRunner)
        whenever(mockPirActionsRunner.start(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.executeOptOutForJobs(listOf(jobRecord1, jobRecord2), mockContext)

        // Then - all deprecated profiles with extracted profiles should execute
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, deprecatedProfile1.id)
        verify(mockBrokerStepsParser).parseStep(testBrokerName, testStepsJson, deprecatedProfile2.id)
        verify(mockPirActionsRunner, times(2)).start(any(), any())
        verify(mockPirActionsRunner, times(2)).stop()
        verify(mockEventsRepository, times(2)).saveEventLog(any())
    }
}
