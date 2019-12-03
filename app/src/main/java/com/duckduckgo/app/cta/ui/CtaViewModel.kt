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
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.*
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CtaViewModel @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val surveyDao: SurveyDao,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val variantManager: VariantManager
) {

    data class CtaViewState(
        val cta: Cta? = null
    )

    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    private val currentViewState: CtaViewState
        get() = ctaViewState.value!!

    private var activeSurvey: Survey? = null

    init {
        ctaViewState.value = CtaViewState()
    }

    fun onSurveyChanged(survey: Survey?) {
        activeSurvey = survey
        if (activeSurvey != null) {
            refreshCta()
        }
    }

    fun refreshCta(site: Site? = null) {
        surveyCta()?.let {
            ctaViewState.postValue(currentViewState.copy(cta = it))
            return
        }

        Schedulers.io().scheduleDirect {
            when {
                canShowDaxIntroCta() -> {
                    ctaViewState.postValue(currentViewState.copy(cta = DaxBubbleCta.DaxIntroCta))
                }
                canShowDaxCtaEndOfJourney(site) -> {
                    ctaViewState.postValue(currentViewState.copy(cta = DaxBubbleCta.DaxEndCta))
                }
                shouldShowDaxCta(site) != null -> {
                    ctaViewState.postValue(currentViewState.copy(cta = shouldShowDaxCta(site)))
                }
                canShowWidgetCta() -> {
                    val ctaType = if (widgetCapabilities.supportsAutomaticWidgetAdd) AddWidgetAuto else AddWidgetInstructions
                    ctaViewState.postValue(currentViewState.copy(cta = ctaType))
                }
                else -> ctaViewState.postValue(currentViewState.copy(cta = null))
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
                !variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest) &&
                !variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ExistingNoCTA)
    }

    @WorkerThread
    private fun canShowDaxIntroCta(): Boolean = !dismissedCtaDao.exists(CtaId.DAX_INTRO) &&
            variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)

    @WorkerThread
    private fun canShowDaxCtaEndOfJourney(site: Site?): Boolean = !dismissedCtaDao.exists(CtaId.DAX_END) && dismissedCtaDao.exists(CtaId.DAX_INTRO) &&
            variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest) &&
            site == null &&
            (dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK) || dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER) ||
                    dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP) || dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND))

    @WorkerThread
    private fun shouldShowDaxCta(site: Site?): Cta? {
        if (!variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)) {
            return null
        }
        site?.let {
            // is major network
            if (it.memberNetwork != null) {
                val network = site.memberNetwork
                if (DaxDialogCta.MAIN_TRACKER_NETWORKS.contains(network?.name) && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)) {
                    return DaxDialogCta.DaxMainNetworkCta(network!!.name, it.uri!!.host!!)
                }
            }
            // is serp
            if (it.url.contains(DaxDialogCta.SERP) && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)) {
                return DaxDialogCta.DaxSerpCta
            }
            // trackers blocked
            return if (!it.url.contains(DaxDialogCta.SERP) && it.trackerCount > 0 && !dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)) {
                DaxDialogCta.DaxTrackersBlockedCta(it.trackingEvents, it.uri!!.host!!)
            } else if (!it.url.contains(DaxDialogCta.SERP) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND) &&
                !dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)
            ) {
                DaxDialogCta.DaxNoSerpCta
            } else {
                null
            }
        }
        return null
    }

    fun onCtaShown() {
        currentViewState.cta?.shownPixel?.let {
            pixel.fire(it)
        }
    }

    fun onCtaDismissed() {
        val cta = currentViewState.cta ?: return
        cta.cancelPixel?.let {
            pixel.fire(it)
        }

        Schedulers.io().scheduleDirect {
            when (cta) {
                is HomePanelCta.Survey -> {
                    activeSurvey = null
                    surveyDao.cancelScheduledSurveys()
                }
                is DaxBubbleCta, is DaxDialogCta -> {
                    dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                    Timber.d("Marcos dismissed ${cta.ctaId}")
                    return@scheduleDirect
                }
                else -> {
                    dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                }
            }
            ctaViewState.postValue(currentViewState.copy(cta = null))
            refreshCta()
        }
    }

    fun onCtaLaunched() {
        currentViewState.cta?.okPixel?.let {
            pixel.fire(it)
        }
    }
}