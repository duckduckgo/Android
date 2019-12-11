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
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
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
    private val onboardingStore: OnboardingStore
) {

    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    private var activeSurvey: Survey? = null

    fun onSurveyChanged(survey: Survey?): Survey? {
        activeSurvey = survey
        return activeSurvey
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

    fun hideTipsForever(cta: Cta) {
        settingsDataStore.hideTips = true
        pixel.fire(Pixel.PixelName.ONBOARDING_DAX_ALL_CTA_HIDDEN, cta.pixelCancelParameters())
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
                !variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest) &&
                !variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ExistingNoCTA)
    }

    @WorkerThread
    private fun canShowDaxIntroCta(): Boolean = !dismissedCtaDao.exists(CtaId.DAX_INTRO) &&
            !settingsDataStore.hideTips &&
            variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)

    @WorkerThread
    private fun canShowDaxCtaEndOfJourney(site: Site?): Boolean = !dismissedCtaDao.exists(CtaId.DAX_END) && dismissedCtaDao.exists(CtaId.DAX_INTRO) &&
            variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest) &&
            !settingsDataStore.hideTips &&
            site == null &&
            (dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK) || dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER) ||
                    dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP) || dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND))

    @WorkerThread
    private fun shouldShowDaxCta(site: Site?): Cta? {
        if (settingsDataStore.hideTips || !variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)) {
            return null
        }
        site?.let {
            // is major network
            if (it.memberNetwork != null) {
                val network = site.memberNetwork
                if (DaxDialogCta.MAIN_TRACKER_NETWORKS.contains(network?.name) && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)) {
                    return DaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, network!!.name, it.uri!!.host!!)
                }
            }
            // is serp
            if (it.url.contains(DaxDialogCta.SERP) && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)) {
                return DaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
            }
            // trackers blocked
            return if (!it.url.contains(DaxDialogCta.SERP) && it.trackerCount > 0 && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)) {
                DaxDialogCta.DaxTrackersBlockedCta(onboardingStore, appInstallStore, it.trackingEvents, it.uri!!.host!!)
            } else if (!it.url.contains(DaxDialogCta.SERP) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)
            ) {
                DaxDialogCta.DaxNoSerpCta(onboardingStore, appInstallStore)
            } else {
                null
            }
        }
        return null
    }

    fun onCtaShown(cta: Cta) {
        cta.shownPixel?.let {
            pixel.fire(it, cta.pixelShownParameters())
        }
    }

    fun registerDaxBubbleCtaShown(cta: Cta) {
        Schedulers.io().scheduleDirect {
            if (cta is DaxBubbleCta) {
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
            when (cta) {
                is HomePanelCta.Survey -> {
                    activeSurvey = null
                    surveyDao.cancelScheduledSurveys()
                }
                else -> {
                    dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                }
            }
            //ctaViewState.postValue(currentViewState.copy(cta = null))
            //refreshCta()
        }
    }

    fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
    }
}