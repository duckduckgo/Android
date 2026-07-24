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
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant
import com.duckduckgo.app.onboarding.OnboardingPromptsToggles.OnboardingPromptsCohorts
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class OnboardingPromptsExperimentManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val toggles: OnboardingPromptsToggles = FakeFeatureToggleFactory.create(OnboardingPromptsToggles::class.java)

    private val testee = OnboardingPromptsExperimentManagerImpl(
        toggles = toggles,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenPrivacyConfigNeverPersistedThenEnrollReturnsNull() = runTest {
        givenCohortEnabled(winner = OnboardingPromptsCohorts.TREATMENT_DOCK_AND_WIDGET)

        assertNull(testee.enroll())
    }

    @Test
    fun whenExperimentDisabledThenEnrollReturnsNull() = runTest {
        givenCohortEnabled(winner = null)

        testee.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    @Test
    fun whenEnrolledInControlThenEnrollReturnsControl() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.CONTROL)

        testee.onPrivacyConfigPersisted()

        assertEquals(OnboardingPromptExperimentVariant.CONTROL, testee.enroll())
    }

    @Test
    fun whenEnrolledInTreatmentDockOnlyThenEnrollReturnsTreatmentDockOnly() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_DOCK_ONLY)

        testee.onPrivacyConfigPersisted()

        assertEquals(OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY, testee.enroll())
    }

    @Test
    fun whenEnrolledInTreatmentWidgetOnlyThenEnrollReturnsTreatmentWidgetOnly() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_WIDGET_ONLY)

        testee.onPrivacyConfigPersisted()

        assertEquals(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY, testee.enroll())
    }

    @Test
    fun whenEnrolledInTreatmentDockAndWidgetThenEnrollReturnsTreatmentDockAndWidget() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_DOCK_AND_WIDGET)

        testee.onPrivacyConfigPersisted()

        assertEquals(OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET, testee.enroll())
    }

    @Test
    fun whenEnrollCalledTwiceThenSameCohortIsReturnedBothTimes() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_WIDGET_ONLY)

        testee.onPrivacyConfigPersisted()

        val first = testee.enroll()
        val second = testee.enroll()

        assertEquals(first, second)
    }

    @Test
    fun whenEnrolledInTreatmentWidgetOnlyThenIsEnrolledInWidgetPromptCohortReturnsTrue() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_WIDGET_ONLY)
        testee.onPrivacyConfigPersisted()
        testee.enroll()

        assertTrue(testee.isEnrolledInWidgetPromptCohort())
    }

    @Test
    fun whenEnrolledInTreatmentDockAndWidgetThenIsEnrolledInWidgetPromptCohortReturnsTrue() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_DOCK_AND_WIDGET)
        testee.onPrivacyConfigPersisted()
        testee.enroll()

        assertTrue(testee.isEnrolledInWidgetPromptCohort())
    }

    @Test
    fun whenEnrolledInTreatmentDockOnlyThenIsEnrolledInWidgetPromptCohortReturnsFalse() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_DOCK_ONLY)
        testee.onPrivacyConfigPersisted()
        testee.enroll()

        assertFalse(testee.isEnrolledInWidgetPromptCohort())
    }

    @Test
    fun whenEnrolledInControlThenIsEnrolledInWidgetPromptCohortReturnsFalse() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.CONTROL)
        testee.onPrivacyConfigPersisted()
        testee.enroll()

        assertFalse(testee.isEnrolledInWidgetPromptCohort())
    }

    @Test
    fun whenNeverEnrolledThenIsEnrolledInWidgetPromptCohortReturnsFalseWithoutEnrolling() = runTest {
        givenCohortEnabled(OnboardingPromptsCohorts.TREATMENT_WIDGET_ONLY)

        assertFalse(testee.isEnrolledInWidgetPromptCohort())
        assertNull(toggles.addToDockAndWidgetExperimentJul25().getCohort())
    }

    private fun givenCohortEnabled(winner: OnboardingPromptsCohorts?) {
        toggles.addToDockAndWidgetExperimentJul25().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                enable = winner != null,
                cohorts = OnboardingPromptsCohorts.values().map {
                    Toggle.State.Cohort(name = it.cohortName, weight = if (it == winner) 1 else 0)
                },
            ),
        )
    }
}
