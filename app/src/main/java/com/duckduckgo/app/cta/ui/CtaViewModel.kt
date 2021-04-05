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
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
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
    private val userWhitelistDao: UserWhitelistDao,
    private val variantManager: VariantManager,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val userStageStore: UserStageStore,
    private val userEventsStore: UserEventsStore,
    private val useOurAppDetector: UseOurAppDetector,
    private val tabRepository: TabRepository,
    private val dispatchers: DispatcherProvider
) {
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    @ExperimentalCoroutinesApi
    @VisibleForTesting
    val isFireButtonPulseAnimationFlowEnabled = ConflatedBroadcastChannel(true)

    @FlowPreview
    @ExperimentalCoroutinesApi
    val showFireButtonPulseAnimation: Flow<Boolean> =
        isFireButtonPulseAnimationFlowEnabled.asFlow()
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
            CtaId.DAX_END
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

    private suspend fun completeStageIfUserInUseOurAppCompleted() {
        if (useOurAppActive()) {
            Timber.d("Completing USE OUR APP ONBOARDING")
            userStageStore.stageCompleted(AppStage.USE_OUR_APP_ONBOARDING)
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

            completeStageIfUserInUseOurAppCompleted()
            completeStageIfDaxOnboardingCompleted()
        }
    }

    fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
    }

    suspend fun refreshCta(dispatcher: CoroutineContext, isBrowserShowing: Boolean, site: Site? = null, locale: Locale = Locale.getDefault()): Cta? {
        surveyCta(locale)?.let {
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

    suspend fun getFireDialogCta(): DaxFireDialogCta? {
        if (!daxOnboardingActive()) return null

        return withContext(dispatchers.io()) {
            if (settingsDataStore.hideTips || daxDialogFireEducationShown()) return@withContext null
            return@withContext DaxFireDialogCta.TryClearDataCta(onboardingStore, appInstallStore)
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
            canShowUseOurAppDialog() -> {
                UseOurAppCta()
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
            canShowUseOurAppDeletionDialog(site) -> {
                val cta = UseOurAppDeletionCta()
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                cta
            }
            else -> null
        }
    }

    private fun surveyCta(locale: Locale): HomePanelCta.Survey? {
        val survey = activeSurvey

        if (survey == null || survey.url == null) {
            return null
        }

        if (locale != Locale.US) {
            return null
        }

        val showOnDay = survey.daysInstalled?.toLong()
        val daysInstalled = appInstallStore.daysInstalled()
        if ((showOnDay == null && daysInstalled >= SURVEY_DEFAULT_MIN_DAYS_INSTALLED) || showOnDay == daysInstalled) {
            return Survey(survey)
        }
        return null
    }

    @WorkerThread
    private suspend fun canShowUseOurAppDeletionDialog(site: Site?): Boolean =
        !settingsDataStore.hideTips && !useOurAppDeletionDialogShown() && useOurAppDetector.isUseOurAppUrl(site?.url) && twoDaysSinceShortcutAdded()

    @WorkerThread
    private suspend fun twoDaysSinceShortcutAdded(): Boolean {
        val timestampKey = userEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED) ?: return false
        val days = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - timestampKey.timestamp)
        return (days >= 2)
    }

    @WorkerThread
    private suspend fun canShowUseOurAppDialog(): Boolean = !settingsDataStore.hideTips && useOurAppActive() && !useOurAppDialogShown()

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
        !daxDialogEndShown() &&
        daxDialogIntroShown() &&
        !settingsDataStore.hideTips &&
        (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogSerpShown() || daxDialogTrackersFoundShown())

    private suspend fun canShowDaxDialogCta(): Boolean {
        if (!daxOnboardingActive() || settingsDataStore.hideTips) {
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
            // Is major network
            if (it.entity != null) {
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
            return if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && it.orderedTrackingEntities().isNotEmpty()) {
                DaxDialogCta.DaxTrackersBlockedCta(onboardingStore, appInstallStore, it.orderedTrackingEntities(), host)
            } else if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                DaxDialogCta.DaxNoSerpCta(onboardingStore, appInstallStore)
            } else {
                null
            }
        }
    }

    fun useOurAppDeletionDialogShown(): Boolean = dismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)

    private fun useOurAppDialogShown(): Boolean = dismissedCtaDao.exists(CtaId.USE_OUR_APP)

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun daxDialogFireEducationShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)

    private fun pulseFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun isSerpUrl(url: String): Boolean = url.contains(DaxDialogCta.SERP)

    private suspend fun useOurAppActive(): Boolean = userStageStore.useOurAppOnboarding()

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun pulseAnimationDisabled(): Boolean = !daxOnboardingActive() || pulseFireButtonShown() || daxDialogFireEducationShown() || settingsDataStore.hideTips

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
                    isFireButtonPulseAnimationFlowEnabled.send(false)
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

    companion object {
        private const val SURVEY_DEFAULT_MIN_DAYS_INSTALLED = 30
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
    }
}
