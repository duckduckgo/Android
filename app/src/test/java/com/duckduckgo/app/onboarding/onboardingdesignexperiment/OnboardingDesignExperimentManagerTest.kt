/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.onboardingdesignexperiment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentCountDataStore
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingExperimentMetricsPixelPlugin
import com.duckduckgo.app.onboardingdesignexperiment.RealOnboardingDesignExperimentManager
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor.PHONE
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor.TABLET
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OnboardingDesignExperimentManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles = mock()
    private val onboardingExperimentMetricsPixelPlugin: OnboardingExperimentMetricsPixelPlugin = mock()
    private val onboardingDesignExperimentCountDataStore: OnboardingDesignExperimentCountDataStore = mock()
    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val deviceInfo: DeviceInfo = mock()
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val settingsDataStore: SettingsDataStore = mock()

    private lateinit var testee: OnboardingDesignExperimentManager

    @Before
    fun before() {
        testee = RealOnboardingDesignExperimentManager(
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            onboardingDesignExperimentToggles = onboardingDesignExperimentToggles,
            onboardingExperimentMetricsPixelPlugin = onboardingExperimentMetricsPixelPlugin,
            onboardingDesignExperimentCountDataStore = onboardingDesignExperimentCountDataStore,
            pixel = pixel,
            appBuildConfig = appBuildConfig,
            deviceInfo = deviceInfo,
            duckDuckGoUrlDetector = duckDuckGoUrlDetector,
        )
    }

    @Test
    fun whenPrivacyConfigPersistedCreatedThenCachedPropertiesAreSet() = runTest {
        val mockToggle = mock<Toggle>()
        val mockCohort = mock<Toggle.State.Cohort>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getCohort()).thenReturn(mockCohort)
        whenever(mockToggle.getCohort()!!.name).thenReturn(OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BUCK.cohortName)

        val lifecycleObserver = testee as PrivacyConfigCallbackPlugin
        lifecycleObserver.onPrivacyConfigPersisted()

        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(testee.isAnyExperimentEnrolledAndEnabled())
        assertTrue(testee.isBuckEnrolledAndEnabled())
    }

    @Test
    fun whenPrivacyConfigPersistedWithDisabledExperimentThenCachedPropertiesReflectDisabledState() = runTest {
        val mockToggle = mock<Toggle>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        val lifecycleObserver = testee as PrivacyConfigCallbackPlugin
        lifecycleObserver.onPrivacyConfigPersisted()

        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(testee.isAnyExperimentEnrolledAndEnabled())
        assertFalse(testee.isBuckEnrolledAndEnabled())
        assertFalse(testee.isBbEnrolledAndEnabled())
        assertFalse(testee.isModifiedControlEnrolledAndEnabled())
    }

    @Test
    fun whenOnPrivacyConfigDownloadedThenCachedPropertiesAreSet() = runTest {
        val mockToggle = mock<Toggle>()
        val mockCohort = mock<Toggle.State.Cohort>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getCohort()).thenReturn(mockCohort)
        whenever(mockToggle.getCohort()!!.name).thenReturn(OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BB.cohortName)

        val privacyConfigCallback = testee as PrivacyConfigCallbackPlugin
        privacyConfigCallback.onPrivacyConfigDownloaded()

        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertTrue(testee.isAnyExperimentEnrolledAndEnabled())
        assertTrue(testee.isBbEnrolledAndEnabled())
    }

    @Test
    fun whenOnPrivacyConfigDownloadedWithDisabledExperimentThenCachedPropertiesReflectDisabledState() = runTest {
        val mockToggle = mock<Toggle>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        val privacyConfigCallback = testee as PrivacyConfigCallbackPlugin
        privacyConfigCallback.onPrivacyConfigDownloaded()

        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertFalse(testee.isAnyExperimentEnrolledAndEnabled())
        assertFalse(testee.isBuckEnrolledAndEnabled())
        assertFalse(testee.isBbEnrolledAndEnabled())
        assertFalse(testee.isModifiedControlEnrolledAndEnabled())
    }

    @Test
    fun whenModifiedControlEnabledThenAnyExperimentEnrolledAndEnabledReturnsTrue() = runTest {
        val mockToggle = mock<Toggle>()
        val mockCohort = mock<Toggle.State.Cohort>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getCohort()).thenReturn(mockCohort)
        whenever(mockToggle.getCohort()!!.name).thenReturn(
            OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.MODIFIED_CONTROL.cohortName,
        )

        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        assertTrue(testee.isAnyExperimentEnrolledAndEnabled())
    }

    @Test
    fun whenBuckEnabledThenAnyExperimentEnrolledAndEnabledReturnsTrue() = runTest {
        val mockToggle = mock<Toggle>()
        val mockCohort = mock<Toggle.State.Cohort>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getCohort()).thenReturn(mockCohort)
        whenever(mockToggle.getCohort()!!.name).thenReturn(OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BUCK.cohortName)

        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        assertTrue(testee.isAnyExperimentEnrolledAndEnabled())
    }

    @Test
    fun whenBBEnabledThenAnyExperimentEnrolledAndEnabledReturnsTrue() = runTest {
        val mockToggle = mock<Toggle>()
        val mockCohort = mock<Toggle.State.Cohort>()

        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getCohort()).thenReturn(mockCohort)
        whenever(mockToggle.getCohort()!!.name).thenReturn(OnboardingDesignExperimentToggles.OnboardingDesignExperimentCohort.BB.cohortName)

        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        assertTrue(testee.isAnyExperimentEnrolledAndEnabled())
    }

    @Test
    fun whenDeviceIsEligibleThenUserIsAttemptedToBeEnrolled() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(appBuildConfig.sdkInt).thenReturn(35)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        verify(mockToggle).enroll()
    }

    @Test
    fun whenDeviceIsTabletThenUserIsNotEnrolled() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(appBuildConfig.sdkInt).thenReturn(35)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(TABLET)

        testee.enroll()

        verify(mockToggle, never()).enroll()
    }

    @Test
    fun whenDeviceIsOlderThanAndroid11ThenUserIsNotEnrolled() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(appBuildConfig.sdkInt).thenReturn(29)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        verify(mockToggle, never()).enroll()
    }

    @Test
    fun whenInstallIsReinstallThenUserIsNotEnrolled() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(onboardingDesignExperimentToggles.onboardingDesignExperimentOct25()).thenReturn(mockToggle)
        whenever(appBuildConfig.sdkInt).thenReturn(29)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(deviceInfo.formFactor()).thenReturn(PHONE)

        testee.enroll()

        verify(mockToggle, never()).enroll()
    }

    @Test
    fun whenWebPageFinishedLoadingWithDDGUrlThenSecondSerpVisitPixelFired() = runTest {
        whenever(duckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        whenever(onboardingDesignExperimentCountDataStore.getSerpVisitCount()).thenReturn(1)
        whenever(onboardingDesignExperimentCountDataStore.increaseSerpVisitCount()).thenReturn(2)

        testee.onWebPageFinishedLoading("https://duckduckgo.com")

        verify(onboardingExperimentMetricsPixelPlugin).getSecondSerpVisitMetric()
    }

    @Test
    fun whenWebPageFinishedLoadingWithNonDDGUrlThenSecondSiteVisitPixelFired() = runTest {
        whenever(duckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(false)
        whenever(onboardingDesignExperimentCountDataStore.getSiteVisitCount()).thenReturn(1)
        whenever(onboardingDesignExperimentCountDataStore.increaseSiteVisitCount()).thenReturn(2)

        testee.onWebPageFinishedLoading("https://example.com")

        verify(onboardingExperimentMetricsPixelPlugin).getSecondSiteVisitMetric()
    }

    @Test
    fun whenWebPageFinishedLoadingWithNullUrlThenNoPixelFired() = runTest {
        testee.onWebPageFinishedLoading(null)

        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSerpVisitMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSiteVisitMetric()
    }

    @Test
    fun whenWebPageFinishedLoadingWithDDGUrlAndSerpVisitCountIsTwoThenNoPixelFired() = runTest {
        whenever(duckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        whenever(onboardingDesignExperimentCountDataStore.getSerpVisitCount()).thenReturn(2)

        testee.onWebPageFinishedLoading("https://duckduckgo.com")

        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSerpVisitMetric()
    }

    @Test
    fun whenWebPageFinishedLoadingWithNonDDGUrlAndSiteVisitCountIsTwoThenNoPixelFired() = runTest {
        whenever(duckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(false)
        whenever(onboardingDesignExperimentCountDataStore.getSiteVisitCount()).thenReturn(2)

        testee.onWebPageFinishedLoading("https://example.com")

        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSiteVisitMetric()
    }

    @Test
    fun whenFireSiteSuggestionOptionSelectedPixelForFirstOptionThenCorrectPixelFired() = runTest {
        testee.fireSiteSuggestionOptionSelectedPixel(0)
        verify(onboardingExperimentMetricsPixelPlugin).getFirstSiteSuggestionMetric()
    }

    @Test
    fun whenFireSiteSuggestionOptionSelectedPixelForSecondOptionThenCorrectPixelFired() = runTest {
        testee.fireSiteSuggestionOptionSelectedPixel(1)
        verify(onboardingExperimentMetricsPixelPlugin).getSecondSiteSuggestionMetric()
    }

    @Test
    fun whenFireSiteSuggestionOptionSelectedPixelForThirdOptionThenCorrectPixelFired() = runTest {
        testee.fireSiteSuggestionOptionSelectedPixel(2)
        verify(onboardingExperimentMetricsPixelPlugin).getThirdSiteSuggestionMetric()
    }

    @Test
    fun whenFireSiteSuggestionOptionSelectedPixelForInvalidOptionThenNoPixelFired() = runTest {
        testee.fireSiteSuggestionOptionSelectedPixel(3)
        verify(onboardingExperimentMetricsPixelPlugin, never()).getFirstSiteSuggestionMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSiteSuggestionMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getThirdSiteSuggestionMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxIntroSearchOptionsCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore))
        verify(onboardingExperimentMetricsPixelPlugin).getTryASearchDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxIntroVisitSiteOptionsCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore))
        verify(onboardingExperimentMetricsPixelPlugin).getVisitSitePromptDisplayedNewTabMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxBubbleDaxEndCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(DaxBubbleCta.DaxEndCta(onboardingStore, appInstallStore))
        verify(onboardingExperimentMetricsPixelPlugin).getFinalOnboardingScreenDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxPrivacyProCtaThenNoPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(DaxBubbleCta.DaxPrivacyProCta(onboardingStore, appInstallStore, 0, 0, 0))
        verify(onboardingExperimentMetricsPixelPlugin, never()).getTryASearchDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getVisitSitePromptDisplayedNewTabMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getFinalOnboardingScreenDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxSerpCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(OnboardingDaxDialogCta.DaxSerpCta(onboardingStore, appInstallStore, testee))
        verify(onboardingExperimentMetricsPixelPlugin).getMessageOnSerpDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxSiteSuggestionsCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(OnboardingDaxDialogCta.DaxSiteSuggestionsCta(onboardingStore, appInstallStore, testee) {})
        verify(onboardingExperimentMetricsPixelPlugin).getVisitSitePromptDisplayedAdjacentMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxTrackersBlockedCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(
            OnboardingDaxDialogCta.DaxTrackersBlockedCta(onboardingStore, appInstallStore, emptyList(), settingsDataStore, testee),
        )
        verify(onboardingExperimentMetricsPixelPlugin).getTrackersBlockedMessageDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxNoTrackersCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(OnboardingDaxDialogCta.DaxNoTrackersCta(onboardingStore, appInstallStore, testee))
        verify(onboardingExperimentMetricsPixelPlugin).getNoTrackersMessageDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxMainNetworkCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(
            OnboardingDaxDialogCta.DaxMainNetworkCta(onboardingStore, appInstallStore, "Facebook", "facebook.com", testee),
        )
        verify(onboardingExperimentMetricsPixelPlugin).getTrackerNetworkMessageDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithDaxFireButtonCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(OnboardingDaxDialogCta.DaxFireButtonCta(onboardingStore, appInstallStore, testee))
        verify(onboardingExperimentMetricsPixelPlugin).getFireButtonPromptDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithOnboardingDaxEndCtaThenCorrectPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(OnboardingDaxDialogCta.DaxEndCta(onboardingStore, appInstallStore, testee))
        verify(onboardingExperimentMetricsPixelPlugin).getFinalOnboardingScreenDisplayedMetric()
    }

    @Test
    fun whenFireInContextDialogShownPixelWithNullCtaThenNoPixelFired() = runTest {
        testee.fireInContextDialogShownPixel(null)
        verify(onboardingExperimentMetricsPixelPlugin, never()).getTryASearchDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getVisitSitePromptDisplayedNewTabMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getFinalOnboardingScreenDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getMessageOnSerpDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getVisitSitePromptDisplayedAdjacentMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getTrackersBlockedMessageDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getNoTrackersMessageDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getTrackerNetworkMessageDisplayedMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getFireButtonPromptDisplayedMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxIntroSearchOptionsCtaAndFirstOptionThenCorrectPixelFired() = runTest {
        testee.fireOptionSelectedPixel(DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore), 0)
        verify(onboardingExperimentMetricsPixelPlugin).getFirstSearchSuggestionMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxIntroSearchOptionsCtaAndSecondOptionThenCorrectPixelFired() = runTest {
        testee.fireOptionSelectedPixel(DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore), 1)
        verify(onboardingExperimentMetricsPixelPlugin).getSecondSearchSuggestionMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxIntroSearchOptionsCtaAndThirdOptionThenCorrectPixelFired() = runTest {
        testee.fireOptionSelectedPixel(DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore), 2)
        verify(onboardingExperimentMetricsPixelPlugin).getThirdSearchSuggestionMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxIntroSearchOptionsCtaAndInvalidOptionThenNoPixelFired() = runTest {
        testee.fireOptionSelectedPixel(DaxBubbleCta.DaxIntroSearchOptionsCta(onboardingStore, appInstallStore), 3)
        verify(onboardingExperimentMetricsPixelPlugin, never()).getFirstSearchSuggestionMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getSecondSearchSuggestionMetric()
        verify(onboardingExperimentMetricsPixelPlugin, never()).getThirdSearchSuggestionMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxIntroVisitSiteOptionsCtaThenCorrectPixelFired() = runTest {
        testee.fireOptionSelectedPixel(DaxBubbleCta.DaxIntroVisitSiteOptionsCta(onboardingStore, appInstallStore), 0)
        verify(onboardingExperimentMetricsPixelPlugin).getFirstSiteSuggestionMetric()
    }

    @Test
    fun whenFireOptionSelectedPixelWithDaxSiteSuggestionsCtaThenCorrectPixelFired() = runTest {
        testee.fireOptionSelectedPixel(OnboardingDaxDialogCta.DaxSiteSuggestionsCta(onboardingStore, appInstallStore, testee) {}, 1)
        verify(onboardingExperimentMetricsPixelPlugin).getSecondSiteSuggestionMetric()
    }
}
