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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class SegmentedOnboardingExperimentManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val segmentedToggles: SegmentedOnboardingFeatureToggles =
        FakeFeatureToggleFactory.create(SegmentedOnboardingFeatureToggles::class.java)

    private val testee = SegmentedOnboardingExperimentManagerImpl(
        segmentedOnboardingFeatureToggles = segmentedToggles,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when no remote state is stored then segmented onboarding is enabled`() = runTest {
        assertTrue(testee.isEnabled())
    }

    @Test
    fun `when flag is disabled remotely then segmented onboarding is disabled`() = runTest {
        segmentedToggles.onboardingFlowByDownloadReason().setRawStoredState(Toggle.State(remoteEnableState = false))

        assertFalse(testee.isEnabled())
    }
}
