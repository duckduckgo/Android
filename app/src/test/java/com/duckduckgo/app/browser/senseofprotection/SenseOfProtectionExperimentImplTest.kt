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

@file:Suppress("DenyListedApi")

package com.duckduckgo.app.browser.senseofprotection

import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.MODIFIED_CONTROL
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.VARIANT_1
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionToggles.Cohorts.VARIANT_2
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import org.junit.Assert.assertEquals

class SenseOfProtectionExperimentImplTest {

    private lateinit var testee: SenseOfProtectionExperimentImpl
    private lateinit var fakeUserBrowserProperties: FakeUserBrowserProperties
    private lateinit var fakeSenseOfProtectionToggles: SenseOfProtectionToggles
    private val cohorts = listOf(
        State.Cohort(name = MODIFIED_CONTROL.cohortName, weight = 1),
        State.Cohort(name = VARIANT_1.cohortName, weight = 1),
        State.Cohort(name = VARIANT_2.cohortName, weight = 1),
    )

    @Before
    fun setup() {
        fakeUserBrowserProperties = FakeUserBrowserProperties()
        fakeSenseOfProtectionToggles = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "senseOfProtection",
        ).build().create(SenseOfProtectionToggles::class.java)
        testee = SenseOfProtectionExperimentImpl(fakeUserBrowserProperties, fakeSenseOfProtectionToggles)
    }

    @Test
    fun `when user is new and experiment is enabled for user's cohort then isEnabled returns true`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(28)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.isEnabled(cohortName = MODIFIED_CONTROL))
    }

    @Test
    fun `when user is new and experiment is enabled but for different cohort then isEnabled returns false`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertFalse(testee.isEnabled(MODIFIED_CONTROL))
    }

    @Test
    fun `when user is new and experiment is disabled then isEnabled returns false`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(10)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = null,
                cohorts = emptyList(),
            ),
        )

        assertFalse(testee.isEnabled(MODIFIED_CONTROL))
    }

    @Test
    fun `when user is existing and not enrolled in new user experiment then isEnabled returns true`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(29) // just above threshold
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = null,
                cohorts = cohorts,
            ),
        )
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.isEnabled(MODIFIED_CONTROL))
    }

    @Test
    fun `when user is existing and enrolled in existing user experiment but existing user experiment disabled then isEnabled returns false`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(100)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = null,
                cohorts = cohorts,
            ),
        )
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = State.Cohort(MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertFalse(testee.isEnabled(MODIFIED_CONTROL))
    }

    @Test
    fun `when user is enrolled in new user experiment then getTabManagerPixelParams returns new user experiment params`() {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertEquals(VARIANT_1.cohortName, params["cohort"])
        assertEquals(fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().featureName().name, params["experiment"])
    }

    @Test
    fun `when user is enrolled in existing user experiment then getTabManagerPixelParams returns existing user experiment params`() {
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = VARIANT_2.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertEquals(VARIANT_2.cohortName, params["cohort"])
        assertEquals(fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().featureName().name, params["experiment"])
    }

    @Test
    fun `when user is not enrolled in any experiment then getTabManagerPixelParams returns empty map`() {
        fakeUserBrowserProperties.setDaysSinceInstalled(30)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = null,
                cohorts = emptyList(),
            ),
        )
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperimentApr25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = null,
                cohorts = emptyList(),
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertTrue(params.isEmpty())
    }
}

private class FakeUserBrowserProperties : UserBrowserProperties {

    private var daysSinceInstall: Long = 0

    fun setDaysSinceInstalled(days: Long) {
        daysSinceInstall = days
    }

    override fun appTheme(): DuckDuckGoTheme {
        TODO("Not yet implemented")
    }

    override suspend fun bookmarks(): Long {
        TODO("Not yet implemented")
    }

    override suspend fun favorites(): Long {
        TODO("Not yet implemented")
    }

    override fun daysSinceInstalled(): Long = daysSinceInstall

    override suspend fun daysUsedSince(since: Date): Long {
        TODO("Not yet implemented")
    }

    override fun defaultBrowser(): Boolean {
        TODO("Not yet implemented")
    }

    override fun emailEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun searchCount(): Long {
        TODO("Not yet implemented")
    }

    override fun widgetAdded(): Boolean {
        TODO("Not yet implemented")
    }
}
