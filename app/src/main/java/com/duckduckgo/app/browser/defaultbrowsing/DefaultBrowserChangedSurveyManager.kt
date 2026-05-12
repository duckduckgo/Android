package com.duckduckgo.app.browser.defaultbrowsing

import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.Locale
import javax.inject.Inject

interface DefaultBrowserChangedSurveyManager {
    fun shouldTriggerSurvey(): Boolean
    fun areNotificationsEnabled(): Boolean
    fun markSurveyShown()
    fun buildSurveyUrl(channel: String): String

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
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val notificationManager: NotificationManagerCompat,
) : DefaultBrowserChangedSurveyManager {

    override fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    override fun shouldTriggerSurvey(): Boolean {
        return defaultBrowserChangedSurveyFeature.self().isEnabled() &&
            !appInstallStore.defaultBrowserChangedSurveyDone &&
            appBuildConfig.deviceLocale.language == Locale.ENGLISH.language &&
            appInstallStore.wasEverDefaultBrowser &&
            !defaultBrowserDetector.isDefaultBrowser()
    }

    override fun markSurveyShown() {
        appInstallStore.defaultBrowserChangedSurveyDone = true
    }

    override fun buildSurveyUrl(channel: String): String {
        return SURVEY_URL.toUri()
            .buildUpon()
            .appendQueryParameter("osv", "${appBuildConfig.sdkInt}")
            .appendQueryParameter("appVer", appBuildConfig.versionName)
            .appendQueryParameter("installAgeBucket", installAgeBucket())
            .appendQueryParameter("channel", channel)
            .build()
            .toString()
    }

    private fun installAgeBucket(): String {
        val days = appInstallStore.daysInstalled()
        return when {
            days <= 1 -> "d1"
            days <= 6 -> "d2_6"
            days <= 28 -> "w2_4"
            days <= 180 -> "m1_6"
            else -> "m6p"
        }
    }

    companion object {
        private const val SURVEY_URL = "https://duckduckgo.com/android-unset-as-default-survey"
    }
}
