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

import android.annotation.SuppressLint
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager.SegmentedOnboardingExperimentVariant
import com.duckduckgo.app.onboarding.SegmentedOnboardingFeatureToggles.Cohorts
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class SegmentedOnboardingExperimentManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val segmentedToggles: SegmentedOnboardingFeatureToggles =
        FakeFeatureToggleFactory.create(SegmentedOnboardingFeatureToggles::class.java)
    private val brandDesignToggles: OnboardingBrandDesignUpdateToggles =
        FakeFeatureToggleFactory.create(OnboardingBrandDesignUpdateToggles::class.java)
    private val passwordImportToggles: OnboardingPasswordImportToggles =
        FakeFeatureToggleFactory.create(OnboardingPasswordImportToggles::class.java)
    private val promptsToggles: OnboardingPromptsToggles =
        FakeFeatureToggleFactory.create(OnboardingPromptsToggles::class.java)
    private val privacyConfigPersistedGate = OnboardingPrivacyConfigPersistedGateImpl()
    private val appBuildConfig: AppBuildConfig = mock()

    private val testee = SegmentedOnboardingExperimentManagerImpl(
        onboardingBrandDesignUpdateToggles = brandDesignToggles,
        segmentedOnboardingFeatureToggles = segmentedToggles,
        onboardingPasswordImportToggles = passwordImportToggles,
        onboardingPromptsToggles = promptsToggles,
        appBuildConfig = appBuildConfig,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        onboardingPrivacyConfigPersistedGate = privacyConfigPersistedGate,
    )

    @Test
    fun `when privacy config never persisted then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        givenCohortEnabled(Cohorts.TREATMENT)

        assertNull(testee.enroll())
    }

    @Test
    fun `when config driven dialogs disabled then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        brandDesignToggles.configDrivenDialogs().setRawStoredState(Toggle.State(enable = false))
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun `when brand design update disabled then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        brandDesignToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = false))
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun `when experiment disabled then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        givenCohortEnabled(winner = null)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun `when enrolled in control then enroll returns control`() = runTest {
        givenPrerequisitesMet()
        givenCohortEnabled(Cohorts.CONTROL)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertEquals(SegmentedOnboardingExperimentVariant.CONTROL, testee.enroll())
    }

    @Test
    fun `when enrolled in treatment then enroll returns treatment`() = runTest {
        givenPrerequisitesMet()
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertEquals(SegmentedOnboardingExperimentVariant.TREATMENT, testee.enroll())
    }

    @Test
    fun `when returning user then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun `when enroll called twice then same variant is returned both times`() = runTest {
        givenPrerequisitesMet()
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertEquals(testee.enroll(), testee.enroll())
    }

    @Test
    fun `when add to dock and widget experiment enabled then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        promptsToggles.addToDockAndWidgetExperimentJul25().setRawStoredState(Toggle.State(enable = true))
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun `when password import experiment enabled then enroll returns null`() = runTest {
        givenPrerequisitesMet()
        passwordImportToggles.passwordImportExperimentAug25().setRawStoredState(Toggle.State(enable = true))
        givenCohortEnabled(Cohorts.TREATMENT)

        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    private suspend fun givenPrerequisitesMet() {
        brandDesignToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = true))
        brandDesignToggles.configDrivenDialogs().setRawStoredState(Toggle.State(enable = true))
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        promptsToggles.addToDockAndWidgetExperimentJul25().setRawStoredState(Toggle.State(enable = false))
        passwordImportToggles.passwordImportExperimentAug25().setRawStoredState(Toggle.State(enable = false))
    }

    private fun givenCohortEnabled(winner: Cohorts?) {
        segmentedToggles.onboardingFlowByDownloadReasonExperiment().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                enable = winner != null,
                cohorts = Cohorts.entries.map {
                    Toggle.State.Cohort(name = it.cohortName, weight = if (it == winner) 1 else 0)
                },
            ),
        )
    }
}
