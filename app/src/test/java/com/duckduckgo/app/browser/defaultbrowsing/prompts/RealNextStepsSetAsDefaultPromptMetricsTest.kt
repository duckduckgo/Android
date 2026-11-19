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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.app.nextsteps.RealNextStepsExperimentMetricSender
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

class RealNextStepsSetAsDefaultPromptMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val inventory: FeatureTogglesInventory = mock()
    private val otherToggle: Toggle = mock {
        on { featureName() } doReturn FeatureName(parentName = "nextStepsItems", name = "self")
    }
    private val experimentToggle: Toggle = mock {
        on { featureName() } doReturn FeatureName(parentName = "nextStepsItems", name = "nextStepsItemsExperiment")
    }
    private val testee = RealNextStepsSetAsDefaultPromptMetrics(RealNextStepsExperimentMetricSender(inventory))

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
    }

    @Test
    fun `prompt confirmation sends the combined and the prompt set as default metrics`() = runTest {
        whenever(inventory.getAllTogglesForParent("nextStepsItems")).thenReturn(listOf(otherToggle, experimentToggle))

        testee.onPromptConfirmationClicked()

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(listOf("set_as_default_clicked", "set_as_default_clicked_prompt"), sent.map { it.metric })
        sent.forEach {
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
            assertEquals(listOf(ConversionWindow(0, 0), ConversionWindow(0, 7)), it.conversionWindow)
            assertEquals(experimentToggle, it.toggle)
        }
    }

    @Test
    fun `prompt shown sends the prompt shown metric`() = runTest {
        whenever(inventory.getAllTogglesForParent("nextStepsItems")).thenReturn(listOf(experimentToggle))

        testee.onPromptShown()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("set_as_default_prompt_shown", sent.metric)
        assertEquals(listOf(ConversionWindow(0, 0), ConversionWindow(0, 7)), sent.conversionWindow)
        assertEquals(experimentToggle, sent.toggle)
    }

    @Test
    fun `prompt conversion sends the combined and the prompt default browser set metrics`() = runTest {
        whenever(inventory.getAllTogglesForParent("nextStepsItems")).thenReturn(listOf(experimentToggle))

        testee.onPromptDefaultBrowserSet()

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(listOf("default_browser_set", "default_browser_set_prompt"), sent.map { it.metric })
        sent.forEach {
            assertEquals(listOf(ConversionWindow(0, 0), ConversionWindow(0, 7)), it.conversionWindow)
            assertEquals(experimentToggle, it.toggle)
        }
    }

    @Test
    fun `prompt confirmation sends nothing when the experiment is not in the inventory`() = runTest {
        whenever(inventory.getAllTogglesForParent("nextStepsItems")).thenReturn(emptyList())

        testee.onPromptShown()
        testee.onPromptConfirmationClicked()
        testee.onPromptDefaultBrowserSet()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }
}
