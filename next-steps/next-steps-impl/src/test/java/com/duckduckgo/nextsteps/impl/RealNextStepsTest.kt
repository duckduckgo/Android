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

package com.duckduckgo.nextsteps.impl

import android.annotation.SuppressLint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.nextsteps.impl.NextStepsItemsExperimentToggles.Cohorts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealNextStepsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val toggles: NextStepsItemsExperimentToggles = FakeFeatureToggleFactory.create(NextStepsItemsExperimentToggles::class.java)
    private val appBuildConfig: AppBuildConfig = mock()

    private val testee = RealNextSteps(
        nextStepsItemsFeatureToggles = toggles,
        appBuildConfig = appBuildConfig,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when kill switch is off then user is not enrolled`() = runTest {
        givenKillSwitch(enabled = false)
        givenCohortEnabled(Cohorts.STACKED_CARDS)

        testee.enroll()

        assertFalse(toggles.nextStepsItemsExperiment().isEnrolled())
    }

    @Test
    fun `when reinstall user then user is not enrolled`() = runTest {
        givenKillSwitch(enabled = true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        givenCohortEnabled(Cohorts.STACKED_CARDS)

        testee.enroll()

        assertFalse(toggles.nextStepsItemsExperiment().isEnrolled())
    }

    @Test
    fun `when stacked cards cohort wins then user is enrolled in stacked cards`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.STACKED_CARDS)

        testee.enroll()

        assertTrue(toggles.nextStepsItemsExperiment().isEnrolledAndEnabled(Cohorts.STACKED_CARDS))
    }

    @Test
    fun `when check list cohort wins then user is enrolled in check list`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.CHECK_LIST)

        testee.enroll()

        assertTrue(toggles.nextStepsItemsExperiment().isEnrolledAndEnabled(Cohorts.CHECK_LIST))
    }

    @Test
    fun `when control cohort wins then user is enrolled in control`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.CONTROL)

        testee.enroll()

        assertTrue(toggles.nextStepsItemsExperiment().isEnrolledAndEnabled(Cohorts.CONTROL))
    }

    @Test
    fun `when experiment has no cohort then user is not enrolled`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(winner = null)

        testee.enroll()

        assertFalse(toggles.nextStepsItemsExperiment().isEnrolled())
    }

    @Test
    fun `when kill switch is off then no section view is provided`() = runTest {
        givenKillSwitch(enabled = false)
        givenCohortEnabled(Cohorts.STACKED_CARDS)
        toggles.nextStepsItemsExperiment().enroll()

        assertNull(testee.provideSectionView(mock()))
    }

    @Test
    fun `when user is not enrolled then no section view is provided`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.STACKED_CARDS)

        assertNull(testee.provideSectionView(mock()))
    }

    @Test
    fun `when user is in control cohort then no section view is provided`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.CONTROL)
        toggles.nextStepsItemsExperiment().enroll()

        assertNull(testee.provideSectionView(mock()))
    }

    @Test
    fun `when user is in check list cohort then no section view is provided yet`() = runTest {
        givenKillSwitch(enabled = true)
        givenCohortEnabled(Cohorts.CHECK_LIST)
        toggles.nextStepsItemsExperiment().enroll()

        assertNull(testee.provideSectionView(mock()))
    }

    private suspend fun givenKillSwitch(enabled: Boolean) {
        toggles.self().setRawStoredState(Toggle.State(enable = enabled))
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
    }

    private fun givenCohortEnabled(winner: Cohorts?) {
        toggles.nextStepsItemsExperiment().setRawStoredState(
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
