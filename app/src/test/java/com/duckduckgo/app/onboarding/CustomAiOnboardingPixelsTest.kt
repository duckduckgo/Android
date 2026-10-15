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

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.AppUserStageStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CustomAiOnboardingPixelsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val appStageFlow = MutableSharedFlow<AppStage>(replay = 1)
    private val appUserStageStore: AppUserStageStore = mock { on { userAppStageFlow() } doReturn appStageFlow }
    private val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    private val pixel: Pixel = mock()
    private val contextualOnboardingTimeLimit = FakeContextualOnboardingTimeLimit()

    @Before
    fun setup() = runTest {
        whenever(customAiOnboardingStore.isEnabled()).thenReturn(true)
        CustomAiOnboardingPixels(
            coroutineScope = coroutineRule.testScope,
            appUserStageStore = appUserStageStore,
            customAiOnboardingStore = customAiOnboardingStore,
            pixel = pixel,
            contextualOnboardingTimeLimit = contextualOnboardingTimeLimit,
        )
    }

    @Test
    fun whenCustomAiOnboardingCompletedThenPlanFinishedPixelFired() = runTest {
        appStageFlow.emit(AppStage.ESTABLISHED)

        verify(pixel).fire(eq(CustomAiOnboardingPixelName.PLAN_FINISHED), any(), any(), any())
    }

    @Test
    fun whenCustomAiOnboardingEndedByTimeLimitThenPlanFinishedPixelNotFired() = runTest {
        contextualOnboardingTimeLimit.onboardingEndedByTimeLimit = true

        appStageFlow.emit(AppStage.ESTABLISHED)

        verify(pixel, never()).fire(eq(CustomAiOnboardingPixelName.PLAN_FINISHED), any(), any(), any())
    }
}
