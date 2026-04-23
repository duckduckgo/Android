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
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.asFlow
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
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
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.ui.store.AppTheme
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    private val mockTabRepository: TabRepository = mock()
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
        whenever(mockTabRepository.flowTabs).thenReturn(db.tabsDao().liveTabs().asFlow())
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
            tabRepository = mockTabRepository,
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

    private fun newBrandDesignCta(): DaxSiteSuggestionsBrandDesignUpdateContextualCta =
        DaxSiteSuggestionsBrandDesignUpdateContextualCta(
            onboardingStore = mockOnboardingStore,
            appInstallStore = mockAppInstallStore,
            onSiteSuggestionOptionClicked = {},
            isLightTheme = true,
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
