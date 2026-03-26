package com.duckduckgo.app.survey.api

import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.UserBrowserProperties
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

@RunWith(AndroidJUnit4::class)
class SurveyRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var surveyDao: SurveyDao
    private lateinit var notificationManager: NotificationManagerCompat
    private val userBrowserProperties: UserBrowserProperties = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var surveyRepository: SurveyRepository

    @Before
    fun setup() {
        surveyDao = FakeSurveyDao()
        notificationManager = NotificationManagerCompat.from(context)
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)

        surveyRepository = SurveyRepositoryImpl(surveyDao, userBrowserProperties, notificationManager, appBuildConfig)
    }

    @Test
    fun whenSurveyUrlIsNullThenSurveyIsNotEligible() {
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = null,
            url = null,
        )

        assertFalse(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenLocaleIsNotAllowedThenSurveyIsNotEligible() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.JAPAN)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = null,
            url = "https://survey.com",
        )

        assertFalse(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledNullThenSurveyIsNotEligible() {
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = null,
            url = "https://survey.com",
        )

        assertFalse(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledMinusOneThenSurveyIsEligible() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(0)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = -1,
            url = "https://survey.com",
        )

        assertTrue(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenRetentionDayBellowRequiredDaysThenUserIsEligibleForSurvey() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(0)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = 1,
            url = "https://survey.com",
        )

        assertTrue(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenLocaleIsNotAllowedAndRequiredDaysInstalledMetThenSurveyIsNotEligible() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.JAPAN)
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(0)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = -1,
            url = "https://survey.com",
        )

        assertFalse(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenLocaleIsCanadaAndRequiredDaysInstalledMetThenSurveyIsEligible() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.CANADA)
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(0)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = -1,
            url = "https://survey.com",
        )

        assertTrue(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenLocaleIsUKAndRequiredDaysInstalledMetThenSurveyIsEligible() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.UK)
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(0)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = -1,
            url = "https://survey.com",
        )

        assertTrue(surveyRepository.isUserEligibleForSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledIsNullAndRetentionDaysBelowDefaultDaysThenReturnMinus1() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(4)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = null,
            url = null,
        )

        assertEquals(-1, surveyRepository.remainingDaysForShowingSurvey(survey))
        assertFalse(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledIsNullAndRetentionDaysAboveDefaultDaysThenReturn0() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(31)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = null,
            url = null,
        )

        assertEquals(0, surveyRepository.remainingDaysForShowingSurvey(survey))
        assertTrue(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledIsMinus1AndNotFirstDayThenRemainingDaysToSurveyIsZero() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(4)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = -1,
            url = null,
        )

        assertEquals(0, surveyRepository.remainingDaysForShowingSurvey(survey))
        assertTrue(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun whenRequiredDaysInstalledAndDaysInstallNotEnoughThenShouldNotShowSurvey() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(2)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = 4,
            url = null,
        )

        assertEquals(2, surveyRepository.remainingDaysForShowingSurvey(survey))
        assertFalse(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun whenRetentionDaysIsAboveRequiredDaysInstalledThenShouldNotShowSurvey() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(4)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = 2,
            url = null,
        )

        assertEquals(-2, surveyRepository.remainingDaysForShowingSurvey(survey))
        assertFalse(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun whenSurveyStatusIsDoneThenShouldNotShowSurvey() {
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(4)
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.DONE,
            daysInstalled = -1,
            url = null,
        )

        assertFalse(surveyRepository.shouldShowSurvey(survey))
    }

    @Test
    fun getScheduledSurveyReturnsLastSurvey() {
        val survey = Survey(
            surveyId = "1",
            status = Survey.Status.SCHEDULED,
            daysInstalled = 2,
            url = null,
        )

        surveyDao.insert(survey.copy(surveyId = "1"))
        surveyDao.insert(survey.copy(surveyId = "2"))
        surveyDao.insert(survey.copy(surveyId = "3"))
        surveyDao.insert(survey.copy(surveyId = "5"))

        assertEquals(survey.copy(surveyId = "5"), surveyRepository.getScheduledSurvey())
    }

    @Test
    fun givenExistingSurveyWhenCheckingIfSurveyExistThenReturnTrue() {
        val survey = Survey(
            surveyId = "id",
            status = Survey.Status.SCHEDULED,
            daysInstalled = 4,
            url = null,
        )

        surveyRepository.persistSurvey(survey)
        assertTrue(surveyRepository.surveyExists("id"))
    }

    @Test
    fun givenNonExistingSurveyWhenCheckingIfSurveyExistThenReturnFalse() {
        assertFalse(surveyRepository.surveyExists("id"))
    }
}

private class FakeSurveyDao : SurveyDao {
    private val surveys: MutableMap<String, Survey> = linkedMapOf()
    override fun insert(survey: Survey) {
        surveys[survey.surveyId] = survey
    }

    override fun update(survey: Survey) {
        insert(survey)
    }

    override fun get(surveyId: String): Survey? {
        return surveys[surveyId]
    }

    override fun getLiveScheduled(): LiveData<Survey> {
        TODO("Not yet implemented")
    }

    override fun getScheduled(): List<Survey> {
        return surveys.values.filter { it.status == Survey.Status.SCHEDULED }
    }

    override fun deleteUnusedSurveys() {
        surveys.values
            .filter {
                it.status == Survey.Status.SCHEDULED || it.status == Survey.Status.NOT_ALLOCATED
            }.map { it.surveyId }
            .forEach { surveys.remove(it) }
    }
}
