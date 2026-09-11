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

import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SegmentedOnboardingSearchRetentionAtbPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val metrics: SegmentedOnboardingExperimentMetrics = mock()

    @Test
    fun `when no download reason stored then no retention metrics are fired`() = runTest {
        val testee = testee(reason = null)

        testee.onSearchRetentionAtbRefreshed("v1-1", "v1-2")

        verify(metrics, never()).fireDownloadReasonSearchRetentionMetrics(any())
    }

    @Test
    fun `when a download reason is stored then retention metrics are fired for it`() = runTest {
        val testee = testee(reason = DownloadReasonSelection.BLOCK_ADS)

        testee.onSearchRetentionAtbRefreshed("v1-1", "v1-2")

        verify(metrics).fireDownloadReasonSearchRetentionMetrics(DownloadReasonSelection.BLOCK_ADS)
    }

    @Test
    fun `when a duck ai prompt refreshes retention then retention metrics are fired for the stored reason`() = runTest {
        val testee = testee(reason = DownloadReasonSelection.AI_CHAT)

        testee.onDuckAiRetentionAtbRefreshed("v1-1", "v1-2", emptyMap())

        verify(metrics).fireDownloadReasonSearchRetentionMetrics(DownloadReasonSelection.AI_CHAT)
    }

    private fun testee(reason: DownloadReasonSelection?) = SegmentedOnboardingSearchRetentionAtbPlugin(
        onboardingStore = mock<OnboardingStore> { on { getDownloadReason() } doReturn reason },
        segmentedOnboardingExperimentMetrics = metrics,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )
}
