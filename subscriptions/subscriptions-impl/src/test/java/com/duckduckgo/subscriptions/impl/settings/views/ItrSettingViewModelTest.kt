package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product.ITR
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.Command.OpenItr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ItrSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var viewModel: ItrSettingViewModel

    @Before
    fun before() {
        viewModel = ItrSettingViewModel(subscriptions, coroutineTestRule.testDispatcherProvider, pixelSender)
    }

    @Test
    fun whenOnItrThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onItr()
            assertTrue(awaitItem() is OpenItr)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnItrThenPixelSent() = runTest {
        viewModel.onItr()
        verify(pixelSender).reportAppSettingsIdtrClick()
    }

    @Test
    fun whenOnResumeIfSubscriptionEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(
            flowOf(
                listOf(ITR),
            ),
        )

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertTrue(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeIfNotSubscriptionEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }
}
