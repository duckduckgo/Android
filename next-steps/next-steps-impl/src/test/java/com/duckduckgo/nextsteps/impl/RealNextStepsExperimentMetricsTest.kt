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

import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealNextStepsExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val experimentToggle: Toggle = mock()
    private val toggles: NextStepsItemsExperimentToggles = mock {
        whenever(it.nextStepsItemsExperiment()).thenReturn(experimentToggle)
    }
    private val testee = RealNextStepsExperimentMetrics(toggles)

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
    }

    @Test
    fun `section shown sends the section shown metric`() = runTest {
        testee.onSectionShown()

        assertSentMetrics("next_steps_section_shown")
    }

    @Test
    fun `item shown sends the shown metric for that item`() = runTest {
        testee.onItemShown("setup_sync")

        assertSentMetrics("next_steps_item_shown_setup_sync")
    }

    @Test
    fun `item cta click sends the click metric for that item`() = runTest {
        testee.onItemCtaClicked("setup_sync")

        assertSentMetrics("next_steps_item_cta_clicked_setup_sync")
    }

    @Test
    fun `default browser cta click also sends the combined set as default metric`() = runTest {
        testee.onItemCtaClicked("setup_default_browser")

        assertSentMetrics("next_steps_item_cta_clicked_setup_default_browser", "set_as_default_clicked")
    }

    @Test
    fun `item completed sends the completed metric for that item`() = runTest {
        testee.onItemCompleted("setup_add_widget")

        assertSentMetrics("next_steps_item_completed_setup_add_widget")
    }

    @Test
    fun `default browser completion also sends the combined default browser set metric`() = runTest {
        testee.onItemCompleted("setup_default_browser")

        assertSentMetrics("next_steps_item_completed_setup_default_browser", "default_browser_set")
    }

    private fun assertSentMetrics(vararg metrics: String) {
        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(metrics.toList(), sent.map { it.metric })
        sent.forEach {
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
            assertEquals(listOf(ConversionWindow(0, 0), ConversionWindow(0, 7)), it.conversionWindow)
            assertEquals(experimentToggle, it.toggle)
        }
    }
}
