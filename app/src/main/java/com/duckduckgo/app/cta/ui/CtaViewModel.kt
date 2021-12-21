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
import com.duckduckgo.app.cta.ui.HomePanelCta.*
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.orderedTrackingEntities
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.isFireproofExperimentEnabled
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.returningUsersNoOnboardingEnabled
import com.duckduckgo.app.statistics.returningUsersWidgetPromotionEnabled
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
class CtaViewModel
@Inject
constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val surveyDao: SurveyDao,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val userWhitelistDao: UserWhitelistDao,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val userStageStore: UserStageStore,
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider,
    private val variantManager: VariantManager,
    private val userEventsStore: UserEventsStore,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector
) {
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    @ExperimentalCoroutinesApi
    @VisibleForTesting
    val isFireButtonPulseAnimationFlowEnabled = ConflatedBroadcastChannel(true)

    @FlowPreview
    @ExperimentalCoroutinesApi
    val showFireButtonPulseAnimation: Flow<Boolean> =
        isFireButtonPulseAnimationFlowEnabled.asFlow().flatMapLatest {
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
            CtaId.DAX_END)
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
            val canSendPixel =
                when (cta) {
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
            cta.cancelPixel?.let { pixel.fire(it, cta.pixelCancelParameters()) }

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
        cta.okPixel?.let { pixel.fire(it, cta.pixelOkParameters()) }
    }

    suspend fun onUserClickFireproofExperimentButton(isAutoFireproofingEnabled: Boolean, cta: Cta) {

        if (cta !is DaxBubbleCta.DaxFireproofCta) return

        withContext(dispatchers.io()) {
            if (isAutoFireproofingEnabled) {
                cta.okPixel?.let { pixel.fire(it, cta.pixelOkParameters()) }
            } else {
                cta.cancelPixel?.let { pixel.fire(it, cta.pixelCancelParameters()) }
            }

            dismissedCtaDao.insert(DismissedCta(cta.ctaId))

            completeStageIfDaxOnboardingCompleted()
        }
    }

    suspend fun refreshCta(
        dispatcher: CoroutineContext,
        isBrowserShowing: Boolean,
        site: Site? = null,
        favoritesOnboarding: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): Cta? {
        surveyCta(locale)?.let {
            return it
        }

        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getBrowserCta(site)
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
                DaxBubbleCta.DaxIntroCta(onboardingStore, appInstallStore)
            }
            canShowDaxFireproofCta() -> {
                DaxBubbleCta.DaxFireproofCta(onboardingStore, appInstallStore)
            }
            canShowDaxCtaEndOfJourney() -> {
                DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
            }
            canShowWidgetCta() -> {
                getWidgetCta()
            }
            else -> null
        }
    }

    private fun getWidget(): HomePanelCta =
        if (widgetCapabilities.supportsAutomaticWidgetAdd) getWidgetAuto()
        else AddWidgetInstructions

    private fun getWidgetAuto() =
        if (variantManager.returningUsersWidgetPromotionEnabled() &&
            onboardingStore.userMarkedAsReturningUser)
            AddReturningUsersWidgetAuto
        else AddWidgetAuto

    private fun getWidgetCta(): HomePanelCta? {
        if (variantManager.returningUsersNoOnboardingEnabled() &&
            onboardingStore.userMarkedAsReturningUser) {
            onboardingStore.countNewTabForReturningUser++
            if (onboardingStore.hasReachedThresholdToShowWidgetForReturningUser()) {
                return getWidget()
            }
            return null
        }
        return getWidget()
    }

    private suspend fun getBrowserCta(site: Site?): Cta? {
        return when {
            canShowDaxDialogCta() -> {
                getDaxDialogCta(site)
            }
            else -> null
        }
    }

    private fun surveyCta(locale: Locale): HomePanelCta.Survey? {
        val survey = activeSurvey

        if (survey?.url == null) {
            return null
        }

        if (!ALLOWED_LOCALES.contains(locale)) {
            return null
        }

        val showOnDay = survey.daysInstalled?.toLong()
        val daysInstalled = appInstallStore.daysInstalled()
        if ((showOnDay == null && daysInstalled >= SURVEY_DEFAULT_MIN_DAYS_INSTALLED) ||
            showOnDay == daysInstalled ||
            showOnDay == SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED) {
            return Survey(survey)
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
    private suspend fun canShowDaxIntroCta(): Boolean =
        daxOnboardingActive() && !daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDaxFireproofCta(): Boolean =
        variantManager.isFireproofExperimentEnabled() &&
            daxOnboardingActive() &&
            !daxDialogEndShown() &&
            !daxDialogFireproofShown() &&
            daxDialogIntroShown() &&
            !settingsDataStore.hideTips &&
            userEventsStore.getUserEvent(UserEventKey.PROMOTED_FIRE_BUTTON_CANCELLED) == null

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean =
        daxOnboardingActive() &&
            !daxDialogEndShown() &&
            daxDialogIntroShown() &&
            !hideTips() &&
            (daxDialogNetworkShown() ||
                daxDialogOtherShown() ||
                daxDialogSerpShown() ||
                daxDialogTrackersFoundShown())

    private suspend fun canShowDaxDialogCta(): Boolean {
        if (!daxOnboardingActive() || hideTips()) {
            return false
        }
        return true
    }

    @WorkerThread
    private fun getDaxDialogCta(site: Site?): Cta? {
        val nonNullSite = site ?: return null

        val host = nonNullSite.domain
        if (host == null || userWhitelistDao.contains(host)) {
            return null
        }

        nonNullSite.let {
            if (duckDuckGoUrlDetector.isDuckDuckGoEmailUrl(it.url)) {
                return null
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() &&
                        DaxDialogCta.mainTrackerNetworks.contains(entity.displayName)) {
                        return DaxDialogCta.DaxMainNetworkCta(
                            onboardingStore, appInstallStore, entity.displayName, host)
                    }
                }
            }

            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return DaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
            }

            // Trackers blocked
            return if (!daxDialogTrackersFoundShown() &&
                !isSerpUrl(it.url) &&
                it.orderedTrackingEntities().isNotEmpty()) {
                DaxDialogCta.DaxTrackersBlockedCta(
                    onboardingStore, appInstallStore, it.orderedTrackingEntities(), host)
            } else if (!isSerpUrl(it.url) &&
                !daxDialogOtherShown() &&
                !daxDialogTrackersFoundShown() &&
                !daxDialogNetworkShown()) {
                DaxDialogCta.DaxNoSerpCta(onboardingStore, appInstallStore)
            } else {
                null
            }
        }
    }

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogFireproofShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIREPROOF)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean =
        dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun daxDialogFireEducationShown(): Boolean =
        dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)

    private fun pulseFireButtonShown(): Boolean =
        dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun isSerpUrl(url: String): Boolean = url.contains(DaxDialogCta.SERP)

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun pulseAnimationDisabled(): Boolean =
        !daxOnboardingActive() ||
            pulseFireButtonShown() ||
            daxDialogFireEducationShown() ||
            hideTips()

    private suspend fun allOnboardingCtasShown(): Boolean {
        return withContext(dispatchers.io()) {
            requiredDaxOnboardingCtas.all { dismissedCtaDao.exists(it) }
        }
    }

    private fun forceStopFireButtonPulseAnimationFlow() =
        tabRepository.flowTabs.distinctUntilChanged().map { tabs ->
            if (tabs.size >= MAX_TABS_OPEN_FIRE_EDUCATION) return@map true
            return@map false
        }

    @ExperimentalCoroutinesApi
    private fun getShowFireButtonPulseAnimationFlow(): Flow<Boolean> =
        dismissedCtaDao
            .dismissedCtas()
            .combine(forceStopFireButtonPulseAnimationFlow(), ::Pair)
            .onEach { (_, forceStopAnimation) ->
                withContext(dispatchers.io()) {
                    if (pulseAnimationDisabled()) {
                        isFireButtonPulseAnimationFlowEnabled.send(false)
                    }
                    if (forceStopAnimation) {
                        dismissPulseAnimation()
                    }
                }
            }
            .shouldShowPulseAnimation()

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

    private fun hideTips() = settingsDataStore.hideTips || onboardingStore.userMarkedAsReturningUser

    companion object {
        private const val SURVEY_DEFAULT_MIN_DAYS_INSTALLED = 30
        private const val SURVEY_NO_MIN_DAYS_INSTALLED_REQUIRED = -1L
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
        private val ALLOWED_LOCALES = listOf(Locale.US, Locale.UK, Locale.CANADA)
    }
}
