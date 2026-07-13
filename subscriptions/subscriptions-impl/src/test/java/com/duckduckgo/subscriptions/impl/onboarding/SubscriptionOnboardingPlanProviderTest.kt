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

package com.duckduckgo.subscriptions.impl.onboarding

import androidx.fragment.app.Fragment
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.GoBack
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.BackPressed
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingEvent.StepFinished
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingPlanProvider.Companion.SUBSCRIPTION_ONBOARDING_PLAN_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionOnboardingPlanProviderTest {

    private val stepStore = SubscriptionOnboardingStepStore(FakeSharedPreferencesProvider())

    @Test
    fun whenPlanBuiltThenIdSetAndOnlyShownPluginsBecomeStepsInOrder() = runTest {
        val plan = providerWith(
            stubPlugin("welcome"),
            stubPlugin("hidden", shouldShow = false),
            stubPlugin("vpn"),
        ).buildPlan()

        assertEquals(SUBSCRIPTION_ONBOARDING_PLAN_ID, plan.id)
        assertEquals(listOf("welcome", "vpn"), plan.steps.map { it.id })
    }

    @Test
    fun whenStepFinishedForCurrentStepThenAdvancesOtherwiseStays() = runTest {
        val step = providerWith(stubPlugin("welcome")).buildPlan().steps.single()

        assertEquals(Advance, step.transition(StepFinished("welcome", COMPLETED)))
        assertEquals(Stay, step.transition(StepFinished("vpn", COMPLETED)))
        assertEquals(Stay, step.transition(object : LinearOnboardingEvent {}))
    }

    @Test
    fun whenBackPressedThenGoesBack() = runTest {
        val step = providerWith(stubPlugin("welcome")).buildPlan().steps.single()

        assertEquals(GoBack, step.transition(BackPressed))
    }

    @Test
    fun whenStepAlreadyCompletedThenItsPreconditionIsFalse() = runTest {
        stepStore.setCompleted("welcome")

        val steps = providerWith(stubPlugin("welcome"), stubPlugin("vpn")).buildPlan().steps

        assertFalse(steps.first { it.id == "welcome" }.precondition())
        assertTrue(steps.first { it.id == "vpn" }.precondition())
    }

    private fun providerWith(vararg stepPlugins: SubscriptionOnboardingStepPlugin) =
        SubscriptionOnboardingPlanProvider(pluginPoint(stepPlugins.toList()), stepStore)

    private fun pluginPoint(stepPlugins: List<SubscriptionOnboardingStepPlugin>) =
        object : PluginPoint<SubscriptionOnboardingStepPlugin> {
            override fun getPlugins(): Collection<SubscriptionOnboardingStepPlugin> = stepPlugins
        }

    private fun stubPlugin(id: String, shouldShow: Boolean = true) =
        object : SubscriptionOnboardingStepPlugin {
            override val stepId: String = id
            override val titleResId: Int = 0
            override suspend fun shouldShow(): Boolean = shouldShow
            override fun createFragment(): Fragment = Fragment()
        }
}
