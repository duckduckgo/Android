package com.duckduckgo.duckchat.impl.subscription

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.subscription.DuckAiPlusSettingsViewModel.Command.OpenDuckAiPlusSettings
import com.duckduckgo.duckchat.impl.subscription.DuckAiPlusSettingsViewModel.ViewState.SettingState
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class DuckAiPlusSettingsViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()

    private val viewModel: DuckAiPlusSettingsViewModel by lazy {
        DuckAiPlusSettingsViewModel(
            subscriptions = subscriptions,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when onDuckAiPlusClicked then OpenDuckAiSettings command is sent`() = runTest {
        viewModel.commands().test {
            viewModel.onDuckAiClicked()
            assertEquals(OpenDuckAiPlusSettings, awaitItem())
        }
    }

    @Test
    fun `when subscription state is unknown then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is inactive and no DuckAiPlus product available then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is inactive and DuckAiPlus product available then SettingState is Disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.DuckAiPlus))

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Disabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is expired and no DuckAiPlus product available then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is expired and DuckAiPlus product available then SettingState is Disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.DuckAiPlus))

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Disabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is waiting and no DuckAiPlus product available then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(emptySet())

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is waiting and DuckAiPlus product available then SettingState is Disabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)
        whenever(subscriptions.getAvailableProducts()).thenReturn(setOf(Product.DuckAiPlus))

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Disabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is auto renewable and DuckAiPlus entitled then SettingState is Enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.DuckAiPlus)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Enabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is auto renewable and not entitled then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is not auto renewable and DuckAiPlus entitled then SettingState is Enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.DuckAiPlus)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.NOT_AUTO_RENEWABLE)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Enabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is not auto renewable and not entitled then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.NOT_AUTO_RENEWABLE)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is grace period and DuckAiPlus entitled then SettingState is Enabled`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.DuckAiPlus)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.GRACE_PERIOD)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Enabled, expectMostRecentItem().settingState)
        }
    }

    @Test
    fun `when subscription state is grace period and not entitled then SettingState is Hidden`() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.GRACE_PERIOD)

        viewModel.onCreate(mock())

        viewModel.viewState.test {
            assertEquals(SettingState.Hidden, expectMostRecentItem().settingState)
        }
    }
}
