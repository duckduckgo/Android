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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles.DuckAiOnboardingExperimentCohort
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckAiOnboardingExperimentManagerTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private val browserConfig: AndroidBrowserConfigFeature = mock()
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock()
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val deviceInfo: DeviceInfo = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private val showInputScreenOnboardingToggle: Toggle = mock()
    private val singleTabFireDialogToggle: Toggle = mock()
    private val brandDesignUpdateToggle: Toggle = mock()
    private val experimentToggle: Toggle = mock()

    private lateinit var testee: DuckAiOnboardingExperimentManagerImpl

    @Before
    fun setup() {
        whenever(browserConfig.showInputScreenOnboarding()).thenReturn(showInputScreenOnboardingToggle)
        whenever(browserConfig.singleTabFireDialog()).thenReturn(singleTabFireDialogToggle)
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(brandDesignUpdateToggle)
        whenever(extendedOnboardingFeatureToggles.onboardingDuckAiExperimentMay26()).thenReturn(experimentToggle)

        testee = DuckAiOnboardingExperimentManagerImpl(
            browserConfig = browserConfig,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            onboardingBrandDesignUpdateToggles = onboardingBrandDesignUpdateToggles,
            extendedOnboardingFeatureToggles = extendedOnboardingFeatureToggles,
            deviceInfo = deviceInfo,
            appBuildConfig = appBuildConfig,
        )
    }

    @Test
    fun whenDefaultVariantForcedThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(appBuildConfig.isDefaultVariantForced).thenReturn(true)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenShowInputScreenOnboardingDisabledThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(showInputScreenOnboardingToggle.isEnabled()).thenReturn(false)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenSingleTabFireDialogDisabledThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(singleTabFireDialogToggle.isEnabled()).thenReturn(false)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenBrandDesignUpdateEnabledThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(brandDesignUpdateToggle.isEnabled()).thenReturn(true)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenDeviceIsTabletThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(deviceInfo.formFactor()).thenReturn(FormFactor.TABLET)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenExperimentToggleDisabledThenReturnsNullAndDoesNotEnroll() = runTest {
        givenPrerequisitesMet()
        whenever(experimentToggle.isEnabled()).thenReturn(false)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle, never()).enroll()
    }

    @Test
    fun whenPrerequisitesMetAndCohortIsControlThenEnrollsAndReturnsControl() = runTest {
        givenPrerequisitesMet()
        givenCohort(DuckAiOnboardingExperimentCohort.CONTROL.cohortName)

        val result = testee.enroll()

        assertEquals(DuckAiOnboardingExperimentVariant.CONTROL, result)
        verify(experimentToggle).enroll()
    }

    @Test
    fun whenPrerequisitesMetAndCohortIsTreatmentWithDuckAiDefaultThenReturnsTreatmentWithDuckAiDefault() = runTest {
        givenPrerequisitesMet()
        givenCohort(DuckAiOnboardingExperimentCohort.TREATMENT_WITH_DUCK_AI_DEFAULT.cohortName)

        val result = testee.enroll()

        assertEquals(DuckAiOnboardingExperimentVariant.TREATMENT_WITH_DUCK_AI_DEFAULT, result)
        verify(experimentToggle).enroll()
    }

    @Test
    fun whenPrerequisitesMetAndCohortIsTreatmentWithSearchDefaultThenReturnsTreatmentWithSearchDefault() = runTest {
        givenPrerequisitesMet()
        givenCohort(DuckAiOnboardingExperimentCohort.TREATMENT_WITH_SEARCH_DEFAULT.cohortName)

        val result = testee.enroll()

        assertEquals(DuckAiOnboardingExperimentVariant.TREATMENT_WITH_SEARCH_DEFAULT, result)
        verify(experimentToggle).enroll()
    }

    @Test
    fun whenPrerequisitesMetAndNoCohortAssignedThenReturnsNull() = runTest {
        givenPrerequisitesMet()
        whenever(experimentToggle.isEnabled()).thenReturn(true)
        whenever(experimentToggle.getCohort()).thenReturn(null)

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle).enroll()
    }

    @Test
    fun whenCohortNameIsUnknownThenReturnsNull() = runTest {
        givenPrerequisitesMet()
        givenCohort("unknownCohort")

        val result = testee.enroll()

        assertNull(result)
        verify(experimentToggle).enroll()
    }

    private suspend fun givenPrerequisitesMet() {
        whenever(appBuildConfig.isDefaultVariantForced).thenReturn(false)
        whenever(showInputScreenOnboardingToggle.isEnabled()).thenReturn(true)
        whenever(singleTabFireDialogToggle.isEnabled()).thenReturn(true)
        whenever(brandDesignUpdateToggle.isEnabled()).thenReturn(false)
        whenever(deviceInfo.formFactor()).thenReturn(FormFactor.PHONE)
    }

    private suspend fun givenCohort(name: String) {
        whenever(experimentToggle.isEnabled()).thenReturn(true)
        whenever(experimentToggle.getCohort()).thenReturn(Toggle.State.Cohort(name = name, weight = 1))
    }
}
