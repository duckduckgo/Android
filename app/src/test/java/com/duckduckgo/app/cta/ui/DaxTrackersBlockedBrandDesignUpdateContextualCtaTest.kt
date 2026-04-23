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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
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
class DaxTrackersBlockedBrandDesignUpdateContextualCtaTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

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

    private val detectedRefreshPatterns: Set<RefreshPattern> = emptySet()

    private lateinit var testee: CtaViewModel

    @Before
    fun before() = runTest {
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.subscriptionPromoModalCta()).thenReturn(mockDisabledToggle)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
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

    @Test
    fun whenBrandDesignUpdateEnabledAndTrackersBlockedConditionsMetThenReturnBrandDesignCta() = runTest {
        givenDaxOnboardingActive()
        givenBrandDesignUpdateEnabled()

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = siteWithBlockedTrackers(),
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is DaxTrackersBlockedBrandDesignUpdateContextualCta)
    }

    @Test
    fun whenBrandDesignUpdateDisabledAndTrackersBlockedConditionsMetThenReturnLegacyCta() = runTest {
        givenDaxOnboardingActive()
        givenBrandDesignUpdateDisabled()

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = siteWithBlockedTrackers(),
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenCtaShownThenShownPixelFired() = runTest {
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
    fun whenUserClicksOkButtonThenOkPixelFiredWithTrackersBlockedCtaPixelParam() = runTest {
        val cta = newCta()

        testee.onUserClickCtaOkButton(cta)

        verify(mockPixel).fire(
            eq(AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON),
            eq(mapOf(Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA)),
            any(),
            any(),
        )
    }

    @Test
    fun whenUserDismissesViaCloseButtonThenClosePixelFiredWithTrackersBlockedCtaPixelParam() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta, viaCloseBtn = true)

        verify(mockPixel).fire(
            eq(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON),
            eq(mapOf(Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA)),
            any(),
            any(),
        )
    }

    @Test
    fun whenUserDismissesWithoutCloseButtonThenNoCancelPixelFired() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta, viaCloseBtn = false)

        verify(mockPixel, never()).fire(eq(AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON), any(), any(), any())
    }

    @Test
    fun whenUserDismissesThenDismissalPersisted() = runTest {
        val cta = newCta()

        testee.onUserDismissedCta(cta)

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND))
    }

    private fun newCta() = DaxTrackersBlockedBrandDesignUpdateContextualCta(
        onboardingStore = mockOnboardingStore,
        appInstallStore = mockAppInstallStore,
        trackers = emptyList(),
        settingsDataStore = mockSettingsDataStore,
        isLightTheme = true,
    )

    private suspend fun givenDaxOnboardingActive() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
    }

    private fun givenBrandDesignUpdateEnabled() {
        whenever(mockOnboardingBrandDesignUpdateToggles.self()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(mockEnabledToggle)
    }

    private fun givenBrandDesignUpdateDisabled() {
        whenever(mockOnboardingBrandDesignUpdateToggles.self()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(mockDisabledToggle)
    }

    private fun siteWithBlockedTrackers(): Site {
        val trackingEvent = TrackingEvent(
            documentUrl = "test.com",
            trackerUrl = "test.com",
            categories = null,
            entity = TdsEntity("Test Tracker", "Test Tracker", 9.0),
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val site: Site = mock()
        whenever(site.url).thenReturn("http://www.cnn.com")
        whenever(site.uri).thenReturn(android.net.Uri.parse("http://www.cnn.com"))
        whenever(site.trackingEvents).thenReturn(listOf(trackingEvent))
        whenever(site.trackerCount).thenReturn(1)
        whenever(site.majorNetworkCount).thenReturn(0)
        whenever(site.allTrackersBlocked).thenReturn(true)
        return site
    }
}
