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
import androidx.core.net.toUri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetExperiment
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetAuto
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetAutoOnboardingExperiment
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetInstructions
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.daxOnboardingActive
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.subscriptions.api.Subscriptions
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import logcat.logcat

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
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val subscriptions: Subscriptions,
    private val duckPlayer: DuckPlayer,
    private val brokenSitePrompt: BrokenSitePrompt,
    private val senseOfProtectionExperiment: SenseOfProtectionExperiment,
    private val onboardingHomeScreenWidgetExperiment: OnboardingHomeScreenWidgetExperiment,
    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
) {
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

    private suspend fun isPrivacyProCtaAvailable(): Boolean =
        subscriptions.isEligible() && extendedOnboardingFeatureToggles.privacyProCta().isEnabled()

    private suspend fun requiredDaxOnboardingCtas(): Array<CtaId> {
        return if (isPrivacyProCtaAvailable()) {
            arrayOf(
                CtaId.DAX_INTRO,
                CtaId.DAX_DIALOG_SERP,
                CtaId.DAX_DIALOG_TRACKERS_FOUND,
                CtaId.DAX_FIRE_BUTTON,
                CtaId.DAX_END,
                CtaId.DAX_INTRO_PRIVACY_PRO,
            )
        } else {
            arrayOf(
                CtaId.DAX_INTRO,
                CtaId.DAX_DIALOG_SERP,
                CtaId.DAX_DIALOG_TRACKERS_FOUND,
                CtaId.DAX_FIRE_BUTTON,
                CtaId.DAX_END,
            )
        }
    }

    suspend fun dismissPulseAnimation() {
        withContext(dispatchers.io()) {
            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_FIRE_BUTTON_PULSE))
        }
    }

    suspend fun onCtaShown(cta: Cta) {
        withContext(dispatchers.io()) {
            cta.shownPixel?.let {
                val canSendPixel = when (cta) {
                    is DaxCta -> cta.canSendShownPixel()
                    else -> true
                }
                if (canSendPixel) {
                    pixel.fire(it, cta.pixelShownParameters())
                }
            }
            if (cta is DaxCta && cta.markAsReadOnShow) {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.ctaShown()
            }
        }
    }

    private suspend fun completeStageIfDaxOnboardingCompleted() {
        if (daxOnboardingActive() && allOnboardingCtasShown()) {
            logcat { "Completing DAX ONBOARDING" }
            userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
        }
    }

    suspend fun onUserDismissedCta(cta: Cta, viaCloseBtn: Boolean = false) {
        withContext(dispatchers.io()) {
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.userDismissedPrompt()
            }

            cta.cancelPixel?.let {
                if (cta is AddWidgetAuto || cta is AddWidgetAutoOnboardingExperiment) {
                    onboardingHomeScreenWidgetExperiment.fireOnboardingWidgetDismiss()
                }
                pixel.fire(it, cta.pixelCancelParameters())
            }
            if (viaCloseBtn) {
                cta.closePixel?.let {
                    pixel.fire(it, cta.pixelCancelParameters())
                }
            }

            dismissedCtaDao.insert(DismissedCta(cta.ctaId))

            completeStageIfDaxOnboardingCompleted()
        }
    }

    suspend fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            if (cta is AddWidgetAuto || cta is AddWidgetAutoOnboardingExperiment) {
                onboardingHomeScreenWidgetExperiment.fireOnboardingWidgetAdd()
            }
            pixel.fire(it, cta.pixelOkParameters())
        }
        if (cta is BrokenSitePromptDialogCta) {
            brokenSitePrompt.userAcceptedPrompt()
        }
    }

    suspend fun refreshCta(
        dispatcher: CoroutineContext,
        isBrowserShowing: Boolean,
        site: Site? = null,
        detectedRefreshPatterns: Set<RefreshPattern>,
    ): Cta? {
        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getBrowserCta(site, detectedRefreshPatterns)
            } else {
                getHomeCta()
            }
        }
    }

    suspend fun getFireDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || daxDialogFireEducationShown()) return@withContext null
            OnboardingDaxDialogCta.DaxFireButtonCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
        }
    }

    suspend fun getSiteSuggestionsDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || !canShowDaxIntroVisitSiteCta()) return@withContext null
            OnboardingDaxDialogCta.DaxSiteSuggestionsCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
        }
    }

    suspend fun getEndStaticDialogCta(): OnboardingDaxDialogCta.DaxEndCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() && daxDialogEndShown()) return@withContext null
            return@withContext OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
        }
    }

    private suspend fun getHomeCta(): Cta? {
        return when {
            // Onboarding disabled
            canShowDaxIntroCta() && extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                settingsDataStore.hideTips = true
                userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
                null
            }

            // Search suggestions
            canShowDaxIntroCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                senseOfProtectionExperiment.enrolUserInNewExperimentIfEligible()
                DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore)
            }

            // Site suggestions
            canShowDaxIntroVisitSiteCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore).apply {
                    isModifiedControlOnboardingExperimentEnabled = onboardingDesignExperimentToggles.modifiedControl().isEnabled()
                }
            }

            // End
            canShowDaxCtaEndOfJourney() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
            }

            // Privacy Pro
            canShowPrivacyProCta() && !isOnboardingExperimentEnabled() -> {
                val titleRes: Int = R.string.onboardingPrivacyProDaxDialogTitle
                val descriptionRes: Int = R.string.onboardingPrivacyProDaxDialogDescriptionRebranding
                val primaryCtaRes: Int = if (freeTrialCopyAvailable()) {
                    R.string.onboardingPrivacyProDaxDialogFreeTrialOkButton
                } else {
                    R.string.onboardingPrivacyProDaxDialogOkButton
                }

                DaxBubbleCta.DaxPrivacyProCta(onboardingStore, appInstallStore, titleRes, descriptionRes, primaryCtaRes)
            }

            // Add Widget
            canShowWidgetCta() -> {
                if (widgetCapabilities.supportsAutomaticWidgetAdd) {
                    onboardingHomeScreenWidgetExperiment.enroll()
                    if (onboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()) {
                        onboardingHomeScreenWidgetExperiment.fireOnboardingWidgetDisplay()
                        AddWidgetAutoOnboardingExperiment
                    } else {
                        onboardingHomeScreenWidgetExperiment.fireOnboardingWidgetDisplay()
                        AddWidgetAuto
                    }
                } else {
                    AddWidgetInstructions
                }
            }

            else -> null
        }
    }

    private fun isOnboardingExperimentEnabled() = onboardingDesignExperimentToggles.buckOnboarding().isEnabled() ||
        onboardingDesignExperimentToggles.bbOnboarding().isEnabled()

    @WorkerThread
    private suspend fun canShowDaxIntroCta(): Boolean = daxOnboardingActive() && !daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDaxIntroVisitSiteCta(): Boolean =
        daxOnboardingActive() && daxDialogIntroShown() && !hideTips() &&
            !(daxDialogIntroVisitSiteShown() || daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogTrackersFoundShown())

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean = daxOnboardingActive() && !daxDialogEndShown() && daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowPrivacyProCta(): Boolean =
        daxOnboardingActive() && !hideTips() && !daxDialogPrivacyProShown() && isPrivacyProCtaAvailable()

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return !widgetCapabilities.hasInstalledWidgets && !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    private suspend fun freeTrialCopyAvailable(): Boolean =
        extendedOnboardingFeatureToggles.freeTrialCopy().isEnabled() && subscriptions.isFreeTrialEligible()

    @WorkerThread
    private suspend fun getBrowserCta(site: Site?, detectedRefreshPatterns: Set<RefreshPattern>): Cta? {
        val nonNullSite = site ?: return null

        val host = nonNullSite.domain
        if (host == null || userAllowListRepository.isDomainInUserAllowList(host) || isSiteNotAllowedForOnboarding(nonNullSite)) {
            return null
        }

        nonNullSite.let {
            if (duckDuckGoUrlDetector.isDuckDuckGoEmailUrl(it.url)) {
                return null
            }

            if (areInContextDaxDialogsCompleted()) {
                return if (brokenSitePrompt.shouldShowBrokenSitePrompt(nonNullSite.url, detectedRefreshPatterns)
                ) {
                    BrokenSitePromptDialogCta()
                } else {
                    null
                }
            }

            // Trackers blocked
            if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && it.orderedTrackerBlockedEntities().isNotEmpty()) {
                return OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                    onboardingStore,
                    appInstallStore,
                    it.orderedTrackerBlockedEntities(),
                    settingsDataStore,
                    onboardingDesignExperimentToggles,
                )
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && !daxDialogTrackersFoundShown() &&
                        OnboardingDaxDialogCta.mainTrackerNetworks.any { mainNetwork -> entity.displayName.contains(mainNetwork) }
                    ) {
                        return OnboardingDaxDialogCta.DaxMainNetworkCta(
                            onboardingStore,
                            appInstallStore,
                            entity.displayName,
                            host,
                            onboardingDesignExperimentToggles,
                        )
                    }
                }
            }

            // SERP
            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return OnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
            }

            // No trackers blocked
            if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                return OnboardingDaxDialogCta.DaxNoTrackersCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
            }

            // End
            if (canShowDaxCtaEndOfJourney() && daxDialogFireEducationShown()) {
                return OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore, onboardingDesignExperimentToggles)
            }

            return null
        }
    }

    private fun isSiteNotAllowedForOnboarding(site: Site): Boolean {
        val uri = site.url.toUri()

        if (subscriptions.isPrivacyProUrl(uri)) return true

        val isDuckPlayerUrl =
            duckPlayer.getDuckPlayerState() == DuckPlayerState.ENABLED &&
                (
                    (duckPlayer.getUserPreferences().privatePlayerMode == AlwaysAsk && duckPlayer.isYouTubeUrl(uri)) ||
                        duckPlayer.isDuckPlayerUri(site.url) || duckPlayer.isSimulatedYoutubeNoCookie(uri)
                    )

        return isDuckPlayerUrl
    }

    suspend fun areBubbleDaxDialogsCompleted(): Boolean {
        return withContext(dispatchers.io()) {
            val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
            val isLastContextDialogShown = if (isPrivacyProCtaAvailable()) daxDialogPrivacyProShown() else daxDialogEndShown()
            noBrowserCtaExperiment || isLastContextDialogShown || hideTips() || !userStageStore.daxOnboardingActive()
        }
    }

    suspend fun areInContextDaxDialogsCompleted(): Boolean {
        return withContext(dispatchers.io()) {
            val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
            val inContextDaxCtasShown = daxDialogSerpShown() && daxDialogTrackersFoundShown() && daxDialogFireEducationShown() && daxDialogEndShown()
            noBrowserCtaExperiment || inContextDaxCtasShown || hideTips() || !userStageStore.daxOnboardingActive()
        }
    }

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    private fun daxDialogIntroVisitSiteShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun daxDialogFireEducationShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)

    private fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogPrivacyProShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO_PRIVACY_PRO)

    private fun pulseFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun isSerpUrl(url: String): Boolean = url.contains(OnboardingDaxDialogCta.SERP)

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun pulseAnimationDisabled(): Boolean =
        !daxOnboardingActive() || pulseFireButtonShown() || daxDialogFireEducationShown() || hideTips()

    private suspend fun allOnboardingCtasShown(): Boolean {
        return requiredDaxOnboardingCtas().all { dismissedCtaDao.exists(it) }
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

    @Deprecated("New users won't have this option available since extended onboarding")
    private fun hideTips() = settingsDataStore.hideTips

    fun isSuggestedSearchOption(query: String): Boolean = onboardingStore.getSearchOptions().map { it.link }.contains(query)

    fun isSuggestedSiteOption(query: String): Boolean = onboardingStore.getSitesOptions().map { it.link }.contains(query)

    companion object {
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
    }
}
