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
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetAutoOnboarding
import com.duckduckgo.app.cta.ui.HomePanelCta.AddWidgetInstructions
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.daxOnboardingActive
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.AggregateTabProvider
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.inputscreen.DuckAiOnboardingEndCtaVariant
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.forPlan
import com.duckduckgo.subscriptions.api.SubscriptionPromoCtaShownPlugin
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@SingleInstanceIn(AppScope::class)
class CtaViewModel @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao,
    private val userAllowListRepository: UserAllowListRepository,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val customAiOnboarding: CustomAiOnboardingStore,
    private val userStageStore: UserStageStore,
    private val aggregateTabProvider: AggregateTabProvider,
    private val dispatchers: DispatcherProvider,
    private val duckChat: DuckChat,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val subscriptions: Subscriptions,
    private val duckPlayer: DuckPlayer,
    private val brokenSitePrompt: BrokenSitePrompt,
    private val subscriptionPromoCtaShownPlugins: PluginPoint<SubscriptionPromoCtaShownPlugin>,
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val appTheme: AppTheme,
    private val deviceInfo: DeviceInfo,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    linearOnboardingOrchestrator: LinearOnboardingOrchestrator,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val onboardingPixelSender: OnboardingPixelSender,
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

    init {
        linearOnboardingOrchestrator.state
            .forPlan(NewUserOnboardingPlanProvider.ROOT_PLAN_ID)
            .filterIsInstance<LinearOnboardingState.Completed>()
            .onEach { completeStageIfDaxOnboardingCompleted() }
            .launchIn(coroutineScope)
    }

    private suspend fun isSubscriptionCtaAvailable(): Boolean =
        subscriptions.isEligible() && hasNoSubscription() && extendedOnboardingFeatureToggles.privacyProCta().isEnabled()

    private suspend fun isBrandDesignUpdateEnabled(): Boolean = withContext(dispatchers.io()) {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()
    }

    private suspend fun isOnboardingImprovementsEnabled(): Boolean = withContext(dispatchers.io()) {
        onboardingBrandDesignUpdateToggles.onboardingImprovements().isEnabled()
    }

    /**
     * Whether the legacy `InputScreenActivity` is in play. When false, the native input widget is shown
     * instead (gated in `RealDuckChat.cacheUserSettings()` by `DuckChatFeature.nativeInputField()` + user settings).
     */
    private fun isInputScreenEnabled(): Boolean = duckAiFeatureState.showInputScreen.value

    private fun contextualOnboardingPixelName(cta: Cta): OnboardingPixelName? = when (cta) {
        is DaxTryASearchBrandDesignUpdateBubbleCta -> OnboardingPixelName.ONBOARDING_SEARCH
        is DaxVisitSiteOptionsBrandDesignUpdateBubbleCta -> OnboardingPixelName.ONBOARDING_VISIT_SITE
        is DaxSerpBrandDesignUpdateContextualCta -> OnboardingPixelName.ONBOARDING_SEARCH_RESULTS
        is DaxTrackersBlockedBrandDesignUpdateContextualCta,
        is DaxMainNetworkBrandDesignUpdateContextualCta,
        is DaxNoTrackersBrandDesignUpdateContextualCta,
        -> OnboardingPixelName.ONBOARDING_TRACKERS_BLOCKED
        is DaxFireButtonBrandDesignUpdateContextualCta -> OnboardingPixelName.ONBOARDING_FIRE_BUTTON
        is DaxEndBrandDesignUpdateBubbleCta,
        is DaxEndBrandDesignUpdateContextualCta,
        -> OnboardingPixelName.ONBOARDING_END
        else -> null
    }

    // Exposed for onboarding dev settings and tests. Used internally for completion checks
    @VisibleForTesting
    suspend fun requiredDaxOnboardingCtas(): List<CtaId> {
        if (onboardingStore.isDuckAiOnboardingFlow()) {
            return listOf(CtaId.DAX_DUCK_AI_FIRE_BUTTON)
        }
        return if (isSubscriptionCtaAvailable()) {
            listOf(
                CtaId.DAX_INTRO,
                CtaId.DAX_DIALOG_SERP,
                CtaId.DAX_DIALOG_TRACKERS_FOUND,
                CtaId.DAX_FIRE_BUTTON,
                CtaId.DAX_END,
                CtaId.DAX_INTRO_PRIVACY_PRO,
            )
        } else {
            listOf(
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
            contextualOnboardingPixelName(cta)?.let { onboardingPixelSender.fire(it, OnboardingPixelAction.Shown) }
            cta.shownPixel?.let {
                val canSendPixel = when (cta) {
                    is DaxCta -> cta.canSendShownPixel()
                    else -> true
                }
                if (canSendPixel) {
                    pixel.fire(it, cta.pixelShownParameters())
                }
            }
            if (cta is DaxDuckAiEndBubbleCta || cta is DaxDuckAiEndBrandDesignUpdateBubbleCta) {
                // Native-input bubble path: mirror prepareAndMarkDuckAiEndCtaForInputScreen's side-effects.
                completeStageIfDaxOnboardingCompleted()
            }
            if (cta is DaxCta && cta.markAsReadOnShow) {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.ctaShown()
            }
            if (cta is DaxBubbleCta.DaxSubscriptionCta || cta is DaxSubscriptionBrandDesignUpdateBubbleCta || cta is SubscriptionPromoModalCta) {
                subscriptionPromoCtaShownPlugins.getPlugins().forEach { it.onSubscriptionPromoCtaShown() }
            }
            if (cta is SubscriptionPromoModalCta) {
                pixel.fire(cta.flow.shownPixel, cta.pixelShownParameters())
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
        }
    }

    private suspend fun completeStageIfDaxOnboardingCompleted() {
        if (daxOnboardingActive() && allOnboardingCtasShown()) {
            logcat { "Completing DAX ONBOARDING" }
            userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
        }
    }

    suspend fun onUserDismissedCta(cta: Cta, viaCloseBtn: Boolean = false, isEngagement: Boolean = false) {
        withContext(dispatchers.io()) {
            if (!isEngagement) {
                when (cta) {
                    is DaxSerpBrandDesignUpdateContextualCta ->
                        onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_SEARCH_RESULTS, OnboardingPixelAction.Clicked(engaged = false))
                    is DaxTrackersBlockedBrandDesignUpdateContextualCta,
                    is DaxMainNetworkBrandDesignUpdateContextualCta,
                    is DaxNoTrackersBrandDesignUpdateContextualCta,
                    -> onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_TRACKERS_BLOCKED, OnboardingPixelAction.Clicked(engaged = false))
                    is DaxFireButtonBrandDesignUpdateContextualCta ->
                        onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Clicked(engaged = false))
                    is DaxEndBrandDesignUpdateBubbleCta,
                    is DaxEndBrandDesignUpdateContextualCta,
                    -> onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_END, OnboardingPixelAction.Clicked(engaged = false))
                    else -> {}
                }
            }
            if (cta is BrokenSitePromptDialogCta) {
                brokenSitePrompt.userDismissedPrompt()
            }

            cta.cancelPixel?.let {
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
        when (cta) {
            is DaxSerpBrandDesignUpdateContextualCta ->
                onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_SEARCH_RESULTS, OnboardingPixelAction.Clicked(engaged = true))
            is DaxTrackersBlockedBrandDesignUpdateContextualCta,
            is DaxMainNetworkBrandDesignUpdateContextualCta,
            is DaxNoTrackersBrandDesignUpdateContextualCta,
            -> onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_TRACKERS_BLOCKED, OnboardingPixelAction.Clicked(engaged = true))
            is DaxEndBrandDesignUpdateBubbleCta,
            is DaxEndBrandDesignUpdateContextualCta,
            -> onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_END, OnboardingPixelAction.Clicked(engaged = true))
            else -> {}
        }
        cta.okPixel?.let {
            pixel.fire(it, cta.pixelOkParameters())
        }
        if (cta is BrokenSitePromptDialogCta) {
            brokenSitePrompt.userAcceptedPrompt()
        }
        if (cta is SubscriptionPromoModalCta) {
            pixel.fire(cta.flow.subscribeClickPixel, cta.pixelOkParameters())
            withContext(dispatchers.io()) {
                dismissedCtaDao.insert(DismissedCta(cta.ctaId))
            }
        }
    }

    suspend fun prepareAndMarkDuckAiEndCtaForInputScreen(): DuckAiOnboardingEndCtaVariant {
        return withContext(dispatchers.io()) {
            val shouldShow = canShowDuckAiEndCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() && !settingsDataStore.hideTips
            if (!shouldShow) return@withContext DuckAiOnboardingEndCtaVariant.NONE

            dismissedCtaDao.insert(DismissedCta(CtaId.DAX_DUCK_AI_END))
            completeStageIfDaxOnboardingCompleted()
            if (canSendShownPixel(onboardingStore, Pixel.PixelValues.DUCK_AI_END_CTA)) {
                val journey = addCtaToHistory(onboardingStore, appInstallStore, Pixel.PixelValues.DUCK_AI_END_CTA)
                pixel.fire(AppPixelName.ONBOARDING_DAX_CTA_SHOWN, mapOf(Pixel.PixelParameter.CTA_SHOWN to journey))
            }
            if (isBrandDesignUpdateEnabled()) {
                DuckAiOnboardingEndCtaVariant.BRAND_DESIGN_UPDATE
            } else {
                DuckAiOnboardingEndCtaVariant.LEGACY
            }
        }
    }

    suspend fun onDuckAiEndCtaInteraction(okClicked: Boolean) {
        withContext(dispatchers.io()) {
            val params = mapOf(Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.DUCK_AI_END_CTA)
            if (okClicked) {
                pixel.fire(AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON, params)
            } else {
                pixel.fire(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON, params)
            }
        }
    }

    suspend fun refreshCta(
        dispatcher: CoroutineContext,
        isBrowserShowing: Boolean,
        site: Site? = null,
        detectedRefreshPatterns: Set<RefreshPattern>,
        suppressDuckAiOnboardingCta: Boolean = false,
    ): Cta? {
        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getBrowserCta(site, detectedRefreshPatterns, suppressDuckAiOnboardingCta)
            } else {
                getHomeCta()
            }
        }
    }

    suspend fun getPromoCtaOnForeground(): Cta? {
        return withContext(dispatchers.io()) {
            when {
                canShowSubscriptionCtaForSkippedOnboarding() -> SubscriptionPromoModalCta(
                    isFreeTrialCopy = freeTrialCopyAvailable(),
                    flow = SubscriptionPromoFlow.SKIPPED_ONBOARDING,
                )
                canShowSubscriptionPromoCta() -> SubscriptionPromoModalCta(
                    isFreeTrialCopy = freeTrialCopyAvailable(),
                    flow = SubscriptionPromoFlow.NUDGE,
                )
                else -> null
            }
        }
    }

    suspend fun getFireDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || daxDialogFireEducationShown()) return@withContext null
            if (isBrandDesignUpdateEnabled()) {
                return@withContext DaxFireButtonBrandDesignUpdateContextualCta(
                    onboardingStore = onboardingStore,
                    appInstallStore = appInstallStore,
                    isLightTheme = appTheme.isLightModeEnabled(),
                    deviceInfo = deviceInfo,
                )
            }
            OnboardingDaxDialogCta.DaxFireButtonCta(onboardingStore, appInstallStore)
        }
    }

    suspend fun getSiteSuggestionsDialogCta(onSiteSuggestionOptionClicked: (index: Int) -> Unit): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() || !canShowDaxIntroVisitSiteCta()) return@withContext null
            if (isBrandDesignUpdateEnabled()) {
                return@withContext DaxSiteSuggestionsBrandDesignUpdateContextualCta(
                    onboardingStore,
                    appInstallStore,
                    isLightTheme = appTheme.isLightModeEnabled(),
                    deviceInfo = deviceInfo,
                )
            }
            OnboardingDaxDialogCta.DaxSiteSuggestionsCta(
                onboardingStore,
                appInstallStore,
                onSiteSuggestionOptionClicked,
            )
        }
    }

    suspend fun getEndStaticDialogCta(): OnboardingDaxDialogCta? {
        return withContext(dispatchers.io()) {
            if (!daxOnboardingActive() && daxDialogEndShown()) return@withContext null
            if (isBrandDesignUpdateEnabled()) {
                return@withContext DaxEndBrandDesignUpdateContextualCta(
                    onboardingStore,
                    appInstallStore,
                    isLightTheme = appTheme.isLightModeEnabled(),
                    deviceInfo = deviceInfo,
                )
            }
            return@withContext OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore)
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

            // Duck.ai onboarding end
            canShowDuckAiEndCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (isInputScreenEnabled()) {
                    // Legacy path: the input screen auto-launches with the end CTA. Suppress home
                    // CTAs until that flow runs (see prepareAndMarkDuckAiEndCtaForInputScreen).
                    null
                } else if (isBrandDesignUpdateEnabled()) {
                    DaxDuckAiEndBrandDesignUpdateBubbleCta(
                        onboardingStore = onboardingStore,
                        appInstallStore = appInstallStore,
                        isLightTheme = appTheme.isLightModeEnabled(),
                        deviceInfo = deviceInfo,
                        isCustomAiOnboardingFlow = customAiOnboarding.isEnabled(),
                    )
                } else {
                    DaxDuckAiEndBubbleCta(onboardingStore, appInstallStore)
                }
            }

            // Search suggestions
            canShowDaxIntroCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (isBrandDesignUpdateEnabled()) {
                    DaxTryASearchBrandDesignUpdateBubbleCta(onboardingStore, appInstallStore, appTheme.isLightModeEnabled(), deviceInfo)
                } else {
                    DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore)
                }
            }

            // Site suggestions
            canShowDaxIntroVisitSiteCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (isBrandDesignUpdateEnabled()) {
                    DaxVisitSiteOptionsBrandDesignUpdateBubbleCta(
                        onboardingStore,
                        appInstallStore,
                        appTheme.isLightModeEnabled(),
                        deviceInfo,
                        onboardingImprovementsEnabled = isOnboardingImprovementsEnabled(),
                    )
                } else {
                    DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore)
                }
            }

            // End
            canShowDaxCtaEndOfJourney() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                if (isBrandDesignUpdateEnabled()) {
                    DaxEndBrandDesignUpdateBubbleCta(
                        onboardingStore,
                        appInstallStore,
                        appTheme.isLightModeEnabled(),
                        deviceInfo,
                        onboardingImprovementsEnabled = isOnboardingImprovementsEnabled(),
                    )
                } else {
                    DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
                }
            }

            // Subscription onboarding
            canShowSubscriptionCta() -> {
                if (isBrandDesignUpdateEnabled()) {
                    DaxSubscriptionBrandDesignUpdateBubbleCta(
                        onboardingStore,
                        appInstallStore,
                        appTheme.isLightModeEnabled(),
                        deviceInfo,
                        isCustomAiOnboardingFlow = customAiOnboarding.isEnabled(),
                        isFreeTrialCopy = freeTrialCopyAvailable(),
                        onboardingImprovementsEnabled = isOnboardingImprovementsEnabled(),
                    )
                } else {
                    DaxBubbleCta.DaxSubscriptionCta(
                        onboardingStore,
                        appInstallStore,
                        isFreeTrialCopy = freeTrialCopyAvailable(),
                    )
                }
            }

            // Add Widget
            canShowWidgetCta() -> {
                if (widgetCapabilities.supportsAutomaticWidgetAdd) {
                    AddWidgetAutoOnboarding
                } else {
                    AddWidgetInstructions
                }
            }

            else -> null
        }
    }

    @WorkerThread
    private suspend fun canShowDaxIntroCta(): Boolean = daxOnboardingActive() && !daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDaxIntroVisitSiteCta(): Boolean =
        daxOnboardingActive() && daxDialogIntroShown() && !hideTips() &&
            !(daxDialogIntroVisitSiteShown() || daxDialogNetworkShown() || daxDialogOtherShown() || daxDialogTrackersFoundShown())

    @WorkerThread
    private suspend fun canShowDaxCtaEndOfJourney(): Boolean = daxOnboardingActive() && !daxDialogEndShown() && daxDialogIntroShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowDuckAiEndCta(): Boolean =
        onboardingStore.isDuckAiOnboardingFlow() && duckAiFireButtonShown() && !duckAiEndShown() && !hideTips()

    @WorkerThread
    private suspend fun canShowSubscriptionCta(): Boolean {
        if (hideTips() || daxDialogSubscriptionShown() || !isSubscriptionCtaAvailable()) return false

        return if (onboardingStore.isDuckAiOnboardingFlow()) {
            // Duck.ai flow: the DAX_ONBOARDING stage completes right after the fire-button CTA
            // (so the input-mode toggle can be enabled), which makes daxOnboardingActive() false
            // on home. Gate on the duck.ai end CTA having been shown to preserve ordering:
            // fire button -> end CTA -> privacy pro.
            duckAiEndShown()
        } else {
            // Regular search flow: privacy pro is part of requiredDaxOnboardingCtas, so the stage
            // stays active until it's dismissed and daxOnboardingActive() carries the gating.
            daxOnboardingActive()
        }
    }

    @WorkerThread
    private suspend fun canShowSubscriptionCtaForSkippedOnboarding(): Boolean =
        extendedOnboardingFeatureToggles.subscriptionPromoModalCta().isEnabled() &&
            hideTips() &&
            appInstallStore.daysInstalled() >= SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS &&
            !daxDialogSubscriptionShown() &&
            isSubscriptionCtaAvailable()

    @WorkerThread
    private suspend fun canShowSubscriptionPromoCta(): Boolean =
        extendedOnboardingFeatureToggles.subscriptionPromoModalCtaExistingUsers().isEnabled() &&
            appInstallStore.daysInstalled() >= SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS &&
            !daxDialogSubscriptionShown() &&
            isSubscriptionCtaAvailable()

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return !widgetCapabilities.hasInstalledWidgets && !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    private suspend fun freeTrialCopyAvailable(): Boolean =
        extendedOnboardingFeatureToggles.freeTrialCopy().isEnabled() && subscriptions.isFreeTrialEligible()

    @WorkerThread
    private suspend fun getBrowserCta(site: Site?, detectedRefreshPatterns: Set<RefreshPattern>, suppressDuckAiOnboardingCta: Boolean): Cta? {
        val nonNullSite = site ?: return null

        val host = nonNullSite.domain
        if (host == null || userAllowListRepository.isDomainInUserAllowList(host) || isSiteNotAllowedForOnboarding(nonNullSite)) {
            return null
        }

        nonNullSite.let {
            if (duckDuckGoUrlDetector.isDuckDuckGoEmailUrl(it.url)) {
                return null
            }

            // Duck.ai-focused onboarding CTAs
            if (duckChat.isDuckChatUrl(it.url.toUri())) {
                if (onboardingStore.isDuckAiOnboardingFlow() && !suppressDuckAiOnboardingCta) {
                    if (!duckAiFireButtonShown()) {
                        if (isBrandDesignUpdateEnabled()) {
                            return DaxDuckAiFireButtonBrandDesignUpdateContextualCta(
                                onboardingStore = onboardingStore,
                                appInstallStore = appInstallStore,
                                isLightTheme = appTheme.isLightModeEnabled(),
                                deviceInfo = deviceInfo,
                            )
                        }
                        return OnboardingDaxDialogCta.DaxDuckAiFireButtonCta(onboardingStore, appInstallStore)
                    }
                }
                return null
            }

            if (areInContextDaxDialogsCompleted()) {
                return if (brokenSitePrompt.shouldShowBrokenSitePrompt(nonNullSite.url, detectedRefreshPatterns)) {
                    BrokenSitePromptDialogCta()
                } else {
                    null
                }
            }

            // Trackers blocked
            if (!daxDialogTrackersFoundShown() && !isSerpUrl(it.url) && it.orderedTrackerBlockedEntities().isNotEmpty()) {
                if (isBrandDesignUpdateEnabled()) {
                    return DaxTrackersBlockedBrandDesignUpdateContextualCta(
                        onboardingStore = onboardingStore,
                        appInstallStore = appInstallStore,
                        trackers = it.orderedTrackerBlockedEntities(),
                        settingsDataStore = settingsDataStore,
                        isLightTheme = appTheme.isLightModeEnabled(),
                        deviceInfo = deviceInfo,
                    )
                }
                return OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                    onboardingStore,
                    appInstallStore,
                    it.orderedTrackerBlockedEntities(),
                    settingsDataStore,
                )
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && !daxDialogTrackersFoundShown() &&
                        OnboardingDaxDialogCta.mainTrackerNetworks.any { mainNetwork -> entity.displayName.contains(mainNetwork) }
                    ) {
                        if (isBrandDesignUpdateEnabled()) {
                            return DaxMainNetworkBrandDesignUpdateContextualCta(
                                onboardingStore = onboardingStore,
                                appInstallStore = appInstallStore,
                                network = entity.displayName,
                                siteHost = host,
                                isLightTheme = appTheme.isLightModeEnabled(),
                                deviceInfo = deviceInfo,
                            )
                        }
                        return OnboardingDaxDialogCta.DaxMainNetworkCta(
                            onboardingStore,
                            appInstallStore,
                            entity.displayName,
                            host,
                        )
                    }
                }
            }

            // SERP
            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                if (isBrandDesignUpdateEnabled()) {
                    return DaxSerpBrandDesignUpdateContextualCta(
                        onboardingStore,
                        appInstallStore,
                        isLightTheme = appTheme.isLightModeEnabled(),
                        deviceInfo = deviceInfo,
                    )
                }
                return OnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
            }

            // No trackers blocked
            if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                if (isBrandDesignUpdateEnabled()) {
                    return DaxNoTrackersBrandDesignUpdateContextualCta(
                        onboardingStore,
                        appInstallStore,
                        isLightTheme = appTheme.isLightModeEnabled(),
                        deviceInfo = deviceInfo,
                    )
                }
                return OnboardingDaxDialogCta.DaxNoTrackersCta(onboardingStore, appInstallStore)
            }

            // End
            if (canShowDaxCtaEndOfJourney() && daxDialogFireEducationShown()) {
                if (isBrandDesignUpdateEnabled()) {
                    return DaxEndBrandDesignUpdateContextualCta(
                        onboardingStore,
                        appInstallStore,
                        isLightTheme = appTheme.isLightModeEnabled(),
                        deviceInfo = deviceInfo,
                    )
                }
                return OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore)
            }

            return null
        }
    }

    private fun isSiteNotAllowedForOnboarding(site: Site): Boolean {
        val uri = site.url.toUri()

        if (subscriptions.isSubscriptionUrl(uri)) return true

        if (duckChat.isDuckChatUrl(uri)) return !onboardingStore.isDuckAiOnboardingFlow()

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
            val isLastContextDialogShown = when {
                onboardingStore.isDuckAiOnboardingFlow() -> duckAiEndShown()
                isSubscriptionCtaAvailable() -> daxDialogSubscriptionShown()
                else -> daxDialogEndShown()
            }
            noBrowserCtaExperiment || isLastContextDialogShown || hideTips() || !userStageStore.daxOnboardingActive()
        }
    }

    suspend fun areInContextDaxDialogsCompleted(): Boolean {
        return withContext(dispatchers.io()) {
            val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
            val inContextDaxCtasShown = if (onboardingStore.isDuckAiOnboardingFlow()) {
                duckAiFireButtonShown() && duckAiEndShown()
            } else {
                daxDialogSerpShown() && daxDialogTrackersFoundShown() && daxDialogFireEducationShown() && daxDialogEndShown()
            }
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

    private fun daxDialogSubscriptionShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO_PRIVACY_PRO)

    private fun pulseFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun duckAiFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DUCK_AI_FIRE_BUTTON)

    private fun duckAiEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DUCK_AI_END)

    private fun isSerpUrl(url: String): Boolean = url.contains(OnboardingDaxDialogCta.SERP)

    private suspend fun daxOnboardingActive(): Boolean = userStageStore.daxOnboardingActive()

    private suspend fun pulseAnimationDisabled(): Boolean =
        !daxOnboardingActive() || pulseFireButtonShown() || daxDialogFireEducationShown() || hideTips()

    private suspend fun allOnboardingCtasShown(): Boolean {
        return requiredDaxOnboardingCtas().all { dismissedCtaDao.exists(it) }
    }

    private fun forceStopFireButtonPulseAnimationFlow() = aggregateTabProvider.observe().distinctUntilChanged()
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

    fun onContextualSearchSubmitted(cta: Cta, query: String) {
        when (cta) {
            is DaxTryASearchBrandDesignUpdateBubbleCta ->
                onboardingPixelSender.fire(
                    OnboardingPixelName.ONBOARDING_SEARCH,
                    OnboardingPixelAction.SuggestionClicked(fromSuggestion = isSuggestedSearchOption(query)),
                )
            is DaxVisitSiteOptionsBrandDesignUpdateBubbleCta ->
                onboardingPixelSender.fire(
                    OnboardingPixelName.ONBOARDING_VISIT_SITE,
                    OnboardingPixelAction.SuggestionClicked(fromSuggestion = isSuggestedSiteOption(query)),
                )
            else -> {}
        }
    }

    fun onContextualFireButtonEngaged(cta: Cta) {
        if (cta is DaxFireButtonBrandDesignUpdateContextualCta) {
            onboardingPixelSender.fire(OnboardingPixelName.ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Clicked(engaged = true))
        }
    }

    fun isSuggestedSearchOption(query: String): Boolean = onboardingStore.getSearchOptions().map { it.link }.contains(query)

    fun isSuggestedSiteOption(query: String): Boolean = onboardingStore.getSitesOptions().map { it.link }.contains(query)

    suspend fun isPromoOnboardingDialogShowing(): Boolean =
        withContext(dispatchers.io()) {
            canShowSubscriptionCtaForSkippedOnboarding() || canShowSubscriptionPromoCta()
        }

    private suspend fun hasNoSubscription(): Boolean = subscriptions.getSubscriptionStatus() == SubscriptionStatus.UNKNOWN

    companion object {
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
        private const val SUBSCRIPTION_SKIPPED_ONBOARDING_MIN_DAYS = 7L
    }
}
