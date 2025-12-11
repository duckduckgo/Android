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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealBrokerStepsParserTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealBrokerStepsParser
    private val mockRepository: PirRepository = mock()
    private lateinit var moshi: Moshi

    private val testBroker = Broker(
        name = "testBroker",
        fileName = "testBroker.json",
        url = "https://testbroker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 1000L,
        removedAt = 0L,
    )

    private val testProfile1 = ExtractedProfile(
        dbId = 1L,
        profileQueryId = 100L,
        brokerName = "testBroker",
        name = "John Doe",
        age = "35",
        addresses = listOf(AddressCityState(city = "New York", state = "NY")),
    )

    private val testProfile2 = ExtractedProfile(
        dbId = 2L,
        profileQueryId = 100L,
        brokerName = "testBroker",
        name = "Jane Smith",
        age = "28",
        addresses = listOf(AddressCityState(city = "Los Angeles", state = "CA")),
    )

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(BrokerAction::class.java, "actionType")
                    .withSubtype(BrokerAction.Extract::class.java, "extract")
                    .withSubtype(BrokerAction.Expectation::class.java, "expectation")
                    .withSubtype(BrokerAction.Click::class.java, "click")
                    .withSubtype(BrokerAction.FillForm::class.java, "fillForm")
                    .withSubtype(BrokerAction.Navigate::class.java, "navigate")
                    .withSubtype(BrokerAction.GetCaptchaInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation")
                    .withSubtype(BrokerAction.Condition::class.java, "condition"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerStepActions::class.java, "stepType")
                    .withSubtype(ScanStepActions::class.java, "scan")
                    .withSubtype(OptOutStepActions::class.java, "optOut"),
            )
            .add(KotlinJsonAdapterFactory())
            .build()

        testee = RealBrokerStepsParser(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            repository = mockRepository,
            moshi = moshi,
        )
    }

    @Test
    fun whenParseStepWithScanJsonThenReturnScanStep() = runTest {
        val scanJson = """
            {
                "stepType": "scan",
                "scanType": "automated",
                "actions": [
                    {
                        "actionType": "navigate",
                        "id": "nav-1",
                        "url": "https://testbroker.com/search"
                    }
                ]
            }
        """.trimIndent()

        val result = testee.parseStep(testBroker, scanJson, null)

        assertEquals(1, result.size)
        assertTrue(result[0] is ScanStep)
        val scanStep = result[0] as ScanStep
        assertEquals(testBroker, scanStep.broker)
        assertEquals("scan", scanStep.step.stepType)
        assertEquals("automated", scanStep.step.scanType)
        assertEquals(1, scanStep.step.actions.size)
    }

    @Test
    fun whenParseStepWithOptOutJsonAndProfileIdThenReturnOptOutStepsForEachProfile() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": [
                    {
                        "actionType": "navigate",
                        "id": "nav-1",
                        "url": "https://testbroker.com/optout"
                    },
                    {
                        "actionType": "fillForm",
                        "id": "fill-1",
                        "selector": "#optout-form",
                        "elements": []
                    }
                ]
            }
        """.trimIndent()
        val profileQueryId = 100L

        whenever(mockRepository.getExtractedProfiles(eq("testBroker"), eq(profileQueryId)))
            .thenReturn(listOf(testProfile1, testProfile2))

        val result = testee.parseStep(testBroker, optOutJson, profileQueryId)

        assertEquals(2, result.size)
        assertTrue(result[0] is OptOutStep)
        assertTrue(result[1] is OptOutStep)

        val optOutStep1 = result[0] as OptOutStep
        assertEquals(testBroker, optOutStep1.broker)
        assertEquals(testProfile1, optOutStep1.profileToOptOut)
        assertEquals("optOut", optOutStep1.step.stepType)
        assertEquals("form", optOutStep1.step.optOutType)
        assertEquals(2, optOutStep1.step.actions.size)

        val optOutStep2 = result[1] as OptOutStep
        assertEquals(testProfile2, optOutStep2.profileToOptOut)
    }

    @Test
    fun whenParseStepWithOptOutJsonButNoProfileIdThenReturnEmptyList() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": []
            }
        """.trimIndent()

        val result = testee.parseStep(testBroker, optOutJson, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenParseStepWithOptOutJsonButNoExtractedProfilesThenReturnEmptyList() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": []
            }
        """.trimIndent()
        val profileQueryId = 100L

        whenever(mockRepository.getExtractedProfiles(eq("testBroker"), eq(profileQueryId)))
            .thenReturn(emptyList())

        val result = testee.parseStep(testBroker, optOutJson, profileQueryId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenParseStepWithMalformedJsonThenReturnEmptyList() = runTest {
        val malformedJson = """{ "invalid": json }"""

        val result = testee.parseStep(testBroker, malformedJson, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenParseStepWithEmptyJsonThenReturnEmptyList() = runTest {
        val emptyJson = ""

        val result = testee.parseStep(testBroker, emptyJson, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenParseEmailConfirmationStepThenReturnStepWithActionsFromEmailConfirmationOnwards() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": [
                    {
                        "actionType": "navigate",
                        "id": "nav-1",
                        "url": "https://testbroker.com/optout"
                    },
                    {
                        "actionType": "navigate",
                        "id": "nav-2",
                        "url": "https://testbroker.com/confirm"
                    },
                    {
                        "actionType": "emailConfirmation",
                        "id": "email-conf-1",
                        "pollingTime": "5000"
                    },
                    {
                        "actionType": "navigate",
                        "id": "nav-3",
                        "url": "https://testbroker.com/done"
                    }
                ]
            }
        """.trimIndent()

        val emailConfirmationJob = EmailConfirmationJobRecord(
            brokerName = "testBroker",
            userProfileId = 100L,
            extractedProfileId = 1L,
            emailData = EmailData(email = "test@example.com", attemptId = "attempt-123"),
            linkFetchData = LinkFetchData(emailConfirmationLink = "https://confirm.link"),
        )

        whenever(mockRepository.getExtractedProfile(eq(1L))).thenReturn(testProfile1)

        val result = testee.parseEmailConfirmationStep(testBroker, optOutJson, emailConfirmationJob)

        assertTrue(result is EmailConfirmationStep)
        val emailConfStep = result as? EmailConfirmationStep
        assertTrue(emailConfStep != null)
        assertEquals(testBroker, emailConfStep!!.broker)
        assertEquals(testProfile1, emailConfStep.profileToOptOut)
        assertEquals(emailConfirmationJob, emailConfStep.emailConfirmationJob)
        assertEquals("optOut", emailConfStep.step.stepType)
        assertEquals("form", emailConfStep.step.optOutType)

        assertEquals(2, emailConfStep.step.actions.size)
        assertTrue(emailConfStep.step.actions[0] is BrokerAction.EmailConfirmation)
        assertEquals("email-conf-1", emailConfStep.step.actions[0].id)
        assertTrue(emailConfStep.step.actions[1] is BrokerAction.Navigate)
    }

    @Test
    fun whenParseEmailConfirmationStepWithNoEmailConfirmationActionThenReturnStepWithEmptyActions() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": [
                    {
                        "actionType": "navigate",
                        "id": "nav-1",
                        "url": "https://testbroker.com/optout"
                    },
                    {
                        "actionType": "navigate",
                        "id": "nav-2",
                        "url": "https://testbroker.com/submit"
                    }
                ]
            }
        """.trimIndent()

        val emailConfirmationJob = EmailConfirmationJobRecord(
            brokerName = "testBroker",
            userProfileId = 100L,
            extractedProfileId = 1L,
            emailData = EmailData(email = "test@example.com", attemptId = "attempt-123"),
        )

        whenever(mockRepository.getExtractedProfile(eq(1L))).thenReturn(testProfile1)

        val result = testee.parseEmailConfirmationStep(testBroker, optOutJson, emailConfirmationJob)

        assertTrue(result is EmailConfirmationStep)
        val emailConfStep = result as? EmailConfirmationStep
        assertTrue(emailConfStep != null)
        assertTrue(emailConfStep!!.step.actions.isEmpty())
    }

    @Test
    fun whenParseEmailConfirmationStepWithMissingProfileThenReturnNull() = runTest {
        val optOutJson = """
            {
                "stepType": "optOut",
                "optOutType": "form",
                "actions": [
                    {
                        "actionType": "emailConfirmation",
                        "id": "email-conf-1",
                        "pollingTime": "5000"
                    }
                ]
            }
        """.trimIndent()

        val emailConfirmationJob = EmailConfirmationJobRecord(
            brokerName = "testBroker",
            userProfileId = 100L,
            extractedProfileId = 999L,
            emailData = EmailData(email = "test@example.com", attemptId = "attempt-123"),
        )

        whenever(mockRepository.getExtractedProfile(eq(999L))).thenReturn(null)

        val result = testee.parseEmailConfirmationStep(testBroker, optOutJson, emailConfirmationJob)

        assertNull(result)
    }

    @Test
    fun whenParseEmailConfirmationStepWithMalformedJsonThenReturnNull() = runTest {
        val malformedJson = """{ "invalid": json }"""

        val emailConfirmationJob = EmailConfirmationJobRecord(
            brokerName = "testBroker",
            userProfileId = 100L,
            extractedProfileId = 1L,
            emailData = EmailData(email = "test@example.com", attemptId = "attempt-123"),
        )

        whenever(mockRepository.getExtractedProfile(eq(1L))).thenReturn(testProfile1)

        val result = testee.parseEmailConfirmationStep(testBroker, malformedJson, emailConfirmationJob)

        assertNull(result)
    }

    @Test
    fun whenParseStepWithMultipleActionTypesThenReturnStepWithAllActions() = runTest {
        val scanJson = """
            {
                "stepType": "scan",
                "scanType": "automated",
                "actions": [
                    {
                        "actionType": "navigate",
                        "id": "nav-1",
                        "url": "https://testbroker.com/search"
                    },
                    {
                        "actionType": "emailConfirmation",
                        "id": "email-1",
                        "pollingTime": "5000"
                    }
                ]
            }
        """.trimIndent()

        val result = testee.parseStep(testBroker, scanJson, null)

        assertEquals(1, result.size)
        val scanStep = result[0] as ScanStep
        assertEquals(2, scanStep.step.actions.size)
        assertTrue(scanStep.step.actions[0] is BrokerAction.Navigate)
        assertTrue(scanStep.step.actions[1] is BrokerAction.EmailConfirmation)
    }
}
