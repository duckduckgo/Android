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
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetAuto
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
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles.Cohorts
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingPixelsPlugin
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.HighlightsOnboardingExperimentManager
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.testPrivacyProOnboardingPrimaryButtonMetricPixel
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.testPrivacyProOnboardingSecondaryButtonMetricPixel
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.testPrivacyProOnboardingShownMetricPixel
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
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
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val subscriptions: Subscriptions,
    private val duckPlayer: DuckPlayer,
    private val highlightsOnboardingExperimentManager: HighlightsOnboardingExperimentManager,
    private val brokenSitePrompt: BrokenSitePrompt,
    private val extendedOnboardingPixelsPlugin: ExtendedOnboardingPixelsPlugin,
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

    private suspend fun requiredDaxOnboardingCtas(): Array<CtaId> {
        val shouldShowPrivacyProCta = subscriptions.isEligible() && extendedOnboardingFeatureToggles.privacyProCta().isEnabled()
        return if (shouldShowPrivacyProCta) {
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
            if (cta is OnboardingDaxDialogCta && cta.markAsReadOnShow) {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.ctaShown()
            }

            if (cta is DaxBubbleCta.DaxPrivacyProCta || cta is DaxBubbleCta.DaxExperimentPrivacyProCta) {
                extendedOnboardingPixelsPlugin.testPrivacyProOnboardingShownMetricPixel()?.getPixelDefinitions()?.forEach {
                    pixel.fire(it.pixelName, it.params)
                }
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
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.userDismissedPrompt()
            }

            cta.cancelPixel?.let {
                pixel.fire(it, cta.pixelCancelParameters())
            }

            dismissedCtaDao.insert(DismissedCta(cta.ctaId))

            completeStageIfDaxOnboardingCompleted()
        }
    }

    suspend fun onUserClickCtaOkButton(cta: Cta) {
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
        if (cta is BrokenSitePromptDialogCta) {
            brokenSitePrompt.userAcceptedPrompt()
        }
        withContext(dispatchers.io()) {
            if (cta is DaxBubbleCta.DaxPrivacyProCta || cta is DaxBubbleCta.DaxExperimentPrivacyProCta) {
                extendedOnboardingPixelsPlugin.testPrivacyProOnboardingPrimaryButtonMetricPixel()?.getPixelDefinitions()?.forEach {
                    pixel.fire(it.pixelName, it.params)
                }
            }
        }
    }

    suspend fun onUserClickCtaSkipButton(cta: Cta) {
        withContext(dispatchers.io()) {
            if (cta is DaxBubbleCta.DaxPrivacyProCta || cta is DaxBubbleCta.DaxExperimentPrivacyProCta) {
                extendedOnboardingPixelsPlugin.testPrivacyProOnboardingSecondaryButtonMetricPixel()?.getPixelDefinitions()?.forEach {
                    pixel.fire(it.pixelName, it.params)
                }
            }
        }
    }

    suspend fun refreshCta(
        dispatcher: CoroutineContext,
        isBrowserShowing: Boolean,
        site: Site? = null,
    ): Cta? {
        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getBrowserCta(site)
            } else {
                getHomeCta()
            }
        }
    }

    suspend fun getFireDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || daxDialogFireEducationShown()) return@withContext null
            if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                return@withContext OnboardingDaxDialogCta.DaxExperimentFireButtonCta(onboardingStore, appInstallStore)
            } else {
                return@withContext OnboardingDaxDialogCta.DaxFireButtonCta(onboardingStore, appInstallStore, settingsDataStore)
            }
        }
    }

    suspend fun getSiteSuggestionsDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || !canShowDaxIntroVisitSiteCta()) return@withContext null
            if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                return@withContext OnboardingDaxDialogCta.DaxExperimentSiteSuggestionsCta(onboardingStore, appInstallStore)
            } else {
                return@withContext OnboardingDaxDialogCta.DaxSiteSuggestionsCta(onboardingStore, appInstallStore)
            }
        }
    }

    suspend fun getEndStaticDialogCta(): OnboardingDaxDialogCta.DaxExperimentEndStaticCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() && daxDialogEndShown()) return@withContext null
            return@withContext OnboardingDaxDialogCta.DaxExperimentEndStaticCta(onboardingStore, appInstallStore)
        }
    }

    private suspend fun getHomeCta(): Cta? {
        return when {
            canShowDaxIntroCta() && extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                settingsDataStore.hideTips = true
                userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
                null
            }

            canShowDaxIntroCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    DaxBubbleCta.DaxExperimentIntroSearchOptionsCta(onboardingStore, appInstallStore)
                } else {
                    DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore)
                }
            }

            canShowDaxIntroVisitSiteCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    DaxBubbleCta.DaxExperimentIntroVisitSiteOptionsCta(onboardingStore, appInstallStore)
                } else {
                    DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore)
                }
            }

            canShowDaxCtaEndOfJourney() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    DaxBubbleCta.DaxExperimentEndCta(onboardingStore, appInstallStore)
                } else {
                    DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
                }
            }

            canShowPrivacyProCta() -> {
                val titleRes: Int
                val descriptionRes: Int
                when {
                    extendedOnboardingFeatureToggles.testPrivacyProOnboardingCopyNov24().isEnabled(Cohorts.STEP) -> {
                        titleRes = R.string.onboardingPrivacyProStepDaxDialogTitle
                        descriptionRes = R.string.onboardingPrivacyProStepDaxDialogDescription
                    }
                    extendedOnboardingFeatureToggles.testPrivacyProOnboardingCopyNov24().isEnabled(Cohorts.PROTECTION) -> {
                        titleRes = R.string.onboardingPrivacyProProtectionDaxDialogTitle
                        descriptionRes = R.string.onboardingPrivacyProProtectionDaxDialogDescription
                    }
                    extendedOnboardingFeatureToggles.testPrivacyProOnboardingCopyNov24().isEnabled(Cohorts.DEAL) -> {
                        titleRes = R.string.onboardingPrivacyProDealDaxDialogTitle
                        descriptionRes = R.string.onboardingPrivacyProDealDaxDialogDescription
                    }
                    else -> {
                        titleRes = R.string.onboardingPrivacyProDaxDialogTitle
                        descriptionRes = R.string.onboardingPrivacyProDaxDialogDescription
                    }
                }

                if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    DaxBubbleCta.DaxExperimentPrivacyProCta(onboardingStore, appInstallStore, titleRes, descriptionRes)
                } else {
                    DaxBubbleCta.DaxPrivacyProCta(onboardingStore, appInstallStore, titleRes, descriptionRes)
                }
            }

            canShowWidgetCta() -> {
                if (widgetCapabilities.supportsAutomaticWidgetAdd) AddWidgetAuto else AddWidgetInstructions
            }

            else -> null
        }
    }

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return !widgetCapabilities.hasInstalledWidgets && !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    @WorkerThread
    private suspend fun canShowDaxIntroCta(): Boolean = daxOnboardingActive() && !daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDaxIntroVisitSiteCta(): Boolean =
        daxOnboardingActive() && daxDialogIntroShown() && !hideTips() &&
            !(daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogTrackersFoundShown())

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean = daxOnboardingActive() &&
        !daxDialogEndShown() &&
        daxDialogIntroShown() &&
        !hideTips() &&
        (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogSerpShown() || daxDialogTrackersFoundShown())

    private suspend fun canShowOnboardingDaxDialogCta(): Boolean {
        return when {
            !daxOnboardingActive() || hideTips() -> false
            extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                settingsDataStore.hideTips = true
                userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
                false
            }

            else -> true
        }
    }

    private suspend fun canShowPrivacyProCta(): Boolean {
        return daxOnboardingActive() && !hideTips() && !daxDialogPrivacyProShown() &&
            subscriptions.isEligible() && extendedOnboardingFeatureToggles.privacyProCta().isEnabled()
    }

    @WorkerThread
    private suspend fun getBrowserCta(site: Site?): Cta? {
        val nonNullSite = site ?: return null

        val host = nonNullSite.domain
        if (host == null || userAllowListRepository.isDomainInUserAllowList(host) || isSiteNotAllowedForOnboarding(nonNullSite)) {
            return null
        }

        nonNullSite.let {
            if (duckDuckGoUrlDetector.isDuckDuckGoEmailUrl(it.url)) {
                return null
            }

            if (!canShowOnboardingDaxDialogCta()) {
                return if (brokenSitePrompt.shouldShowBrokenSitePrompt(nonNullSite.url)) {
                    BrokenSitePromptDialogCta()
                } else {
                    null
                }
            }

            // Trackers blocked
            if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && it.orderedTrackerBlockedEntities().isNotEmpty()) {
                return if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    OnboardingDaxDialogCta.DaxExperimentTrackersBlockedCta(
                        onboardingStore,
                        appInstallStore,
                        it.orderedTrackerBlockedEntities(),
                        settingsDataStore,
                    )
                } else {
                    OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                        onboardingStore,
                        appInstallStore,
                        it.orderedTrackerBlockedEntities(),
                        settingsDataStore,
                    )
                }
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && !daxDialogTrackersFoundShown() && OnboardingDaxDialogCta.mainTrackerNetworks.any { mainNetwork ->
                        entity.displayName.contains(mainNetwork)
                    }
                    ) {
                        return if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                            OnboardingDaxDialogCta.DaxExperimentMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                        } else {
                            OnboardingDaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                        }
                    }
                }
            }

            // SERP
            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    OnboardingDaxDialogCta.DaxExperimentSerpCta(onboardingStore, appInstallStore)
                } else {
                    OnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
                }
            }

            // No trackers blocked
            if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                return if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    OnboardingDaxDialogCta.DaxExperimentNoTrackersCta(onboardingStore, appInstallStore)
                } else {
                    OnboardingDaxDialogCta.DaxNoTrackersCta(onboardingStore, appInstallStore)
                }
            }

            // End
            if (canShowDaxCtaEndOfJourney() && daxDialogFireEducationShown()) {
                return if (highlightsOnboardingExperimentManager.isHighlightsEnabled()) {
                    OnboardingDaxDialogCta.DaxExperimentEndStaticCta(onboardingStore, appInstallStore)
                } else {
                    OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore, settingsDataStore)
                }
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

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    // We only want to show New Tab when the Home CTAs from Onboarding has finished
    // https://app.asana.com/0/1157893581871903/1207769731595075/f
    suspend fun areBubbleDaxDialogsCompleted(): Boolean {
        return withContext(dispatchers.io()) {
            val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
            val bubbleCtasShown = daxDialogEndShown() && (daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogTrackersFoundShown())
            noBrowserCtaExperiment || bubbleCtasShown || hideTips() || !userStageStore.daxOnboardingActive()
        }
    }

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

    fun getCohortOrigin(): String {
        val cohort = extendedOnboardingFeatureToggles.testPrivacyProOnboardingCopyNov24().getCohort()
        return when (cohort?.name) {
            Cohorts.STEP.cohortName -> "_${Cohorts.STEP.cohortName}"
            Cohorts.PROTECTION.cohortName -> "_${Cohorts.PROTECTION.cohortName}"
            Cohorts.DEAL.cohortName -> "_${Cohorts.DEAL.cohortName}"
            Cohorts.CONTROL.cohortName -> "_${Cohorts.CONTROL.cohortName}"
            else -> ""
        }
    }

    companion object {
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
    }
}
