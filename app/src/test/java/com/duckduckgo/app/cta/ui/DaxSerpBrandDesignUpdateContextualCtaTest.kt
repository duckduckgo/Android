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
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.asFlow
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

@FlowPreview
@RunWith(AndroidJUnit4::class)
class DaxSerpBrandDesignUpdateContextualCtaTest {

    @get:Rule
    @Suppress("unused")
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

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

    private val detectedRefreshPatterns: Set<RefreshPattern> = emptySet()

    private lateinit var testee: CtaViewModel
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val enabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    @Before
    fun before() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(disabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.subscriptionPromoModalCta()).thenReturn(disabledToggle)
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
        whenever(mockOnboardingBrandDesignUpdateToggles.self()).thenReturn(enabledToggle)
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

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
    fun whenRefreshCtaOnSerpAndBrandDesignFlagEnabledThenReturnsBrandDesignSerpCta() = runTest {
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site(url = "http://www.duckduckgo.com"),
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is DaxSerpBrandDesignUpdateContextualCta)
        assertFalse(value is OnboardingDaxDialogCta.DaxSerpCta)
    }

    @Test
    fun whenRefreshCtaOnSerpAndBrandDesignFlagDisabledThenReturnsLegacySerpCta() = runTest {
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site(url = "http://www.duckduckgo.com"),
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxSerpCta)
        assertFalse(value is DaxSerpBrandDesignUpdateContextualCta)
    }

    @Test
    fun ctaIdMatchesLegacySerpCta() {
        val cta = newCta()
        assertEquals(CtaId.DAX_DIALOG_SERP, cta.ctaId)
    }

    @Test
    fun shownPixelMatchesLegacySerpCta() {
        val cta = newCta()
        assertEquals(AppPixelName.ONBOARDING_DAX_CTA_SHOWN, cta.shownPixel)
    }

    @Test
    fun okPixelMatchesLegacySerpCta() {
        val cta = newCta()
        assertEquals(AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON, cta.okPixel)
    }

    @Test
    fun cancelPixelIsNullAsInLegacySerpCta() {
        val cta = newCta()
        assertNull(cta.cancelPixel)
    }

    @Test
    fun closePixelMatchesLegacySerpCta() {
        val cta = newCta()
        assertEquals(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON, cta.closePixel)
    }

    @Test
    fun pixelShownParametersCarrySerpCtaToken() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        val params = newCta().pixelShownParameters()

        val journey = params[Pixel.PixelParameter.CTA_SHOWN]
        assertTrue(
            "Expected journey to contain SERP token, was: $journey",
            journey.orEmpty().contains(Pixel.PixelValues.DAX_SERP_CTA),
        )
    }

    @Test
    fun pixelOkParametersCarrySerpCtaToken() {
        val params = newCta().pixelOkParameters()
        assertEquals(Pixel.PixelValues.DAX_SERP_CTA, params[Pixel.PixelParameter.CTA_SHOWN])
    }

    @Test
    fun whenCtaShownThenShownPixelFired() = runTest {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val cta = newCta()

        testee.onCtaShown(cta)

        verify(mockPixel).fire(
            eq(AppPixelName.ONBOARDING_DAX_CTA_SHOWN),
            any(),
            any(),
            any(),
        )
    }

    @Test
    fun whenUserClicksOkButtonThenOkPixelFiredWithSerpCtaPixelParam() = runTest {
        val cta = newCta()

        testee.onUserClickCtaOkButton(cta)

        verify(mockPixel).fire(
            eq(AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON),
            eq(mapOf(Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.DAX_SERP_CTA)),
            any(),
            any(),
        )
    }

    @Test
    fun whenUserDismissesViaCloseButtonThenClosePixelFiredWithSerpCtaPixelParam() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta, viaCloseBtn = true)

        verify(mockPixel).fire(
            eq(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON),
            eq(mapOf(Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.DAX_SERP_CTA)),
            any(),
            any(),
        )
    }

    @Test
    fun whenUserDismissesWithoutCloseButtonThenNoCloseOrCancelPixelFired() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta, viaCloseBtn = false)

        verify(mockPixel, never()).fire(eq(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON), any(), any(), any())
    }

    @Test
    fun whenUserDismissesThenDismissalPersisted() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta)

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_SERP))
    }

    private fun newCta(): DaxSerpBrandDesignUpdateContextualCta =
        DaxSerpBrandDesignUpdateContextualCta(
            onboardingStore = mockOnboardingStore,
            appInstallStore = mockAppInstallStore,
            isLightTheme = true,
        )

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        https: HttpsStatus = HttpsStatus.SECURE,
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(uri)
        whenever(site.https).thenReturn(https)
        whenever(site.entity).thenReturn(null)
        whenever(site.trackingEvents).thenReturn(emptyList())
        whenever(site.trackerCount).thenReturn(0)
        whenever(site.majorNetworkCount).thenReturn(0)
        whenever(site.allTrackersBlocked).thenReturn(true)
        return site
    }
}
