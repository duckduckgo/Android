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
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.CloseOnboarding
import com.duckduckgo.subscriptions.impl.ui.onboarding.SubscriptionOnboardingViewModel.Command.ShowStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionOnboardingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val stepPlugins: ActivePluginPoint<SubscriptionOnboardingStepPlugin> = mock()

    private fun viewModel() = SubscriptionOnboardingViewModel(stepPlugins)

    @Test
    fun whenStepsExistThenEachCompletionShowsNextStepInOrder() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(listOf(fakeStep("first"), fakeStep("second")))
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.onStepCompleted()
            assertEquals("first", (awaitItem() as ShowStep).pluginName)

            viewModel.onStepCompleted()
            assertEquals("second", (awaitItem() as ShowStep).pluginName)

            viewModel.onStepCompleted()
            assertTrue(awaitItem() is CloseOnboarding)
        }
    }

    @Test
    fun whenNoStepsThenFirstCompletionClosesOnboarding() = runTest {
        whenever(stepPlugins.getPlugins()).thenReturn(emptyList())
        val viewModel = viewModel()

        viewModel.commands().test {
            viewModel.onStepCompleted()
            assertTrue(awaitItem() is CloseOnboarding)
        }
    }

    private fun fakeStep(name: String) = object : SubscriptionOnboardingStepPlugin {
        override val name: String = name
        override val toolbarTitle: Int = 0
        override fun getOnboardingStepView(
            context: Context,
            navigator: SubscriptionOnboardingStepNavigator,
        ): View = throw UnsupportedOperationException()
    }
}
