package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsData
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.AddEmail
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.ManageEmail
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddDeviceViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var viewModel: AddDeviceViewModel

    @Before
    fun before() {
        viewModel = AddDeviceViewModel(subscriptionsManager, coroutineTestRule.testDispatcherProvider, pixelSender)
    }

    @Test
    fun whenUseEmailIfNoDataThenEmitError() = runTest {
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is Error)
        }
    }

    @Test
    fun whenUseEmailIfFailureThenEmitError() = runTest {
        whenever(subscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Failure("error"),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is Error)
        }
    }

    @Test
    fun whenUseEmailIEmailBlankThenEmitAddEmail() = runTest {
        whenever(subscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Success(email = "", externalId = "test", entitlements = emptyList()),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is AddEmail)
        }
    }

    @Test
    fun whenUseEmailIEmailNullThenEmitAddEmail() = runTest {
        whenever(subscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Success(email = null, externalId = "test", entitlements = emptyList()),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is AddEmail)
        }
    }

    @Test
    fun whenUseEmailIfEmailThenEmitManage() = runTest {
        whenever(subscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Success(email = "email@email.com", externalId = "test", entitlements = emptyList()),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is ManageEmail)
        }
    }

    @Test
    fun whenOnResumeIfEmailExistsThenEmitIt() = runTest {
        whenever(subscriptionsManager.getSubscriptionData()).thenReturn(
            SubscriptionsData.Success(email = "email@email.com", externalId = "test", entitlements = emptyList()),
        )
        viewModel.viewState.test {
            viewModel.onResume(mock())
            assertNull(awaitItem().email)
            viewModel.onResume(mock())
            assertEquals("email@email.com", awaitItem().email)
        }
    }

    @Test
    fun whenOnResumeIfEmailBlankThenEmitIt() = runTest {
        viewModel.viewState.test {
            viewModel.onResume(mock())
            assertNull(awaitItem().email)
        }
    }

    @Test
    fun whenEnterEmailClickedThenPixelIsSent() = runTest {
        viewModel.useEmail()
        verify(pixelSender).reportAddDeviceEnterEmailClick()
    }
}
