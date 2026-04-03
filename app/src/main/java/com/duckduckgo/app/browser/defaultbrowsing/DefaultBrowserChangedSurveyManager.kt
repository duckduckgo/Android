package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.Locale
import javax.inject.Inject

interface DefaultBrowserChangedSurveyManager {
    fun shouldTriggerSurvey(): Boolean
    fun markSurveyPending()
    fun markSurveyDone()
    fun recordNotificationSentThisSession()
    fun wasNotificationSentThisSession(): Boolean

    companion object {
        const val SURVEY_ID_PUSH = "default-browser-changed-push"
        const val SURVEY_ID_IN_APP = "default-browser-changed-inapp"
        val SURVEY_IDS = setOf(SURVEY_ID_PUSH, SURVEY_ID_IN_APP)
    }
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDefaultBrowserChangedSurveyManager @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val defaultBrowserChangedSurveyFeature: DefaultBrowserChangedSurveyFeature,
    private val appBuildConfig: AppBuildConfig,
) : DefaultBrowserChangedSurveyManager {

    private var notificationSentThisSession = false

    override fun shouldTriggerSurvey(): Boolean {
        return defaultBrowserChangedSurveyFeature.self().isEnabled() &&
            appBuildConfig.deviceLocale.language == Locale.ENGLISH.language &&
            appInstallStore.defaultBrowserChangedSurveyPending &&
            !appInstallStore.defaultBrowserChangedSurveyDone
    }

    override fun markSurveyPending() {
        if (!appInstallStore.defaultBrowserChangedSurveyDone) {
            appInstallStore.defaultBrowserChangedSurveyPending = true
        }
    }

    override fun markSurveyDone() {
        appInstallStore.defaultBrowserChangedSurveyPending = false
        appInstallStore.defaultBrowserChangedSurveyDone = true
    }

    override fun recordNotificationSentThisSession() {
        notificationSentThisSession = true
    }

    override fun wasNotificationSentThisSession(): Boolean {
        return notificationSentThisSession
    }
}
