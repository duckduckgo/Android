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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingCompletionSummaryRow
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.Companion.PIR_ROW_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionOnboardingProgressTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val stepStore: SubscriptionOnboardingStepStore = mock()
    private val subscriptions: Subscriptions = mock()
    private val pirFeature: PirFeature = mock()

    @Test
    fun whenAllShownStepsCompletedAndNoPirThenPercentageIsOneHundred() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        val progress = createProgress(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        assertEquals(100, progress.completionPercentage())
    }

    @Test
    fun whenNothingCompletedThenPercentageIsZero() = runTest {
        val progress = createProgress(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        assertEquals(0, progress.completionPercentage())
    }

    @Test
    fun whenStepHasNoSummaryRowThenItIsNotCounted() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        val progress = createProgress(
            plugins = listOf(
                fakePlugin("vpn"),
                fakePlugin("welcome", summaryRow = null),
            ),
        )

        assertEquals(100, progress.completionPercentage())
    }

    @Test
    fun whenStepShouldNotShowThenItIsNotCounted() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        val progress = createProgress(
            plugins = listOf(
                fakePlugin("vpn"),
                fakePlugin("duck_ai", shouldShow = false),
            ),
        )

        assertEquals(100, progress.completionPercentage())
    }

    @Test
    fun whenPirEntitledAndEligibleThenPirCountsTowardsPercentage() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        whenever(stepStore.isCompleted("duck_ai")).thenReturn(true)
        whenever(stepStore.isCompleted(PIR_ROW_ID)).thenReturn(false)
        whenever(pirFeature.getPirFeatureState()).thenReturn(PirFeatureState.ENABLED)
        val progress = createProgress(
            plugins = listOf(fakePlugin("vpn"), fakePlugin("itr"), fakePlugin("duck_ai")),
            entitlements = listOf(Product.PIR),
        )

        assertEquals(75, progress.completionPercentage())
    }

    @Test
    fun whenPirEntitledButNotEligibleThenPirNotCounted() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(pirFeature.getPirFeatureState()).thenReturn(PirFeatureState.DISABLED)
        val progress = createProgress(plugins = listOf(fakePlugin("vpn")), entitlements = listOf(Product.PIR))

        assertEquals(100, progress.completionPercentage())
    }

    @Test
    fun whenPirEligibleButNotEntitledThenPirNotCounted() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(pirFeature.getPirFeatureState()).thenReturn(PirFeatureState.ENABLED)
        val progress = createProgress(plugins = listOf(fakePlugin("vpn")))

        assertEquals(100, progress.completionPercentage())
    }

    @Test
    fun whenNoStepsThenPercentageIsZero() = runTest {
        val progress = createProgress(plugins = emptyList())

        assertEquals(0, progress.completionPercentage())
    }

    private fun createProgress(
        plugins: List<SubscriptionOnboardingStepPlugin> = emptyList(),
        entitlements: List<Product> = emptyList(),
    ): SubscriptionOnboardingProgress {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(entitlements))
        return SubscriptionOnboardingProgress(
            stepPlugins = object : PluginPoint<SubscriptionOnboardingStepPlugin> {
                override fun getPlugins(): Collection<SubscriptionOnboardingStepPlugin> = plugins
            },
            stepStore = stepStore,
            subscriptions = subscriptions,
            pirFeature = pirFeature,
        )
    }

    private fun fakePlugin(
        id: String,
        shouldShow: Boolean = true,
        summaryRow: SubscriptionOnboardingCompletionSummaryRow? = SubscriptionOnboardingCompletionSummaryRow(
            labelResId = R.string.subscriptionOnboardingFeature2Title,
            pendingIconResId = R.drawable.identity_theft_restoration_grayscale_color_24,
        ),
    ): SubscriptionOnboardingStepPlugin = object : SubscriptionOnboardingStepPlugin {
        override val stepId: String = id
        override val completionSummaryRow: SubscriptionOnboardingCompletionSummaryRow? = summaryRow
        override suspend fun shouldShow(): Boolean = shouldShow
        override fun createFragment(): Fragment = Fragment()
    }
}
