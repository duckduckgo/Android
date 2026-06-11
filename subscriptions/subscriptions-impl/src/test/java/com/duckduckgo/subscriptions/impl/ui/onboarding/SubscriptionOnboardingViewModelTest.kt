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
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.INTRO
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.STEP
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepType.SUMMARY
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingDataStore
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.CloseOnboarding
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.ShowStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionOnboardingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin> = mock()
    private val dataStore: SubscriptionOnboardingDataStore = mock()

    private fun viewModel() = SubscriptionOnboardingViewModel(stepPlugins, dataStore)

    @Test
    fun whenPurchaseOriginThenShowsIntroStepsThenSummaryInOrder() = runTest {
        // returned out of order to prove partition orders INTRO -> STEP(priority) -> SUMMARY
        whenever(stepPlugins.getPlugins()).thenReturn(
            listOf(fakeStep("step1"), fakeStep("summary", SUMMARY), fakeStep("intro", INTRO), fakeStep("step2")),
        )
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.PURCHASE)
            assertEquals("intro", (awaitItem() as ShowStep).pluginName)

            viewModel.onNextStep()
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)

            viewModel.onStepCompleted()
            assertEquals("step2", (awaitItem() as ShowStep).pluginName)

            viewModel.onStepCompleted()
            assertEquals("summary", (awaitItem() as ShowStep).pluginName)

            viewModel.onNextStep()
            assertTrue(awaitItem() is CloseOnboarding)
        }
        verify(dataStore).markCompleted("step1")
        verify(dataStore).markCompleted("step2")
    }

    @Test
    fun whenSettingsOriginThenSkipsIntroAndResumesAtFirstIncompleteStep() = runTest {
        // step1 completed, step2 skipped (incomplete): reopening should land on step2, not the summary.
        whenever(dataStore.isCompleted("step1")).thenReturn(true)
        whenever(dataStore.isCompleted("step2")).thenReturn(false)
        whenever(stepPlugins.getPlugins()).thenReturn(
            listOf(fakeStep("intro", INTRO), fakeStep("step1"), fakeStep("step2"), fakeStep("summary", SUMMARY)),
        )
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.SETTINGS)
            assertEquals("step2", (awaitItem() as ShowStep).pluginName)
        }
    }

    @Test
    fun whenSettingsOriginAndNothingCompletedThenStartsAtFirstStep() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(
            listOf(fakeStep("intro", INTRO), fakeStep("step1"), fakeStep("summary", SUMMARY)),
        )
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.SETTINGS)
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)
        }
    }

    @Test
    fun whenSettingsOriginAndAllStepsCompletedThenShowsSummary() = runTest {
        whenever(dataStore.isCompleted("step1")).thenReturn(true)
        whenever(dataStore.isCompleted("step2")).thenReturn(true)
        whenever(stepPlugins.getPlugins()).thenReturn(
            listOf(fakeStep("intro", INTRO), fakeStep("step1"), fakeStep("step2"), fakeStep("summary", SUMMARY)),
        )
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.SETTINGS)
            assertEquals("summary", (awaitItem() as ShowStep).pluginName)
        }
    }

    @Test
    fun whenOnNextStepThenDoesNotPersistCompletion() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(listOf(fakeStep("step1"), fakeStep("step2")))
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.PURCHASE)
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)

            viewModel.onNextStep()
            assertEquals("step2", (awaitItem() as ShowStep).pluginName)
        }
        verify(dataStore, never()).markCompleted("step1")
    }

    @Test
    fun whenBackStepThenReturnsToPreviousIncludingCompletedStep() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(listOf(fakeStep("step1"), fakeStep("step2")))
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.PURCHASE)
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)

            viewModel.onStepCompleted()
            assertEquals("step2", (awaitItem() as ShowStep).pluginName)

            viewModel.onBackStep()
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)
        }
    }

    @Test
    fun whenBackStepAtFirstScreenThenCloses() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(listOf(fakeStep("step1")))
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.PURCHASE)
            assertEquals("step1", (awaitItem() as ShowStep).pluginName)

            viewModel.onBackStep()
            assertTrue(awaitItem() is CloseOnboarding)
        }
    }

    @Test
    fun whenNoScreensThenCloses() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(emptyList())
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.start(SubscriptionOnboardingOrigin.PURCHASE)
            assertTrue(awaitItem() is CloseOnboarding)
        }
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
