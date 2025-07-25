/*
 * Copyright (c) 2024 DuckDuckGo
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
import com.duckduckgo.pir.api.PirFeatureToggle
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Disabled
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled.Type
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Hidden
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirSettingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionPixelSender: SubscriptionPixelSender = mock()
    private val subscriptions: Subscriptions = mock()
    private val pirFeatureToggle: PirFeatureToggle = mock()
    private lateinit var pirSettingsViewModel: PirSettingViewModel

    @Before
    fun before() {
        pirSettingsViewModel = PirSettingViewModel(
            subscriptionPixelSender,
            subscriptions,
            pirFeatureToggle,
        )
    }

    @Test
    fun `when onPir then report app settings pixel sent`() = runTest {
        pirSettingsViewModel.onPir(Type.DESKTOP)
        verify(subscriptionPixelSender).reportAppSettingsPirClick()
    }

    @Test
    fun `when subscription state is unknown then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(UNKNOWN)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and no pir product available then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and pir product available then PirState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.PIR))

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is expired and no pir product available then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is expired and pir product available then PirState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.PIR))

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and no pir product available then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and pir product available then PirState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.PIR))

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and entitled then PirState is enabled and beta FF is false`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(false)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DESKTOP),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and entitled then PirState is enabled and beta FF is true`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(true)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DASHBOARD),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and not entitled then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and entitled then PirState is enabled and beta FF is false`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(false)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DESKTOP),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and entitled then PirState is enabled and beta FF is true`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(true)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DASHBOARD),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and not entitled then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and entitled then PirState is enabled and beta FF is false`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(false)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DESKTOP),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and entitled then PirState is enabled and beta FF is true`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)
        whenever(pirFeatureToggle.isPirBetaEnabled()).thenReturn(true)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Enabled(Type.DASHBOARD),
                expectMostRecentItem().pirState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and not entitled then PirState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        pirSettingsViewModel.onCreate(mock())

        pirSettingsViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().pirState,
            )
        }
    }
}
