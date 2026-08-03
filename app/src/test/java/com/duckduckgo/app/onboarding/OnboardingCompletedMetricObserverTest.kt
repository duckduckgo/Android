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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class OnboardingCompletedMetricObserverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val appStageFlow = MutableSharedFlow<AppStage>(replay = 1)
    private val userStageStore: UserStageStore = mock { on { userAppStageFlow() } doReturn appStageFlow }
    private val metrics: OnboardingPromptsExperimentMetrics = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = OnboardingCompletedMetricObserver(
        appCoroutineScope = coroutineRule.testScope,
        userStageStore = userStageStore,
        onboardingPromptsExperimentMetrics = metrics,
    )

    @Test
    fun `when user app stage becomes established then onboarding completed metric is fired`() = runTest {
        testee.onCreate(lifecycleOwner)

        appStageFlow.emit(AppStage.ESTABLISHED)

        verify(metrics).fireOnboardingCompletedMetric()
    }

    @Test
    fun `when user app stage is not established then onboarding completed metric is not fired`() = runTest {
        testee.onCreate(lifecycleOwner)

        appStageFlow.emit(AppStage.NEW)
        appStageFlow.emit(AppStage.DAX_ONBOARDING)

        verify(metrics, never()).fireOnboardingCompletedMetric()
    }
}
