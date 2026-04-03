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
    fun markSurveyAsShown()
    fun recordDefaultSet()
    fun recordNotificationSentThisSession()
    fun wasNotificationSentThisSession(): Boolean
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
            appInstallStore.wasEverDefault &&
            !appInstallStore.defaultBrowser &&
            !appInstallStore.defaultBrowserChangedSurveyShown
    }

    override fun markSurveyAsShown() {
        appInstallStore.defaultBrowserChangedSurveyShown = true
    }

    override fun recordDefaultSet() {
        appInstallStore.wasEverDefault = true
    }

    override fun recordNotificationSentThisSession() {
        notificationSentThisSession = true
    }

    override fun wasNotificationSentThisSession(): Boolean {
        return notificationSentThisSession
    }
}
