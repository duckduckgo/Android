package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command.OpenPir
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
class PirSettingViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var viewModel: PirSettingViewModel

    @Before
    fun before() {
        viewModel = PirSettingViewModel(subscriptions, coroutineTestRule.testDispatcherProvider, pixelSender)
    }

    @Test
    fun whenOnPirThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onPir()
            assertTrue(awaitItem() is OpenPir)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnPirThenPixelSent() = runTest {
        viewModel.onPir()
        verify(pixelSender).reportAppSettingsPirClick()
    }

    @Test
    fun whenOnResumeIfEntitlementPresentEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(PIR)))

        viewModel.onResume(mock())

        viewModel.viewState.test {
            assertTrue(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnResumeIfEntitlementNotPresentEmitViewState() = runTest {
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))

        viewModel.onResume(mock())
        viewModel.viewState.test {
            assertFalse(awaitItem().hasSubscription)
            cancelAndConsumeRemainingEvents()
        }
    }
}
