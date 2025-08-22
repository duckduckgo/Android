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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.fakes.FakePixel
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.Toggle.State
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class SenseOfProtectionExperimentImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var senseOfProtectionPixelsPluginMock: SenseOfProtectionPixelsPlugin

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
        MockitoAnnotations.openMocks(this)

        fakeUserBrowserProperties = FakeUserBrowserProperties()
        fakeSenseOfProtectionToggles = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "senseOfProtection",
        ).build().create(SenseOfProtectionToggles::class.java)
        testee = SenseOfProtectionExperimentImpl(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            userBrowserProperties = fakeUserBrowserProperties,
            senseOfProtectionToggles = fakeSenseOfProtectionToggles,
            senseOfProtectionPixelsPlugin = senseOfProtectionPixelsPluginMock,
            pixel = FakePixel(),
        )
    }

    @Test
    @Ignore("new visual design is now always enabled")
    fun `when user is new and and visual design updates not enabled then user can be enrolled`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(28)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        testee.enrolUserInNewExperimentIfEligible()

        assertTrue(testee.enrolUserInNewExperimentIfEligible())
    }

    @Test
    fun `when user is new and and visual design updates enabled then user can't be enrolled`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(28)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        testee.enrolUserInNewExperimentIfEligible()

        assertFalse(testee.enrolUserInNewExperimentIfEligible())
    }

    @Test
    @Ignore("new visual design is now always enabled")
    fun `when user is new and experiment is enabled but for different cohort then isEnabled returns false`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertFalse(testee.enrolUserInNewExperimentIfEligible())
    }

    @Test
    @Ignore("new visual design is now always enabled")
    fun `when user is new and experiment is disabled then isEnabled returns false`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(10)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = null,
                cohorts = emptyList(),
            ),
        )

        assertFalse(testee.enrolUserInNewExperimentIfEligible())
    }

    @Test
    fun `when user is enrolled in new user experiment then getTabManagerPixelParams returns new user experiment params`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertEquals(VARIANT_1.cohortName, params["cohort"])
        assertEquals(fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().featureName().name, params["experiment"])
    }

    @Test
    fun `when user is enrolled in new user experiment but experiment is disabled then getTabManagerPixelParams returns empty map`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = State.Cohort(name = VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertTrue(params.isEmpty())
    }

    @Test
    fun `when user is enrolled in existing user experiment then getTabManagerPixelParams returns existing user experiment params`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = VARIANT_2.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertEquals(VARIANT_2.cohortName, params["cohort"])
        assertEquals(fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().featureName().name, params["experiment"])
    }

    @Test
    fun `when user is enrolled in existing user experiment but experiment is disabled then getTabManagerPixelParams returns empty map`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = State.Cohort(name = VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        val params = testee.getTabManagerPixelParams()

        assertTrue(params.isEmpty())
    }

    @Test
    fun `when user is not enrolled in any experiment then getTabManagerPixelParams returns empty map`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(30)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = null,
                cohorts = emptyList(),
            ),
        )
        fakeSenseOfProtectionToggles.senseOfProtectionExistingUserExperiment27May25().setRawStoredState(
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

    @Test
    fun `when user is enrolled in modified control variant then we can detect it`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.isUserEnrolledInModifiedControlCohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInVariant1CohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInVariant2CohortAndExperimentEnabled())

        assertTrue(testee.isUserEnrolledInAVariantAndExperimentEnabled())
    }

    @Test
    fun `when user is enrolled in variant 1 then other variants are not enabled`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.isUserEnrolledInVariant1CohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInModifiedControlCohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInVariant2CohortAndExperimentEnabled())

        assertTrue(testee.isUserEnrolledInAVariantAndExperimentEnabled())
    }

    @Test
    fun `when user is enrolled in variant 2 then other variants are not enabled`() = runTest {
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_2.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.isUserEnrolledInVariant2CohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInVariant1CohortAndExperimentEnabled())
        assertFalse(testee.isUserEnrolledInModifiedControlCohortAndExperimentEnabled())

        assertTrue(testee.isUserEnrolledInAVariantAndExperimentEnabled())
    }

    @Test
    fun `when user is enrolled in modified control then legacy privacy shield is shown`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(MODIFIED_CONTROL.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertFalse(testee.shouldShowNewPrivacyShield())
    }

    @Test
    fun `when user is not enrolled in any variant`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = cohorts,
            ),
        )

        assertFalse(testee.shouldShowNewPrivacyShield())
    }

    @Test
    fun `when user is enrolled in variant 1 then new privacy shield is shown`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_1.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.shouldShowNewPrivacyShield())
    }

    @Test
    fun `when user is enrolled in variant 2 then new privacy shield is shown`() = runTest {
        fakeUserBrowserProperties.setDaysSinceInstalled(20)
        fakeSenseOfProtectionToggles.senseOfProtectionNewUserExperiment27May25().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(VARIANT_2.cohortName, weight = 1),
                cohorts = cohorts,
            ),
        )

        assertTrue(testee.shouldShowNewPrivacyShield())
    }
}

class FakeUserBrowserProperties : UserBrowserProperties {

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
