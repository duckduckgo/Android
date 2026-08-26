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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getDisplayableCountry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SubscriptionOnboardingVpnViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun whenConnectionInfoLoadsThenViewStateShowsIpAndFlagFormattedLocation() = runTest {
        val testee = SubscriptionOnboardingVpnViewModel(
            FakeConnectionService(ConnectionInfo(ip = "137.220.87.36", city = "Birmingham", country = "GB")),
            coroutineRule.testDispatcherProvider,
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals("137.220.87.36", state.ipAddress)
            assertEquals("🇬🇧 Birmingham, ${getDisplayableCountry("GB")}", state.location)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenConnectionInfoFailsThenViewStateShowsUnknownPlaceholders() = runTest {
        val testee = SubscriptionOnboardingVpnViewModel(
            FakeConnectionService(info = null),
            coroutineRule.testDispatcherProvider,
        )

        testee.viewState().test {
            val state = awaitItem()
            assertEquals("XXX.XXX.XX.XXX", state.ipAddress)
            assertEquals("XX, XX", state.location)
            cancelAndConsumeRemainingEvents()
        }
    }

    private class FakeConnectionService(
        private val info: ConnectionInfo?,
    ) : SubscriptionOnboardingConnectionService {
        override suspend fun getConnectionInfo(): ConnectionInfo = info ?: throw RuntimeException("unavailable")
    }
}
