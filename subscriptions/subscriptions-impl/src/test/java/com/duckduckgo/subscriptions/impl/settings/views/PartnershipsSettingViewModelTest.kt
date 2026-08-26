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

package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.internal.PartnershipsHubUrlProvider
import com.duckduckgo.subscriptions.impl.settings.views.PartnershipsSettingViewModel.Command.OpenPartnershipsHub
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PartnershipsSettingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val urlProvider: PartnershipsHubUrlProvider = mock()
    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    private fun viewModel() = PartnershipsSettingViewModel(
        subscriptions = subscriptions,
        subscriptionsFeature = subscriptionsFeature,
        partnershipsHubUrlProvider = urlProvider,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun givenFeatureEnabledWhenSubscriptionActiveThenPartnershipsRowVisible() = runTest {
        subscriptionsFeature.partnershipsHub().setRawStoredState(State(true))

        activeStatuses().forEach { status ->
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(status))

            val testee = viewModel()
            testee.onCreate(mock())

            testee.viewState.test {
                assertTrue("expected visible for $status", expectMostRecentItem().isVisible)
            }
        }
    }

    @Test
    fun givenActiveSubscriptionWhenFeatureDisabledThenPartnershipsRowHidden() = runTest {
        subscriptionsFeature.partnershipsHub().setRawStoredState(State(false))
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(AUTO_RENEWABLE))

        val testee = viewModel()
        testee.onCreate(mock())

        testee.viewState.test {
            assertFalse(expectMostRecentItem().isVisible)
        }
    }

    @Test
    fun givenFeatureEnabledWhenSubscriptionNotActiveThenPartnershipsRowHidden() = runTest {
        subscriptionsFeature.partnershipsHub().setRawStoredState(State(true))

        inactiveStatuses().forEach { status ->
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(status))

            val testee = viewModel()
            testee.onCreate(mock())

            testee.viewState.test {
                assertFalse("expected hidden for $status", expectMostRecentItem().isVisible)
            }
        }
    }

    @Test
    fun givenFeatureEnabledWhenSubscriptionExpiresThenPartnershipsRowHidden() = runTest {
        subscriptionsFeature.partnershipsHub().setRawStoredState(State(true))
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(AUTO_RENEWABLE, EXPIRED))

        val testee = viewModel()
        testee.onCreate(mock())

        testee.viewState.test {
            assertFalse(expectMostRecentItem().isVisible)
        }
    }

    @Test
    fun whenOnPartnershipsHubClickedThenCommandSentWithHubUrl() = runTest {
        whenever(urlProvider.partnershipsHubUrl).thenReturn("https://duckduckgo.com/partner-benefits")

        val testee = viewModel()

        testee.commands().test {
            testee.onPartnershipsHubClicked()
            assertEquals(OpenPartnershipsHub("https://duckduckgo.com/partner-benefits"), awaitItem())
        }
    }

    private fun activeStatuses(): List<SubscriptionStatus> = listOf(AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD)

    private fun inactiveStatuses(): List<SubscriptionStatus> = listOf(UNKNOWN, INACTIVE, EXPIRED, WAITING)
}
