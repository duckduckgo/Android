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

package com.duckduckgo.subscriptions.impl.settings

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
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
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState.ItrState.Disabled
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState.ItrState.Enabled
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState.ItrState.Hidden
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ItrSettingViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionPixelSender: SubscriptionPixelSender = mock()
    private val subscriptions: Subscriptions = mock()
    private lateinit var itrSettingViewModel: ItrSettingViewModel

    @Before
    fun before() {
        itrSettingViewModel = ItrSettingViewModel(
            subscriptions,
            subscriptionPixelSender,
        )
    }

    @Test
    fun `when onItr then report app settings pixel sent`() = runTest {
        itrSettingViewModel.onItr()
        verify(subscriptionPixelSender).reportAppSettingsIdtrClick()
    }

    @Test
    fun `when subscription state is unknown then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(UNKNOWN)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and no itr product available then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is inactive and row_itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ROW_ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is exitred and no itr product available then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is exitred and itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is exitred and row_itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ROW_ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and no itr product available then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is waiting and row_itr product available then ItrState is disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.ROW_ITR))

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Disabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and row_itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ROW_ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is auto renewable and not entitled then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and row_itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ROW_ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is not auto renewable and not entitled then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(NOT_AUTO_RENEWABLE)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and row_itr entitled then ItrState is enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.ROW_ITR)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Enabled,
                expectMostRecentItem().itrState,
            )
        }
    }

    @Test
    fun `when subscription state is grace period and not entitled then ItrState is hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(GRACE_PERIOD)

        itrSettingViewModel.onCreate(mock())

        itrSettingViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().itrState,
            )
        }
    }
}
