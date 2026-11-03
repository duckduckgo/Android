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

package com.duckduckgo.subscriptions.impl.onboarding.welcome

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.onboarding.welcome.SubscriptionOnboardingWelcomeStepPlugin.Companion.WELCOME_STEP_ID
import com.duckduckgo.subscriptions.impl.onboarding.welcome.SubscriptionOnboardingWelcomeViewModel.Command.LaunchConfetti
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SubscriptionOnboardingWelcomeViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val controller: SubscriptionOnboardingController = mock()
    private val onboardingStore: SubscriptionOnboardingStore = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()

    private fun viewModelStartingOn(
        date: LocalDate,
        isFreeTrial: Boolean = true,
    ): SubscriptionOnboardingWelcomeViewModel {
        runBlocking {
            whenever(subscriptionsManager.getSubscription()).thenReturn(subscription(isFreeTrial))
        }
        val timeProvider = object : CurrentTimeProvider {
            override fun elapsedRealtime(): Long = 0
            override fun currentTimeMillis(): Long = 0
            override fun localDateTimeNow(): LocalDateTime = date.atStartOfDay()
        }
        return SubscriptionOnboardingWelcomeViewModel(
            controller,
            timeProvider,
            onboardingStore,
            subscriptionsManager,
            coroutineRule.testDispatcherProvider,
        )
    }

    private fun subscription(isFreeTrial: Boolean) = Subscription(
        productId = "test-plan",
        billingPeriod = "Monthly",
        startedAt = 0L,
        expiresOrRenewsAt = 0L,
        status = AUTO_RENEWABLE,
        platform = "android",
        activeOffers = if (isFreeTrial) listOf(ActiveOfferType.TRIAL) else emptyList(),
    )

    @Test
    fun whenFreeTrialThenViewStateIsFreeTrialWithBannerData() {
        val state = viewModelStartingOn(LocalDate.of(2026, 5, 7), isFreeTrial = true).viewState.value

        assertTrue(state.isFreeTrial)
        assertEquals(7, state.freeTrialDayLabels.size)
    }

    @Test
    fun whenNotFreeTrialThenViewStateIsNotFreeTrialWithNoBannerData() {
        val state = viewModelStartingOn(LocalDate.of(2026, 5, 7), isFreeTrial = false).viewState.value

        assertFalse(state.isFreeTrial)
        assertEquals("", state.formattedBillingDate)
        assertTrue(state.freeTrialDayLabels.isEmpty())
    }

    @Test
    fun whenMidMonthThenDayLabelsAreSevenConsecutiveDays() {
        val state = viewModelStartingOn(LocalDate.of(2026, 5, 7)).viewState.value

        assertEquals(listOf("7", "8", "9", "10", "11", "12", "13"), state.freeTrialDayLabels)
    }

    @Test
    fun whenTrialCrossesMonthEndThenDayLabelsRollOver() {
        val state = viewModelStartingOn(LocalDate.of(2026, 5, 30)).viewState.value

        assertEquals(listOf("30", "31", "1", "2", "3", "4", "5"), state.freeTrialDayLabels)
    }

    @Test
    fun whenTrialCrossesNonLeapFebruaryThenDayLabelsRollOverAt28() {
        val state = viewModelStartingOn(LocalDate.of(2025, 2, 26)).viewState.value

        assertEquals(listOf("26", "27", "28", "1", "2", "3", "4"), state.freeTrialDayLabels)
    }

    @Test
    fun whenTrialCrossesLeapFebruaryThenDayLabelsInclude29() {
        val state = viewModelStartingOn(LocalDate.of(2024, 2, 26)).viewState.value

        assertEquals(listOf("26", "27", "28", "29", "1", "2", "3"), state.freeTrialDayLabels)
    }

    @Test
    fun whenTrialCrossesYearEndThenDayLabelsRollOver() {
        val state = viewModelStartingOn(LocalDate.of(2026, 12, 30)).viewState.value

        assertEquals(listOf("30", "31", "1", "2", "3", "4", "5"), state.freeTrialDayLabels)
    }

    @Test
    fun whenBuiltThenBillingDateIsSevenDaysAfterStart() {
        val date = LocalDate.of(2026, 5, 7)
        val expected = date.plusDays(7).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))

        assertEquals(expected, viewModelStartingOn(date).viewState.value.formattedBillingDate)
    }

    @Test
    fun whenPrimaryCtaClickedThenCompletesWelcomeStep() {
        viewModelStartingOn(LocalDate.of(2026, 5, 7)).onPrimaryCtaClicked()

        verify(controller).onStepFinished(WELCOME_STEP_ID, COMPLETED)
    }

    @Test
    fun whenScreenShownAndWelcomeStepNotCompletedThenLaunchesConfetti() = runTest {
        whenever(onboardingStore.isStepCompleted(WELCOME_STEP_ID)).thenReturn(false)
        val viewModel = viewModelStartingOn(LocalDate.of(2026, 5, 7))

        viewModel.commands.test {
            viewModel.onScreenShown()

            assertEquals(LaunchConfetti, awaitItem())
        }
    }

    @Test
    fun whenScreenShownAndWelcomeStepAlreadyCompletedThenDoesNotLaunchConfetti() = runTest {
        whenever(onboardingStore.isStepCompleted(WELCOME_STEP_ID)).thenReturn(true)
        val viewModel = viewModelStartingOn(LocalDate.of(2026, 5, 7))

        viewModel.commands.test {
            viewModel.onScreenShown()

            expectNoEvents()
        }
    }

    @Test
    fun whenScreenShownAgainThenDoesNotLaunchConfettiTwice() = runTest {
        whenever(onboardingStore.isStepCompleted(WELCOME_STEP_ID)).thenReturn(false)
        val viewModel = viewModelStartingOn(LocalDate.of(2026, 5, 7))

        viewModel.commands.test {
            viewModel.onScreenShown()
            assertEquals(LaunchConfetti, awaitItem())

            viewModel.onScreenShown()

            expectNoEvents()
        }
    }
}
