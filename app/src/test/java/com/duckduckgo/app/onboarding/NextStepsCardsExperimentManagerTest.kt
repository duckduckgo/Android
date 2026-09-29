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
import com.duckduckgo.app.onboarding.NextStepsCardsExperimentManager.NextStepsCardsExperimentVariant
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.remote.messaging.impl.nextstepscards.NextStepsCardsExperimentToggles
import com.duckduckgo.remote.messaging.impl.nextstepscards.NextStepsCardsExperimentToggles.Cohorts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class NextStepsCardsExperimentManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val toggles: NextStepsCardsExperimentToggles = FakeFeatureToggleFactory.create(NextStepsCardsExperimentToggles::class.java)
    private val privacyConfigPersistedGate = OnboardingPrivacyConfigPersistedGateImpl()
    private val appBuildConfig: AppBuildConfig = mock()

    private val testee = NextStepsCardsExperimentManagerImpl(
        nextStepsCardsFeatureToggles = toggles,
        appBuildConfig = appBuildConfig,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        onboardingPrivacyConfigPersistedGate = privacyConfigPersistedGate,
    )

    @Test
    fun `when privacy config never persisted then enroll returns null and does not enrol`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.TREATMENT)

        assertNull(testee.enroll())
        assertFalse(toggles.nextStepsCardsExperiment().isEnrolled())
    }

    @Test
    fun `when kill switch is off then enroll returns null and does not enrol`() = runTest {
        givenKillSwitch(enabled = false)
        givenCohortEnabled(Cohorts.TREATMENT)
        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
        assertFalse(toggles.nextStepsCardsExperiment().isEnrolled())
    }

    @Test
    fun `when reinstall user then enroll returns null and does not enrol`() = runTest {
        givenKillSwitch(enabled = true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        givenCohortEnabled(Cohorts.TREATMENT)
        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
        assertFalse(toggles.nextStepsCardsExperiment().isEnrolled())
    }

    @Test
    fun `when enrolled in treatment then enroll returns treatment`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.TREATMENT)
        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertEquals(NextStepsCardsExperimentVariant.TREATMENT, testee.enroll())
    }

    @Test
    fun `when enrolled in control then enroll returns control`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.CONTROL)
        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertEquals(NextStepsCardsExperimentVariant.CONTROL, testee.enroll())
    }

    @Test
    fun `when experiment has no cohort then enroll returns null`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(winner = null)
        privacyConfigPersistedGate.onPrivacyConfigPersisted()

        assertNull(testee.enroll())
    }

    private suspend fun givenKillSwitch(enabled: Boolean) {
        toggles.self().setRawStoredState(Toggle.State(enable = enabled))
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
    }

    private fun givenCohortEnabled(winner: Cohorts?) {
        toggles.nextStepsCardsExperiment().setRawStoredState(
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
