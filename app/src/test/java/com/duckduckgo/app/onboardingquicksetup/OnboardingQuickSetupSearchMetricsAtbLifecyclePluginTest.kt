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

package com.duckduckgo.app.onboardingquicksetup

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class OnboardingQuickSetupSearchMetricsAtbLifecyclePluginTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val toggles = FakeFeatureToggleFactory.create(OnboardingQuickSetupToggles::class.java)
    private lateinit var plugin: OnboardingQuickSetupSearchMetricsAtbLifecyclePlugin

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        plugin = OnboardingQuickSetupSearchMetricsAtbLifecyclePlugin(
            toggles = toggles,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when toggle is enabled and enrolled then sends one metric per tracked search count`() = runTest {
        enableAndEnroll()

        plugin.onSearchRetentionAtbRefreshed("", "")

        val values = fakeMetricsPixelExtension.sentMetrics.map { it.value }.toSet()
        assertEquals(setOf("1", "2", "3", "4", "5", "7", "8", "9", "10"), values)
    }

    @Test
    fun `when toggle is enabled and enrolled then all sent metrics are search COUNT_WHEN_IN_WINDOW pixels`() = runTest {
        enableAndEnroll()

        plugin.onSearchRetentionAtbRefreshed("", "")

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertTrue(sent.isNotEmpty())
        assertTrue(sent.all { it.metric == "search" })
        assertTrue(sent.all { it.type == MetricType.COUNT_WHEN_IN_WINDOW })
    }

    @Test
    fun `when toggle is enabled and enrolled then first-search metric uses D1 to D3 single window`() = runTest {
        enableAndEnroll()

        plugin.onSearchRetentionAtbRefreshed("", "")

        val firstSearchMetric = fakeMetricsPixelExtension.sentMetrics.single { it.value == "1" }

        assertEquals(listOf(ConversionWindow(lowerWindow = 1, upperWindow = 3)), firstSearchMetric.conversionWindow)
    }

    @Test
    fun `when toggle is enabled and enrolled then values 2 through 10 use D0 to D7 daily windows`() = runTest {
        enableAndEnroll()

        plugin.onSearchRetentionAtbRefreshed("", "")

        val expectedDailyWindows = (0..7).map { ConversionWindow(lowerWindow = it, upperWindow = it) }

        fakeMetricsPixelExtension.sentMetrics
            .filter { it.value != "1" }
            .forEach { assertEquals(expectedDailyWindows, it.conversionWindow) }
    }

    @Test
    fun `when toggle is enabled and enrolled then all metrics are bound to the quick-setup experiment toggle`() = runTest {
        enableAndEnroll()

        plugin.onSearchRetentionAtbRefreshed("", "")

        val expectedName = toggles.onboardingQuickSetupExperimentMay26().featureName().name
        assertTrue(fakeMetricsPixelExtension.sentMetrics.all { it.toggle.featureName().name == expectedName })
    }

    @Test
    fun `when toggle is disabled then no metrics are sent`() = runTest {
        disabledButEnrolled()

        plugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `when toggle is enabled but not enrolled then no metrics are sent`() = runTest {
        enabledButNotEnrolled()

        plugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    @Test
    fun `when app retention atb refreshed then no metrics are sent`() = runTest {
        enableAndEnroll()

        plugin.onAppRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    private fun enableAndEnroll() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val cohort = State.Cohort(name = "treatment", weight = 1, enrollmentDateET = today)
        toggles.onboardingQuickSetupExperimentMay26().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(cohort),
                assignedCohort = cohort,
            ),
        )
    }

    private fun disabledButEnrolled() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val cohort = State.Cohort(name = "treatment", weight = 1, enrollmentDateET = today)
        toggles.onboardingQuickSetupExperimentMay26().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                cohorts = listOf(cohort),
                assignedCohort = cohort,
            ),
        )
    }

    private fun enabledButNotEnrolled() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val cohort = State.Cohort(name = "treatment", weight = 1, enrollmentDateET = today)
        toggles.onboardingQuickSetupExperimentMay26().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(cohort),
                assignedCohort = null,
            ),
        )
    }
}
