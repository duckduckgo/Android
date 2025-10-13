/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.survey.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.NOT_ALLOCATED
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.networkprotection.impl.cohort.NetpCohortStore
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import retrofit2.Call
import retrofit2.Response
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SurveyDownloaderTest {
    private var mockService: SurveyService = mock()
    private var mockEmailManager: EmailManager = mock()
    private var mockCall: Call<SurveyGroup?> = mock()
    private val mockSurveyRepository: SurveyRepository = mock()
    private lateinit var netpCohortStore: NetpCohortStore
    private lateinit var testee: SurveyDownloader

    private val testSurvey = Survey("abc", SURVEY_URL, 7, SCHEDULED)

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun setup() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.error(404, "error".toResponseBody()))
        whenever(mockSurveyRepository.isUserEligibleForSurvey(testSurvey)).thenReturn(true)
        netpCohortStore = FakeNetpCohortStore()
        val netpMockCall: Call<SurveyGroup?> = mock()
        whenever(netpMockCall.execute()).thenReturn(Response.error(400, "".toResponseBody()))
        whenever(mockService.surveyNetPWaitlistBeta()).thenReturn(netpMockCall)

        testee = SurveyDownloader(mockService, mockEmailManager, mockSurveyRepository, netpCohortStore)
    }

    @Test
    fun whenNewSurveyAllocatedThenSavedAsScheduledAndUnusedSurveysDeleted() {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository).persistSurvey(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockSurveyRepository).deleteUnusedSurveys()
    }

    @Test
    fun whenNewSurveyNotAllocatedThenSavedAsUnallocatedAndUnusedSurveysDeleted() {
        val surveyNotAllocated = Survey("abc", null, null, NOT_ALLOCATED)
        whenever(mockSurveyRepository.isUserEligibleForSurvey(surveyNotAllocated)).thenReturn(true)
        whenever(mockCall.execute()).thenReturn(Response.success(surveyNoAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository).persistSurvey(surveyNotAllocated)
        verify(mockSurveyRepository).deleteUnusedSurveys()
    }

    @Test
    fun whenSurveyAlreadyExistsThenNotSavedAndUnusedSurveysNotDeleted() {
        whenever(mockSurveyRepository.surveyExists(any())).thenReturn(true)
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository, never()).persistSurvey(any())
        verify(mockSurveyRepository, never()).deleteUnusedSurveys()
    }

    @Test
    fun whenSuccessfulRequestReturnsNoSurveysThenUnusedSurveysDeleted() {
        whenever(mockCall.execute()).thenReturn(Response.success(null))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository).deleteUnusedSurveys()
    }

    @Test(expected = RuntimeException::class)
    fun whenRequestUnsuccessfulThenExceptionThrown() {
        whenever(mockCall.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
    }

    @Test
    fun whenSurveyForEmailReceivedAndUserIsSignedInThenCreateSurveyWithCorrectCohort() {
        val surveyWithCohort = Survey("abc", SURVEY_URL_WITH_COHORT, -1, SCHEDULED)
        whenever(mockSurveyRepository.isUserEligibleForSurvey(surveyWithCohort)).thenReturn(true)
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocationForEmail("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository).persistSurvey(surveyWithCohort)
    }

    @Test
    fun whenSurveyForEmailReceivedAndUserIsNotSignedInThenDoNotCreateSurvey() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocationForEmail("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository, never()).persistSurvey(any())
    }

    @Test
    fun whenNewSurveyAllocatedAndUserIsEligibleThenSavedAsScheduled() = runTest {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository).persistSurvey(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockSurveyRepository).deleteUnusedSurveys()
    }

    @Test
    fun whenNewSurveyAllocatedAndUserIsNotEligibleThenSavedAsScheduled() = runTest {
        whenever(mockSurveyRepository.isUserEligibleForSurvey(testSurvey)).thenReturn(false)
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockSurveyRepository, never()).persistSurvey(any())
    }

    private fun surveyWithAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(
                url = SURVEY_URL,
                installationDay = 1,
                ratioOfUsersToShow = 0.0,
                isEmailSignedInRequired = null,
                urlParameters = emptyList(),
                isNetPOnboardedRequired = null,
                daysSinceNetPEnabled = null,
            ),
            SurveyGroup.SurveyOption(
                url = SURVEY_URL,
                installationDay = 7,
                ratioOfUsersToShow = 1.0,
                isEmailSignedInRequired = null,
                urlParameters = emptyList(),
                isNetPOnboardedRequired = null,
                daysSinceNetPEnabled = null,
            ),
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyNoAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(
                url = SURVEY_URL,
                installationDay = 1,
                ratioOfUsersToShow = 0.0,
                isEmailSignedInRequired = null,
                urlParameters = emptyList(),
                isNetPOnboardedRequired = null,
                daysSinceNetPEnabled = null,
            ),
            SurveyGroup.SurveyOption(
                url = SURVEY_URL,
                installationDay = 7,
                ratioOfUsersToShow = 0.0,
                isEmailSignedInRequired = null,
                urlParameters = emptyList(),
                isNetPOnboardedRequired = null,
                daysSinceNetPEnabled = null,
            ),
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyWithAllocationForEmail(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(
                url = SURVEY_URL,
                installationDay = -1,
                ratioOfUsersToShow = 1.0,
                isEmailSignedInRequired = true,
                urlParameters = listOf(SurveyUrlParameter.EmailCohortParam.parameter),
                isNetPOnboardedRequired = null,
                daysSinceNetPEnabled = null,
            ),
        )
        return SurveyGroup(id, surveyOptions)
    }

    companion object {
        const val SURVEY_URL = "https://survey.com"
        const val SURVEY_URL_WITH_COHORT = "https://survey.com?cohort=cohort"
    }
}

private class FakeNetpCohortStore(
    override var cohortLocalDate: LocalDate? = null,
) : NetpCohortStore
