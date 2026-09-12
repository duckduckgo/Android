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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PasswordImportExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val inventory: FeatureTogglesInventory = mock()
    private val experimentToggle: Toggle = mock {
        on { featureName() } doReturn FeatureName(parentName = "onboardingPasswordImport", name = "passwordImportExperimentAug25")
    }
    private lateinit var testee: PasswordImportExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        testee = PasswordImportExperimentMetricsImpl(inventory = inventory)
    }

    @Test
    fun whenImportStartedThenSendsStartedMetricForExperimentToggle() = runTest {
        givenExperimentEnrolled()

        testee.fireImportStartedMetric()

        assertSentMetric("password_import_started")
    }

    @Test
    fun whenImportSuccessThenSendsSuccessMetricForExperimentToggle() = runTest {
        givenExperimentEnrolled()

        testee.fireImportSuccessMetric()

        assertSentMetric("password_import_success")
    }

    @Test
    fun whenImportFailedThenSendsFailedMetricForExperimentToggle() = runTest {
        givenExperimentEnrolled()

        testee.fireImportFailedMetric()

        assertSentMetric("password_import_failed")
    }

    @Test
    fun whenImportCancelledThenSendsCancelledMetricForExperimentToggle() = runTest {
        givenExperimentEnrolled()

        testee.fireImportCancelledMetric()

        assertSentMetric("password_import_cancelled")
    }

    @Test
    fun whenExperimentToggleNotInInventoryThenNoMetricSent() = runTest {
        whenever(inventory.getAllTogglesForParent("onboardingPasswordImport")).thenReturn(emptyList())

        testee.fireImportStartedMetric()
        testee.fireImportSuccessMetric()
        testee.fireImportFailedMetric()
        testee.fireImportCancelledMetric()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    private suspend fun givenExperimentEnrolled() {
        whenever(inventory.getAllTogglesForParent("onboardingPasswordImport")).thenReturn(listOf(experimentToggle))
    }

    private fun assertSentMetric(metric: String) {
        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals(metric, sent.metric)
        assertEquals("1", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(
            listOf(
                ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ConversionWindow(lowerWindow = 0, upperWindow = 14),
            ),
            sent.conversionWindow,
        )
        assertEquals(experimentToggle, sent.toggle)
    }
}
