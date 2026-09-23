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

package com.duckduckgo.subscriptions.impl.onboarding.completion

import androidx.fragment.app.Fragment
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.pir.api.dashboard.PirFeatureState
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingCompletionSummaryRow
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingHandoffState
import com.duckduckgo.subscriptions.impl.onboarding.completion.SubscriptionOnboardingCompletionViewModel.Companion.PIR_ROW_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionOnboardingCompletionViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val controller: SubscriptionOnboardingController = mock()
    private val stepStore: SubscriptionOnboardingStepStore = mock()
    private val subscriptions: Subscriptions = mock()
    private val pirFeature: PirFeature = mock()

    @Test
    fun whenStepsHaveSummaryEntriesThenOnlyThoseBecomeRows() = runTest {
        val testee = createViewModel(
            plugins = listOf(
                fakePlugin("welcome", summaryEntry = null),
                fakePlugin("vpn"),
                fakePlugin("itr"),
            ),
        )

        testee.viewState().test {
            val rows = awaitItem().rows
            assertEquals(listOf("vpn", "itr"), rows.map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStepShouldNotShowThenItIsNotARow() = runTest {
        val testee = createViewModel(
            plugins = listOf(
                fakePlugin("vpn"),
                fakePlugin("duck_ai", shouldShow = false),
            ),
        )

        testee.viewState().test {
            assertEquals(listOf("vpn"), awaitItem().rows.map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAllShownStepsCompletedAndNoPirThenPercentageIsOneHundred() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        testee.viewState().test {
            assertEquals(100, awaitItem().completionPercentage)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPirEnabledThenPirRowIsAppendedIncompleteAndCountsTowardsPercentage() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        whenever(stepStore.isCompleted("duck_ai")).thenReturn(true)
        whenever(pirFeature.getPirFeatureState()).thenReturn(PirFeatureState.ENABLED)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr"), fakePlugin("duck_ai")))

        testee.viewState().test {
            val state = awaitItem()
            assertEquals(listOf("vpn", "itr", "duck_ai", PIR_ROW_ID), state.rows.map { it.id })
            val pirRow = state.rows.last()
            assertFalse(pirRow.completed)
            assertEquals(75, state.completionPercentage)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPirNotEnabledThenNoPirRow() = runTest {
        whenever(pirFeature.getPirFeatureState()).thenReturn(PirFeatureState.DISABLED)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn")))

        testee.viewState().test {
            assertEquals(listOf("vpn"), awaitItem().rows.map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNothingCompletedThenPercentageIsZero() = runTest {
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        testee.viewState().test {
            assertEquals(0, awaitItem().completionPercentage)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenHandoffThenViewStateIsHandoff() = runTest {
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn")), handoff = true)

        testee.viewState().test {
            assertTrue(awaitItem().handoff)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNotHandoffThenViewStateIsNotHandoff() = runTest {
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn")))

        testee.viewState().test {
            assertFalse(awaitItem().handoff)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenHundredPercentAndNotHandoffThenCelebratory() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        testee.viewState().test {
            assertTrue(awaitItem().celebratory)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenHundredPercentButHandoffThenNotCelebratory() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        whenever(stepStore.isCompleted("itr")).thenReturn(true)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")), handoff = true)

        testee.viewState().test {
            assertFalse(awaitItem().celebratory)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenLessThanHundredPercentThenNotCelebratory() = runTest {
        whenever(stepStore.isCompleted("vpn")).thenReturn(true)
        val testee = createViewModel(plugins = listOf(fakePlugin("vpn"), fakePlugin("itr")))

        testee.viewState().test {
            assertFalse(awaitItem().celebratory)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDoneClickedThenOnboardingExited() = runTest {
        createViewModel().onDoneClicked()

        verify(controller).exitOnboarding()
    }

    private fun createViewModel(
        plugins: List<SubscriptionOnboardingStepPlugin> = emptyList(),
        entitlements: List<Product> = emptyList(),
        handoff: Boolean = false,
    ): SubscriptionOnboardingCompletionViewModel {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(entitlements))
        return SubscriptionOnboardingCompletionViewModel(
            controller = controller,
            stepPlugins = object : PluginPoint<SubscriptionOnboardingStepPlugin> {
                override fun getPlugins(): Collection<SubscriptionOnboardingStepPlugin> = plugins
            },
            stepStore = stepStore,
            subscriptions = subscriptions,
            pirFeature = pirFeature,
            handoffState = SubscriptionOnboardingHandoffState().apply { isHandoff = handoff },
        )
    }

    private fun fakePlugin(
        id: String,
        shouldShow: Boolean = true,
        summaryEntry: SubscriptionOnboardingCompletionSummaryRow? = SubscriptionOnboardingCompletionSummaryRow(
            labelResId = R.string.subscriptionOnboardingFeature2Title,
            pendingIconResId = R.drawable.identity_theft_restoration_grayscale_color_24,
        ),
    ): SubscriptionOnboardingStepPlugin = object : SubscriptionOnboardingStepPlugin {
        override val stepId: String = id
        override val completionSummaryRow: SubscriptionOnboardingCompletionSummaryRow? = summaryEntry
        override suspend fun shouldShow(): Boolean = shouldShow
        override fun createFragment(): Fragment = Fragment()
    }
}
