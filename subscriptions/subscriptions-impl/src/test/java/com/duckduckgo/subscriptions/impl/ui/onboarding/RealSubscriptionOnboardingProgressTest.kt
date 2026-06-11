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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import android.content.Context
import android.view.View
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.INTRO
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.STEP
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.SUMMARY
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealSubscriptionOnboardingProgressTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin> = mock()
    private val dataStore: SubscriptionOnboardingDataStore = mock()

    private fun progress() = RealSubscriptionOnboardingProgress(
        stepPlugins = stepPlugins,
        dataStore = dataStore,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun totalCountsOnlyStepTypeAndPercentIsDerived() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(
            listOf(fakeStep("intro", INTRO), fakeStep("step1"), fakeStep("step2"), fakeStep("summary", SUMMARY)),
        )
        whenever(dataStore.completedSteps).thenReturn(setOf("step1"))
        val progress = progress()

        assertEquals(2, progress.totalStepCount())
        assertEquals(1, progress.completedStepCount())
        assertEquals(50, progress.completionPercent())
    }

    @Test
    fun completedCountIgnoresStepsThatAreNoLongerRegistered() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(listOf(fakeStep("step1")))
        whenever(dataStore.completedSteps).thenReturn(setOf("step1", "removedStep"))
        val progress = progress()

        assertEquals(1, progress.completedStepCount())
        assertEquals(100, progress.completionPercent())
    }

    @Test
    fun whenNoStepsThenPercentIsZero() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(emptyList())
        whenever(dataStore.completedSteps).thenReturn(emptySet())
        val progress = progress()

        assertEquals(0, progress.totalStepCount())
        assertEquals(0, progress.completionPercent())
    }

    private fun fakeStep(
        name: String,
        type: SubscriptionOnboardingStepType = STEP,
    ) = object : SubscriptionOnboardingStepPlugin {
        override val name: String = name
        override val toolbarTitle: Int = 0
        override val stepType: SubscriptionOnboardingStepType = type
        override fun getOnboardingStepView(
            context: Context,
            navigator: SubscriptionOnboardingStepNavigator,
        ): View = throw UnsupportedOperationException()
    }
}
