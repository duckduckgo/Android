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
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.NOT_ALLOCATED
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.mobile.android.vpn.cohort.AtpCohortManager
import com.duckduckgo.mobile.android.vpn.waitlist.store.AtpWaitlistStateRepository
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState
import org.mockito.kotlin.*
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Call
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class SurveyDownloaderTest {
    private var mockDao: SurveyDao = mock()
    private var mockService: SurveyService = mock()
    private var mockEmailManager: EmailManager = mock()
    private var mockAtpCohortManager: AtpCohortManager = mock()
    private var mockAtpWaitlistState: AtpWaitlistStateRepository = mock()
    private var mockVariantManager: VariantManager = mock()
    private var mockCall: Call<SurveyGroup?> = mock()
    private var testee = SurveyDownloader(mockService, mockDao, mockEmailManager, mockAtpCohortManager, mockAtpWaitlistState, mockVariantManager)

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun setup() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.error(404, "error".toResponseBody()))

        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
    }

    @Test
    fun whenAppTpSurveyContainsSurveyAndUserJoinedAfterCutDateThenReturnAppTpSurvey() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao).insert(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsWaitlistSurveyAndUserHasJoinedWaitlistAfterCutDateThenReturnAppTpWaitlistSurvey() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocationForAppTPWaitlist("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
        whenever(mockAtpWaitlistState.getState()).thenReturn(WaitlistState.JoinedWaitlist(true))
        whenever(mockAtpWaitlistState.joinedAfterCuttingDate()).thenReturn(true)

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao).insert(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsWaitlistSurveyAndUserHasJoinedWaitlistBeforeCutDateThenReturnAppTpWaitlistSurvey() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocationForAppTPWaitlist("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
        whenever(mockAtpWaitlistState.getState()).thenReturn(WaitlistState.JoinedWaitlist(true))
        whenever(mockAtpWaitlistState.joinedAfterCuttingDate()).thenReturn(false)

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao, never()).insert(any())
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsWaitlistSurveyAndUserHasNotJoinedWaitlistThenNoSurveyScheduled() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocationForAppTPWaitlist("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
        whenever(mockAtpWaitlistState.getState()).thenReturn(WaitlistState.NotJoinedQueue)

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao, never()).insert(any())
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsRetentionStudySurveyAndUserIsNotInRetentionStudyThenNoSurveyScheduled() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocationForAppTPRetentionStudy("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
        whenever(mockAtpWaitlistState.getState()).thenReturn(WaitlistState.NotJoinedQueue)
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.ACTIVE_VARIANTS.first { it.key == "na" })

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao, never()).insert(any())
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsRetentionStudySurveyAndUserInRetentionStudyThenSurveyScheduled() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(surveyWithAllocationForAppTPWaitlist("abc")))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)
        whenever(mockAtpWaitlistState.getState()).thenReturn(WaitlistState.JoinedWaitlist(true))
        whenever(mockAtpWaitlistState.joinedAfterCuttingDate()).thenReturn(true)
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.ACTIVE_VARIANTS.first { it.key == "nb" })

        testee.download().blockingAwait()

        verify(mockService, never()).survey()
        verify(mockDao).insert(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenAppTpSurveyContainsEmptySurveyThenCallV2Survey() {
        val mockAppTPCall = mock<Call<SurveyGroup?>>()
        whenever(mockAppTPCall.execute()).thenReturn(Response.success(null))
        whenever(mockService.surveyAppTp()).thenReturn(mockAppTPCall)

        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)

        testee.download().blockingAwait()

        verify(mockService).survey()
    }

    @Test
    fun whenNewSurveyAllocatedThenSavedAsScheduledAndUnusedSurveysDeleted() {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", SURVEY_URL, 7, SCHEDULED))
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenNewSurveyNotAllocatedThenSavedAsUnallocatedAndUnusedSurveysDeleted() {
        whenever(mockCall.execute()).thenReturn(Response.success(surveyNoAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", null, null, NOT_ALLOCATED))
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test
    fun whenSurveyAlreadyExistsThenNotSavedAndUnusedSurveysNotDeleted() {
        whenever(mockDao.exists(any())).thenReturn(true)
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocation("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao, never()).insert(any())
        verify(mockDao, never()).deleteUnusedSurveys()
    }

    @Test
    fun whenSuccessfulRequestReturnsNoSurveysThenUnusedSurveysDeleted() {
        whenever(mockCall.execute()).thenReturn(Response.success(null))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).deleteUnusedSurveys()
    }

    @Test(expected = RuntimeException::class)
    fun whenRequestUnsuccessfulThenExceptionThrown() {
        whenever(mockCall.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
    }

    @Test
    fun whenSurveyForEmailReceivedAndUserIsSignedInThenCreateSurveyWithCorrectCohort() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocationForEmail("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", SURVEY_URL_WITH_COHORT, -1, SCHEDULED))
    }

    @Test
    fun whenSurveyForEmailReceivedAndUserIsNotSignedInThenDoNotCreateSurvey() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocationForEmail("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)
        testee.download().blockingAwait()
        verify(mockDao, never()).insert(any())
    }

    @Test
    fun whenSurveyForAtpReceivedThenCreateSurvey() {
        whenever(mockAtpCohortManager.getCohort()).thenReturn("cohort")
        whenever(mockCall.execute()).thenReturn(Response.success(surveyWithAllocationForAtp("abc")))
        whenever(mockService.survey()).thenReturn(mockCall)

        testee.download().blockingAwait()
        verify(mockDao).insert(Survey("abc", SURVEY_URL_WITH_ATP_COHORT, -1, SCHEDULED))
    }

    private fun surveyWithAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 1, 0.0, null, null, null, null, emptyList()),
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 1.0, null, null, null, null, emptyList())
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyNoAllocation(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 1, 0.0, null, null, null, null, emptyList()),
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 0.0, null, null, null, null, emptyList())
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyWithAllocationForEmail(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, -1, 1.0, true, null, null, null, listOf(SurveyUrlParameter.EmailCohortParam.parameter))
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyWithAllocationForAtp(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, -1, 1.0, null, true, null, null, listOf(SurveyUrlParameter.AtpCohortParam.parameter))
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyWithAllocationForAppTPWaitlist(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 1.0, null, true, true, null, emptyList())
        )
        return SurveyGroup(id, surveyOptions)
    }

    private fun surveyWithAllocationForAppTPRetentionStudy(id: String): SurveyGroup {
        val surveyOptions = listOf(
            SurveyGroup.SurveyOption(SURVEY_URL, 7, 1.0, null, true, true, true, emptyList())
        )
        return SurveyGroup(id, surveyOptions)
    }

    companion object {
        const val SURVEY_URL = "https://survey.com"
        const val SURVEY_URL_WITH_COHORT = "https://survey.com?cohort=cohort"
        const val SURVEY_URL_WITH_ATP_COHORT = "https://survey.com?atp_cohort=cohort"
    }
}
