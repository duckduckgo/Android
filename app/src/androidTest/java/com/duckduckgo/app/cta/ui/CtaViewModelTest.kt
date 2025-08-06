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

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.asFlow
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.DuckDuckGoUrlDetectorImpl
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetExperiment
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
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
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import com.duckduckgo.subscriptions.api.Subscriptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@FlowPreview
class CtaViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

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

    private val detectedRefreshPatterns: Set<RefreshPattern> = emptySet()

    private val mockBrokenSitePrompt: BrokenSitePrompt = mock()

    private val mockSenseOfProtectionExperiment: SenseOfProtectionExperiment = mock()
    private val mockOnboardingHomeScreenWidgetExperiment: OnboardingHomeScreenWidgetExperiment = mock()

    private val mockOnboardingDesignExperimentToggles: OnboardingDesignExperimentToggles = mock()

    private val mockRebrandingFeatureToggle: SubscriptionRebrandingFeatureToggle = mock()

    private val requiredDaxOnboardingCtas: List<CtaId> = listOf(
        CtaId.DAX_INTRO,
        CtaId.DAX_DIALOG_SERP,
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        CtaId.DAX_DIALOG_NETWORK,
        CtaId.DAX_FIRE_BUTTON,
        CtaId.DAX_END,
    )

    private lateinit var testee: CtaViewModel

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockEnabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    @Before
    fun before() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
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
        whenever(mockOnboardingDesignExperimentToggles.modifiedControl()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.buckOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.bbOnboarding()).thenReturn(mockDisabledToggle)

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
            duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
            extendedOnboardingFeatureToggles = mockExtendedOnboardingFeatureToggles,
            subscriptions = mockSubscriptions,
            duckPlayer = mockDuckPlayer,
            brokenSitePrompt = mockBrokenSitePrompt,
            senseOfProtectionExperiment = mockSenseOfProtectionExperiment,
            onboardingHomeScreenWidgetExperiment = mockOnboardingHomeScreenWidgetExperiment,
            onboardingDesignExperimentToggles = mockOnboardingDesignExperimentToggles,
            rebrandingFeatureToggle = mockRebrandingFeatureToggle,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenCtaShownAndCtaIsDaxAndCanNotSendPixelThenPixelIsNotFired() = runTest {
        testee.onCtaShown(DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel, never()).fire(eq(SURVEY_CTA_SHOWN), any(), any(), eq(Count))
    }

    @Test
    fun whenBrokenSitePromptDialogCtaIsShownThenPixelIsFired() = runTest {
        testee.onCtaShown(BrokenSitePromptDialogCta())
        verify(mockPixel).fire(eq(SITE_NOT_WORKING_SHOWN), any(), any(), eq(Count))
    }

    @Test
    fun whenUserClicksReportBrokenSiteThenPixelIsFired() = runTest {
        testee.onUserClickCtaOkButton(BrokenSitePromptDialogCta())
        verify(mockPixel).fire(eq(SITE_NOT_WORKING_WEBSITE_BROKEN), any(), any(), eq(Count))
    }

    @Test
    fun whenCtaShownAndCtaIsDaxAndCanSendPixelThenPixelIsFired() = runTest {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("s:0")
        testee.onCtaShown(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockPixel, never()).fire(eq(SURVEY_CTA_SHOWN), any(), any(), eq(Count))
    }

    @Test
    fun whenCtaShownAndCtaIsNotDaxThenPixelIsFired() = runTest {
        testee.onCtaShown(HomePanelCta.AddWidgetAuto)
        verify(mockPixel).fire(eq(WIDGET_CTA_SHOWN), any(), any(), eq(Count))
    }

    @Test
    fun whenCtaLaunchedPixelIsFired() = runTest {
        testee.onUserClickCtaOkButton(HomePanelCta.AddWidgetAuto)
        verify(mockPixel).fire(eq(WIDGET_CTA_LAUNCHED), any(), any(), eq(Count))
    }

    @Test
    fun whenCtaDismissedThenCancelPixelIsFired() = runTest {
        testee.onUserDismissedCta(HomePanelCta.AddWidgetAuto)
        verify(mockPixel).fire(eq(WIDGET_CTA_DISMISSED), any(), any(), eq(Count))
    }

    @Test
    fun whenOnboardingCtaDismissedViaCloseBtnThenPixelIsFired() = runTest {
        val testCta = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)

        testee.onUserDismissedCta(testCta, true)

        verify(mockPixel).fire(eq(ONBOARDING_DAX_CTA_DISMISS_BUTTON), any(), any(), eq(Count))
    }

    @Test
    fun whenOnboardingCtaDismissedWithoutCloseBtnThenPixelIsNotFired() = runTest {
        val testCta = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)

        testee.onUserDismissedCta(testCta)

        verify(mockPixel, never()).fire(eq(ONBOARDING_DAX_CTA_DISMISS_BUTTON), any(), any(), eq(Count))
    }

    @Test
    fun whenNonSurveyCtaDismissedCtaThenDatabaseNotified() = runTest {
        testee.onUserDismissedCta(HomePanelCta.AddWidgetAuto)
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.ADD_WIDGET))
    }

    @Test
    fun whenCtaDismissedAndUserHasPendingOnboardingCtasThenStageNotCompleted() = runTest {
        givenDaxOnboardingActive()
        givenShownDaxOnboardingCtas(emptyList())
        testee.onUserDismissedCta(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore, times(0)).stageCompleted(any())
    }

    @Test
    fun whenCtaDismissedAndAllDaxOnboardingCtasShownThenStageCompleted() = runTest {
        givenDaxOnboardingActive()
        givenShownDaxOnboardingCtas(requiredDaxOnboardingCtas)
        testee.onUserDismissedCta(OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentToggles))
        verify(mockUserStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenRegisterDaxBubbleIntroCtaThenDatabaseNotified() = runTest {
        testee.onUserDismissedCta(DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
    }

    @Test
    fun whenRegisterDaxBubbleIntroVisitSiteCtaThenDatabaseNotified() = runTest {
        testee.onUserDismissedCta(DaxBubbleCta.DaxIntroVisitSiteOptionsCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO_VISIT_SITE))
    }

    @Test
    fun whenRegisterDaxBubbleEndCtaThenDatabaseNotified() = runTest {
        testee.onUserDismissedCta(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
    }

    @Test
    fun whenRegisterCtaAndUserHasPendingOnboardingCtasThenStageNotCompleted() = runTest {
        givenDaxOnboardingActive()
        givenShownDaxOnboardingCtas(emptyList())
        testee.onUserDismissedCta(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore, times(0)).stageCompleted(any())
    }

    @Test
    fun whenRegisterCtaAndAllDaxOnboardingCtasShownThenStageCompleted() = runTest {
        givenDaxOnboardingActive()
        givenShownDaxOnboardingCtas(requiredDaxOnboardingCtas)
        testee.onUserDismissedCta(DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore))
        verify(mockUserStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndSiteIsNullThenReturnNull() = runTest {
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = null,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndHideTipsIsTrueThenReturnNull() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndHideTipsIsTrueAndShouldShowBrokenSitePromptThenReturnBrokenSitePrompt() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val detectedRefreshPatterns = setOf(RefreshPattern.THRICE_IN_20_SECONDS)
        whenever(mockBrokenSitePrompt.shouldShowBrokenSitePrompt(any(), any())).thenReturn(true)

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertTrue(value is BrokenSitePromptDialogCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueAndWidgetCompatibleThenReturnWidgetCta() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndPrivacyOffForSiteThenReturnNull() = runTest {
        givenDaxOnboardingActive()
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(true)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueThenReturnWidgetAutoCta() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueAndExperimentEnabledThenReturnAddWidgetAutoOnboardingExperiment() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is HomePanelCta.AddWidgetAutoOnboardingExperiment)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndHideTipsIsTrueThenReturnWidgetInstructionsCta() = runTest {
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is HomePanelCta.AddWidgetInstructions)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingMajorTrackerSiteThenReturnNetworkCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val expectedCtaText = context.resources.getString(
            R.string.daxMainNetworkCtaText,
            "Facebook",
            "facebook.com",
            "Facebook",
        )

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns,
        ) as OnboardingDaxDialogCta

        assertTrue(value is OnboardingDaxDialogCta.DaxMainNetworkCta)
        val actualText = (value as OnboardingDaxDialogCta.DaxMainNetworkCta).getTrackersDescription(context)
        assertEquals(expectedCtaText, actualText)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingOnSiteOwnedByMajorTrackerThenReturnNetworkCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://m.instagram.com", entity = TestEntity("Facebook", "Facebook", 9.0))
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        ) as OnboardingDaxDialogCta
        val expectedCtaText = context.resources.getString(
            R.string.daxMainNetworkOwnedCtaText,
            "Facebook",
            "instagram.com",
            "Facebook",
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxMainNetworkCta)
        val actualText = (value as OnboardingDaxDialogCta.DaxMainNetworkCta).getTrackersDescription(context)
        assertEquals(expectedCtaText, actualText)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingThenReturnTrackersBlockedCta() = runTest {
        givenDaxOnboardingActive()
        val trackingEvent = TrackingEvent(
            documentUrl = "test.com",
            trackerUrl = "test.com",
            categories = null,
            entity = TestEntity("test", "test", 9.0),
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val site = site(url = "http://www.cnn.com", trackerCount = 1, events = listOf(trackingEvent))
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndTrackersAreNotMajorThenReturnTrackersBlockedCta() = runTest {
        givenDaxOnboardingActive()
        val trackingEvent = TrackingEvent(
            documentUrl = "test.com",
            trackerUrl = "test.com",
            categories = null,
            entity = TestEntity("test", "test", 0.123),
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val site = site(url = "http://www.cnn.com", trackerCount = 1, events = listOf(trackingEvent))
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxTrackersBlockedCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndNoTrackersInformationThenReturnNoTrackersCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.cnn.com", trackerCount = 1)
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxNoTrackersCta)
    }

    @Test
    fun whenRefreshCtaOnSerpWhileBrowsingThenReturnSerpCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.duckduckgo.com")
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxSerpCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingThenReturnNoTrackersCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxNoTrackersCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingAndNoAutoconsentThenReturnOtherCta() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertTrue(value is OnboardingDaxDialogCta.DaxNoTrackersCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabThenValueReturnedIsNotDaxDialogCtaType() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "http://www.wikipedia.com")
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = false,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )

        assertFalse(value is OnboardingDaxDialogCta)
    }

    @Test
    fun whenRefreshOnboardingCtaAndCanShowDaxCtaEndOfJourneyButNotFireDialogShownThenOnboardingEndCtaDontShow() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)

        val site = site(url = "http://www.cnn.com", trackerCount = 1)
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertFalse(value is OnboardingDaxDialogCta.DaxEndCta)
    }

    @Test
    fun whenRefreshOnboardingCtaAndCanShowDaxCtaEndThenOnboardingEndCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)).thenReturn(true)

        val site = site(url = "http://www.cnn.com", trackerCount = 1)
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertTrue(value is OnboardingDaxDialogCta.DaxEndCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasNotPreviouslyShownThenIntroCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxIntroSearchOptionsCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasShownThenVisitSiteCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxIntroVisitSiteOptionsCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroAndVisitSiteCtasWereShownThenEndCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        givenAtLeastOneDaxDialogCtaShown()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxEndCta)
    }

    @Test
    fun whenRefreshCtaWhileBrowsingWithDaxOnboardingCompletedButNotAllCtasWereShownThenReturnNull() = runTest {
        givenShownDaxOnboardingCtas(listOf(CtaId.DAX_INTRO))
        givenUserIsEstablished()

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = true, detectedRefreshPatterns = detectedRefreshPatterns)
        assertNull(value)
    }

    @Test
    fun whenRefreshCtaWhileDaxOnboardingActiveAndBrowsingOnDuckDuckGoEmailUrlThenReturnNull() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "https://duckduckgo.com/email/")
        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun whenUserHidesAllTipsThenFireButtonAnimationShouldNotShow() = runTest {
        givenDaxOnboardingActive()
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenUserHasTwoOrMoreTabsThenFireButtonAnimationShouldNotShow() = runTest {
        givenDaxOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenFireAnimationStopsThenDaxFireButtonDisabled() = runTest {
        givenDaxOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        testee.showFireButtonPulseAnimation.first()

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
    }

    @Test
    fun whenFireButtonAnimationActiveAndUserOpensANewTabThenFireButtonAnimationStops() = runTest {
        givenDaxOnboardingActive()
        db.tabsDao().insertTab(TabEntity(tabId = "0", position = 0))
        db.dismissedCtaDao().insert(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND))
        db.tabsDao().insertTab(TabEntity(tabId = "1", position = 1))

        val collector = launch {
            testee.showFireButtonPulseAnimation.collect {
                assertFalse(it)
            }
        }
        collector.cancel()
    }

    @Test
    fun whenUserHasAlreadySeenFireButtonCtaThenFireButtonAnimationShouldNotShow() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenUserHasAlreadySeenFireButtonPulseAnimationThenFireButtonAnimationShouldNotShow() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON_PULSE)).thenReturn(true)

        assertFalse(testee.showFireButtonPulseAnimation.first())
    }

    @Test
    fun whenTipsActiveAndUserSeesAnyTriggerFirePulseAnimationCtaThenFireButtonAnimationShouldShow() = runTest {
        givenDaxOnboardingActive()
        val willTriggerFirePulseAnimationCtas = listOf(CtaId.DAX_DIALOG_TRACKERS_FOUND, CtaId.DAX_DIALOG_NETWORK, CtaId.DAX_DIALOG_OTHER)
        val launch = launch {
            testee.showFireButtonPulseAnimation.drop(1).collect {
                assertTrue(it)
            }
        }
        willTriggerFirePulseAnimationCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        launch.cancel()
    }

    @Test
    fun whenFirePulseAnimationDismissedThenCtaInsertedInDatabase() = runTest {
        testee.dismissPulseAnimation()

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON_PULSE))
    }

    @Test
    fun whenOnboardingCompletedThenNewDismissedCtasDoNotEmitValues() = runTest {
        givenDaxOnboardingCompleted()
        val launch = launch {
            testee.showFireButtonPulseAnimation.collect { /* noop */ }
        }
        clearInvocations(mockDismissedCtaDao)

        requiredDaxOnboardingCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        verifyNoMoreInteractions(mockDismissedCtaDao)
        launch.cancel()
    }

    @Test
    fun whenTipsActiveAndUserSeesAnyNonTriggerFirePulseAnimationCtaThenFireButtonAnimationShouldNotShow() = runTest {
        givenDaxOnboardingActive()
        val willTriggerFirePulseAnimationCtas = listOf(CtaId.DAX_DIALOG_TRACKERS_FOUND, CtaId.DAX_DIALOG_NETWORK, CtaId.DAX_DIALOG_OTHER)
        val willNotTriggerFirePulseAnimationCtas = CtaId.values().toList() - willTriggerFirePulseAnimationCtas

        val launch = launch {
            testee.showFireButtonPulseAnimation.collect {
                assertFalse(it)
            }
        }
        willNotTriggerFirePulseAnimationCtas.forEach {
            db.dismissedCtaDao().insert(DismissedCta(it))
        }

        launch.cancel()
    }

    @Test
    fun whenFireCtaDismissedThenFireDialogCtaIsNull() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_FIRE_BUTTON)).thenReturn(true)

        val fireDialogCta = testee.getFireDialogCta()

        assertNull(fireDialogCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasNotPreviouslyShownThenSearchSuggestionsCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_SERP)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxIntroSearchOptionsCta)
    }

    @Test
    fun whenRegisterDismissedDaxIntroVisitSiteCtaThenDatabaseNotified() = runTest {
        testee.onUserDismissedCta(
            DaxBubbleCta.DaxIntroVisitSiteOptionsCta(
                mockOnboardingStore,
                mockAppInstallStore,
            ),
        )
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO_VISIT_SITE))
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasPreviouslyShownThenIntroVisitSiteCtaShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxIntroVisitSiteOptionsCta)
    }

    @Test
    fun whenRefreshCtaOnHomeTabAndIntroCtaWasPreviouslyShownAndUserAlreadyVisitASiteThenIntroVisitSiteCtaIsNotShown() = runTest {
        givenDaxOnboardingActive()
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxIntroVisitSiteOptionsCta)
    }

    @Test
    fun whenCtaShownIfCtaIsNotMarkedAsReadOnShowThenCtaNotInsertedInDatabase() = runTest {
        testee.onCtaShown(OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentToggles))

        verify(mockDismissedCtaDao, never()).insert(DismissedCta(CtaId.DAX_DIALOG_SERP))
    }

    @Test
    fun whenCtaShownIfCtaIsMarkedAsReadOnShowThenCtaInsertedInDatabase() = runTest {
        testee.onCtaShown(OnboardingDaxDialogCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentToggles))

        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
    }

    @Test
    fun givenNoBrowserCtasExperimentWhenRefreshCtaOnHomeTabThenSkipOnboardingHomeCtas() = runTest {
        givenDaxOnboardingActive()
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxIntroSearchOptionsCta)
    }

    @Test
    fun givenNoBrowserCtasExperimentWhenFirstRefreshCtaOnHomeTabThenDontReturnWidgetCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is HomePanelCta.AddWidgetAuto)
    }

    @Test
    fun givenNoBrowserCtasExperimentWhenRefreshCtaWhileBrowsingThenReturnNull() = runTest {
        givenDaxOnboardingActive()
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        val site = site(url = "http://www.facebook.com", entity = TestEntity("Facebook", "Facebook", 9.0))

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun givenPrivacyProCtaExperimentWhenRefreshCtaOnHomeTabThenReturnPrivacyProCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.privacyProCta()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.modifiedControl()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.buckOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.bbOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.freeTrialCopy()).thenReturn(mockDisabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertTrue(value is DaxBubbleCta.DaxPrivacyProCta)
    }

    @Test
    fun givenPrivacyProCtaExperimentWhenRefreshCtaOnHomeTabAndModifiedControlOnboardingExperimentEnabledThenDoNotReturnPrivacyProCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockOnboardingDesignExperimentToggles.modifiedControl()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.buckOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.bbOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.privacyProCta()).thenReturn(mockEnabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxPrivacyProCta)
    }

    @Test
    fun givenPrivacyProCtaExperimentWhenRefreshCtaOnHomeTabAndBuckOnboardingExperimentEnabledThenDoNotReturnPrivacyProCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockOnboardingDesignExperimentToggles.buckOnboarding()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.bbOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.privacyProCta()).thenReturn(mockEnabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxPrivacyProCta)
    }

    @Test
    fun givenPrivacyProCtaExperimentWhenRefreshCtaOnHomeTabAndBBOnboardingExperimentEnabledThenDoNotReturnPrivacyProCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockOnboardingDesignExperimentToggles.bbOnboarding()).thenReturn(mockEnabledToggle)
        whenever(mockOnboardingDesignExperimentToggles.buckOnboarding()).thenReturn(mockDisabledToggle)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockExtendedOnboardingFeatureToggles.privacyProCta()).thenReturn(mockEnabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxPrivacyProCta)
    }

    @Test
    fun givenPrivacyProCtaExperimentDisabledWhenRefreshCtaOnHomeTabThenDontReturnPrivacyProCta() = runTest {
        givenDaxOnboardingActive()
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_VISIT_SITE)).thenReturn(true)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockOnboardingHomeScreenWidgetExperiment.isOnboardingHomeScreenWidgetExperiment()).thenReturn(false)

        val value = testee.refreshCta(coroutineRule.testDispatcher, isBrowserShowing = false, detectedRefreshPatterns = detectedRefreshPatterns)
        assertFalse(value is DaxBubbleCta.DaxPrivacyProCta)
    }

    @Test
    fun givenPrivacyProSiteWhenRefreshCtaWhileBrowsingThenReturnNull() = runTest {
        val privacyProUrl = "https://duckduckgo.com/pro"
        whenever(mockSubscriptions.isPrivacyProUrl(privacyProUrl.toUri())).thenReturn(true)
        givenDaxOnboardingActive()
        val site = site(url = privacyProUrl)

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    @Test
    fun givenDuckPlayerSiteWhenRefreshCtaWhileBrowsingThenReturnNull() = runTest {
        givenDaxOnboardingActive()
        val site = site(url = "duck://player/12345")

        whenever(mockDuckPlayer.isDuckPlayerUri(any())).thenReturn(true)
        whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)
        whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(false, AlwaysAsk))
        whenever(mockDuckPlayer.isYouTubeUrl(any())).thenReturn(false)
        whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(false)

        val value = testee.refreshCta(
            coroutineRule.testDispatcher,
            isBrowserShowing = true,
            site = site,
            detectedRefreshPatterns = detectedRefreshPatterns,
        )
        assertNull(value)
    }

    private suspend fun givenDaxOnboardingActive() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
    }

    private suspend fun givenDaxOnboardingCompleted() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
    }

    private suspend fun givenUserIsEstablished() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
    }

    private fun givenAtLeastOneDaxDialogCtaShown() {
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)
    }

    private fun givenShownDaxOnboardingCtas(shownCtas: List<CtaId>) {
        shownCtas.forEach {
            whenever(mockDismissedCtaDao.exists(it)).thenReturn(true)
        }
    }

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        https: HttpsStatus = HttpsStatus.SECURE,
        trackerCount: Int = 0,
        events: List<TrackingEvent> = emptyList(),
        majorNetworkCount: Int = 0,
        allTrackersBlocked: Boolean = true,
        entity: Entity? = null,
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(uri)
        whenever(site.https).thenReturn(https)
        whenever(site.entity).thenReturn(entity)
        whenever(site.trackingEvents).thenReturn(events)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.majorNetworkCount).thenReturn(majorNetworkCount)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        return site
    }
}
