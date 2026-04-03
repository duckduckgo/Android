package com.duckduckgo.app.browser.defaultbrowsing

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DefaultBrowserChangedInAppSurveyObserver @Inject constructor(
    private val defaultBrowserChangedSurveyManager: DefaultBrowserChangedSurveyManager,
) : ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is SurveyActivity) return
        @Suppress("DEPRECATION")
        val survey = activity.intent?.getSerializableExtra(SURVEY_EXTRA_KEY) as? Survey ?: return
        if (survey.surveyId in SURVEY_IDS) {
            logcat { "Survey opened from notification or in-app, marking as shown" }
            defaultBrowserChangedSurveyManager.markSurveyAsShown()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is BrowserActivity) return
        if (!defaultBrowserChangedSurveyManager.shouldTriggerSurvey()) return
        if (defaultBrowserChangedSurveyManager.wasNotificationSentThisSession()) return

        logcat { "Showing in-app default-browser-changed survey" }

        // Post to run after all lifecycle callbacks for this resume cycle complete,
        // avoiding race conditions with other observers that could reclaim focus.
        Handler(Looper.getMainLooper()).post {
            // Re-check in case state changed during the post delay
            if (!defaultBrowserChangedSurveyManager.shouldTriggerSurvey()) return@post

            defaultBrowserChangedSurveyManager.markSurveyAsShown()
            val survey = Survey(
                surveyId = SURVEY_ID_IN_APP,
                url = IN_APP_SURVEY_URL,
                daysInstalled = null,
                status = Survey.Status.SCHEDULED,
            )
            activity.startActivity(SurveyActivity.intent(activity, survey, SurveySource.IN_APP))
        }
    }

    companion object {
        // TODO replace once survey URL available
        const val IN_APP_SURVEY_URL = "https://example.com/test-survey?channel=in_app"
        private const val SURVEY_EXTRA_KEY = "SURVEY_EXTRA"
        private const val SURVEY_ID_PUSH = "default-browser-changed-push"
        private const val SURVEY_ID_IN_APP = "default-browser-changed-inapp"
        private val SURVEY_IDS = setOf(SURVEY_ID_PUSH, SURVEY_ID_IN_APP)
    }
}
