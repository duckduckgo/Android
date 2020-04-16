/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.cta.ui

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.*
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.daxOnboardingActive
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class CtaViewModel @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val surveyDao: SurveyDao,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val variantManager: VariantManager,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val settingsPrivacySettingsStore: PrivacySettingsStore,
    private val userStageStore: UserStageStore,
    private val dispatchers: DispatcherProvider
) {
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    private var activeSurvey: Survey? = null

    private val requiredDaxOnboardingCtas: Array<CtaId> = arrayOf(
        CtaId.DAX_INTRO,
        CtaId.DAX_DIALOG_SERP,
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        CtaId.DAX_DIALOG_NETWORK,
        CtaId.DAX_END
    )

    fun onSurveyChanged(survey: Survey?): Survey? {
        activeSurvey = survey
        return activeSurvey
    }

    suspend fun hideTipsForever(cta: Cta) {
        settingsDataStore.hideTips = true
        pixel.fire(Pixel.PixelName.ONBOARDING_DAX_ALL_CTA_HIDDEN, cta.pixelCancelParameters())
        userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
    }

    fun onCtaShown(cta: Cta) {
        cta.shownPixel?.let {
            val canSendPixel = when (cta) {
                is DaxCta -> cta.canSendShownPixel()
                else -> true
            }
            if (canSendPixel) {
                pixel.fire(it, cta.pixelShownParameters())
            }
        }
    }

    suspend fun registerDaxBubbleCtaDismissed(cta: Cta) {
        withContext(dispatchers.io()) {
            if (cta is DaxBubbleCta) {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                completeStageIfDaxOnboardingCompleted()
            }
        }
    }

    private suspend fun completeStageIfDaxOnboardingCompleted() {
        if (daxOnboardingActive() && allOnboardingCtasShown()) {
            Timber.d("Completing DAX ONBOARDING")
            userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
        }
    }

    suspend fun onUserDismissedCta(cta: Cta) {
        withContext(dispatchers.io()) {
            cta.cancelPixel?.let {
                pixel.fire(it, cta.pixelCancelParameters())
            }

            if (cta is HomePanelCta.Survey) {
                activeSurvey = null
                surveyDao.cancelScheduledSurveys()
            } else {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }

            completeStageIfDaxOnboardingCompleted()
        }
    }

    fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
    }

    fun onUserClickCtaSecondaryButton(cta: SecondaryButtonCta) {
        cta.secondaryButtonPixel?.let {
            pixel.fire(it, cta.pixelSecondaryButtonParameters())
        }
    }

    suspend fun refreshCta(dispatcher: CoroutineContext, isBrowserShowing: Boolean, site: Site? = null): Cta? {
        surveyCta()?.let {
            return it
        }

        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getBrowserCta(site)
            } else {
                getHomeCta()
            }
        }
    }

    private suspend fun getHomeCta(): Cta? {
        return when {
            canShowDaxIntroCta() -> {
                DaxBubbleCta.DaxIntroCta(onboardingStore, appInstallStore)
            }
            canShowDaxCtaEndOfJourney() -> {
                DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
            }
            canShowWidgetCta() -> {
                if (widgetCapabilities.supportsAutomaticWidgetAdd) AddWidgetAuto else AddWidgetInstructions
            }
            else -> null
        }
    }

    private suspend fun getBrowserCta(site: Site?): Cta? {
        return when {
            canShowDaxDialogCta() -> {
                getDaxDialogCta(site)
            }
            else -> null
        }
    }

    private fun surveyCta(): HomePanelCta.Survey? {
        val survey = activeSurvey
        if (survey?.url != null) {
            val showOnDay = survey.daysInstalled?.toLong()
            val daysInstalled = appInstallStore.daysInstalled()
            if (showOnDay == null || showOnDay == daysInstalled) {
                return Survey(survey)
            }
        }
        return null
    }

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return widgetCapabilities.supportsStandardWidgetAdd &&
                !widgetCapabilities.hasInstalledWidgets &&
                !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    @WorkerThread
    private suspend fun canShowDaxIntroCta(): Boolean = daxOnboardingActive() && !daxDialogIntroShown() && !settingsDataStore.hideTips

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean = daxOnboardingActive() &&
            hasPrivacySettingsOn() &&
            !daxDialogEndShown() &&
            daxDialogIntroShown() &&
            !settingsDataStore.hideTips &&
            (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogSerpShown() || daxDialogTrackersFoundShown())

    private suspend fun canShowDaxDialogCta(): Boolean {
        if (!daxOnboardingActive() || settingsDataStore.hideTips || !hasPrivacySettingsOn()) {
            return false
        }
        return true
    }

    @WorkerThread
    private fun getDaxDialogCta(site: Site?): Cta? {
        val nonNullSite = site ?: return null

        nonNullSite.let {
            // Is major network
            val host = it.uri?.host
            if (it.entity != null && host != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && DaxDialogCta.mainTrackerNetworks.contains(entity.displayName)) {
                        return DaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                    }
                }
            }

            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return DaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
            }

            // Trackers blocked
            return if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && hasTrackersInformation(it.trackingEvents) && host != null) {
                DaxDialogCta.DaxTrackersBlockedCta(onboardingStore, appInstallStore, it.trackingEvents, host)
            } else if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                DaxDialogCta.DaxNoSerpCta(onboardingStore, appInstallStore)
            } else {
                null
            }
        }
    }

    private fun hasTrackersInformation(events: List<TrackingEvent>): Boolean =
        events.asSequence()
            .filter { it.entity?.isMajor == true }
            .map { it.entity?.displayName }
            .filterNotNull()
            .any()

    private fun hasPrivacySettingsOn(): Boolean = settingsPrivacySettingsStore.privacyOn

    private fun variant(): Variant = variantManager.getVariant()

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun isSerpUrl(url: String): Boolean = url.contains(DaxDialogCta.SERP)

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun allOnboardingCtasShown(): Boolean {
        return withContext(dispatchers.io()) {
            requiredDaxOnboardingCtas.all {
                dismissedCtaDao.exists(it)
            }
        }
    }
}