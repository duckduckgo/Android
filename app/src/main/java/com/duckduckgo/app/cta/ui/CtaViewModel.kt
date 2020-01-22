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
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.withContext
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
    private val settingsPrivacySettingsStore: PrivacySettingsStore
) {

    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    private var activeSurvey: Survey? = null

    fun onSurveyChanged(survey: Survey?): Survey? {
        activeSurvey = survey
        return activeSurvey
    }

    fun hideTipsForever(cta: Cta) {
        settingsDataStore.hideTips = true
        pixel.fire(Pixel.PixelName.ONBOARDING_DAX_ALL_CTA_HIDDEN, cta.pixelCancelParameters())
    }

    fun onCtaShown(cta: Cta) {
        cta.shownPixel?.let {
            pixel.fire(it, cta.pixelShownParameters())
        }
    }

    fun registerDaxBubbleCtaShown(cta: Cta) {
        if (cta is DaxBubbleCta) {
            Schedulers.io().scheduleDirect {
                onCtaShown(cta)
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
        }
    }

    fun onUserDismissedCta(cta: Cta) {
        cta.cancelPixel?.let {
            pixel.fire(it, cta.pixelCancelParameters())
        }

        Schedulers.io().scheduleDirect {
            if (cta is HomePanelCta.Survey) {
                activeSurvey = null
                surveyDao.cancelScheduledSurveys()
            } else {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
        }
    }

    fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
    }

    suspend fun refreshCta(dispatcher: CoroutineContext, isNewTab: Boolean, site: Site? = null): Cta? {
        surveyCta()?.let {
            return it
        }

        return withContext(dispatcher) {
            when {
                canShowDaxIntroCta() && isNewTab -> {
                    DaxBubbleCta.DaxIntroCta(onboardingStore, appInstallStore)
                }
                canShowDaxCtaEndOfJourney(site) && isNewTab -> {
                    DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
                }
                shouldShowDaxCta(site) != null && !isNewTab -> {
                    shouldShowDaxCta(site)
                }
                canShowWidgetCta() && isNewTab -> {
                    if (widgetCapabilities.supportsAutomaticWidgetAdd) AddWidgetAuto else AddWidgetInstructions
                }
                else -> null
            }
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
                !dismissedCtaDao.exists(CtaId.ADD_WIDGET) &&
                !isFromConceptTestVariant() &&
                !isFromNoCtaVariant()
    }

    @WorkerThread
    private fun canShowDaxIntroCta(): Boolean = isFromConceptTestVariant() && !daxDialogIntroShown() && !settingsDataStore.hideTips

    @WorkerThread
    private fun canShowDaxCtaEndOfJourney(site: Site?): Boolean = isFromConceptTestVariant() &&
            hasPrivacySettingsOn() &&
            !daxDialogEndShown() &&
            daxDialogIntroShown() &&
            !settingsDataStore.hideTips &&
            site == null &&
            (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogSerpShown() || daxDialogTrackersFoundShown())

    @WorkerThread
    private fun shouldShowDaxCta(site: Site?): Cta? {
        if (settingsDataStore.hideTips || !isFromConceptTestVariant() || !hasPrivacySettingsOn()) {
            return null
        }

        val nonNullSite = site ?: return null

        nonNullSite.let {
            // Is major network
            val host = it.uri?.host
            if (it.entity != null && host != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && DaxDialogCta.MAIN_TRACKER_NETWORKS.contains(entity.displayName)) {
                        return DaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                    }
                }
            }
            // Is Serp
            if (!daxDialogSerpShown() && isSerpUrl(it.url)) {
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

    private fun isFromConceptTestVariant(): Boolean = variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)

    private fun isFromNoCtaVariant(): Boolean = variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ExistingNoCta)

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun isSerpUrl(url: String): Boolean = url.contains(DaxDialogCta.SERP)
}