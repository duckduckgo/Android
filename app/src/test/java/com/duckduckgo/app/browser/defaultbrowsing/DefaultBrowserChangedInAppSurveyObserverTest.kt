package com.duckduckgo.app.browser.defaultbrowsing

import android.app.Activity
import android.content.Intent
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultBrowserChangedInAppSurveyObserverTest {

    private val surveyManager: DefaultBrowserChangedSurveyManager = mock()
    private val browserActivity: BrowserActivity = mock()
    private val otherActivity: Activity = mock()
    private lateinit var observer: DefaultBrowserChangedInAppSurveyObserver

    @Before
    fun setup() {
        observer = DefaultBrowserChangedInAppSurveyObserver(surveyManager)
    }

    @Test
    fun whenNotBrowserActivityThenDoNothing() {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        whenever(surveyManager.wasNotificationSentThisSession()).thenReturn(false)

        observer.onActivityResumed(otherActivity)

        verify(surveyManager, never()).markSurveyDone()
    }

    @Test
    fun whenShouldNotTriggerSurveyThenDoNothing() {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(false)

        observer.onActivityResumed(browserActivity)

        verify(surveyManager, never()).markSurveyDone()
        verify(browserActivity, never()).startActivity(any())
    }

    @Test
    fun whenNotificationSentThisSessionThenDoNothing() {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        whenever(surveyManager.wasNotificationSentThisSession()).thenReturn(true)

        observer.onActivityResumed(browserActivity)

        verify(surveyManager, never()).markSurveyDone()
        verify(browserActivity, never()).startActivity(any())
    }

    @Test
    fun whenSurveyActivityCreatedWithPushSurveyIdThenMarkAsShown() {
        val surveyActivity: SurveyActivity = mock()
        val intent: Intent = mock()
        val survey = Survey("default-browser-changed-push", "https://example.com", null, Survey.Status.SCHEDULED)
        whenever(surveyActivity.intent).thenReturn(intent)
        @Suppress("DEPRECATION")
        whenever(intent.getSerializableExtra("SURVEY_EXTRA")).thenReturn(survey)

        observer.onActivityCreated(surveyActivity, null)

        verify(surveyManager).markSurveyDone()
    }

    @Test
    fun whenSurveyActivityCreatedWithInAppSurveyIdThenMarkAsShown() {
        val surveyActivity: SurveyActivity = mock()
        val intent: Intent = mock()
        val survey = Survey("default-browser-changed-inapp", "https://example.com", null, Survey.Status.SCHEDULED)
        whenever(surveyActivity.intent).thenReturn(intent)
        @Suppress("DEPRECATION")
        whenever(intent.getSerializableExtra("SURVEY_EXTRA")).thenReturn(survey)

        observer.onActivityCreated(surveyActivity, null)

        verify(surveyManager).markSurveyDone()
    }

    @Test
    fun whenSurveyActivityCreatedWithUnrelatedSurveyIdThenDoNotMarkAsShown() {
        val surveyActivity: SurveyActivity = mock()
        val intent: Intent = mock()
        val survey = Survey("some-other-survey", "https://example.com", null, Survey.Status.SCHEDULED)
        whenever(surveyActivity.intent).thenReturn(intent)
        @Suppress("DEPRECATION")
        whenever(intent.getSerializableExtra("SURVEY_EXTRA")).thenReturn(survey)

        observer.onActivityCreated(surveyActivity, null)

        verify(surveyManager, never()).markSurveyDone()
    }
}
