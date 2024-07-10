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
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
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
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
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
        if (cta is OnboardingDaxDialogCta && cta.markAsReadOnShow) {
            dismissedCtaDao.insert(DismissedCta(cta.ctaId))
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

            dismissedCtaDao.insert(DismissedCta(cta.ctaId))

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
    ): Cta? {
        return withContext(dispatcher) {
            if (isBrowserShowing) {
                getDaxDialogCta(site)
            } else {
                getHomeCta()
            }
        }
    }

    suspend fun getFireDialogCta(): OnboardingDaxDialogCta.DaxFireButtonCta? {
        if (!daxOnboardingActive() || daxDialogFireEducationShown()) return null
        return withContext(dispatchers.io()) {
            return@withContext OnboardingDaxDialogCta.DaxFireButtonCta(onboardingStore, appInstallStore)
        }
    }

    suspend fun getSiteSuggestionsDialogCta(): OnboardingDaxDialogCta.DaxSiteSuggestionsCta? {
        if (!daxOnboardingActive() || !canShowDaxIntroVisitSiteCta()) return null
        return withContext(dispatchers.io()) {
            return@withContext OnboardingDaxDialogCta.DaxSiteSuggestionsCta(onboardingStore, appInstallStore)
        }
    }

    private suspend fun getHomeCta(): Cta? {
        return when {
            canShowDaxIntroCta() && extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                dismissedCtaDao.insert(DismissedCta(CtaId.DAX_INTRO))
                dismissedCtaDao.insert(DismissedCta(CtaId.DAX_END))
                null
            }
            canShowDaxIntroCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore)
            }

            canShowDaxIntroVisitSiteCta() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore)
            }

            canShowDaxCtaEndOfJourney() && !extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore)
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

    private suspend fun canShowDaxDialogCta(): Boolean {
        return when {
            !daxOnboardingActive() || hideTips() -> false
            extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled() -> {
                settingsDataStore.hideTips = true
                dismissedCtaDao.insert(DismissedCta(CtaId.DAX_END))
                userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)
                false
            }
            else -> true
        }
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
                return OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                    onboardingStore,
                    appInstallStore,
                    it.orderedTrackerBlockedEntities(),
                )
            }

            // Is major network
            if (it.entity != null) {
                it.entity?.let { entity ->
                    if (!daxDialogNetworkShown() && OnboardingDaxDialogCta.mainTrackerNetworks.contains(entity.displayName)) {
                        return OnboardingDaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, entity.displayName, host)
                    }
                }
            }

            // SERP
            if (isSerpUrl(it.url) && !daxDialogSerpShown()) {
                return OnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore)
            }

            // No trackers blocked
            if (!isSerpUrl(it.url) && !daxDialogOtherShown() && !daxDialogTrackersFoundShown() && !daxDialogNetworkShown()) {
                return OnboardingDaxDialogCta.DaxNoTrackersCta(onboardingStore, appInstallStore)
            }

            // End
            if (canShowDaxCtaEndOfJourney() && daxDialogFireEducationShown()) {
                return OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore)
            }

            return null
        }
    }

    private fun daxDialogIntroShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_INTRO)

    // We only want to show New Tab when the Home CTAs from Onboarding has finished
    // https://app.asana.com/0/1157893581871903/1207769731595075/f
    fun daxDialogEndShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_END)

    private fun daxDialogSerpShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)

    private fun daxDialogOtherShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)

    private fun daxDialogTrackersFoundShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)

    private fun daxDialogNetworkShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)

    private fun daxDialogFireEducationShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)

    private fun pulseFireButtonShown(): Boolean = dismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)

    private fun isSerpUrl(url: String): Boolean = url.contains(OnboardingDaxDialogCta.SERP)

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

    @Deprecated("New users won't have this option available since extended onboarding")
    private fun hideTips() = settingsDataStore.hideTips

    fun isSuggestedSearchOption(query: String): Boolean = onboardingStore.getSearchOptions().map { it.link }.contains(query)

    fun isSuggestedSiteOption(query: String): Boolean = onboardingStore.getSitesOptions().map { it.link }.contains(query)

    companion object {
        private const val MAX_TABS_OPEN_FIRE_EDUCATION = 2
    }
}
