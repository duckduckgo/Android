/*
 * Copyright (c) 2024 DuckDuckGo
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
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment.OnboardingHomeScreenWidgetExperiment
import com.duckduckgo.app.browser.refreshpixels.RefreshPixelSender
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_NETWORK
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_OTHER
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_SERP
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_TRACKERS_FOUND
import com.duckduckgo.app.cta.model.CtaId.DAX_END
import com.duckduckgo.app.cta.model.CtaId.DAX_FIRE_BUTTON
import com.duckduckgo.app.cta.model.CtaId.DAX_INTRO_PRIVACY_PRO
import com.duckduckgo.app.cta.model.CtaId.DAX_INTRO_VISIT_SITE
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

class OnboardingDaxDialogTests {

    private lateinit var testee: CtaViewModel

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val appInstallStore: AppInstallStore = mock()
    private val pixel: Pixel = mock()
    private val widgetCapabilities: WidgetCapabilities = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val userAllowListRepository: UserAllowListRepository = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val userStageStore: UserStageStore = mock()
    private val tabRepository: TabRepository = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockBrokenSitePrompt: BrokenSitePrompt = mock()
    private val mockRefreshPixelSender: RefreshPixelSender = mock()
    private val mockSubscriptions: Subscriptions = mock()
    private val mockSenseOfProtectionExperiment: SenseOfProtectionExperiment = mock()
    private val mockOnboardingHomeScreenWidgetExperiment: OnboardingHomeScreenWidgetExperiment = mock()
    private val mockOnboardingDesignExperimentToggles: OnboardingDesignExperimentToggles = mock()

    val mockEnabledToggle: Toggle = org.mockito.kotlin.mock { on { it.isEnabled() } doReturn true }
    val mockDisabledToggle: Toggle = org.mockito.kotlin.mock { on { it.isEnabled() } doReturn false }

    @Before
    fun before() = runTest {
        whenever(extendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
        whenever(extendedOnboardingFeatureToggles.privacyProCta()).thenReturn(mockDisabledToggle)
        whenever(mockSubscriptions.isEligible()).thenReturn(false)

        testee = CtaViewModel(
            appInstallStore,
            pixel,
            widgetCapabilities,
            dismissedCtaDao,
            userAllowListRepository,
            settingsDataStore,
            onboardingStore,
            userStageStore,
            tabRepository,
            coroutineRule.testDispatcherProvider,
            duckDuckGoUrlDetector,
            extendedOnboardingFeatureToggles,
            mockSubscriptions,
            mockDuckPlayer,
            mockBrokenSitePrompt,
            mockSenseOfProtectionExperiment,
            mockOnboardingHomeScreenWidgetExperiment,
            mockOnboardingDesignExperimentToggles,
        )
    }

    @Test
    fun whenNoOnboardingExperimentEnabledThenOnboardingComplete() = runTest {
        whenever(extendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenOnboardingActiveThenOnboardingIsNotComplete() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertFalse(onboardingComplete)
    }

    @Test
    fun whenDaxDialogEndAndDaxDialogNetworkShownThenOnboardingComplete() = runTest {
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_NETWORK)).thenReturn(true)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenDaxDialogEndAndDaxDialogTrackersFoundThenOnboardingComplete() = runTest {
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenDaxDialogEndAndDaxDialogOtherFoundThenOnboardingComplete() = runTest {
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_OTHER)).thenReturn(true)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenDaxDialogEndNotShownThenOnboardingNotComplete() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(false)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertFalse(onboardingComplete)
    }

    @Test
    fun whenHideTipsThenOnboardingComplete() = runTest {
        whenever(settingsDataStore.hideTips).thenReturn(true)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenOnboardingDismissedThenOnboardingComplete() = runTest {
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(false)

        val onboardingComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(onboardingComplete)
    }

    @Test
    fun whenOnboardingCompleteThenAreBubbleDaxDialogsCompletedIsTrue() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_SERP)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_FIRE_BUTTON)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(false)

        val inContextDaxDialogsComplete = testee.areBubbleDaxDialogsCompleted()
        assertTrue(inContextDaxDialogsComplete)
    }

    @Test
    fun whenAllInContextDialogsShownThenAreInContextDialogsCompletedIsTrue() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_SERP)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_FIRE_BUTTON)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(true)

        whenever(dismissedCtaDao.exists(DAX_INTRO_VISIT_SITE)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_INTRO_PRIVACY_PRO)).thenReturn(false)

        val inContextDaxDialogsComplete = testee.areInContextDaxDialogsCompleted()
        assertTrue(inContextDaxDialogsComplete)
    }

    @Test
    fun whenNotAllInContextDialogsShownThenAreInContextDialogsCompletedIsFalse() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_SERP)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_FIRE_BUTTON)).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(true)

        val inContextDaxDialogsComplete = testee.areInContextDaxDialogsCompleted()
        assertFalse(inContextDaxDialogsComplete)
    }

    @Test
    fun whenHideTipsSetThenAreInContextDialogsCompletedIsTrue() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_SERP)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_FIRE_BUTTON)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_NETWORK)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_INTRO_PRIVACY_PRO)).thenReturn(false)

        val inContextDaxDialogsComplete = testee.areInContextDaxDialogsCompleted()
        assertTrue(inContextDaxDialogsComplete)
    }

    @Test
    fun whenOnboardingCompleteThenAreInContextDialogsCompletedIsTrue() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_SERP)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_FIRE_BUTTON)).thenReturn(false)
        whenever(dismissedCtaDao.exists(DAX_END)).thenReturn(false)

        val inContextDaxDialogsComplete = testee.areInContextDaxDialogsCompleted()
        assertTrue(inContextDaxDialogsComplete)
    }
}
