package com.duckduckgo.subscriptions.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.Account
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.AddEmail
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.ManageEmail
import kotlinx.coroutines.flow.MutableSharedFlow
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
        whenever(subscriptionsManager.getSubscription()).thenReturn(null)
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is Error)
        }
    }

    @Test
    fun whenUseEmailIEmailBlankThenEmitAddEmail() = runTest {
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "", externalId = "externalId"),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is AddEmail)
        }
    }

    @Test
    fun whenUseEmailIEmailNullThenEmitAddEmail() = runTest {
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = null, externalId = "externalId"),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is AddEmail)
        }
    }

    @Test
    fun whenUseEmailIfEmailThenEmitManage() = runTest {
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "email@email.com", externalId = "externalId"),
        )
        viewModel.commands().test {
            viewModel.useEmail()
            assertTrue(awaitItem() is ManageEmail)
        }
    }

    @Test
    fun whenOnCreateIfEmailExistsThenEmitIt() = runTest {
        whenever(subscriptionsManager.getAccount()).thenReturn(
            Account(email = "email@email.com", externalId = "externalId"),
        )

        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)
        viewModel.viewState.test {
            assertEquals("email@email.com", awaitItem().email)
        }
    }

    @Test
    fun whenOnCreateIfEmailBlankThenEmitIt() = runTest {
        val flowTest: MutableSharedFlow<SubscriptionStatus> = MutableSharedFlow()
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(flowTest)

        viewModel.onCreate(mock())
        flowTest.emit(AUTO_RENEWABLE)

        viewModel.viewState.test {
            assertNull(awaitItem().email)
        }
    }

    @Test
    fun whenEnterEmailClickedThenPixelIsSent() = runTest {
        viewModel.useEmail()
        verify(pixelSender).reportAddDeviceEnterEmailClick()
    }
}
