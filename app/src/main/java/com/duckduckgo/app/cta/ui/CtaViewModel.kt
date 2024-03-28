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

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetAuto
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetInstructions
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.onboarding.ui.page.experiment.ExtendedOnboardingExperimentVariantManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
class CtaViewModel @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val userAllowListRepository: UserAllowListRepository,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val userStageStore: UserStageStore,
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val surveyRepository: SurveyRepository,
    private val extendedOnboardingExperimentVariantManager: ExtendedOnboardingExperimentVariantManager,
) {
    val surveyLiveData: LiveData<Survey> = surveyRepository.getScheduledLiveSurvey()

    @ExperimentalCoroutinesApi
    @VisibleForTesting
    val isFireButtonPulseAnimationFlowEnabled = MutableStateFlow(true)

    @FlowPreview
    @ExperimentalCoroutinesApi
    val showFireButtonPulseAnimation: Flow<Boolean> =
        isFireButtonPulseAnimationFlowEnabled
            .flatMapLatest {
                when (it) {
                    true -> getShowFireButtonPulseAnimationFlow()
                    false -> flowOf(false)
                }
            }

    private var activeSurvey: Survey? = null

    private val requiredDaxOnboardingCtas: Array<CtaId> by lazy {
        arrayOf(
            CtaId.DAX_INTRO,
            CtaId.DAX_DIALOG_SERP,
            CtaId.DAX_DIALOG_TRACKERS_FOUND,
            CtaId.DAX_DIALOG_NETWORK,
            CtaId.DAX_FIRE_BUTTON,
            CtaId.DAX_END,
        )
    }

    suspend fun dismissPulseAnimation() {
        withContext(dispatchers.io()) {
            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_FIRE_BUTTON_PULSE))
        }
    }

    fun onSurveyChanged(survey: Survey?): Boolean {
        val wasCleared = activeSurvey != null && survey == null
        activeSurvey = survey
        return wasCleared
    }

    suspend fun hideTipsForever(cta: Cta) {
        settingsDataStore.hideTips = true
        pixel.fire(AppPixelName.ONBOARDING_DAX_ALL_CTA_HIDDEN, cta.pixelCancelParameters())
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
            if (cta is DaxBubbleCta || cta is ExperimentDaxBubbleOptionsCta) {
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
                surveyRepository.cancelScheduledSurveys()
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

    suspend fun refreshCta(
        dispatcher: CoroutineContext,
        isBrowserShowing: Boolean,
        site: Site? = null,
        favoritesOnboarding: Boolean = false,
    ): Cta? {
        surveyCta()?.let {
            return it
        }

        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getDaxDialogCta(site)
            } else {
                Timber.i("favoritesOnboarding: - refreshCta $favoritesOnboarding")
                if (favoritesOnboarding) {
                    BubbleCta.DaxFavoritesOnboardingCta()
                } else {
                    getHomeCta()
                }
            }
        }
    }

    suspend fun getFireDialogCta(): DaxFireDialogCta? {
        if (!daxOnboardingActive()) return null

        return withContext(dispatchers.io()) {
            if (hideTips() || daxDialogFireEducationShown()) return@withContext null
            return@withContext DaxFireDialogCta.TryClearDataCta(onboardingStore, appInstallStore)
        }
    }

    private suspend fun getHomeCta(): Cta? {
        return when {
            canShowDaxIntroCta() -> {
                if (extendedOnboardingExperimentVariantManager.isAestheticUpdatesEnabled()) {
                    ExperimentDaxBubbleOptionsCta.ExperimentDaxIntroSearchOptionsCta(onboardingStore, appInstallStore)
                } else {
                    DaxBubbleCta.DaxIntroCta(onboardingStore, appInstallStore)
                }
            }

            canShowDaxIntroVisitSiteCta() && extendedOnboardingExperimentVariantManager.isAestheticUpdatesEnabled() -> {
                ExperimentDaxBubbleOptionsCta.ExperimentDaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore)
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

    private fun surveyCta(): HomePanelCta.Survey? {
        val survey = activeSurvey ?: return null

        if (surveyRepository.shouldShowSurvey(survey)) {
            return HomePanelCta.Survey(survey)
        }
        return null
    }

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return !widgetCapabilities.hasInstalledWidgets && !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    @WorkerThread
    private suspend fun canShowDaxIntroCta(): Boolean = daxOnboardingActive() && !daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDaxIntroVisitSiteCta(): Boolean =
        daxOnboardingActive() && daxDialogIntroShown() && !daxDialogIntroVisitSiteShown() && !hideTips() &&
            !(daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogTrackersFoundShown())

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean = daxOnboardingActive() &&
        !daxDialogEndShown() &&
        daxDialogIntroShown() &&
        !hideTips() &&
        (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogSerpShown() || daxDialogTrackersFoundShown())

    private suspend fun canShowDaxDialogCta(): Boolean {
        if (!daxOnboardingActive() || hideTips()) {
            return false
        }
        return true
    }

    @WorkerThread
    private suspend fun getDaxDialogCta(site: Site?): Cta? {
        val nonNullSite = site ?: return null

        val host = nonNullSite.domain
        if (host == null || userAllowListRepository.isDomainInUserAllowList(host)) {
            return null
        }

        nonNullSite.let {
            if (duckDuckGoUrlDetector.isDuckDuckGoEmailUrl(it.url)) {
                return null
            }

            if (!canShowDaxDialogCta()) return null

            // Trackers blocked
            if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && it.orderedTrackerBlockedEntities().isNotEmpty()) {
                return if (extendedOnboardingExperimentVariantManager.isAestheticUpdatesEnabled()) {
                    ExperimentOnboardingDaxDialogCta.DaxTrackersBlockedCta(
                        onboardingStore,
                        appInstallStore,
                        it.orderedTrackerBlockedEntities(),
                    )
                } else {
                    DaxDialogCta.DaxTrackersBlockedCta(onboardingStore, appInstallStore, it.orderedTrackerBlockedEntities(), host)
                }
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && DaxDialogCta.mainTrackerNetworks.contains(entity.displayName)) {
                        return DaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                    }
                }
            }

            // SERP
            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return if (extendedOnboardingExperimentVariantManager.isAestheticUpdatesEnabled()) {
                    ExperimentOnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
                } else {
                    DaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
                }
            }

            if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                return DaxDialogCta.DaxNoSerpCta(onboardingStore, appInstallStore)
            }
            return null
        }
    }

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogIntroVisitSiteShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun daxDialogFireEducationShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)

    private fun pulseFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun isSerpUrl(url: String): Boolean = url.contains(DaxDialogCta.SERP)

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun pulseAnimationDisabled(): Boolean =
        !daxOnboardingActive() || pulseFireButtonShown() || daxDialogFireEducationShown() || hideTips()

    private suspend fun allOnboardingCtasShown(): Boolean {
        return withContext(dispatchers.io()) {
            requiredDaxOnboardingCtas.all {
                dismissedCtaDao.exists(it)
            }
        }
    }

    private fun forceStopFireButtonPulseAnimationFlow() = tabRepository.flowTabs.distinctUntilChanged()
        .map { tabs ->
            if (tabs.size >= MAX_TABS_OPEN_FIRE_EDUCATION) return@map true
            return@map false
        }

    @ExperimentalCoroutinesApi
    private fun getShowFireButtonPulseAnimationFlow(): Flow<Boolean> = dismissedCtaDao.dismissedCtas()
        .combine(forceStopFireButtonPulseAnimationFlow(), ::Pair)
        .onEach { (_, forceStopAnimation) ->
            withContext(dispatchers.io()) {
                if (pulseAnimationDisabled()) {
                    isFireButtonPulseAnimationFlowEnabled.emit(false)
                }
                if (forceStopAnimation) {
                    dismissPulseAnimation()
                }
            }
        }.shouldShowPulseAnimation()

    private fun Flow<Pair<List<DismissedCta>, Boolean>>.shouldShowPulseAnimation(): Flow<Boolean> {
        return this.map { (dismissedCtaDao, forceStopAnimation) ->
            withContext(dispatchers.io()) {
                if (forceStopAnimation) return@withContext false
                if (pulseAnimationDisabled()) return@withContext false

                return@withContext dismissedCtaDao.any {
                    it.ctaId == CtaId.DAX_DIALOG_TRACKERS_FOUND ||
                        it.ctaId == CtaId.DAX_DIALOG_OTHER ||
                        it.ctaId == CtaId.DAX_DIALOG_NETWORK
                }
            }
        }
    }

    private fun hideTips() = settingsDataStore.hideTips

    companion object {
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
    }
}
