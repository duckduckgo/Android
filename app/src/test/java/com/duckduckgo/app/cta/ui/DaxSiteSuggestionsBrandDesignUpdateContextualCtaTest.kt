/*
 * Copyright (c) 2026 DuckDuckGo
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

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.asFlow
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentMetrics
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_DAX_CTA_SHOWN
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CTA_SHOWN
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA
import com.duckduckgo.app.tabs.model.AggregateTabProvider
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionPromoCtaShownPlugin
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [DaxSiteSuggestionsBrandDesignUpdateContextualCta].
 *
 * Verifies the CTA is constructed when the brand-design flag is on and that the pixel
 * parameters the legacy CTA carried (`shownPixel`, `okPixel`, `closePixel` with the
 * `DAX_INITIAL_VISIT_SITE_CTA` param) still fire via `CtaViewModel`. Telemetry parity
 * is the regression-surface a stale stub would break, so it's exercised explicitly.
 */
@RunWith(AndroidJUnit4::class)
class DaxSiteSuggestionsBrandDesignUpdateContextualCtaTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase

    private val mockWidgetCapabilities: WidgetCapabilities = mock()
    private val mockDismissedCtaDao: DismissedCtaDao = mock()
    private val mockPixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockUserStageStore: UserStageStore = mock()
    private val mockAggregateTabProvider: AggregateTabProvider = mock()
    private val mockExtendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockSubscriptions: Subscriptions = mock()
    private val mockBrokenSitePrompt: BrokenSitePrompt = mock()
    private val mockSubscriptionPromoCtaShownPlugin: SubscriptionPromoCtaShownPlugin = mock()
    private val mockSubscriptionPromoCtaShownPlugins: PluginPoint<SubscriptionPromoCtaShownPlugin> = mock {
        on { getPlugins() } doReturn listOf(mockSubscriptionPromoCtaShownPlugin)
    }
    private val mockDuckChat: DuckChat = mock()
    private val mockOnboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock()
    private val mockAppTheme: AppTheme = mock { on { isLightModeEnabled() } doReturn true }
    private val mockDuckAiOnboardingExperimentMetrics: DuckAiOnboardingExperimentMetrics = mock()
    private val mockDeviceInfo: DeviceInfo = mock()

    private val mockEnabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    private lateinit var testee: CtaViewModel

    @Before
    fun before() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.subscriptionPromoModalCta()).thenReturn(mockDisabledToggle)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockDismissedCtaDao.dismissedCtas()).thenReturn(db.dismissedCtaDao().dismissedCtas())
        whenever(mockAggregateTabProvider.observe()).thenReturn(db.tabsDao().liveTabs().asFlow())
        whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(DISABLED)
        whenever(mockDuckPlayer.isDuckPlayerUri(any())).thenReturn(false)
        whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(false, AlwaysAsk))
        whenever(mockDuckPlayer.isYouTubeUrl(any())).thenReturn(false)
        whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(false)
        whenever(mockBrokenSitePrompt.shouldShowBrokenSitePrompt(any(), any())).thenReturn(false)
        whenever(mockBrokenSitePrompt.isFeatureEnabled()).thenReturn(false)
        whenever(mockBrokenSitePrompt.getUserRefreshPatterns()).thenReturn(emptySet())
        whenever(mockSubscriptions.isEligible()).thenReturn(false)

        testee = CtaViewModel(
            appInstallStore = mockAppInstallStore,
            pixel = mockPixel,
            widgetCapabilities = mockWidgetCapabilities,
            dismissedCtaDao = mockDismissedCtaDao,
            userAllowListRepository = mockUserAllowListRepository,
            settingsDataStore = mockSettingsDataStore,
            onboardingStore = mockOnboardingStore,
            userStageStore = mockUserStageStore,
            aggregateTabProvider = mockAggregateTabProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            duckChat = mockDuckChat,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
            extendedOnboardingFeatureToggles = mockExtendedOnboardingFeatureToggles,
            subscriptions = mockSubscriptions,
            duckPlayer = mockDuckPlayer,
            brokenSitePrompt = mockBrokenSitePrompt,
            subscriptionPromoCtaShownPlugins = mockSubscriptionPromoCtaShownPlugins,
            onboardingBrandDesignUpdateToggles = mockOnboardingBrandDesignUpdateToggles,
            appTheme = mockAppTheme,
            duckAiOnboardingExperimentMetrics = mockDuckAiOnboardingExperimentMetrics,
            deviceInfo = mockDeviceInfo,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenBrandDesignUpdateEnabledThenGetSiteSuggestionsCtaReturnsBrandDesignClass() = runTest {
        givenSiteSuggestionsCtaPreconditions()
        whenever(mockOnboardingBrandDesignUpdateToggles.self()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(mockEnabledToggle)

        val value = testee.getSiteSuggestionsDialogCta(onSiteSuggestionOptionClicked = {})

        assertTrue(value is DaxSiteSuggestionsBrandDesignUpdateContextualCta)
    }

    @Test
    fun whenBrandDesignUpdateDisabledThenGetSiteSuggestionsCtaReturnsLegacyClass() = runTest {
        givenSiteSuggestionsCtaPreconditions()
        whenever(mockOnboardingBrandDesignUpdateToggles.self()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(mockDisabledToggle)

        val value = testee.getSiteSuggestionsDialogCta(onSiteSuggestionOptionClicked = {})

        assertTrue(value is OnboardingDaxDialogCta.DaxSiteSuggestionsCta)
    }

    @Test
    fun whenOnCtaShownThenShownPixelFiresWithCtaShownParameter() = runTest {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("")
        val cta = newBrandDesignCta()

        testee.onCtaShown(cta)

        verify(mockPixel).fire(
            eq(ONBOARDING_DAX_CTA_SHOWN),
            org.mockito.kotlin.argThat { containsKey(CTA_SHOWN) },
            any(),
            eq(Count),
        )
    }

    @Test
    fun whenOnUserClickCtaOkButtonThenOkPixelFiresWithCtaShownParameter() = runTest {
        val cta = newBrandDesignCta()

        testee.onUserClickCtaOkButton(cta)

        verify(mockPixel).fire(
            eq(ONBOARDING_DAX_CTA_OK_BUTTON),
            eq(mapOf(CTA_SHOWN to DAX_INITIAL_VISIT_SITE_CTA)),
            any(),
            eq(Count),
        )
    }

    @Test
    fun whenOnUserDismissedViaCloseButtonThenClosePixelFires() = runTest {
        val cta = newBrandDesignCta()

        testee.onUserDismissedCta(cta, viaCloseBtn = true)

        verify(mockPixel).fire(
            eq(ONBOARDING_DAX_CTA_DISMISS_BUTTON),
            any(),
            any(),
            eq(Count),
        )
    }

    @Test
    fun applyWavingDaxState_phoneLandscape_hidesDax() {
        val dax: LottieAnimationView = mock()
        val container = stubContainerAndDax(dax, formFactor = FormFactor.PHONE, orientation = Configuration.ORIENTATION_LANDSCAPE)
        whenever(dax.isAnimating).thenReturn(false)

        newBrandDesignCta().applyWavingDaxState(container)

        verify(dax).isVisible = false
        verify(dax, never()).isVisible = true
    }

    @Test
    fun applyWavingDaxState_tabletLandscape_anchorsDaxToCard() {
        val dax: LottieAnimationView = mock()
        val lp = stubContainerAndDaxWithLayoutParams(dax, formFactor = FormFactor.TABLET, orientation = Configuration.ORIENTATION_LANDSCAPE)

        newBrandDesignCta().applyWavingDaxState(stubContainerWithDax(dax))

        assertEquals(R.id.contextualBrandDesignCardView, lp.startToStart)
        verify(dax).isVisible = true
        verify(dax).translationX = -70f
    }

    @Test
    fun applyWavingDaxState_tabletPortrait_anchorsDaxToCard() {
        val dax: LottieAnimationView = mock()
        val lp = stubContainerAndDaxWithLayoutParams(dax, formFactor = FormFactor.TABLET, orientation = Configuration.ORIENTATION_PORTRAIT)

        newBrandDesignCta().applyWavingDaxState(stubContainerWithDax(dax))

        assertEquals(R.id.contextualBrandDesignCardView, lp.startToStart)
        verify(dax).isVisible = true
    }

    @Test
    fun applyWavingDaxState_phonePortrait_anchorsDaxToParent() {
        val dax: LottieAnimationView = mock()
        val lp = stubContainerAndDaxWithLayoutParams(dax, formFactor = FormFactor.PHONE, orientation = Configuration.ORIENTATION_PORTRAIT)

        newBrandDesignCta().applyWavingDaxState(stubContainerWithDax(dax))

        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.startToStart)
        verify(dax).isVisible = true
    }

    private fun stubContainerWithDax(dax: LottieAnimationView): View {
        val container: View = mock()
        whenever(container.findViewById<LottieAnimationView>(R.id.wavingDax)).thenReturn(dax)
        return container
    }

    private fun stubContainerAndDax(
        dax: LottieAnimationView,
        formFactor: FormFactor,
        orientation: Int,
    ): View {
        val configuration = Configuration().apply { this.orientation = orientation }
        val resources: Resources = mock()
        val daxContext: Context = mock()
        whenever(dax.context).thenReturn(daxContext)
        whenever(daxContext.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(mockDeviceInfo.formFactor()).thenReturn(formFactor)
        return stubContainerWithDax(dax)
    }

    private fun stubContainerAndDaxWithLayoutParams(
        dax: LottieAnimationView,
        formFactor: FormFactor,
        orientation: Int,
    ): ConstraintLayout.LayoutParams {
        val lp = ConstraintLayout.LayoutParams(0, 0)
        val configuration = Configuration().apply { this.orientation = orientation }
        val displayMetrics = DisplayMetrics().apply { density = 1f }
        val resources: Resources = mock()
        val daxContext: Context = mock()
        whenever(dax.layoutParams).thenReturn(lp)
        whenever(dax.context).thenReturn(daxContext)
        whenever(dax.resources).thenReturn(resources)
        whenever(daxContext.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(mockDeviceInfo.formFactor()).thenReturn(formFactor)
        return lp
    }

    private fun newBrandDesignCta(): DaxSiteSuggestionsBrandDesignUpdateContextualCta =
        DaxSiteSuggestionsBrandDesignUpdateContextualCta(
            onboardingStore = mockOnboardingStore,
            appInstallStore = mockAppInstallStore,
            isLightTheme = true,
            deviceInfo = mockDeviceInfo,
        )

    private suspend fun givenSiteSuggestionsCtaPreconditions() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_NETWORK)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_OTHER)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)
    }
}
